package com.elftech.pingwifi.model

enum class RunStatus { IDLE, RUNNING, DONE, ERROR }

data class WifiInfoData(
    val isWifi: Boolean,
    val ssid: String?,
    val bssid: String?,
    val linkSpeedMbps: Int?,
    val rssiDbm: Int?,
    val frequencyMhz: Int?
)

data class SpeedTestState(
    val status: RunStatus = RunStatus.IDLE,
    val progressPct: Float = 0f,
    val mbps: Double = 0.0,
    val error: String? = null
)

data class TracerouteState(
    val status: RunStatus = RunStatus.IDLE,
    val lines: List<String> = emptyList(),
    val error: String? = null
)
