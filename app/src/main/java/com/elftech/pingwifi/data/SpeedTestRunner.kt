package com.elftech.pingwifi.data

import android.os.SystemClock
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
import kotlin.math.min

/**
 * SpeedTestRunner totalmente em Kotlin, sem dependências nativas.
 * Compatível com páginas de memória de 16KB.
 */
class SpeedTestRunner(private val scope: CoroutineScope) {
    private var activeJob: Job? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun startDownload(
        url: String,
        onProgress: (Float, Double) -> Unit,
        onDone: (Double) -> Unit,
        onError: (String) -> Unit,
        reportIntervalMs: Int = 250,
        testDurationMs: Long = 10000L // Teste por 10 segundos
    ) {
        // Cancela qualquer teste anterior
        activeJob?.cancel()

        activeJob = scope.launch(Dispatchers.IO) {
            try {
                runSpeedTest(
                    url = url,
                    onProgress = onProgress,
                    onDone = onDone,
                    onError = onError,
                    reportIntervalMs = reportIntervalMs,
                    testDurationMs = testDurationMs
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Erro desconhecido")
                }
            }
        }
    }

    private suspend fun runSpeedTest(
        url: String,
        onProgress: (Float, Double) -> Unit,
        onDone: (Double) -> Unit,
        onError: (String) -> Unit,
        reportIntervalMs: Int,
        testDurationMs: Long
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .addHeader("Cache-Control", "no-cache")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw IOException("Resposta sem corpo")
                val contentLength = body.contentLength()
                val source = body.source()

                // Buffer otimizado para páginas de 16KB
                val bufferSize = 16 * 1024 // 16KB alinhado
                val buffer = Buffer()

                var totalBytesRead = 0L
                val startTime = SystemClock.elapsedRealtime()
                var lastReportTime = startTime
                val endTime = startTime + testDurationMs

                // Array para cálculo de média móvel de velocidade
                val speedSamples = mutableListOf<Pair<Long, Long>>() // (bytes, time)

                while (isActive && SystemClock.elapsedRealtime() < endTime) {
                    val bytesRead = source.read(buffer, bufferSize.toLong())
                    if (bytesRead == -1L) break

                    totalBytesRead += bytesRead
                    buffer.clear()

                    val currentTime = SystemClock.elapsedRealtime()
                    speedSamples.add(totalBytesRead to currentTime)

                    // Remove amostras antigas (mantém apenas últimos 2 segundos)
                    speedSamples.removeAll { (currentTime - it.second) > 2000 }

                    // Relatório de progresso
                    if (currentTime - lastReportTime >= reportIntervalMs) {
                        lastReportTime = currentTime

                        // Calcula velocidade média dos últimos 2 segundos
                        val mbps = calculateSpeed(speedSamples, startTime)

                        // Calcula progresso baseado no tempo ou bytes
                        val progress = if (contentLength > 0) {
                            min(100f, (totalBytesRead * 100f) / contentLength)
                        } else {
                            val elapsed = currentTime - startTime
                            min(100f, (elapsed * 100f) / testDurationMs)
                        }

                        withContext(Dispatchers.Main) {
                            onProgress(progress, mbps)
                        }
                    }
                }

                // Cálculo final
                val totalTime = SystemClock.elapsedRealtime() - startTime
                val finalMbps = if (totalTime > 0) {
                    (totalBytesRead * 8.0) / (totalTime / 1000.0) / 1_000_000.0
                } else {
                    0.0
                }

                withContext(Dispatchers.Main) {
                    onDone(finalMbps)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                when (e) {
                    is IOException -> onError("Erro de conexão: ${e.message}")
                    is SecurityException -> onError("Erro de segurança: ${e.message}")
                    else -> onError("Erro: ${e.message}")
                }
            }
        }
    }

    private fun calculateSpeed(samples: List<Pair<Long, Long>>, startTime: Long): Double {
        if (samples.size < 2) return 0.0

        val recentSamples = samples.takeLast(10) // Últimas 10 amostras
        if (recentSamples.size < 2) return 0.0

        val firstSample = recentSamples.first()
        val lastSample = recentSamples.last()

        val bytes = lastSample.first - firstSample.first
        val timeMs = lastSample.second - firstSample.second

        return if (timeMs > 0) {
            (bytes * 8.0) / (timeMs / 1000.0) / 1_000_000.0
        } else {
            0.0
        }
    }

    fun startUpload(
        url: String,
        onProgress: (Float, Double) -> Unit,
        onDone: (Double) -> Unit,
        onError: (String) -> Unit,
        reportIntervalMs: Int = 250,
        testDurationMs: Long = 10000L
    ) {
        // Implementação similar ao download, mas enviando dados
        activeJob?.cancel()

        activeJob = scope.launch(Dispatchers.IO) {
            try {
                // Gera dados aleatórios alinhados em 16KB
                val chunkSize = 64 * 1024 // 64KB chunks
                val dataChunk = ByteArray(chunkSize) { (it % 256).toByte() }

                // Por simplicidade, vamos simular o upload
                // Em produção, você faria um POST real
                withContext(Dispatchers.Main) {
                    onError("Upload não implementado nesta versão")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Erro no upload")
                }
            }
        }
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null
    }
}