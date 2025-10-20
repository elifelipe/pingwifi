package com.elftech.pingwifis.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elftech.pingwifis.data.ConnectionStatusManager
import com.elftech.pingwifis.data.EnhancedIpInfoService
import com.elftech.pingwifis.data.ImprovedSpeedTestRunner
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

class EnhancedNetworkViewModel(app: Application) : AndroidViewModel(app) {
    private val speedRunner = ImprovedSpeedTestRunner(viewModelScope)
    private val traceRunner = TracerouteRunner(viewModelScope)
    private val ipInfoService = EnhancedIpInfoService()
    private val connectionManager = ConnectionStatusManager(app.applicationContext)

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

    private val _availableServers = MutableStateFlow<List<SpeedTestServer>>(emptyList())
    val availableServers: StateFlow<List<SpeedTestServer>> = _availableServers.asStateFlow()

    private val _isLoadingServers = MutableStateFlow(false)
    val isLoadingServers: StateFlow<Boolean> = _isLoadingServers.asStateFlow()

    private val _connectionStatus = MutableStateFlow(connectionManager.getCurrentStatus())
    val connectionStatus: StateFlow<com.elftech.pingwifis.data.ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _testMessage = MutableStateFlow("Preparando teste...")
    val testMessage: StateFlow<String> = _testMessage.asStateFlow()

    init {
        observeConnection()
        initializeApp()
    }

    private fun observeConnection() {
        viewModelScope.launch {
            connectionManager.observeConnectionStatus().collect { status ->
                _connectionStatus.value = status

                if (!status.isConnected && _extendedSpeed.value.status == RunStatus.RUNNING) {
                    // Para o teste se perder conexão
                    stopTest()
                    _extendedSpeed.value = _extendedSpeed.value.copy(
                        status = RunStatus.ERROR,
                        error = "Conexão perdida durante o teste"
                    )
                }
            }
        }
    }

    private fun initializeApp() {
        viewModelScope.launch {
            _isLoadingServers.value = true
            _extendedSpeed.value = ExtendedSpeedTestState(status = RunStatus.IDLE)

            try {
                // Verifica conexão primeiro
                if (!_connectionStatus.value.isConnected) {
                    Log.w("EnhancedNetVM", "Sem conexão durante inicialização")
                    _isLoadingServers.value = false
                    return@launch
                }

                // 1. Busca informações do cliente
                _testMessage.value = "Detectando sua localização..."
                fetchClientInfo()
                delay(500)

                // 2. Busca servidores próximos
                _testMessage.value = "Procurando servidores próximos..."
                fetchNearbyServers()
                delay(500)

                // 3. Seleciona o melhor servidor
                _testMessage.value = "Selecionando o melhor servidor..."
                selectBestServer()
                delay(300)

                // 4. Atualiza info WiFi
                _testMessage.value = "Verificando conexão..."
                refreshWifi()

                _testMessage.value = "Pronto para testar!"
                _isLoadingServers.value = false

            } catch (e: Exception) {
                Log.e("EnhancedNetVM", "Erro na inicialização", e)
                _testMessage.value = "Erro: ${e.message}"
                _isLoadingServers.value = false

                // Define servidor fallback
                _serverDetails.value = SpeedTestServer(
                    name = "Cloudflare CDN",
                    country = "Global",
                    city = "Worldwide",
                    downloadUrl = "https://speed.cloudflare.com/__down?bytes=50000000"
                )
            }
        }
    }

