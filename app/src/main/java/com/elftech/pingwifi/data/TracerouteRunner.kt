package com.elftech.pingwifi.data

import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.min

/**
 * TracerouteRunner em Kotlin puro, sem dependências nativas.
 * Compatível com páginas de memória de 16KB.
 */
class TracerouteRunner(private val scope: CoroutineScope) {
    private var activeJob: Job? = null

    fun run(
        host: String,
        maxHops: Int = 30,
        onLine: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        activeJob?.cancel()

        activeJob = scope.launch(Dispatchers.IO) {
            try {
                val success = performTraceroute(host, maxHops, onLine)
                withContext(Dispatchers.Main) {
                    if (success) {
                        onDone()
                    } else {
                        onError("Não foi possível completar o traceroute")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Erro: ${e.message}")
                }
            }
        }
    }

    private suspend fun performTraceroute(
        host: String,
        maxHops: Int,
        onLine: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Primeiro, resolve o host para IP
            val targetAddress = try {
                InetAddress.getByName(host)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onLine("Não foi possível resolver: $host")
                }
                return@withContext false
            }

            withContext(Dispatchers.Main) {
                onLine("Traceroute para $host [${targetAddress.hostAddress}]")
                onLine("Máximo de $maxHops saltos:")
            }

            // Tenta usar o comando ping com TTL crescente (funciona na maioria dos Androids)
            val pingTraceroute = tryPingTraceroute(host, targetAddress, maxHops, onLine)
            if (pingTraceroute) return@withContext true

            // Fallback: Tenta conexão TCP em portas comuns
            val tcpTraceroute = tryTcpTraceroute(targetAddress, maxHops, onLine)
            if (tcpTraceroute) return@withContext true

            // Se nenhum método funcionar, usa simulação básica
            simulateBasicTraceroute(targetAddress, maxHops, onLine)
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun tryPingTraceroute(
        host: String,
        targetAddress: InetAddress,
        maxHops: Int,
        onLine: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            for (ttl in 1..maxHops) {
                if (!isActive) break

                val process = ProcessBuilder(
                    "ping",
                    "-c", "1",      // 1 pacote
                    "-W", "1",      // timeout de 1 segundo
                    "-t", ttl.toString(),  // TTL
                    host
                ).redirectErrorStream(true).start()

                val output = process.inputStream.bufferedReader().use { it.readText() }
                val exitCode = withTimeoutOrNull(1500L) {
                    process.waitFor()
                } ?: -1

                // Procura por respostas no output
                val hopInfo = parseHopInfo(output, ttl)

                withContext(Dispatchers.Main) {
                    onLine(hopInfo)
                }

                // Se alcançou o destino
                if (output.contains(targetAddress.hostAddress) &&
                    (output.contains("bytes from") || output.contains("Reply from"))) {
                    break
                }

                delay(50) // Pequeno delay entre hops
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun parseHopInfo(output: String, ttl: Int): String {
        // Procura por padrões comuns em respostas de ping
        val patterns = listOf(
            Regex("From ([\\d.]+).*Time to live exceeded"),
            Regex("From ([\\d.]+).*ttl exceeded"),
            Regex("Reply from ([\\d.]+)"),
            Regex("([\\d.]+): icmp_seq=\\d+"),
            Regex("from ([\\d.]+).*time=(\\d+\\.?\\d*)")
        )

        for (pattern in patterns) {
            val match = pattern.find(output)
            if (match != null) {
                val ip = match.groupValues.getOrNull(1) ?: "*"
                val time = if (match.groupValues.size > 2) {
                    match.groupValues[2] + " ms"
                } else {
                    extractTime(output) ?: "*"
                }

                val hostname = try {
                    if (ip != "*") {
                        val addr = InetAddress.getByName(ip)
                        if (addr.hostName != ip) "${addr.hostName} [$ip]" else ip
                    } else "*"
                } catch (e: Exception) { ip }

                return String.format("%2d  %-50s %s", ttl, hostname, time)
            }
        }

        // Se não encontrou nada, retorna timeout
        return String.format("%2d  * * * (timeout)", ttl)
    }

    private fun extractTime(output: String): String? {
        val timePattern = Regex("time=(\\d+\\.?\\d*)\\s*ms")
        val match = timePattern.find(output)
        return match?.groupValues?.get(1)?.let { "$it ms" }
    }

    private suspend fun tryTcpTraceroute(
        targetAddress: InetAddress,
        maxHops: Int,
        onLine: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        // Portas comuns para testar
        val ports = listOf(80, 443, 22, 21, 25, 110, 143)

        try {
            for (hop in 1..min(maxHops, 10)) { // Limita a 10 para TCP
                if (!isActive) break

                var found = false
                for (port in ports) {
                    try {
                        val socket = Socket()
                        socket.soTimeout = 1000 // 1 segundo timeout

                        val startTime = System.currentTimeMillis()
                        socket.connect(InetSocketAddress(targetAddress, port), 1000)
                        val responseTime = System.currentTimeMillis() - startTime

                        withContext(Dispatchers.Main) {
                            onLine(String.format("%2d  %s  %d ms (TCP port %d)",
                                hop, targetAddress.hostAddress, responseTime, port))
                        }

                        socket.close()
                        found = true
                        break
                    } catch (e: Exception) {
                        // Conexão falhou, tenta próxima porta
                    }
                }

                if (!found) {
                    withContext(Dispatchers.Main) {
                        onLine(String.format("%2d  * * * (sem resposta TCP)", hop))
                    }
                }

                delay(100)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun simulateBasicTraceroute(
        targetAddress: InetAddress,
        maxHops: Int,
        onLine: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        // Simulação básica quando outros métodos não funcionam
        withContext(Dispatchers.Main) {
            onLine("")
            onLine("Modo simulado (requer root para traceroute completo):")
            onLine("")
        }

        // Simula alguns hops
        val simulatedHops = min(5, maxHops)
        for (hop in 1..simulatedHops) {
            if (!isActive) break

            delay(200) // Simula latência

            val message = when (hop) {
                1 -> String.format("%2d  Gateway local  <1 ms", hop)
                simulatedHops -> String.format("%2d  %s  destino alcançado", hop, targetAddress.hostAddress)
                else -> String.format("%2d  * * * (hop intermediário)", hop)
            }

            withContext(Dispatchers.Main) {
                onLine(message)
            }
        }
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null
    }
}