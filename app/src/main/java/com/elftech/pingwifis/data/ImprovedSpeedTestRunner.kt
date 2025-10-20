package com.elftech.pingwifis.data

import android.os.SystemClock
import com.elftech.pingwifis.data.model.SpeedTestServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min

class ImprovedSpeedTestRunner(private val scope: CoroutineScope) {
    private var activeJob: Job? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun startDownload(
        url: String,
        onProgress: (Float, Double) -> Unit,
        onDone: (Double) -> Unit,
        onError: (String) -> Unit,
        reportIntervalMs: Int = 250,
        testDurationMs: Long = 10000L
    ) {
        activeJob?.cancel()

        activeJob = scope.launch(Dispatchers.IO) {
            try {
                runDownloadTest(
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

    private suspend fun runDownloadTest(
        url: String,
        onProgress: (Float, Double) -> Unit,
        onDone: (Double) -> Unit,
        onError: (String) -> Unit,
        reportIntervalMs: Int,
        testDurationMs: Long
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            .addHeader("Pragma", "no-cache")
            .addHeader("Expires", "0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw IOException("Resposta sem corpo")
                val contentLength = body.contentLength()
                val source = body.source()

                val bufferSize = 64 * 1024 // 64KB
                val buffer = Buffer()

                var totalBytesRead = 0L
                val startTime = SystemClock.elapsedRealtime()
                var lastReportTime = startTime
                val endTime = startTime + testDurationMs

                val speedSamples = mutableListOf<Pair<Long, Long>>()

                while (isActive && SystemClock.elapsedRealtime() < endTime) {
                    val bytesRead = source.read(buffer, bufferSize.toLong())
                    if (bytesRead == -1L) break

                    totalBytesRead += bytesRead
                    buffer.clear()

                    val currentTime = SystemClock.elapsedRealtime()
                    speedSamples.add(totalBytesRead to currentTime)

                    // Mantém apenas últimos 2 segundos de amostras
                    speedSamples.removeAll { (currentTime - it.second) > 2000 }

                    if (currentTime - lastReportTime >= reportIntervalMs) {
                        lastReportTime = currentTime

                        val mbps = calculateSpeed(speedSamples)

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

    fun startUpload(
        server: SpeedTestServer,
        onProgress: (Float, Double) -> Unit,
        onDone: (Double) -> Unit,
        onError: (String) -> Unit,
        reportIntervalMs: Int = 250,
        testDurationMs: Long = 10000L
    ) {
        activeJob?.cancel()

        // Tenta derivar a URL de upload da URL de download. Isso é uma suposição comum.
        val uploadUrl = server.downloadUrl.replaceAfterLast('/', "")

        activeJob = scope.launch(Dispatchers.IO) {
            try {
                runUploadTest(
                    url = uploadUrl,
                    onProgress = onProgress,
                    onDone = onDone,
                    onError = onError,
                    reportIntervalMs = reportIntervalMs,
                    testDurationMs = testDurationMs
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Erro no upload")
                }
            }
        }
    }

    private suspend fun runUploadTest(
        url: String,
        onProgress: (Float, Double) -> Unit,
        onDone: (Double) -> Unit,
        onError: (String) -> Unit,
        reportIntervalMs: Int,
        testDurationMs: Long
    ) = withContext(Dispatchers.IO) {
        if (url.isEmpty()) {
            onError("URL de upload não disponível")
            return@withContext
        }

        try {
            val chunkSize = 256 * 1024 // 256KB por chunk
            val dataChunk = ByteArray(chunkSize) { (it % 256).toByte() }

            var totalBytesUploaded = 0L
            val startTime = SystemClock.elapsedRealtime()
            var lastReportTime = startTime
            val endTime = startTime + testDurationMs

            val speedSamples = mutableListOf<Pair<Long, Long>>()

            while (isActive && SystemClock.elapsedRealtime() < endTime) {
                val requestBody = dataChunk.toRequestBody("application/octet-stream".toMediaType())

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .addHeader("Cache-Control", "no-cache")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        totalBytesUploaded += chunkSize

                        val currentTime = SystemClock.elapsedRealtime()
                        speedSamples.add(totalBytesUploaded to currentTime)
                        speedSamples.removeAll { (currentTime - it.second) > 2000 }

                        if (currentTime - lastReportTime >= reportIntervalMs) {
                            lastReportTime = currentTime

                            val mbps = calculateSpeed(speedSamples)
                            val progress = min(100f, ((currentTime - startTime) * 100f) / testDurationMs)

                            withContext(Dispatchers.Main) {
                                onProgress(progress, mbps)
                            }
                        }
                    } else {
                        // Se o servidor não aceitar POST, encerra o teste de upload
                        throw IOException("Servidor de upload não aceitou a conexão: ${response.code}")
                    }
                }
            }

            val totalTime = SystemClock.elapsedRealtime() - startTime
            val finalMbps = if (totalTime > 0) {
                (totalBytesUploaded * 8.0) / (totalTime / 1000.0) / 1_000_000.0
            } else {
                0.0
            }

            withContext(Dispatchers.Main) {
                onDone(finalMbps)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Erro no upload: ${e.message}")
            }
        }
    }

    private fun calculateSpeed(samples: List<Pair<Long, Long>>): Double {
        if (samples.size < 2) return 0.0

        val recentSamples = samples.takeLast(10)
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

    fun stop() {
        activeJob?.cancel()
        activeJob = null
    }
}

