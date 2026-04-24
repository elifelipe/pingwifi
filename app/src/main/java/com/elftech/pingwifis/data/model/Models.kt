package com.elftech.pingwifis.data.model

data class LastTestResult(
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Int,
    val jitterMs: Int,
    val serverName: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class VpnInfo(
    val isActive: Boolean,
    val interfaceName: String? = null
)

data class DnsServer(val name: String, val address: String)

data class DnsResult(
    val server: DnsServer,
    val latencyMs: Long = -1L,
    val resolved: Boolean = false,
    val resolvedIp: String? = null,
    val error: String? = null
)

data class DnsTestState(
    val status: RunStatus = RunStatus.IDLE,
    val results: List<DnsResult> = emptyList(),
    val target: String = "google.com"
)

data class WifiChannelData(
    val channel: Int,
    val frequencyMhz: Int,
    val band: String,
    val networkSsids: List<String>,
    val currentChannel: Boolean = false
)

data class WifiChannelState(
    val status: RunStatus = RunStatus.IDLE,
    val channels: List<WifiChannelData> = emptyList(),
    val error: String? = null
)

data class QualitySettings(
    val minDownloadMbps: Float = 10f,
    val notificationsEnabled: Boolean = false
)

data class ClientInfo(
    val ipAddress: String,
    val city: String,
    val country: String,
    val isp: String,
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

data class SpeedTestServer(
    val name: String,
    val country: String,
    val city: String,
    val downloadUrl: String,
    val uploadUrl: String? = null,
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

data class WifiInfoData(
    val isWifi: Boolean,
    val ssid: String?,
    val bssid: String?,
    val linkSpeedMbps: Int?,
    val rssiDbm: Int?,
    val frequencyMhz: Int?
)

enum class RunStatus {
    IDLE, RUNNING, DONE, ERROR
}

enum class TestPhase {
    IDLE, PING, DOWNLOAD, UPLOAD, COMPLETED
}

enum class ServerSelectionMode {
    NEARBY,
    CDN,
    HYBRID
}

data class ExtendedSpeedTestState(
    val status: RunStatus = RunStatus.IDLE,
    val currentPhase: TestPhase = TestPhase.IDLE,
    val progressPct: Float = 0f,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val pingMs: Int = 0,
    val jitterMs: Int = 0,
    val error: String? = null,
    // Armazena as amostras de velocidade para os gráficos separadamente
    val downloadSpeedSamples: List<Double> = emptyList(),
    val uploadSpeedSamples: List<Double> = emptyList()
)

data class NetworkScanState(
    val status: RunStatus = RunStatus.IDLE,
    val devices: List<com.elftech.pingwifis.data.NetworkDevice> = emptyList(),
    val progress: Int = 0,
    val error: String? = null,
    val scanningDevice: com.elftech.pingwifis.data.NetworkDevice? = null
)

data class TracerouteState(
    val status: RunStatus = RunStatus.IDLE,
    val lines: List<String> = emptyList(),
    val error: String? = null
)

data class PingTestState(
    val status: RunStatus = RunStatus.IDLE,
    val host: String = "8.8.8.8",
    val results: List<com.elftech.pingwifis.data.PingResult> = emptyList(),
    val summary: com.elftech.pingwifis.data.PingSummary? = null,
    val error: String? = null
)
