package com.elftech.pingwifis.data

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Scanner de rede profissional - Detecta todos os dispositivos ativos.
 * Inspirado em Fing e Network Analyzer.
 */
class NetworkScanner(private val scope: CoroutineScope) {
    private var activeScanJob: Job? = null

    companion object {
        private const val SCAN_TIMEOUT_MS = 500
        private val COMMON_PORTS = listOf(80, 443, 22, 445, 3389, 8080, 21, 23)
    }

    /**
     * Inicia scan completo da rede
     */
    fun startNetworkScan(
        networkPrefix: String, // Ex: "192.168.1"
        onProgress: (Int, NetworkDevice?) -> Unit, // (progresso%, device encontrado)
        onComplete: (List<NetworkDevice>) -> Unit,
        onError: (String) -> Unit
    ) {
        activeScanJob?.cancel()

        activeScanJob = scope.launch(Dispatchers.IO) {
            try {
                val devices = mutableListOf<NetworkDevice>()
                val totalHosts = 254

                // Primeiro, lê a tabela ARP para resultados instantâneos
                withContext(Dispatchers.Main) {
                    onProgress(0, null)
                }

                val arpDevices = getArpTableDevices(networkPrefix)
                devices.addAll(arpDevices)

                // Notifica dispositivos do ARP imediatamente
                arpDevices.forEach { device ->
                    withContext(Dispatchers.Main) {
                        onProgress(5, device)
                    }
                }

                delay(200)

                // Agora faz scan completo em paralelo (mais rápido)
                val jobs = mutableListOf<Job>()

                for (lastOctet in 1..254) {
                    if (!isActive) break

                    val job = launch {
                        val ip = "$networkPrefix.$lastOctet"
                        val device = scanSingleHost(ip)

                        if (device != null) {
                            synchronized(devices) {
                                // Evita duplicatas do ARP
                                if (!devices.any { it.ipAddress == device.ipAddress }) {
                                    devices.add(device)
                                }
                            }

                            withContext(Dispatchers.Main) {
                                val progress = ((lastOctet * 100) / totalHosts).coerceIn(0, 100)
                                onProgress(progress, device)
                            }
                        }
                    }

                    jobs.add(job)

                    // Limita concorrência para não sobrecarregar
                    if (jobs.size >= 50) {
                        jobs.joinAll()
                        jobs.clear()
                    }
                }

                // Aguarda jobs restantes
                jobs.joinAll()

                // Ordena por IP
                val sortedDevices = devices.sortedBy { device ->
                    device.ipAddress.split(".").last().toIntOrNull() ?: 999
                }

                withContext(Dispatchers.Main) {
                    onComplete(sortedDevices)
                }

            } catch (e: CancellationException) {
                Log.d("NetworkScanner", "Scan cancelado")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Erro no scan: ${e.message}")
                }
            }
        }
    }

    /**
     * Lê a tabela ARP do sistema (dispositivos já conhecidos)
     */
    private fun getArpTableDevices(networkPrefix: String): List<NetworkDevice> {
        val devices = mutableListOf<NetworkDevice>()

        try {
            val process = Runtime.getRuntime().exec("cat /proc/net/arp")
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String?
            reader.readLine() // Pula cabeçalho

            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.split(Regex("\\s+"))

                if (parts.size >= 6) {
                    val ip = parts[0]
                    val macAddress = parts[3]
                    val deviceInterface = parts[5]

                    // Verifica se está na rede desejada
                    if (ip.startsWith(networkPrefix) &&
                        macAddress != "00:00:00:00:00:00" &&
                        macAddress.contains(":")) {

                        val vendor = getVendorFromMac(macAddress)

                        devices.add(
                            NetworkDevice(
                                ipAddress = ip,
                                macAddress = macAddress,
                                hostname = getHostname(ip),
                                vendor = vendor,
                                isOnline = true,
                                openPorts = emptyList(),
                                responseTime = 0,
                                deviceType = guessDeviceType(macAddress, vendor)
                            )
                        )
                    }
                }
            }

            Log.d("NetworkScanner", "Found ${devices.size} devices in ARP table")

        } catch (e: Exception) {
            Log.w("NetworkScanner", "Failed to read ARP table: ${e.message}")
        }

        return devices
    }

    /**
     * Scanneia um único host
     */
    private suspend fun scanSingleHost(ip: String): NetworkDevice? {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()

                // Tenta InetAddress.isReachable primeiro (mais rápido)
                val addr = InetAddress.getByName(ip)
                val isReachable = addr.isReachable(SCAN_TIMEOUT_MS)

                if (isReachable) {
                    val responseTime = (System.currentTimeMillis() - startTime).toInt()
                    val hostname = try {
                        addr.canonicalHostName.takeIf { it != ip } ?: "Unknown"
                    } catch (e: Exception) {
                        "Unknown"
                    }

                    val openPorts = scanPorts(ip)
                    val macAddress = getMacFromArp(ip) ?: "Unknown"
                    val vendor = getVendorFromMac(macAddress)

                    return@withContext NetworkDevice(
                        ipAddress = ip,
                        macAddress = macAddress,
                        hostname = hostname,
                        vendor = vendor,
                        isOnline = true,
                        openPorts = openPorts,
                        responseTime = responseTime,
                        deviceType = guessDeviceType(macAddress, vendor, openPorts)
                    )
                }

                // Se isReachable falhou, tenta TCP
                val openPorts = scanPorts(ip)
                if (openPorts.isNotEmpty()) {
                    val responseTime = (System.currentTimeMillis() - startTime).toInt()
                    val macAddress = getMacFromArp(ip) ?: "Unknown"
                    val vendor = getVendorFromMac(macAddress)

                    return@withContext NetworkDevice(
                        ipAddress = ip,
                        macAddress = macAddress,
                        hostname = getHostname(ip),
                        vendor = vendor,
                        isOnline = true,
                        openPorts = openPorts,
                        responseTime = responseTime,
                        deviceType = guessDeviceType(macAddress, vendor, openPorts)
                    )
                }

                null
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Scanneia portas comuns
     */
    private fun scanPorts(ip: String): List<Int> {
        val openPorts = mutableListOf<Int>()

        for (port in COMMON_PORTS) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), SCAN_TIMEOUT_MS / 4)
                socket.close()
                openPorts.add(port)
            } catch (e: Exception) {
                // Porta fechada
            }
        }

        return openPorts
    }

    /**
     * Obtém MAC address da tabela ARP
     */
    private fun getMacFromArp(ip: String): String? {
        try {
            val process = Runtime.getRuntime().exec("cat /proc/net/arp")
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.contains(ip)) {
                    val parts = line!!.split(Regex("\\s+"))
                    if (parts.size >= 4) {
                        val mac = parts[3]
                        if (mac != "00:00:00:00:00:00" && mac.contains(":")) {
                            return mac
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignora
        }

        return null
    }

    /**
     * Obtém hostname
     */
    private fun getHostname(ip: String): String {
        return try {
            val addr = InetAddress.getByName(ip)
            addr.canonicalHostName.takeIf { it != ip } ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Identifica fabricante pelo MAC address (simplificado)
     */
    private fun getVendorFromMac(mac: String): String {
        if (mac == "Unknown") return "Unknown"

        val prefix = mac.substring(0, 8).uppercase()

        return when {
            prefix.startsWith("00:50:56") -> "VMware"
            prefix.startsWith("00:0C:29") -> "VMware"
            prefix.startsWith("08:00:27") -> "VirtualBox"
            prefix.startsWith("52:54:00") -> "QEMU/KVM"
            prefix.startsWith("00:1B:63") -> "Apple"
            prefix.startsWith("00:25:00") -> "Apple"
            prefix.startsWith("00:1C:B3") -> "Apple"
            prefix.startsWith("AC:DE:48") -> "Apple"
            prefix.startsWith("F0:18:98") -> "Apple"
            prefix.startsWith("28:CF:E9") -> "Apple"
            prefix.startsWith("00:17:F2") -> "Apple"
            prefix.startsWith("D8:30:62") -> "Apple"
            prefix.startsWith("00:23:6C") -> "Samsung"
            prefix.startsWith("00:1D:25") -> "Samsung"
            prefix.startsWith("E8:50:8B") -> "Samsung"
            prefix.startsWith("34:23:87") -> "Samsung"
            prefix.startsWith("18:3F:47") -> "Xiaomi"
            prefix.startsWith("F4:8E:92") -> "Xiaomi"
            prefix.startsWith("64:09:80") -> "Xiaomi"
            prefix.startsWith("B0:E2:35") -> "Huawei"
            prefix.startsWith("00:E0:FC") -> "Huawei"
            prefix.startsWith("D4:6E:0E") -> "TP-Link"
            prefix.startsWith("EC:08:6B") -> "TP-Link"
            prefix.startsWith("00:27:22") -> "TP-Link"
            else -> "Unknown"
        }
    }

    /**
     * Tenta adivinhar o tipo de dispositivo
     */
    private fun guessDeviceType(
        mac: String,
        vendor: String,
        openPorts: List<Int> = emptyList()
    ): DeviceType {
        return when {
            // Roteadores/Gateways (portas típicas)
            openPorts.contains(80) && openPorts.contains(443) -> DeviceType.ROUTER
            openPorts.contains(22) && openPorts.contains(80) -> DeviceType.ROUTER

            // Computadores
            openPorts.contains(3389) -> DeviceType.COMPUTER // RDP
            openPorts.contains(445) -> DeviceType.COMPUTER // SMB
            openPorts.contains(22) -> DeviceType.COMPUTER // SSH

            // Smartphones/Tablets (fabricantes)
            vendor.contains("Apple", ignoreCase = true) -> DeviceType.MOBILE
            vendor.contains("Samsung", ignoreCase = true) -> DeviceType.MOBILE
            vendor.contains("Xiaomi", ignoreCase = true) -> DeviceType.MOBILE
            vendor.contains("Huawei", ignoreCase = true) -> DeviceType.MOBILE

            // Roteadores (fabricantes)
            vendor.contains("TP-Link", ignoreCase = true) -> DeviceType.ROUTER
            vendor.contains("D-Link", ignoreCase = true) -> DeviceType.ROUTER

            // Máquinas virtuais
            vendor.contains("VMware", ignoreCase = true) -> DeviceType.COMPUTER
            vendor.contains("VirtualBox", ignoreCase = true) -> DeviceType.COMPUTER
            vendor.contains("QEMU", ignoreCase = true) -> DeviceType.COMPUTER

            else -> DeviceType.UNKNOWN
        }
    }

    fun stopScan() {
        activeScanJob?.cancel()
        activeScanJob = null
    }
}

/**
 * Modelo de dispositivo de rede
 */
data class NetworkDevice(
    val ipAddress: String,
    val macAddress: String,
    val hostname: String,
    val vendor: String,
    val isOnline: Boolean,
    val openPorts: List<Int>,
    val responseTime: Int, // em ms
    val deviceType: DeviceType
)

enum class DeviceType {
    ROUTER,
    COMPUTER,
    MOBILE,
    PRINTER,
    TV,
    CAMERA,
    UNKNOWN
}