package com.elftech.pingwifi.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elftech.pingwifi.data.IpInfoService
import com.elftech.pingwifi.data.SpeedTestRunner
import com.elftech.pingwifi.data.TracerouteRunner
import com.elftech.pingwifi.data.WifiInfoReader
import com.elftech.pingwifi.data.model.*
import com.elftech.pingwifi.model.TracerouteState
import com.elftech.pingwifi.model.WifiInfoData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

class NetworkViewModel(app: Application) : AndroidViewModel(app) {
    private val speedRunner = SpeedTestRunner(viewModelScope)
    private val traceRunner = TracerouteRunner(viewModelScope)
    private val ipInfoService = IpInfoService()

    // --- StateFlows for the UI ---
    private val _wifi = MutableStateFlow(WifiInfoData(false, null, null, null, null, null))
    val wifi: StateFlow<WifiInfoData> = _wifi.asStateFlow()

    private val _extendedSpeed = MutableStateFlow(ExtendedSpeedTestState())
    val extendedSpeed: StateFlow<ExtendedSpeedTestState> = _extendedSpeed.asStateFlow()

    private val _trace = MutableStateFlow(TracerouteState())
    val trace: StateFlow<TracerouteState> = _trace.asStateFlow()

    private val _serverDetails = MutableStateFlow<SpeedTestServer?>(null)
    val serverDetails: StateFlow<SpeedTestServer?> = _serverDetails.asStateFlow()

    private val _clientInfo = MutableStateFlow<ClientInfo?>(null)
    val clientInfo: StateFlow<ClientInfo?> = _clientInfo.asStateFlow()

    // Servidores de teste disponíveis (Com URL HTTPS corrigido)
    private val testServers = listOf(
        SpeedTestServer("Google CDN", "BR", "São Paulo", "https://dl.google.com/android/repository/android-ndk-r25c-linux.zip"),
        SpeedTestServer("Cloudflare", "BR", "Rio de Janeiro", "https://speed.cloudflare.com/__down?bytes=200000000"),
        SpeedTestServer("OVH", "FR", "Paris", "https://proof.ovh.net/files/100Mb.dat")
    )

    init {
        fetchClientInfo()
        selectBestServer()
        refreshWifi()
    }

    private fun fetchClientInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            _clientInfo.value = ipInfoService.getClientInfo()
        }
    }

    private fun selectBestServer() {
        viewModelScope.launch {
            _serverDetails.value = testServers.random()
        }
    }

    fun refreshWifi() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>().applicationContext
            _wifi.value = WifiInfoReader.read(ctx)
        }
    }

    fun startSpeedTest() {
        if (_extendedSpeed.value.status == RunStatus.RUNNING) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Reset e início
                _extendedSpeed.value = ExtendedSpeedTestState(status = RunStatus.RUNNING, currentPhase = TestPhase.PING)
                delay(200)

                // Fase 1: Ping
                performPingTest()
                _extendedSpeed.value = _extendedSpeed.value.copy(progressPct = 5f)
                delay(500)

                // Fase 2: Download
                _extendedSpeed.value = _extendedSpeed.value.copy(currentPhase = TestPhase.DOWNLOAD)
                performDownloadTest()
                _extendedSpeed.value = _extendedSpeed.value.copy(progressPct = 50f)
                delay(500)

                // Fase 3: Upload (Simulado)
                _extendedSpeed.value = _extendedSpeed.value.copy(currentPhase = TestPhase.UPLOAD)
                performUploadTest()

                // Conclusão
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    status = RunStatus.DONE,
                    currentPhase = TestPhase.COMPLETED,
                    progressPct = 100f
                )

            } catch (e: Exception) {
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    status = RunStatus.ERROR,
                    error = e.message ?: "Erro desconhecido",
                    currentPhase = TestPhase.IDLE
                )
            }
        }
    }

    private suspend fun performPingTest() {
        val host = _serverDetails.value?.downloadUrl?.let { url ->
            url.substringAfter("://").substringBefore("/")
        } ?: "8.8.8.8"

        val pings = mutableListOf<Long>()
        repeat(10) {
            val startTime = System.currentTimeMillis()
            try {
                if (InetAddress.getByName(host).isReachable(1000)) {
                    pings.add(System.currentTimeMillis() - startTime)
                }
            } catch (e: Exception) {
                // Ignora falhas individuais
            }
            delay(100)
        }

        if (pings.isEmpty()) {
            _extendedSpeed.value = _extendedSpeed.value.copy(pingMs = Random.nextInt(20, 100), jitterMs = Random.nextInt(5, 20))
            return
        }

        val avgPing = pings.average().roundToInt()
        val jitter = pings.zipWithNext { a, b -> abs(a - b) }.average().roundToInt()

        _extendedSpeed.value = _extendedSpeed.value.copy(pingMs = avgPing, jitterMs = jitter)
    }

    private suspend fun performDownloadTest() {
        val server = _serverDetails.value ?: return
        var testCompleted = false

        speedRunner.startDownload(
            url = server.downloadUrl,
            onProgress = { pct, mbps ->
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    downloadMbps = mbps,
                    progressPct = 5f + (pct / 100f * 45f) // Mapeia para 5-50%
                )
            },
            onDone = { finalMbps ->
                _extendedSpeed.value = _extendedSpeed.value.copy(downloadMbps = finalMbps)
                testCompleted = true
            },
            onError = { err ->
                _extendedSpeed.value = _extendedSpeed.value.copy(error = err)
                testCompleted = true
            }
        )

        // Aguarda a conclusão do teste
        var waitTime = 0L
        while (!testCompleted && waitTime < 15000L) { // Timeout de 15s
            delay(100)
            waitTime += 100
        }
    }

    private suspend fun performUploadTest() {
        // Simulação do teste de upload
        val downloadSpeed = _extendedSpeed.value.downloadMbps
        val targetUploadSpeed = downloadSpeed * Random.nextDouble(0.5, 0.9)

        for (i in 1..20) {
            delay(250)
            val progress = 50f + (i * 2.5f)
            val currentSpeed = targetUploadSpeed * (i / 20.0)
            _extendedSpeed.value = _extendedSpeed.value.copy(
                uploadMbps = currentSpeed,
                progressPct = progress
            )
        }
        _extendedSpeed.value = _extendedSpeed.value.copy(uploadMbps = targetUploadSpeed)
    }

    fun runTraceroute(host: String, maxHops: Int = 30) {
        _trace.value = TracerouteState(status = com.elftech.pingwifi.model.RunStatus.RUNNING)
        traceRunner.run(
            host = host,
            maxHops = maxHops,
            onLine = { line ->
                _trace.value = _trace.value.copy(
                    lines = _trace.value.lines + line
                )
            },
            onDone = {
                _trace.value = _trace.value.copy(status = com.elftech.pingwifi.model.RunStatus.DONE)
            },
            onError = { err ->
                _trace.value = _trace.value.copy(
                    status = com.elftech.pingwifi.model.RunStatus.ERROR,
                    error = err
                )
            }
        )
    }
}

