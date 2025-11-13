package com.elftech.pingwifis.data

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * PingTestRunner PROFISSIONAL com detecção ARP para dispositivos sem portas abertas.
 * Versão 3.0 - Detecta QUALQUER dispositivo na rede local.
 */
class PingTestRunner(private val scope: CoroutineScope) {
    private var activeJob: Job? = null

    companion object {
        private const val DEFAULT_PING_COUNT = 10
        private const val TIMEOUT_MS = 3000
        private const val LOCAL_NETWORK_TIMEOUT_MS = 800

        // Portas comuns para TCP ping (ordem de prioridade)
        private val COMMON_PORTS = listOf(
            80, 443, 8080, 22, 3389, 445, 139,
            21, 23, 25, 53, 110, 143, 3306, 5432
        )
    }

    fun runPingTest(
        host: String,
        count: Int = DEFAULT_PING_COUNT,
        onProgress: (PingResult) -> Unit,
        onComplete: (PingSummary) -> Unit,
        onError: (String) -> Unit
    ) {
        activeJob?.cancel()

        activeJob = scope.launch(Dispatchers.IO) {
            try {
                val results = mutableListOf<PingResult>()

                // Resolve o host primeiro
                withContext(Dispatchers.Main) {
                    onProgress(PingResult(0, 0, "Resolvendo endereço...", PingStatus.RESOLVING))
                }

                val ipAddress = try {
                    InetAddress.getByName(host).hostAddress
                        ?: throw Exception("Não foi possível resolver o host")
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onError("Erro ao resolver host: ${e.message}")
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    onProgress(PingResult(0, 0, "Endereço resolvido: $ipAddress", PingStatus.RESOLVED))
                }

                delay(500)

                // Detecta se é rede local
                val isLocalNetwork = isLocalNetworkAddress(ipAddress)
                val timeout = if (isLocalNetwork) LOCAL_NETWORK_TIMEOUT_MS else TIMEOUT_MS

                // Para rede local, tenta detectar via ARP primeiro
                var deviceInfo = ""
                if (isLocalNetwork) {
                    withContext(Dispatchers.Main) {
                        onProgress(PingResult(0, 0, "Verificando se o dispositivo está ativo na rede...", PingStatus.RESOLVED))
                    }

                    val isAlive = checkDeviceAliveViaArp(ipAddress)
                    if (isAlive) {
                        deviceInfo = " [Detectado na rede via ARP]"
                    }
                }

                // Testa qual porta está aberta (apenas uma vez antes do loop)
                val bestPort = findBestPort(ipAddress, timeout)

                if (bestPort != null) {
                    withContext(Dispatchers.Main) {
                        onProgress(PingResult(
                            0, 0,
                            "Usando TCP porta $bestPort para testes$deviceInfo",
                            PingStatus.RESOLVED
                        ))
                    }
                } else if (isLocalNetwork && deviceInfo.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onProgress(PingResult(
                            0, 0,
                            "Dispositivo ativo, mas sem portas TCP abertas. Usando detecção por conectividade$deviceInfo",
                            PingStatus.RESOLVED
                        ))
                    }
                }

                delay(500)

                // Executa os pings
                for (sequence in 1..count) {
                    if (!isActive) break

                    val result = performSinglePing(
                        host,
                        ipAddress,
                        sequence,
                        timeout,
                        isLocalNetwork,
                        bestPort
                    )
                    results.add(result)

                    withContext(Dispatchers.Main) {
                        onProgress(result)
                    }

                    // Delay entre pings
                    if (sequence < count) {
                        delay(1000)
                    }
                }

                // Calcula estatísticas
                val summary = calculateSummary(results, host, ipAddress)

                withContext(Dispatchers.Main) {
                    onComplete(summary)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Erro durante o teste: ${e.message}")
                }
            }
        }
    }

    /**
     * NOVO: Verifica se o dispositivo está ativo na rede via tabela ARP
     * Funciona mesmo para dispositivos sem portas abertas!
     */
    private suspend fun checkDeviceAliveViaArp(ipAddress: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Força um pacote para o IP (criar entrada ARP)
                try {
                    InetAddress.getByName(ipAddress).isReachable(500)
                } catch (e: Exception) {
                    // Ignora erro, só queremos criar entrada ARP
                }

                // Lê a tabela ARP do sistema
                val process = Runtime.getRuntime().exec("cat /proc/net/arp")
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.contains(ipAddress)) {
                        // Verifica se tem MAC address (não é 00:00:00:00:00:00)
                        val parts = line!!.split(Regex("\\s+"))
                        if (parts.size >= 4) {
                            val macAddress = parts[3]
                            if (macAddress != "00:00:00:00:00:00" && macAddress.contains(":")) {
                                Log.d("PingTest", "Device $ipAddress found in ARP with MAC: $macAddress")
                                return@withContext true
                            }
                        }
                    }
                }

                false
            } catch (e: Exception) {
                Log.w("PingTest", "ARP check failed: ${e.message}")
                false
            }
        }
    }

    /**
     * MELHORADO: Encontra a melhor porta TCP aberta para usar nos testes
     */
    private suspend fun findBestPort(ipAddress: String, timeout: Int): Int? {
        return withContext(Dispatchers.IO) {
            // Testa as portas mais comuns em paralelo (rápido)
            val quickPorts = listOf(80, 443, 8080, 22, 3389)
            val jobs = quickPorts.map { port ->
                async {
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(ipAddress, port), timeout / 2)
                        socket.close()
                        port
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            // Retorna a primeira porta que responder
            val foundPort = jobs.awaitAll().firstOrNull { it != null }

            if (foundPort != null) {
                Log.d("PingTest", "Found open port $foundPort on $ipAddress")
            }

            foundPort
        }
    }

    /**
     * MELHORADO: Executa um único ping usando múltiplos métodos
     */
    private suspend fun performSinglePing(
        host: String,
        ipAddress: String,
        sequence: Int,
        timeout: Int,
        isLocalNetwork: Boolean,
        preferredPort: Int?
    ): PingResult {
        return withContext(Dispatchers.IO) {
            try {
                // MÉTODO 1: TCP ping com porta conhecida (MAIS RÁPIDO)
                if (preferredPort != null) {
                    val tcpResult = tcpPingSinglePort(ipAddress, preferredPort, sequence, timeout)
                    if (tcpResult.status == PingStatus.SUCCESS) {
                        return@withContext tcpResult
                    }
                }

                // MÉTODO 2: InetAddress.isReachable() (funciona para alguns dispositivos)
                val reachableResult = inetAddressPing(ipAddress, sequence, timeout)
                if (reachableResult.status == PingStatus.SUCCESS) {
                    return@withContext reachableResult
                }

                // MÉTODO 3: Para rede local, verifica via ARP + conectividade básica
                if (isLocalNetwork) {
                    val arpResult = arpBasedPing(ipAddress, sequence, timeout)
                    if (arpResult.status == PingStatus.SUCCESS) {
                        return@withContext arpResult
                    }
                }

                // MÉTODO 4: TCP ping testando múltiplas portas (último recurso)
                val multiPortResult = tcpPingMultiplePorts(ipAddress, sequence, timeout)
                if (multiPortResult.status == PingStatus.SUCCESS) {
                    return@withContext multiPortResult
                }

                // Se todos falharam
                PingResult(
                    sequence = sequence,
                    latencyMs = timeout,
                    message = "$sequence: Request timeout for $ipAddress",
                    status = PingStatus.TIMEOUT
                )

            } catch (e: Exception) {
                PingResult(
                    sequence = sequence,
                    latencyMs = 0,
                    message = "$sequence: Error - ${e.message}",
                    status = PingStatus.ERROR
                )
            }
        }
    }

    /**
     * NOVO: Ping baseado em ARP + conectividade básica
     * Funciona para dispositivos sem portas abertas!
     */
    private suspend fun arpBasedPing(
        ipAddress: String,
        sequence: Int,
        timeout: Int
    ): PingResult {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()

                // Tenta criar conexão (vai gerar entrada ARP mesmo se falhar)
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(ipAddress, 80), timeout / 2)
                    socket.close()
                } catch (e: Exception) {
                    // Ignora - só queremos gerar tráfego
                }

                // Verifica se apareceu na tabela ARP
                val isInArp = checkDeviceAliveViaArp(ipAddress)
                val latency = (System.currentTimeMillis() - startTime).toInt()

                if (isInArp) {
                    PingResult(
                        sequence = sequence,
                        latencyMs = latency.coerceIn(1, timeout),
                        message = "$sequence: Reply from $ipAddress: time=${latency}ms (ARP)",
                        status = PingStatus.SUCCESS
                    )
                } else {
                    PingResult(
                        sequence = sequence,
                        latencyMs = timeout,
                        message = "$sequence: Timeout",
                        status = PingStatus.TIMEOUT
                    )
                }
            } catch (e: Exception) {
                PingResult(
                    sequence = sequence,
                    latencyMs = 0,
                    message = "$sequence: Error",
                    status = PingStatus.ERROR
                )
            }
        }
    }

    /**
     * TCP Ping em uma porta específica
     */
    private fun tcpPingSinglePort(
        ipAddress: String,
        port: Int,
        sequence: Int,
        timeout: Int
    ): PingResult {
        return try {
            val socket = Socket()
            val startTime = System.currentTimeMillis()

            socket.connect(InetSocketAddress(ipAddress, port), timeout)
            val latency = (System.currentTimeMillis() - startTime).toInt()
            socket.close()

            PingResult(
                sequence = sequence,
                latencyMs = latency,
                message = "$sequence: Reply from $ipAddress: time=${latency}ms (port $port)",
                status = PingStatus.SUCCESS
            )
        } catch (e: Exception) {
            PingResult(
                sequence = sequence,
                latencyMs = timeout,
                message = "$sequence: Timeout",
                status = PingStatus.TIMEOUT
            )
        }
    }

    /**
     * TCP Ping testando múltiplas portas
     */
    private fun tcpPingMultiplePorts(
        ipAddress: String,
        sequence: Int,
        timeout: Int
    ): PingResult {
        for (port in COMMON_PORTS.take(8)) {
            try {
                val socket = Socket()
                val startTime = System.currentTimeMillis()

                socket.connect(InetSocketAddress(ipAddress, port), timeout / 8)
                val latency = (System.currentTimeMillis() - startTime).toInt()
                socket.close()

                return PingResult(
                    sequence = sequence,
                    latencyMs = latency,
                    message = "$sequence: Reply from $ipAddress: time=${latency}ms (port $port)",
                    status = PingStatus.SUCCESS
                )
            } catch (e: Exception) {
                continue
            }
        }

        return PingResult(
            sequence = sequence,
            latencyMs = timeout,
            message = "$sequence: No response",
            status = PingStatus.TIMEOUT
        )
    }

    /**
     * InetAddress.isReachable() - Tenta ICMP
     */
    private fun inetAddressPing(
        ipAddress: String,
        sequence: Int,
        timeout: Int
    ): PingResult {
        return try {
            val startTime = System.currentTimeMillis()
            val addr = InetAddress.getByName(ipAddress)

            val isReachable = addr.isReachable(timeout)
            val latency = (System.currentTimeMillis() - startTime).toInt()

            if (isReachable) {
                PingResult(
                    sequence = sequence,
                    latencyMs = latency,
                    message = "$sequence: Reply from $ipAddress: time=${latency}ms (ICMP)",
                    status = PingStatus.SUCCESS
                )
            } else {
                PingResult(
                    sequence = sequence,
                    latencyMs = timeout,
                    message = "$sequence: Timeout",
                    status = PingStatus.TIMEOUT
                )
            }
        } catch (e: Exception) {
            PingResult(
                sequence = sequence,
                latencyMs = 0,
                message = "$sequence: Error",
                status = PingStatus.ERROR
            )
        }
    }

    /**
     * Detecta se é um endereço de rede local
     */
    private fun isLocalNetworkAddress(ip: String): Boolean {
        return try {
            when {
                ip.startsWith("192.168.") -> true
                ip.startsWith("10.") -> true
                ip.startsWith("172.") -> {
                    val secondOctet = ip.split(".").getOrNull(1)?.toIntOrNull() ?: 0
                    secondOctet in 16..31
                }
                ip.startsWith("127.") -> true
                ip == "localhost" -> true
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Calcula as estatísticas do teste
     */
    private fun calculateSummary(
        results: List<PingResult>,
        host: String,
        ipAddress: String
    ): PingSummary {
        val successfulPings = results.filter { it.status == PingStatus.SUCCESS }
        val transmitted = results.size
        val received = successfulPings.size
        val packetLoss = if (transmitted > 0) {
            ((transmitted - received).toFloat() / transmitted * 100).roundToInt()
        } else {
            100
        }

        val latencies = successfulPings.map { it.latencyMs }
        val minLatency = latencies.minOrNull() ?: 0
        val maxLatency = latencies.maxOrNull() ?: 0
        val avgLatency = if (latencies.isNotEmpty()) {
            latencies.average().roundToInt()
        } else {
            0
        }

        val jitter = if (latencies.size > 1) {
            val differences = latencies.zipWithNext { a, b -> abs(a - b) }
            differences.average().roundToInt()
        } else {
            0
        }

        return PingSummary(
            host = host,
            ipAddress = ipAddress,
            packetsTransmitted = transmitted,
            packetsReceived = received,
            packetLossPercent = packetLoss,
            minLatencyMs = minLatency,
            maxLatencyMs = maxLatency,
            avgLatencyMs = avgLatency,
            jitterMs = jitter,
            allResults = results
        )
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null
    }
}

// Data classes permanecem as mesmas
data class PingResult(
    val sequence: Int,
    val latencyMs: Int,
    val message: String,
    val status: PingStatus
)

enum class PingStatus {
    RESOLVING,
    RESOLVED,
    SUCCESS,
    TIMEOUT,
    ERROR
}

data class PingSummary(
    val host: String,
    val ipAddress: String,
    val packetsTransmitted: Int,
    val packetsReceived: Int,
    val packetLossPercent: Int,
    val minLatencyMs: Int,
    val maxLatencyMs: Int,
    val avgLatencyMs: Int,
    val jitterMs: Int,
    val allResults: List<PingResult>
)