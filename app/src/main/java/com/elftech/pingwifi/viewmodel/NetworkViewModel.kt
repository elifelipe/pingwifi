package com.elftech.pingwifi.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elftech.pingwifi.data.SpeedTestRunner
import com.elftech.pingwifi.data.TracerouteRunner
import com.elftech.pingwifi.data.WifiInfoReader
import com.elftech.pingwifi.model.RunStatus
import com.elftech.pingwifi.model.SpeedTestState
import com.elftech.pingwifi.model.TracerouteState
import com.elftech.pingwifi.model.WifiInfoData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Data class for server details
data class SpeedTestServer(
    val name: String,
    val country: String,
    val city: String,
    val downloadUrl: String
)

// Data class for client information
data class ClientInfo(
    val ipAddress: String,
    val city: String,
    val country: String
)

class NetworkViewModel(app: Application) : AndroidViewModel(app) {
    private val speedRunner = SpeedTestRunner(viewModelScope)
    private val traceRunner = TracerouteRunner(viewModelScope)
    private val serverFinder = InternetSpeedTester()

    // --- StateFlows for the UI ---
    private val _wifi = MutableStateFlow(WifiInfoData(false, null, null, null, null, null))
    val wifi: StateFlow<WifiInfoData> = _wifi

    private val _speed = MutableStateFlow(SpeedTestState())
    val speed: StateFlow<SpeedTestState> = _speed

    private val _trace = MutableStateFlow(TracerouteState())
    val trace: StateFlow<TracerouteState> = _trace

    private val _serverDetails = MutableStateFlow<SpeedTestServer?>(null)
    val serverDetails: StateFlow<SpeedTestServer?> = _serverDetails

    private val _clientInfo = MutableStateFlow<ClientInfo?>(null)
    val clientInfo: StateFlow<ClientInfo?> = _clientInfo

    private val _availableServers = MutableStateFlow<List<SpeedTestServer>>(emptyList())
    val availableServers: StateFlow<List<SpeedTestServer>> = _availableServers

    init {
        _availableServers.value = serverFinder.getAvailableServers()
        fetchClientInfo()
    }

    private fun fetchClientInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            // TODO: Replace this mock data with a real API call
            // to a service like ip-api.com or ipinfo.io to get user data.
            _clientInfo.value = ClientInfo(
                ipAddress = "192.141.29.10",
                city = "Joinville",
                country = "BR"
            )
        }
    }

    fun refreshWifi() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>().applicationContext
            _wifi.value = WifiInfoReader.read(ctx)
        }
    }

    fun startSpeedTest(serverToTest: SpeedTestServer? = null) {
        if (_speed.value.status == RunStatus.RUNNING) return

        val server = serverToTest ?: _serverDetails.value ?: serverFinder.getAvailableServers().firstOrNull()

        if (server == null) {
            _speed.value = _speed.value.copy(status = RunStatus.ERROR, error = "Nenhum servidor de teste disponível.")
            return
        }

        _speed.value = SpeedTestState(status = RunStatus.RUNNING)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _serverDetails.value = server

                speedRunner.startDownload(
                    url = server.downloadUrl,
                    onProgress = { pct, mbps ->
                        _speed.value = _speed.value.copy(progressPct = pct, mbps = mbps)
                    },
                    onDone = { mbps ->
                        _speed.value = _speed.value.copy(
                            status = RunStatus.DONE,
                            mbps = mbps,
                            progressPct = 100f
                        )
                    },
                    onError = { err ->
                        _speed.value = _speed.value.copy(
                            status = RunStatus.ERROR,
                            error = err
                        )
                    }
                )
            } catch (e: Exception) {
                _speed.value = _speed.value.copy(
                    status = RunStatus.ERROR,
                    error = e.message ?: "Falha ao iniciar o teste"
                )
            }
        }
    }

    fun runTraceroute(host: String, maxHops: Int = 30) {
        _trace.value = TracerouteState(status = RunStatus.RUNNING)
        traceRunner.run(
            host = host,
            maxHops = maxHops,
            onLine = { line -> _trace.value = _trace.value.copy(lines = _trace.value.lines + line) },
            onDone = { _trace.value = _trace.value.copy(status = RunStatus.DONE) },
            onError = { err -> _trace.value = _trace.value.copy(status = RunStatus.ERROR, error = err) }
        )
    }
}


/**
 * Provides a list of reliable speed test servers.
 */
class InternetSpeedTester {
    private val servers = listOf(
        // O servidor do Google CDN é mantido pois sempre funciona.
        SpeedTestServer(
            name = "Google CDN",
            country = "Global",
            city = "N/A",
            downloadUrl = "https://dl.google.com/android/repository/android-ndk-r25c-linux.zip"
        )

        /*
         * ===================================================================================
         * NOTA: Os servidores abaixo foram desativados para corrigir o erro de "resolve host".
         * Para reativá-los, substitua as URLs de exemplo pelos IPs e caminhos
         * REAIS dos seus arquivos de teste em cada servidor.
         * Exemplo: "http://192.168.1.10/teste.zip"
         * ===================================================================================

        ,SpeedTestServer(
            name = "Servidor Joinville",
            country = "BR",
            city = "Joinville",
            downloadUrl = "http://IP.DO.SEU.SERVIDOR.JOINVILLE/arquivo_teste.zip"
        ),
        SpeedTestServer(
            name = "Servidor Palmas",
            country = "BR",
            city = "Palmas",
            downloadUrl = "http://IP.DO.SEU.SERVIDOR.PALMAS/arquivo_teste.zip"
        ),
        SpeedTestServer(
            name = "Servidor Jaraguá",
            country = "BR",
            city = "Jaraguá do Sul",
            downloadUrl = "http://IP.DO.SEU.SERVIDOR.JARAGUA/arquivo_teste.zip"
        )
        */
    )

    /**
     * Returns the list of available test servers.
     */
    fun getAvailableServers(): List<SpeedTestServer> {
        return servers
    }
}
