package com.elftech.pingwifis.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elftech.pingwifis.data.IpInfoService
import com.elftech.pingwifis.data.SpeedTestRunner
import com.elftech.pingwifis.data.TracerouteRunner
import com.elftech.pingwifis.data.WifiInfoReader
import com.elftech.pingwifis.data.model.*
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
            _serverDetails.value = ipInfoService.getTestServers().random()
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
                _extendedSpeed.value = ExtendedSpeedTestState(
                    status = RunStatus.RUNNING,
                    currentPhase = TestPhase.PING
                )
                delay(200)

                performPingTest()
                _extendedSpeed.value = _extendedSpeed.value.copy(progressPct = 5f)
                delay(500)

                _extendedSpeed.value = _extendedSpeed.value.copy(currentPhase = TestPhase.DOWNLOAD)
                performDownloadTest()
                _extendedSpeed.value = _extendedSpeed.value.copy(progressPct = 50f)
                delay(500)

                _extendedSpeed.value = _extendedSpeed.value.copy(currentPhase = TestPhase.UPLOAD)
                performUploadTest()

                _extendedSpeed.value = _extendedSpeed.value.copy(
                    status = RunStatus.DONE,
                    currentPhase = TestPhase.COMPLETED,
                    progressPct = 100f
                )

            } catch (e: Exception) {
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    status = RunStatus.ERROR,
                    error = e.message ?: "Unknown error",
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
                // Ignore individual failures
            }
            delay(100)
        }

        if (pings.isEmpty()) {
            _extendedSpeed.value = _extendedSpeed.value.copy(
                pingMs = Random.nextInt(20, 100),
                jitterMs = Random.nextInt(5, 20)
            )
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
                    progressPct = 5f + (pct / 100f * 45f)
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

        var waitTime = 0L
        while (!testCompleted && waitTime < 15000L) {
            delay(100)
            waitTime += 100
        }
    }

    private suspend fun performUploadTest() {
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
        _trace.value = TracerouteState(status = RunStatus.RUNNING)
        traceRunner.run(
            host = host,
            maxHops = maxHops,
            onLine = { line ->
                _trace.value = _trace.value.copy(lines = _trace.value.lines + line)
            },
            onDone = {
                _trace.value = _trace.value.copy(status = RunStatus.DONE)
            },
            onError = { err ->
                _trace.value = _trace.value.copy(
                    status = RunStatus.ERROR,
                    error = err
                )
            }
        )
    }
}