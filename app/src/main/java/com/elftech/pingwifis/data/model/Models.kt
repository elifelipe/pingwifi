// File: app/src/main/java/com/elftech/pingwifis/data/model/Models.kt
// This is the ONLY model file - delete all others!

package com.elftech.pingwifis.data.model

data class ClientInfo(
    val ipAddress: String,
    val city: String,
    val country: String
)

data class SpeedTestServer(
    val name: String,
    val country: String,
    val city: String,
    val downloadUrl: String
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

data class ExtendedSpeedTestState(
    val status: RunStatus = RunStatus.IDLE,
    val currentPhase: TestPhase = TestPhase.IDLE,
    val progressPct: Float = 0f,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val pingMs: Int = 0,
    val jitterMs: Int = 0,
    val error: String? = null
)

data class TracerouteState(
    val status: RunStatus = RunStatus.IDLE,
    val lines: List<String> = emptyList(),
    val error: String? = null
)