package com.elftech.pingwifi.data.model

/**
 * Classes de dados compartilhadas por toda a aplicação
 */

// Informações do cliente (IP, localização)
data class ClientInfo(
    val ipAddress: String,
    val city: String,
    val country: String
)

// Detalhes do servidor de teste
data class SpeedTestServer(
    val name: String,
    val country: String,
    val city: String,
    val downloadUrl: String,
    val uploadUrl: String = ""
)

// Estado estendido do teste de velocidade
data class ExtendedSpeedTestState(
    val status: RunStatus = RunStatus.IDLE,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val pingMs: Int = 0,
    val jitterMs: Int = 0,
    val packetLoss: Double = 0.0, // Métrica adicional
    val progressPct: Float = 0f,
    val currentPhase: TestPhase = TestPhase.IDLE,
    val error: String? = null
)

// Status de execução
enum class RunStatus {
    IDLE, RUNNING, DONE, ERROR
}

// Fases do teste
enum class TestPhase {
    IDLE, PING, DOWNLOAD, UPLOAD, COMPLETED
}

// Encapsula o resultado final de um teste
data class TestResult(
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Int,
    val jitterMs: Int
)

