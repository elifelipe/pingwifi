package com.elftech.pingwifi.data

import android.os.SystemClock
import fr.bmartel.speedtest.SpeedTestReport
import fr.bmartel.speedtest.SpeedTestSocket
import fr.bmartel.speedtest.inter.ISpeedTestListener
import fr.bmartel.speedtest.model.SpeedTestError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.IOException
import java.util.concurrent.TimeUnit

class SpeedTestRunner(private val scope: CoroutineScope) {
    private var activeJob: Job? = null
    private var activeSocket: SpeedTestSocket? = null

    fun startDownload(
        url: String,
        onProgress: (Float, Double) -> Unit,
        onDone: (Double) -> Unit,
        onError: (String) -> Unit,
        reportIntervalMs: Int = 250,
        socketTimeoutMs: Int = 15000,
        watchdogMs: Long = 4000L       // se nada acontecer em 4s, fazemos fallback
    ) {
        // encerra qualquer teste anterior
        activeJob?.cancel()
        activeSocket?.forceStopTask()

        activeJob = scope.launch(Dispatchers.IO) {
            var hadProgress = false
            val startT = SystemClock.elapsedRealtime()

            val speedTestSocket = SpeedTestSocket().apply {
                setSocketTimeout(socketTimeoutMs)
            }
            activeSocket = speedTestSocket

            var lastPct = -1            // degrau de 1% (Int)
            var lastEmit = 0L

            // ---- watchdog: se não houver progresso em N segundos, cai para OkHttp
            val watchdog = launch {
                while (isActive) {
                    delay(250)
                    val elapsed = SystemClock.elapsedRealtime() - startT
                    if (!hadProgress && elapsed >= watchdogMs) {
                        // nada aconteceu: cancela socket e usa fallback
                        try { speedTestSocket.forceStopTask() } catch (_: Throwable) {}
                        cancel() // encerra watchdog
                        runOkHttpFallback(url, onProgress, onDone, onError)
                        return@launch
                    }
                }
            }

            try {
                speedTestSocket.addSpeedTestListener(object : ISpeedTestListener {
                    override fun onCompletion(report: SpeedTestReport) {
                        hadProgress = true
                        val mbps = report.transferRateBit.toDouble() / 1_000_000.0
                        scope.launch(Dispatchers.Main) { onDone(mbps) }
                        watchdog.cancel()
                    }

                    override fun onProgress(percent: Float, report: SpeedTestReport) {
                        if (!isActive) return
                        hadProgress = true
                        val now = SystemClock.elapsedRealtime()
                        val pctStep = percent.toInt() // 0..100
                        if (pctStep != lastPct && now - lastEmit >= 60L) {
                            lastPct = pctStep
                            lastEmit = now
                            val mbps = report.transferRateBit.toDouble() / 1_000_000.0
                            scope.launch(Dispatchers.Main) { onProgress(percent, mbps) }
                        }
                    }

                    override fun onError(err: SpeedTestError, msg: String) {
                        // erro da lib → tenta fallback HTTPS
                        if (!isActive) return
                        watchdog.cancel()

                        // CORREÇÃO: Lança uma nova corrotina para chamar a função suspensa
                        scope.launch(Dispatchers.IO) {
                            runOkHttpFallback(url, onProgress, onDone, onError)
                        }
                    }
                })

                // inicia com intervalo de relatório menor (menos jank)
                speedTestSocket.startDownload(url, reportIntervalMs)

            } catch (e: Throwable) {
                watchdog.cancel()
                // Falha ao iniciar → fallback direto
                runOkHttpFallback(url, onProgress, onDone, onError)
            }
        }
    }

    private suspend fun runOkHttpFallback(
        url: String,
        onProgress: (Float, Double) -> Unit,
        onDone: (Double) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val req = Request.Builder().url(url).build()

        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Resposta sem corpo")
                val totalLen = body.contentLength() // pode ser -1
                val source = body.source()
                val buf = Buffer()

                var totalBytes = 0L
                val t0 = SystemClock.elapsedRealtime()
                var lastEmit = t0

                while (true) {
                    val read = source.read(buf, 64 * 1024)
                    if (read == -1L) break
                    totalBytes += read
                    buf.clear()

                    val now = SystemClock.elapsedRealtime()
                    if (now - lastEmit >= 250L) {
                        lastEmit = now
                        val secs = (now - t0) / 1000.0
                        val mbps = (totalBytes * 8) / 1_000_000.0 / secs.coerceAtLeast(0.001)
                        if (totalLen > 0) {
                            val pct = (totalBytes * 100f / totalLen).coerceIn(0f, 100f)
                            withContext(Dispatchers.Main) { onProgress(pct, mbps) }
                        } else {
                            // sem content-length: só atualiza velocidade
                            withContext(Dispatchers.Main) { onProgress(0f, mbps) }
                        }
                    }
                }

                val secs = (SystemClock.elapsedRealtime() - t0) / 1000.0
                val mbps = (totalBytes * 8) / 1_000_000.0 / secs.coerceAtLeast(0.001)
                withContext(Dispatchers.Main) { onDone(mbps) }
            }
        } catch (e: Throwable) {
            withContext(Dispatchers.Main) {
                onError(e.message ?: "Falha no teste de velocidade")
            }
        }
    }

    fun stop() {
        activeJob?.cancel()
        try { activeSocket?.forceStopTask() } catch (_: Throwable) {}
        activeJob = null
        activeSocket = null
    }
}