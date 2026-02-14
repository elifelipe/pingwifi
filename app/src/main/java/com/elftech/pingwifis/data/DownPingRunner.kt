package com.elftech.pingwifis.data

import com.elftech.pingwifis.data.model.DownPingReport
import com.elftech.pingwifis.data.model.HttpCheckResult
import com.elftech.pingwifis.data.model.OsiLayerResult
import com.elftech.pingwifis.data.model.OsiLayerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.UnknownServiceException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DownPingRunner(private val scope: CoroutineScope) {
    private var activeJob: Job? = null
    private val pingRunner = PingTestRunner(scope)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun run(
        target: String,
        pingCount: Int = 6,
        onProgress: (String) -> Unit,
        onPingProgress: (PingResult) -> Unit,
        onComplete: (DownPingReport) -> Unit,
        onError: (String) -> Unit
    ) {
        activeJob?.cancel()
        pingRunner.stop()

        activeJob = scope.launch(Dispatchers.IO) {
            try {
                val host = normalizeHost(target)
                if (host.isBlank()) {
                    onError("Digite um IP ou domínio válido")
                    return@launch
                }

                onProgress("Resolvendo DNS...")
                val resolvedIps = InetAddress.getAllByName(host)
                    .mapNotNull { it.hostAddress }
                    .distinct()

                if (resolvedIps.isEmpty()) {
                    onError("Não foi possível resolver o destino")
                    return@launch
                }

                onProgress("Executando ping ($pingCount pacotes)...")
                val pingSummary = runPing(host = host, count = pingCount, onPingProgress = onPingProgress)

                onProgress("Validando camada de transporte (TCP)...")
                val tcpPortResults = linkedMapOf(
                    53 to testTcpPort(host, 53),
                    80 to testTcpPort(host, 80),
                    443 to testTcpPort(host, 443)
                )

                onProgress("Executando verificações HTTP/HTTPS...")
                val httpChecks = listOf(
                    runHttpCheck("https://$host/"),
                    runHttpCheck("http://$host/")
                )

                val osiResults = buildOsiResults(
                    resolvedIps = resolvedIps,
                    pingSummary = pingSummary,
                    tcpPortResults = tcpPortResults,
                    httpChecks = httpChecks
                )

                onComplete(
                    DownPingReport(
                        target = target,
                        normalizedHost = host,
                        resolvedIps = resolvedIps,
                        pingSummary = pingSummary,
                        tcpPortResults = tcpPortResults,
                        httpChecks = httpChecks,
                        osiResults = osiResults
                    )
                )
            } catch (e: Exception) {
                onError("Falha no diagnóstico: ${e.message ?: "erro desconhecido"}")
            }
        }
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null
        pingRunner.stop()
    }

    private fun normalizeHost(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            val uri = URI(candidate)
            uri.host ?: trimmed.substringBefore("/").substringBefore("?").substringBefore("#")
        } catch (_: Exception) {
            trimmed.substringBefore("/").substringBefore("?").substringBefore("#")
        }
    }

    private suspend fun runPing(
        host: String,
        count: Int,
        onPingProgress: (PingResult) -> Unit
    ): PingSummary = suspendCancellableCoroutine { cont ->
        pingRunner.runPingTest(
            host = host,
            count = count,
            onProgress = { onPingProgress(it) },
            onComplete = { summary -> if (cont.isActive) cont.resume(summary) },
            onError = { error -> if (cont.isActive) cont.resumeWithException(IllegalStateException(error)) }
        )
        cont.invokeOnCancellation { pingRunner.stop() }
    }

    private fun testTcpPort(host: String, port: Int, timeoutMs: Int = 2500): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun runHttpCheck(url: String): HttpCheckResult {
        return try {
            val request = Request.Builder().url(url).get().build()
            val startNs = System.nanoTime()
            httpClient.newCall(request).execute().use { response ->
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                HttpCheckResult(
                    url = url,
                    ok = response.isSuccessful,
                    statusCode = response.code,
                    latencyMs = elapsedMs,
                    message = "HTTP ${response.code}"
                )
            }
        } catch (e: UnknownServiceException) {
            HttpCheckResult(
                url = url,
                ok = false,
                message = "Cleartext HTTP bloqueado pela política do app"
            )
        } catch (e: IOException) {
            HttpCheckResult(
                url = url,
                ok = false,
                message = e.message ?: "Erro de rede"
            )
        } catch (e: Exception) {
            HttpCheckResult(
                url = url,
                ok = false,
                message = e.message ?: "Falha desconhecida"
            )
        }
    }

    private fun buildOsiResults(
        resolvedIps: List<String>,
        pingSummary: PingSummary,
        tcpPortResults: Map<Int, Boolean>,
        httpChecks: List<HttpCheckResult>
    ): List<OsiLayerResult> {
        val dnsOk = resolvedIps.isNotEmpty()
        val pingLoss = pingSummary.packetLossPercent
        val pingOk = pingLoss < 100
        val transportOk = tcpPortResults.values.any { it }
        val httpsResult = httpChecks.firstOrNull { it.url.startsWith("https://") }
        val appOk = httpChecks.any { it.ok }

        return listOf(
            OsiLayerResult(
                layer = 1,
                name = "Física",
                status = OsiLayerStatus.INFO,
                details = "Camada física não é mensurável diretamente pelo app."
            ),
            OsiLayerResult(
                layer = 2,
                name = "Enlace",
                status = OsiLayerStatus.INFO,
                details = "Estado de enlace depende da interface local (Wi-Fi/4G)."
            ),
            OsiLayerResult(
                layer = 3,
                name = "Rede",
                status = when {
                    dnsOk && pingOk && pingLoss <= 10 -> OsiLayerStatus.PASS
                    dnsOk && pingOk -> OsiLayerStatus.WARN
                    else -> OsiLayerStatus.FAIL
                },
                details = "DNS ${if (dnsOk) "OK" else "falhou"}, perda de pacotes ${pingLoss}%."
            ),
            OsiLayerResult(
                layer = 4,
                name = "Transporte",
                status = if (transportOk) OsiLayerStatus.PASS else OsiLayerStatus.FAIL,
                details = "TCP aberto nas portas: ${
                    tcpPortResults.filterValues { it }.keys.joinToString().ifBlank { "nenhuma" }
                }."
            ),
            OsiLayerResult(
                layer = 5,
                name = "Sessão",
                status = if (transportOk && appOk) OsiLayerStatus.PASS else OsiLayerStatus.WARN,
                details = "Sessão depende de TCP + aplicação. Status geral: ${if (appOk) "estável" else "instável"}."
            ),
            OsiLayerResult(
                layer = 6,
                name = "Apresentação",
                status = when {
                    httpsResult?.ok == true -> OsiLayerStatus.PASS
                    httpsResult != null -> OsiLayerStatus.WARN
                    else -> OsiLayerStatus.INFO
                },
                details = httpsResult?.let {
                    "HTTPS: ${it.message.ifBlank { "sem detalhes" }}."
                } ?: "Sem teste HTTPS."
            ),
            OsiLayerResult(
                layer = 7,
                name = "Aplicação",
                status = if (appOk) OsiLayerStatus.PASS else OsiLayerStatus.FAIL,
                details = "HTTP/HTTPS ${if (appOk) "respondeu" else "sem resposta válida"}."
            )
        )
    }
}
