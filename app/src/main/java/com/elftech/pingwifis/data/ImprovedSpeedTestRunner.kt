package com.elftech.pingwifis.data

import android.os.SystemClock
import com.elftech.pingwifis.data.model.SpeedTestServer
import kotlinx.coroutines.*
import okhttp3.*
// Imports adicionados:
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/**
 * Versão Multi-Thread (Multi-Conexão) do SpeedTestRunner.
 *
 * Esta classe emula o comportamento de apps profissionais (como WiFiman/Speedtest.net)
 * ao lançar MÚLTIPLAS conexões paralelas (N_THREADS) para saturar a banda
 * e descobrir a velocidade máxima real da conexão.
 */
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

    // Define o número de conexões paralelas
    companion object {
        private const val N_THREADS = 8
        private const val DOWNLOAD_URL_QUERY = "?nthreads=$N_THREADS" // Alguns servidores usam isso
    }

    // Fila segura para coletar amostras de velocidade de todas as threads
    private val speedSamples = ConcurrentLinkedQueue<Pair<Long, Long>>()

    fun startDownload(
        url: String,
        onProgress: (Float, Double) -> Unit,
        onDone: (Double) -> Unit,
        onError: (String) -> Unit,
        onNewSample: (Double) -> Unit,
        reportIntervalMs: Int = 250,
        testDurationMs: Long = 10000L
    ) {
        activeJob?.cancel()

        activeJob = scope.launch(Dispatchers.IO) {
            try {
                speedSamples.clear()
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
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        onError(e.message ?: "Erro desconhecido")
                    }
                }
            }
        }
    }

    /**
     * Gerenciador de Download Multi-Thread.
     * Esta função lança N_THREADS de downloads em paralelo e,
     * em um loop principal, coleta e reporta a velocidade agregada.
     */
    private suspend fun runDownloadTest(
        url: String,
        onProgress: (Float, Double) -> Unit,
        onDone: (Double) -> Unit,
        onError: (String) -> Unit,
        onNewSample: (Double) -> Unit,
        reportIntervalMs: Int,
        testDurationMs: Long
    ) = coroutineScope { // Cria um escopo para gerenciar os jobs filhos
        val startTime = SystemClock.elapsedRealtime()
        val endTime = startTime + testDurationMs
        var totalBytesRead = 0L

        // Lança N_THREADS de downloads em paralelo
        val downloadJobs = (1..N_THREADS).map {
            launch(Dispatchers.IO) {
                // Cada thread tem sua própria conexão
                // Adiciona uma query aleatória para evitar cache de
                val uniqueUrl = "$url$DOWNLOAD_URL_QUERY&rand=${System.nanoTime()}&ckSize=0.5"
                runSingleDownloadInstance(uniqueUrl, endTime)
            }
        }

        // Loop principal de relatório (o "manager")
        var lastReportTime = startTime
        // 'isActive' aqui está correto, pois estamos dentro de um coroutineScope
        while (isActive && SystemClock.elapsedRealtime() < endTime) {
            delay(reportIntervalMs.toLong())

            val currentTime = SystemClock.elapsedRealtime()
            // Limpa amostras antigas (média móvel de 2 seg)
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
        }

        // Garante que todos os jobs de download sejam cancelados ao final
        downloadJobs.forEach { it.cancel() }

        // Coleta o total de bytes de todas as amostras
        // Nota: Esta é uma aproximação. O cálculo final real é a média das últimas amostras.
        val finalMbps = calculateAverageSpeed(speedSamples)

        // Limpa as amostras para o teste de upload
        speedSamples.clear()

        withContext(Dispatchers.Main) {
            onDone(finalMbps)
        }
    }

    /**
     * Executa uma *única* instância de download.
     * Esta função é chamada N_THREADS vezes em paralelo.
     */
    private suspend fun runSingleDownloadInstance(url: String, endTime: Long) {
        val request = Request.Builder()
            .url(url)
            .addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            .addHeader("Pragma", "no-cache")
            .addHeader("Expires", "0")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return // Ignora falhas silenciosamente

                val body = response.body ?: return
                val source = body.source()
                val buffer = Buffer()
                val bufferSize = 64 * 1024 // 64K

                while (coroutineContext.isActive && SystemClock.elapsedRealtime() < endTime) {
                    val bytesRead = source.read(buffer, bufferSize.toLong())
                    if (bytesRead == -1L) break

                    buffer.clear()
                    // Reporta os bytes lidos e o tempo para a fila segura
                    speedSamples.add(bytesRead to SystemClock.elapsedRealtime())
                }
            }
        } catch (e: IOException) {
            // Ignora erros de conexão em threads individuais
        }
    }


    fun startUpload(
        server: SpeedTestServer,
        onProgress: (Float, Double) -> Unit,
        onDone: (Double) -> Unit,
        onError: (String) -> Unit,
        onNewSample: (Double) -> Unit,
        reportIntervalMs: Int = 250,
        testDurationMs: Long = 10000L // 10 segundos
    ) {
        activeJob?.cancel()

        val uploadUrl = server.uploadUrl
        if (uploadUrl == null) {
            scope.launch(Dispatchers.Main) {
                onProgress(100f, 0.0); onNewSample(0.0); onDone(0.0)
            }
            return
        }

        activeJob = scope.launch(Dispatchers.IO) {
            try {
                speedSamples.clear()
                runUploadTest(
                    url = uploadUrl,
                    onProgress = onProgress,
                    onDone = onDone,
                    onError = onError,
                    onNewSample = onNewSample,
                    reportIntervalMs = reportIntervalMs,
                    testDurationMs = testDurationMs
                )
            } catch (e: Exception) {
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        onError(e.message ?: "Erro no upload")
                    }
                }
            }
        }
    }

    /**
     * Gerenciador de Upload Multi-Thread.
     */
    private suspend fun runUploadTest(
        url: String,
        onProgress: (Float, Double) -> Unit,
        onDone: (Double) -> Unit,
        onError: (String) -> Unit,
        onNewSample: (Double) -> Unit,
        reportIntervalMs: Int,
        testDurationMs: Long
    ) = coroutineScope {
        val startTime = SystemClock.elapsedRealtime()
        val endTime = startTime + testDurationMs

        // Lança N_THREADS de uploads em paralelo
        val uploadJobs = (1..N_THREADS).map {
            launch(Dispatchers.IO) {
                val uniqueUrl = "$url?rand=${System.nanoTime()}"
                runSingleUploadInstance(uniqueUrl, endTime)
            }
        }

        // Loop principal de relatório
        var lastReportTime = startTime
        while (isActive && SystemClock.elapsedRealtime() < endTime) {
            delay(reportIntervalMs.toLong())

            val currentTime = SystemClock.elapsedRealtime()
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
        }

        uploadJobs.forEach { it.cancel() }
        val finalMbps = calculateAverageSpeed(speedSamples)
        speedSamples.clear()

        withContext(Dispatchers.Main) {
            onDone(finalMbps)
        }
    }

    /**
     * Executa uma *única* instância de upload.
     */
    private suspend fun runSingleUploadInstance(url: String, endTime: Long) {
        try {
            val chunkSize = 256 * 1024 // 256K
            val dataChunk = ByteArray(chunkSize) { 0 }
            val requestBody = dataChunk.toRequestBody("application/octet-stream".toMediaType())

            while (coroutineContext.isActive && SystemClock.elapsedRealtime() < endTime) {
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .addHeader("Cache-Control", "no-cache")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        // Servidor rejeitou, para esta thread
                        return
                    }
                    // Reporta sucesso no upload do chunk
                    speedSamples.add(chunkSize.toLong() to SystemClock.elapsedRealtime())
                }
            }
        } catch (e: IOException) {
            // Ignora
        }
    }

    /**
     * Calcula a velocidade média com base nas amostras (bytes, timestamp)
     * coletadas de TODAS as threads.
     */
    private fun calculateAverageSpeed(samples: Collection<Pair<Long, Long>>): Double {
        if (samples.isEmpty()) return 0.0

        // Pega apenas amostras dos últimos 2 segundos
        val currentTime = SystemClock.elapsedRealtime()
        val recentSamples = samples.filter { (currentTime - it.second) <= 2000 }
        if (recentSamples.isEmpty()) return 0.0

        val totalBytes = recentSamples.sumOf { it.first }
        val firstTime = recentSamples.minOf { it.second }
        val lastTime = recentSamples.maxOf { it.second }
        val timeDelta = (lastTime - firstTime).coerceAtLeast(1) // Evita divisão por zero

        return (totalBytes * 8.0) / (timeDelta / 1000.0) / 1_000_000.0
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null
    }
}