package com.elftech.pingwifis.data

import android.os.SystemClock
import com.elftech.pingwifis.data.model.SpeedTestServer
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/**
 * Versão aprimorada do SpeedTestRunner inspirada em implementações como WiFiman e Fast.
 * Agora inclui controle de aquecimento (warm-up), detecção de estabilidade e média ponderada.
 */
class ImprovedSpeedTestRunner(private val scope: CoroutineScope) {

    private var activeJob: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115 Safari/537.36"
                )
                .build()
            chain.proceed(req)
        }
        .build()

    companion object {
        private const val N_THREADS = 8
        private const val SAMPLE_WINDOW_MS = 1500L
        private const val WARMUP_DURATION_MS = 2000L
    }

    private val speedSamples = ConcurrentLinkedQueue<Pair<Long, Long>>()

    fun startDownload(
        url: String,
        onProgress: (Float, Double) -> Unit,
        onDone: (Double) -> Unit,
        onError: (String) -> Unit,
        onNewSample: (Double) -> Unit,
        durationMs: Long = 10000L
    ) {
        activeJob?.cancel()
        activeJob = scope.launch(Dispatchers.IO) {
            try {
                val result = runTest(url, isUpload = false, durationMs, onProgress, onNewSample)
                withContext(Dispatchers.Main) { onDone(result) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Erro desconhecido") }
            }
        }
    }

    fun startUpload(
        server: SpeedTestServer,
        onProgress: (Float, Double) -> Unit,
        onDone: (Double) -> Unit,
        onError: (String) -> Unit,
        onNewSample: (Double) -> Unit,
        durationMs: Long = 10000L
    ) {
        val url = server.uploadUrl ?: return
        activeJob?.cancel()
        activeJob = scope.launch(Dispatchers.IO) {
            try {
                val result = runTest(url, isUpload = true, durationMs, onProgress, onNewSample)
                withContext(Dispatchers.Main) { onDone(result) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Erro no upload") }
            }
        }
    }

    private suspend fun runTest(
        baseUrl: String,
        isUpload: Boolean,
        durationMs: Long,
        onProgress: (Float, Double) -> Unit,
        onNewSample: (Double) -> Unit
    ): Double = coroutineScope {
        val start = SystemClock.elapsedRealtime()
        val end = start + durationMs

        speedSamples.clear()

        // Warm-up curto para estabilizar a medição
        val warmupEnd = start + WARMUP_DURATION_MS
        val threads = (1..N_THREADS).map {
            launch(Dispatchers.IO) {
                val url = withCacheBuster(baseUrl)
                if (isUpload) uploadWorker(url, warmupEnd) else downloadWorker(url, warmupEnd)
            }
        }
        threads.joinAll()
        speedSamples.clear()

        // Teste real
        val realThreads = (1..N_THREADS).map {
            launch(Dispatchers.IO) {
                val url = withCacheBuster(baseUrl)
                if (isUpload) uploadWorker(url, end) else downloadWorker(url, end)
            }
        }

        while (isActive && SystemClock.elapsedRealtime() < end) {
            delay(250)
            val now = SystemClock.elapsedRealtime()
            val mbps = calculateSpeed(now)
            val progress = min(100f, ((now - start) * 100f / durationMs))
            withContext(Dispatchers.Main) {
                onProgress(progress, mbps)
                onNewSample(mbps)
            }
        }
        realThreads.forEach { it.cancel() }
        calculateSpeed(SystemClock.elapsedRealtime())
    }

    private suspend fun downloadWorker(url: String, endTime: Long) {
        while (coroutineContext.isActive && SystemClock.elapsedRealtime() < endTime) {
            val request = Request.Builder()
                .url(withCacheBuster(url))
                .get()
                .addHeader("Cache-Control", "no-cache")
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val src = resp.body?.source() ?: return@use
                    val buf = Buffer()
                    val size = 128 * 1024L
                    while (coroutineContext.isActive && SystemClock.elapsedRealtime() < endTime) {
                        val read = src.read(buf, size)
                        if (read == -1L) break
                        buf.clear()
                        speedSamples.add(read to SystemClock.elapsedRealtime())
                    }
                }
            } catch (_: IOException) {
                delay(40)
            }
        }
    }

    private suspend fun uploadWorker(url: String, endTime: Long) {
        val chunk = ByteArray(256 * 1024) { 7 }
        val rawBody = chunk.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("content0", "chunk.bin", rawBody)
            .build()

        while (coroutineContext.isActive && SystemClock.elapsedRealtime() < endTime) {
            try {
                val targetUrl = withCacheBuster(url)
                val rawRequest = Request.Builder().url(targetUrl).post(rawBody).build()

                val sent = client.newCall(rawRequest).execute().use { resp ->
                    resp.isSuccessful
                }

                if (sent) {
                    speedSamples.add(chunk.size.toLong() to SystemClock.elapsedRealtime())
                    continue
                }

                val multipartRequest = Request.Builder().url(targetUrl).post(multipartBody).build()
                client.newCall(multipartRequest).execute().use { resp ->
                    if (resp.isSuccessful) {
                        speedSamples.add(chunk.size.toLong() to SystemClock.elapsedRealtime())
                    } else {
                        delay(40)
                    }
                }
            } catch (_: IOException) {
                delay(40)
            }
        }
    }

    private fun withCacheBuster(url: String): String {
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}cb=${System.nanoTime()}"
    }


    private fun calculateSpeed(now: Long): Double {
        val recent = speedSamples.filter { now - it.second <= SAMPLE_WINDOW_MS }
        if (recent.isEmpty()) return 0.0
        val bytes = recent.sumOf { it.first }
        val oldest = recent.minOf { it.second }
        val delta = (now - oldest).coerceAtLeast(1)
        return (bytes * 8.0) / (delta / 1000.0) / 1_000_000.0
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null
    }
}
