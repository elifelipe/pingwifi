package com.elftech.pingwifi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elftech.pingwifi.data.model.ClientInfo
import com.elftech.pingwifi.data.model.SpeedTestServer
import com.elftech.pingwifi.model.RunStatus
import com.elftech.pingwifi.model.WifiInfoData
import kotlinx.coroutines.delay

@Composable
fun FastHomeTab(
    status: RunStatus,
    mbps: Double,
    progress: Float,
    error: String?,
    wifiData: WifiInfoData,
    serverDetails: SpeedTestServer?,
    clientInfo: ClientInfo?,
    onStart: () -> Unit,
) {
    // Dispara o teste automaticamente na primeira abertura
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            onStart()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround // Better vertical spacing
    ) {
        // Top section: Status and Speed Indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = when (status) {
                    RunStatus.RUNNING -> "Testando velocidade" + animatedDots()
                    RunStatus.DONE -> "Velocidade de download"
                    RunStatus.ERROR -> "Falha no teste"
                    else -> "Pronto para testar"
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            SpeedIndicator(mbps = mbps, progress = progress, status = status)
        }

        // Middle section: Button and Error Message
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onStart,
                enabled = status != RunStatus.RUNNING,
                shape = CircleShape,
                modifier = Modifier.size(width = 220.dp, height = 50.dp)
            ) {
                Text("Testar novamente", fontSize = 16.sp)
            }

            // Usa diretamente o parâmetro 'error' que já é fornecido.
            error?.let {
                Text(
                    text = "Erro: $it",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Bottom section: Connection Details
        ConnectionDetailsCard(
            wifiData = wifiData,
            serverDetails = serverDetails,
            clientInfo = clientInfo
        )
    }
}

@Composable
private fun SpeedIndicator(mbps: Double, progress: Float, status: RunStatus) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(250.dp)
    ) {
        val animatedProgress by animateFloatAsState(
            targetValue = if (status == RunStatus.RUNNING) progress.coerceIn(0f, 100f) / 100f else 0f,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "fast_ring"
        )

        val indicatorProgress = when (status) {
            RunStatus.DONE -> 1f // Full circle on completion
            else -> animatedProgress
        }

        CircularProgressIndicator(
            progress = { indicatorProgress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 12.dp,
            strokeCap = StrokeCap.Round,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedMbps(mbps = mbps, emphasize = status != RunStatus.ERROR)
            Text(
                text = "Mbps",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}


@Composable
private fun AnimatedMbps(mbps: Double, emphasize: Boolean) {
    val txt = remember(mbps) { "%.1f".format(mbps.coerceAtLeast(0.0)) }
    AnimatedContent(targetState = txt, label = "fast_mbps") { value ->
        Text(
            text = value,
            fontSize = if (emphasize) 80.sp else 60.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ConnectionDetailsCard(
    wifiData: WifiInfoData,
    serverDetails: SpeedTestServer?,
    clientInfo: ClientInfo?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Detalhes da Conexão",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Divider(Modifier.padding(vertical = 4.dp))

            // ** MODIFICADO: Linha "Localização" foi adicionada de volta **
            DetailRow("Cliente", clientInfo?.ipAddress ?: "Buscando...")
            val clientLocation = clientInfo?.let { "${it.city}, ${it.country}" } ?: "Buscando..."
            DetailRow("Localização", clientLocation)

            val serverLocation = serverDetails?.let {
                if (it.city != "N/A") "${it.city}, ${it.country}" else it.country
            } ?: "Buscando..."
            DetailRow("Servidor", serverLocation)

            DetailRow("SSID", wifiData.ssid ?: "N/A")
            DetailRow("Velocidade do Link", wifiData.linkSpeedMbps?.let { "$it Mbps" } ?: "-")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


@Composable
private fun animatedDots(): String {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            tick = (tick + 1) % 4
        }
    }
    return when (tick) {
        1 -> "."
        2 -> ".."
        3 -> "..."
        else -> ""
    }
}