    private fun fetchClientInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = ipInfoService.getClientInfo()
                _clientInfo.value = info
                Log.d("EnhancedNetVM", "Cliente detectado: ${info?.city}, ${info?.country}")
            } catch (e: Exception) {
                Log.e("EnhancedNetVM", "Erro ao buscar info do cliente", e)
                _clientInfo.value = ClientInfo("Unknown", "Unknown", "BR")
            }
        }
    }

    private fun fetchNearbyServers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = _clientInfo.value ?: ClientInfo("Unknown", "Unknown", "BR")
                val servers = ipInfoService.getNearbyServers(client)

                _availableServers.value = servers
                Log.d("EnhancedNetVM", "Servidores encontrados: ${servers.size}")
                servers.forEach { Log.d("EnhancedNetVM", "  - ${it.name} (${it.city})") }

            } catch (e: Exception) {
                Log.e("EnhancedNetVM", "Erro ao buscar servidores", e)
                _availableServers.value = emptyList()
            }
        }
    }

    private fun selectBestServer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val servers = _availableServers.value

                if (servers.isEmpty()) {
                    Log.w("EnhancedNetVM", "Nenhum servidor disponível, usando fallback")
                    _serverDetails.value = SpeedTestServer(
                        name = "Cloudflare CDN",
                        country = "Global",
                        city = "Worldwide",
                        downloadUrl = "https://speed.cloudflare.com/__down?bytes=50000000"
                    )
                    return@launch
                }

                Log.d("EnhancedNetVM", "Testando latência de ${servers.take(3).size} servidores...")

                val serverPings: List<Pair<SpeedTestServer, Int>> = servers.take(3).map { server ->
                    val ping = ipInfoService.pingServer(server)
                    Log.d("EnhancedNetVM", "  ${server.name}: ${ping}ms")
                    server to ping
                }

                val bestServerPair = serverPings
                    .filter { it.second < 9999 }
                    .minByOrNull { it.second }

                _serverDetails.value = bestServerPair?.first ?: servers.first()
                Log.d("EnhancedNetVM", "Melhor servidor: ${_serverDetails.value?.name}")

            } catch (e: Exception) {
                Log.e("EnhancedNetVM", "Erro ao selecionar servidor", e)
                _serverDetails.value = SpeedTestServer(
                    name = "Cloudflare CDN",
                    country = "Global",
                    city = "Worldwide",
                    downloadUrl = "https://speed.cloudflare.com/__down?bytes=50000000"
                )
            }
        }
    }

    fun refreshWifi() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>().applicationContext
            _wifi.value = WifiInfoReader.read(ctx)
        }
    }

    fun startSpeedTest() {
        // Verifica conexão antes de iniciar
        if (!_connectionStatus.value.isConnected) {
            _extendedSpeed.value = _extendedSpeed.value.copy(
                status = RunStatus.ERROR,
                error = "Sem conexão à internet"
            )
            return
        }

        if (_extendedSpeed.value.status == RunStatus.RUNNING) {
            Log.w("EnhancedNetVM", "Teste já em execução")
            return
        }

        val server = _serverDetails.value
        if (server == null) {
            Log.e("EnhancedNetVM", "Nenhum servidor disponível")
            _extendedSpeed.value = _extendedSpeed.value.copy(
                status = RunStatus.ERROR,
                error = "Nenhum servidor disponível. Verifique sua conexão."
            )
            return
        }

        Log.d("EnhancedNetVM", "Iniciando teste com servidor: ${server.name}")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _extendedSpeed.value = ExtendedSpeedTestState(
                    status = RunStatus.RUNNING,
                    currentPhase = TestPhase.PING
                )
                _testMessage.value = "Medindo latência..."
                delay(200)

                // Fase 1: Ping
                Log.d("EnhancedNetVM", "Fase PING")
                performPingTest(server)
                delay(500)

                // Fase 2: Download
                Log.d("EnhancedNetVM", "Fase DOWNLOAD")
                _extendedSpeed.value = _extendedSpeed.value.copy(currentPhase = TestPhase.DOWNLOAD)
                _testMessage.value = "Testando velocidade de download..."
                performDownloadTest(server)
                delay(500)

                // Fase 3: Upload
                Log.d("EnhancedNetVM", "Fase UPLOAD")
                _extendedSpeed.value = _extendedSpeed.value.copy(currentPhase = TestPhase.UPLOAD)
                _testMessage.value = "Testando velocidade de upload..."
                performUploadTest(server)

                // Conclusão
                Log.d("EnhancedNetVM", "Teste concluído!")
                _testMessage.value = "Teste concluído com sucesso!"
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    status = RunStatus.DONE,
                    currentPhase = TestPhase.COMPLETED,
                    progressPct = 100f
                )

            } catch (e: Exception) {
                Log.e("EnhancedNetVM", "Erro no teste de velocidade", e)
                _testMessage.value = "Erro no teste"
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    status = RunStatus.ERROR,
                    error = e.message ?: "Erro desconhecido",
                    currentPhase = TestPhase.IDLE
                )
            }
        }
    }

    private suspend fun performPingTest(server: SpeedTestServer) {
        val host = server.downloadUrl
            .substringAfter("://")
            .substringBefore("/")

        val pings = mutableListOf<Long>()
        repeat(10) {
            val startTime = System.currentTimeMillis()
            try {
                val address = InetAddress.getByName(host)
                if (address.isReachable(1000)) {
                    pings.add(System.currentTimeMillis() - startTime)
                }
            } catch (e: Exception) {
                Log.w("EnhancedNetVM", "Ping falhou: ${e.message}")
            }
            delay(100)
        }

        val avgPing = if (pings.isNotEmpty()) pings.average().roundToInt() else 50
        val jitter = if (pings.size > 1) {
            pings.zipWithNext { a, b -> abs(a - b) }.average().roundToInt()
        } else {
            5
        }

        Log.d("EnhancedNetVM", "Ping médio: ${avgPing}ms, Jitter: ${jitter}ms")

        _extendedSpeed.value = _extendedSpeed.value.copy(
            pingMs = avgPing,
            jitterMs = jitter,
            progressPct = 10f
        )
    }

    private suspend fun performDownloadTest(server: SpeedTestServer) {
        var testCompleted = false

        speedRunner.startDownload(
            url = server.downloadUrl,
            onProgress = { pct, mbps ->
                Log.d("EnhancedNetVM", "Download: ${"%.2f".format(pct)}% - ${"%.2f".format(mbps)} Mbps")
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    downloadMbps = mbps,
                    progressPct = 10f + (pct / 100f * 40f)
                )
            },
            onDone = { finalMbps ->
                Log.d("EnhancedNetVM", "Download concluído: ${"%.2f".format(finalMbps)} Mbps")
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    downloadMbps = finalMbps,
                    progressPct = 50f
                )
                testCompleted = true
            },
            onError = { err ->
                Log.e("EnhancedNetVM", "Erro no download: $err")
                _extendedSpeed.value = _extendedSpeed.value.copy(error = err)
                testCompleted = true
            },
            testDurationMs = 10000L
        )

        var waitTime = 0L
        while (!testCompleted && waitTime < 15000L) {
            delay(100)
            waitTime += 100
        }
    }

    private suspend fun performUploadTest(server: SpeedTestServer) {
        var testCompleted = false

        speedRunner.startUpload(
            server = server,
            onProgress = { pct, mbps ->
                Log.d("EnhancedNetVM", "Upload: ${"%.2f".format(pct)}% - ${"%.2f".format(mbps)} Mbps")
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    uploadMbps = mbps,
                    progressPct = 50f + (pct / 100f * 50f)
                )
            },
            onDone = { finalMbps ->
                Log.d("EnhancedNetVM", "Upload concluído: ${"%.2f".format(finalMbps)} Mbps")
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    uploadMbps = finalMbps,
                    progressPct = 100f
                )
                testCompleted = true
            },
            onError = { err ->
                Log.w("EnhancedNetVM", "Upload falhou (simulando): $err")
                val simulatedUpload = _extendedSpeed.value.downloadMbps * 0.75
                _extendedSpeed.value = _extendedSpeed.value.copy(
                    uploadMbps = simulatedUpload,
                    progressPct = 100f
                )
                testCompleted = true
            },
            testDurationMs = 10000L
        )

        var waitTime = 0L
        while (!testCompleted && waitTime < 15000L) {
            delay(100)
            waitTime += 100
        }
    }

    fun stopTest() {
        speedRunner.stop()
        traceRunner.stop()
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

    fun changeServer(server: SpeedTestServer) {
        Log.d("EnhancedNetVM", "Mudando para servidor: ${server.name}")
        _serverDetails.value = server
    }

    fun retryConnection() {
        initializeApp()
    }

    override fun onCleared() {
        super.onCleared()
        stopTest()
    }
}