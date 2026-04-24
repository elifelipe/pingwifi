package com.elftech.pingwifis.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elftech.pingwifis.data.*
import com.elftech.pingwifis.data.model.*
import com.elftech.pingwifis.widget.PingWifiWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class EnhancedNetworkViewModel(app: Application) : AndroidViewModel(app) {
    private val speedRunner = ImprovedSpeedTestRunner(viewModelScope)
    private val traceRunner = TracerouteRunner(viewModelScope)
    private val pingRunner = PingTestRunner(viewModelScope)
    private val downPingRunner = DownPingRunner(viewModelScope)
    private val networkScanner = NetworkScanner(viewModelScope)
    private val dnsTestRunner = DnsTestRunner(viewModelScope)
    private val wifiChannelAnalyzer = WifiChannelAnalyzer(app.applicationContext, viewModelScope)
    private val qualityNotifHelper = QualityNotificationHelper(app.applicationContext)

    private val prefs = app.getSharedPreferences("pingwifi_prefs", Context.MODE_PRIVATE)

    private val _networkScan = MutableStateFlow(NetworkScanState())
    val networkScan: StateFlow<NetworkScanState> = _networkScan.asStateFlow()

    private val ipInfoService = EnhancedIpInfoService()
    private val connectionManager = ConnectionStatusManager(app.applicationContext)

    private val _wifi = MutableStateFlow(WifiInfoData(false, null, null, null, null, null))
    val wifi: StateFlow<WifiInfoData> = _wifi.asStateFlow()

    private val _extendedSpeed = MutableStateFlow(ExtendedSpeedTestState())
    val extendedSpeed: StateFlow<ExtendedSpeedTestState> = _extendedSpeed.asStateFlow()

    private val _trace = MutableStateFlow(TracerouteState())
    val trace: StateFlow<TracerouteState> = _trace.asStateFlow()

    private val _ping = MutableStateFlow(PingTestState())
    val ping: StateFlow<PingTestState> = _ping.asStateFlow()

    private val _downPing = MutableStateFlow(DownPingState())
    val downPing: StateFlow<DownPingState> = _downPing.asStateFlow()

    private val _serverDetails = MutableStateFlow<SpeedTestServer?>(null)
    val serverDetails: StateFlow<SpeedTestServer?> = _serverDetails.asStateFlow()

    private val _clientInfo = MutableStateFlow<ClientInfo?>(null)
    val clientInfo: StateFlow<ClientInfo?> = _clientInfo.asStateFlow()

    private val _availableServers = MutableStateFlow<List<SpeedTestServer>>(emptyList())
    val availableServers: StateFlow<List<SpeedTestServer>> = _availableServers.asStateFlow()

    private var nearbyServersCache: List<SpeedTestServer> = emptyList()
    private var cdnServersCache: List<SpeedTestServer> = emptyList()

    private val _isLoadingServers = MutableStateFlow(false)
    val isLoadingServers: StateFlow<Boolean> = _isLoadingServers.asStateFlow()

    private val _connectionStatus = MutableStateFlow(connectionManager.getCurrentStatus())
    val connectionStatus: StateFlow<com.elftech.pingwifis.data.ConnectionStatus> =
        _connectionStatus.asStateFlow()

    private val _testMessage = MutableStateFlow("Preparando teste...")
    val testMessage: StateFlow<String> = _testMessage.asStateFlow()

    // Último teste salvo
    private val _lastTestResult = MutableStateFlow<LastTestResult?>(loadLastTestResult())
    val lastTestResult: StateFlow<LastTestResult?> = _lastTestResult.asStateFlow()

    // VPN
    private val _vpnInfo = MutableStateFlow(VpnInfo(false))
    val vpnInfo: StateFlow<VpnInfo> = _vpnInfo.asStateFlow()

    // DNS Test
    private val _dnsTestState = MutableStateFlow(DnsTestState())
    val dnsTestState: StateFlow<DnsTestState> = _dnsTestState.asStateFlow()

    // WiFi Channels
    private val _wifiChannelState = MutableStateFlow(WifiChannelState())
    val wifiChannelState: StateFlow<WifiChannelState> = _wifiChannelState.asStateFlow()

    // Quality settings
    private val _qualitySettings = MutableStateFlow(loadQualitySettings())
    val qualitySettings: StateFlow<QualitySettings> = _qualitySettings.asStateFlow()

    init {
        observeConnection()
        initializeApp()
        refreshVpnStatus()
    }

    private fun observeConnection() {
        viewModelScope.launch {
            connectionManager.observeConnectionStatus().collect { status ->
                val wasConnected = _connectionStatus.value.isConnected
                _connectionStatus.value = status

                if (status.isConnected && !wasConnected) {
                    initializeApp()
                    refreshVpnStatus()
                } else if (!status.isConnected && _extendedSpeed.value.status == RunStatus.RUNNING) {
                    stopTest()
                    _extendedSpeed.value = _extendedSpeed.value.copy(
                        status = RunStatus.ERROR,
                        error = "Conexão perdida durante o teste"
                    )
                }
            }
        }
    }

    fun refreshVpnStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            _vpnInfo.value = VpnDetector.detect(getApplication<Application>().applicationContext)
        }
    }

    fun startDnsTest(hostname: String = _dnsTestState.value.target) {
        if (hostname.isBlank()) return
        _dnsTestState.value = DnsTestState(
            status = RunStatus.RUNNING,
            results = emptyList(),
            target = hostname
        )
        dnsTestRunner.runTest(
            hostname = hostname,
            onResult = { result ->
                _dnsTestState.value = _dnsTestState.value.copy(
                    results = _dnsTestState.value.results + result
                )
            },
            onComplete = {
                _dnsTestState.value = _dnsTestState.value.copy(status = RunStatus.DONE)
            },
            onError = { err ->
                _dnsTestState.value = _dnsTestState.value.copy(
                    status = RunStatus.ERROR,
                    results = _dnsTestState.value.results
                )
                Log.e("EnhancedNetVM", "DNS test error: $err")
            }
        )
    }

    fun updateDnsTarget(target: String) {
        _dnsTestState.value = _dnsTestState.value.copy(target = target)
    }

    fun scanWifiChannels() {
        _wifiChannelState.value = WifiChannelState(status = RunStatus.RUNNING)
        wifiChannelAnalyzer.analyze(
            currentFrequencyMhz = _wifi.value.frequencyMhz,
            onComplete = { state -> _wifiChannelState.value = state },
            onError = { err ->
                _wifiChannelState.value = WifiChannelState(
                    status = RunStatus.ERROR,
                    error = err
                )
            }
        )
    }

    fun updateQualitySettings(settings: QualitySettings) {
        _qualitySettings.value = settings
        prefs.edit()
            .putFloat("quality_threshold", settings.minDownloadMbps)
            .putBoolean("quality_notifications", settings.notificationsEnabled)
            .apply()
    }

    fun startNetworkScan() {
        if (!_connectionStatus.value.isConnected) {
            _networkScan.value = NetworkScanState(
                status = RunStatus.ERROR, error = "Sem conexão à internet"
            )
            return
        }

        val networkPrefix = getNetworkPrefix()
        if (networkPrefix == null) {
            _networkScan.value = NetworkScanState(
                status = RunStatus.ERROR, error = "Não foi possível determinar a rede local"
            )
            return
        }

        _networkScan.value = NetworkScanState(status = RunStatus.RUNNING, devices = emptyList())

        networkScanner.startNetworkScan(
            networkPrefix = networkPrefix,
            onProgress = { progress, device ->
                _networkScan.value = _networkScan.value.copy(
                    progress = progress,
                    scanningDevice = device,
                    devices = if (device != null && !_networkScan.value.devices.any { it.ipAddress == device.ipAddress }) {
                        _networkScan.value.devices + device
                    } else {
                        _networkScan.value.devices
                    }
                )
            },
            onComplete = { devices ->
                _networkScan.value = NetworkScanState(
                    status = RunStatus.DONE, devices = devices, progress = 100
                )
            },
            onError = { error ->
                _networkScan.value = NetworkScanState(status = RunStatus.ERROR, error = error)
            }
        )
    }

    fun stopNetworkScan() {
        networkScanner.stopScan()
        if (_networkScan.value.status == RunStatus.RUNNING) {
            _networkScan.value = _networkScan.value.copy(status = RunStatus.IDLE)
        }
    }

    private fun getNetworkPrefix(): String? {
        return try {
            val ctx = getApplication<Application>().applicationContext
            val wifi = android.net.wifi.WifiManager::class.java.cast(
                ctx.getSystemService(android.content.Context.WIFI_SERVICE)
            )
            val dhcpInfo = wifi?.dhcpInfo
            if (dhcpInfo != null) {
                val ip = dhcpInfo.ipAddress
                String.format("%d.%d.%d", ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff)
            } else null
        } catch (e: Exception) {
            Log.e("EnhancedNetVM", "Failed to get network prefix", e)
            null
        }
    }

    private fun initializeApp() {
        viewModelScope.launch {
            _isLoadingServers.value = true
            _extendedSpeed.value = ExtendedSpeedTestState(status = RunStatus.IDLE)

            try {
                if (!_connectionStatus.value.isConnected) {
                    _testMessage.value = "Sem conexão. Verifique a internet."
                    _isLoadingServers.value = false
                    return@launch
                }

                _testMessage.value = "Identificando sua conexão..."
                var info = ipInfoService.getClientInfo()

                if (info == null) {
                    _clientInfo.value = null
                    throw Exception("Não foi possível obter informações da sua conexão.")
                }

                if (info.city.equals("Unknown", ignoreCase = true)) {
                    info = info.copy(city = "Localização")
                }

                _clientInfo.value = info
                delay(200)

                _testMessage.value = "Buscando provedores próximos e CDNs..."
                val allServers = ipInfoService.getNearbyServers(info)

                nearbyServersCache = allServers.filterNot { ipInfoService.isCdnServer(it) }
                cdnServersCache = allServers.filter { ipInfoService.isCdnServer(it) }
                _availableServers.value =
                    if (nearbyServersCache.isNotEmpty()) nearbyServersCache else cdnServersCache
                _testMessage.value =
                    "Servidores: ${nearbyServersCache.size} próximos, ${cdnServersCache.size} CDN"

                if (_availableServers.value.isEmpty()) {
                    throw Exception("Nenhum servidor de teste encontrado.")
                }
                delay(200)

                _testMessage.value = "Selecionando o melhor servidor..."
                selectBestServerByPing()
                delay(200)

                refreshWifi()
                _testMessage.value = "Pronto para testar!"
            } catch (e: Exception) {
                Log.e("EnhancedNetVM", "Erro na inicialização", e)
                _testMessage.value = "Erro: ${e.message}"
                _serverDetails.value = SpeedTestServer(
                    "Cloudflare", "Global", "Worldwide",
                    "https://speed.cloudflare.com/__down?bytes=50000000",
                    "https://speed.cloudflare.com/__up"
                )
            } finally {
                _isLoadingServers.value = false
            }
        }
    }

    private fun selectBestServerByPing() {
        viewModelScope.launch(Dispatchers.IO) {
            val serversToTest = _availableServers.value.take(5)
            if (serversToTest.isEmpty()) {
                _serverDetails.value = _availableServers.value.firstOrNull()
                return@launch
            }

            val serverPings = serversToTest.map { server ->
                val ping = ipInfoService.pingServer(server)
                server to ping
            }

            val bestServerPair = serverPings
                .filter { it.second < 9999 }
                .minByOrNull { it.second }

            _serverDetails.value = bestServerPair?.first ?: serversToTest.first()
        }
    }

    fun refreshWifi() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>().applicationContext
            _wifi.value = WifiInfoReader.read(ctx)
        }
    }

    fun startSpeedTest() {
        if (!_connectionStatus.value.isConnected) {
            _extendedSpeed.value =
                _extendedSpeed.value.copy(status = RunStatus.ERROR, error = "Sem conexão à internet")
            return
        }
        if (_extendedSpeed.value.status == RunStatus.RUNNING) return

        val server = _serverDetails.value
        if (server == null) {
            _extendedSpeed.value = _extendedSpeed.value.copy(
                status = RunStatus.ERROR, error = "Nenhum servidor de teste disponível."
            )
            return
        }

        viewModelScope.launch {
            try {
                _extendedSpeed.value = ExtendedSpeedTestState(
                    status = RunStatus.RUNNING,
                    currentPhase = TestPhase.PING,
                    downloadSpeedSamples = emptyList(),
                    uploadSpeedSamples = emptyList()
                )
                _testMessage.value = "Medindo latência e jitter..."
                delay(200)

                performPingTest(server)
                delay(500)

                _extendedSpeed.value = _extendedSpeed.value.copy(currentPhase = TestPhase.DOWNLOAD)
                _testMessage.value = "Testando velocidade de download..."
                performDownloadTest(server)
                delay(500)

                _extendedSpeed.value = _extendedSpeed.value.copy(currentPhase = TestPhase.UPLOAD)
                _testMessage.value = "Testando velocidade de upload..."
                performUploadTest(server)

                _testMessage.value = "Teste concluído!"
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    status = RunStatus.DONE, currentPhase = TestPhase.COMPLETED, progressPct = 100f
                )

                val result = LastTestResult(
                    downloadMbps = _extendedSpeed.value.downloadMbps,
                    uploadMbps = _extendedSpeed.value.uploadMbps,
                    pingMs = _extendedSpeed.value.pingMs,
                    jitterMs = _extendedSpeed.value.jitterMs,
                    serverName = "${server.name} – ${server.city}"
                )
                _lastTestResult.value = result
                saveLastTestResult(result)

                val settings = _qualitySettings.value
                if (settings.notificationsEnabled &&
                    result.downloadMbps < settings.minDownloadMbps
                ) {
                    qualityNotifHelper.showLowSpeedAlert(result.downloadMbps, settings.minDownloadMbps)
                }

            } catch (e: Exception) {
                _testMessage.value = "Erro durante o teste."
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    status = RunStatus.ERROR, error = e.message, currentPhase = TestPhase.IDLE
                )
            }
        }
    }

    private suspend fun performPingTest(server: SpeedTestServer) {
        val pings = mutableListOf<Long>()
        repeat(10) {
            try {
                val pingTime = ipInfoService.pingServer(server)
                if (pingTime < 9999) pings.add(pingTime.toLong())
            } catch (e: Exception) { Log.w("EnhancedNetVM", "Ping falhou: ${e.message}") }
            delay(100)
        }

        val avgPing = if (pings.isNotEmpty()) pings.average().roundToInt() else 999
        val jitter = if (pings.size > 1) pings.zipWithNext { a, b -> abs(a - b) }.average().roundToInt() else 999
        _extendedSpeed.value = _extendedSpeed.value.copy(pingMs = avgPing, jitterMs = jitter, progressPct = 10f)
    }

    private suspend fun performDownloadTest(server: SpeedTestServer) {
        var testCompleted = false
        speedRunner.startDownload(
            url = server.downloadUrl,
            onProgress = { pct, mbps ->
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    downloadMbps = mbps, progressPct = 10f + (pct / 100f * 40f)
                )
            },
            onDone = { finalMbps ->
                _extendedSpeed.value = _extendedSpeed.value.copy(downloadMbps = finalMbps, progressPct = 50f)
                testCompleted = true
            },
            onError = { err ->
                _extendedSpeed.value = _extendedSpeed.value.copy(error = err)
                testCompleted = true
            },
            onNewSample = { sample ->
                val currentSamples = _extendedSpeed.value.downloadSpeedSamples
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    downloadSpeedSamples = (currentSamples + sample).takeLast(100)
                )
            }
        )
        var waitTime = 0L
        while (!testCompleted && waitTime < 15000L) { delay(100); waitTime += 100 }
    }

    private suspend fun performUploadTest(server: SpeedTestServer) {
        var testCompleted = false
        speedRunner.startUpload(
            server = server,
            onProgress = { pct, mbps ->
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    uploadMbps = mbps, progressPct = 50f + (pct / 100f * 50f)
                )
            },
            onDone = { finalMbps ->
                _extendedSpeed.value = _extendedSpeed.value.copy(uploadMbps = finalMbps, progressPct = 100f)
                testCompleted = true
            },
            onError = { err ->
                Log.w("EnhancedNetVM", "Upload falhou: $err")
                _extendedSpeed.value = _extendedSpeed.value.copy(progressPct = 100f)
                testCompleted = true
            },
            onNewSample = { sample ->
                val currentSamples = _extendedSpeed.value.uploadSpeedSamples
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    uploadSpeedSamples = (currentSamples + sample).takeLast(100)
                )
            }
        )
        var waitTime = 0L
        while (!testCompleted && waitTime < 15000L) { delay(100); waitTime += 100 }
    }

    fun stopTest() {
        speedRunner.stop()
        traceRunner.stop()
        pingRunner.stop()
        downPingRunner.stop()
    }

    fun runTraceroute(host: String, maxHops: Int = 30) {
        _trace.value = TracerouteState(status = RunStatus.RUNNING)
        traceRunner.run(host, maxHops,
            { line -> _trace.value = _trace.value.copy(lines = _trace.value.lines + line) },
            { _trace.value = _trace.value.copy(status = RunStatus.DONE) },
            { err -> _trace.value = _trace.value.copy(status = RunStatus.ERROR, error = err) }
        )
    }

    fun updatePingHost(host: String) {
        _ping.value = _ping.value.copy(host = host)
    }

    fun startPingTest(host: String, count: Int = 10) {
        if (!_connectionStatus.value.isConnected) {
            _ping.value = _ping.value.copy(status = RunStatus.ERROR, error = "Sem conexão à internet")
            return
        }
        if (host.isBlank()) {
            _ping.value = _ping.value.copy(status = RunStatus.ERROR, error = "Digite um endereço válido")
            return
        }

        _ping.value = PingTestState(status = RunStatus.RUNNING, host = host)

        pingRunner.runPingTest(
            host = host, count = count,
            onProgress = { result ->
                _ping.value = _ping.value.copy(results = _ping.value.results + result)
            },
            onComplete = { summary ->
                _ping.value = _ping.value.copy(status = RunStatus.DONE, summary = summary)
            },
            onError = { error ->
                _ping.value = _ping.value.copy(status = RunStatus.ERROR, error = error)
            }
        )
    }

    fun stopPingTest() {
        pingRunner.stop()
        if (_ping.value.status == RunStatus.RUNNING) {
            _ping.value = _ping.value.copy(status = RunStatus.IDLE)
        }
    }

    fun updateDownPingTarget(target: String) {
        _downPing.value = _downPing.value.copy(target = target)
    }

    fun startDownPing(target: String, pingCount: Int = 6) {
        if (!_connectionStatus.value.isConnected) {
            _downPing.value = _downPing.value.copy(
                status = RunStatus.ERROR, error = "Sem conexão à internet"
            )
            return
        }
        if (target.isBlank()) {
            _downPing.value = _downPing.value.copy(
                status = RunStatus.ERROR, error = "Digite um IP ou domínio válido"
            )
            return
        }

        _downPing.value = DownPingState(
            status = RunStatus.RUNNING, target = target, progressMessage = "Preparando diagnóstico..."
        )

        downPingRunner.run(
            target = target, pingCount = pingCount.coerceIn(3, 20),
            onProgress = { message ->
                _downPing.value = _downPing.value.copy(progressMessage = message)
            },
            onPingProgress = { result ->
                _downPing.value = _downPing.value.copy(
                    pingResults = _downPing.value.pingResults + result
                )
            },
            onComplete = { report ->
                _downPing.value = _downPing.value.copy(
                    status = RunStatus.DONE, progressMessage = "Diagnóstico concluído", report = report
                )
            },
            onError = { error ->
                _downPing.value = _downPing.value.copy(
                    status = RunStatus.ERROR, progressMessage = "Falha no diagnóstico", error = error
                )
            }
        )
    }

    fun stopDownPing() {
        downPingRunner.stop()
        if (_downPing.value.status == RunStatus.RUNNING) {
            _downPing.value = _downPing.value.copy(
                status = RunStatus.IDLE, progressMessage = "Diagnóstico interrompido"
            )
        }
    }

    fun changeServer(server: SpeedTestServer) {
        _serverDetails.value = server
    }

    fun retryConnection() {
        initializeApp()
    }

    // --- Persistence helpers ---

    private fun loadLastTestResult(): LastTestResult? {
        val download = prefs.getFloat("last_download", -1f)
        if (download < 0) return null
        return LastTestResult(
            downloadMbps = download.toDouble(),
            uploadMbps = prefs.getFloat("last_upload", 0f).toDouble(),
            pingMs = prefs.getInt("last_ping", 0),
            jitterMs = prefs.getInt("last_jitter", 0),
            serverName = prefs.getString("last_server", "") ?: "",
            timestamp = prefs.getLong("last_timestamp", 0L)
        )
    }

    private fun saveLastTestResult(result: LastTestResult) {
        prefs.edit()
            .putFloat("last_download", result.downloadMbps.toFloat())
            .putFloat("last_upload", result.uploadMbps.toFloat())
            .putInt("last_ping", result.pingMs)
            .putInt("last_jitter", result.jitterMs)
            .putString("last_server", result.serverName)
            .putLong("last_timestamp", result.timestamp)
            .apply()
        PingWifiWidgetProvider.updateAllWidgets(getApplication<Application>().applicationContext)
    }

    private fun loadQualitySettings() = QualitySettings(
        minDownloadMbps = prefs.getFloat("quality_threshold", 10f),
        notificationsEnabled = prefs.getBoolean("quality_notifications", false)
    )

    override fun onCleared() {
        super.onCleared()
        stopTest()
        networkScanner.stopScan()
    }
}
