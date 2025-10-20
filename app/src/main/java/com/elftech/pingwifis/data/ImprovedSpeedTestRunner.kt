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
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val requestWithUserAgent = originalRequest.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36")
                .build()
            chain.proceed(requestWithUserAgent)
        }
        .build()

    fun startDownload(
        url: String,
        onProgress: (Float, Double) -> Unit,
        onDone: (Double) -> Unit,
        onError: (String) -> Unit,
        onNewSample: (Double) -> Unit,
        reportIntervalMs: Int = 250,
        testDurationMs: Long = 10000L // Duração de 10 segundos para o download
    ) {
        activeJob?.cancel()

        activeJob = scope.launch(Dispatchers.IO) {
            try {
                runDownloadTest(
                    url = url,
                    onProgress = onProgress,
                    onDone = onDone,
                    onError = onError,
                    onNewSample = onNewSample,
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
        onNewSample: (Double) -> Unit,
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

                val bufferSize = 64 * 1024
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
                    speedSamples.add(bytesRead to currentTime) // Amostra: (bytes lidos nesta iteração, tempo atual)

                    // Mantém apenas últimos 2 segundos de amostras para o cálculo da média
                    speedSamples.removeAll { (currentTime - it.second) > 2000 }

                    if (currentTime - lastReportTime >= reportIntervalMs) {
                        lastReportTime = currentTime

                        val mbps = calculateAverageSpeed(speedSamples)
                        val progress = if (contentLength > 0) {
                            min(100f, (totalBytesRead * 100f) / contentLength)
                        } else {
                            val elapsed = currentTime - startTime
                            min(100f, (elapsed * 100f) / testDurationMs)
                        }

                        withContext(Dispatchers.Main) {
                            onProgress(progress, mbps)
                            onNewSample(mbps) // Envia a amostra de velocidade suavizada para o gráfico
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
        onNewSample: (Double) -> Unit,
        reportIntervalMs: Int = 250,
        testDurationMs: Long = 6000L // Duração de 6 segundos para o upload
    ) {
        activeJob?.cancel()

        // LÓGICA DE CORREÇÃO: Verifica se existe um URL de upload explícito.
        val uploadUrl = server.uploadUrl
        if (uploadUrl == null) {
            // Se não houver URL, não tenta adivinhar. Em vez disso, pula o teste de upload
            // de forma graciosa, finalizando-o imediatamente com 0 Mbps.
            scope.launch(Dispatchers.Main) {
                onProgress(100f, 0.0) // Garante que a UI veja o progresso como completo
                onNewSample(0.0)
                onDone(0.0)
            }
            return
        }

        activeJob = scope.launch(Dispatchers.IO) {
            try {
                runUploadTest(
                    url = uploadUrl, // Usa o URL verificado
                    onProgress = onProgress,
                    onDone = onDone,
                    onError = onError,
                    onNewSample = onNewSample,
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
        onNewSample: (Double) -> Unit,
        reportIntervalMs: Int,
        testDurationMs: Long
    ) = withContext(Dispatchers.IO) {
        // A checagem de URL vazia/nula agora é feita em startUpload
        try {
            val chunkSize = 256 * 1024
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
                        speedSamples.add(chunkSize.toLong() to currentTime)
                        speedSamples.removeAll { (currentTime - it.second) > 2000 }

                        if (currentTime - lastReportTime >= reportIntervalMs) {
                            lastReportTime = currentTime

                            val mbps = calculateAverageSpeed(speedSamples)
                            val progress = min(100f, ((currentTime - startTime) * 100f) / testDurationMs)

                            withContext(Dispatchers.Main) {
                                onProgress(progress, mbps)
                                onNewSample(mbps)
                            }
                        }
                    } else {
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

    // Lógica de cálculo de velocidade aprimorada para usar uma média móvel.
    private fun calculateAverageSpeed(samples: List<Pair<Long, Long>>): Double {
        if (samples.isEmpty()) return 0.0

        val totalBytes = samples.sumOf { it.first }
        val firstTime = samples.first().second
        val lastTime = samples.last().second
        val timeDelta = lastTime - firstTime

        return if (timeDelta > 0) {
            (totalBytes * 8.0) / (timeDelta / 1000.0) / 1_000_000.0
        } else {
            0.0
        }
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null
    }
}

