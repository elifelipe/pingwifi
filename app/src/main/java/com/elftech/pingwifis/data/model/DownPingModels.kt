package com.elftech.pingwifis.data.model

import com.elftech.pingwifis.data.PingResult
import com.elftech.pingwifis.data.PingSummary

enum class OsiLayerStatus {
    PASS, WARN, FAIL, INFO
}

data class OsiLayerResult(
    val layer: Int,
    val name: String,
    val status: OsiLayerStatus,
    val details: String
)

data class HttpCheckResult(
    val url: String,
    val ok: Boolean,
    val statusCode: Int? = null,
    val latencyMs: Long = 0L,
    val message: String = ""
)

data class DownPingReport(
    val target: String,
    val normalizedHost: String,
    val resolvedIps: List<String>,
    val pingSummary: PingSummary,
    val tcpPortResults: Map<Int, Boolean>,
    val httpChecks: List<HttpCheckResult>,
    val osiResults: List<OsiLayerResult>
)

data class DownPingState(
    val status: RunStatus = RunStatus.IDLE,
    val target: String = "google.com",
    val progressMessage: String = "Pronto para diagnóstico",
    val pingResults: List<PingResult> = emptyList(),
    val report: DownPingReport? = null,
    val error: String? = null
)
