package com.elftech.pingwifis.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elftech.pingwifis.data.model.*
import kotlinx.coroutines.delay

@Composable
fun ModernSpeedTestScreen(
    status: RunStatus,
    testPhase: TestPhase,
    downloadMbps: Double,
    uploadMbps: Double,
    progress: Float,
    error: String?,
    wifiData: WifiInfoData,
    serverDetails: SpeedTestServer?,
    clientInfo: ClientInfo?,
    pingMs: Int,
    jitterMs: Int,
    isLoadingServers: Boolean,
    onStartTest: () -> Unit,
    onChangeServer: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Dispara teste automaticamente
    LaunchedEffect(serverDetails) {
        if (serverDetails != null && status == RunStatus.IDLE) {
            delay(800)
            onStartTest()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E293B),
                        Color(0xFF0F172A)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            ModernHeader(clientInfo, isLoadingServers)

            Spacer(modifier = Modifier.height(24.dp))

            // Speed Gauge
            ModernSpeedGauge(
                status = status,
                testPhase = testPhase,
                downloadSpeed = downloadMbps,
                uploadSpeed = uploadMbps
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Metrics Cards
            ModernMetricsGrid(
                pingMs = pingMs,
                jitterMs = jitterMs,
                downloadMbps = downloadMbps,
                uploadMbps = uploadMbps,
                status = status,
                testPhase = testPhase
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            ModernActionButton(
                status = status,
                onStartTest = onStartTest
            )

            error?.let {
                Text(
                    text = it,
                    color = Color(0xFFEF4444),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Connection Details
            ModernConnectionCard(
                clientInfo = clientInfo,
                serverDetails = serverDetails,
                wifiData = wifiData,
                onChangeServer = onChangeServer
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ModernHeader(clientInfo: ClientInfo?, isLoading: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Wifi,
                contentDescription = null,
                tint = Color(0xFF3B82F6),
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "PingWiFi",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF60A5FA)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Detectando servidor ideal...",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        } else {
            clientInfo?.let {
                Text(
                    text = "${it.city}, ${it.country}",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun ModernSpeedGauge(
    status: RunStatus,
    testPhase: TestPhase,
    downloadSpeed: Double,
    uploadSpeed: Double
) {
    val displaySpeed = when (testPhase) {
        TestPhase.DOWNLOAD -> downloadSpeed
        TestPhase.UPLOAD -> uploadSpeed
        TestPhase.COMPLETED -> downloadSpeed
        else -> 0.0
    }

    val animatedSpeed by animateFloatAsState(
        targetValue = displaySpeed.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "speed"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        // Phase Indicator
        AnimatedContent(
            targetState = testPhase,
            label = "phase",
            transitionSpec = {
                (fadeIn() + slideInVertically()).togetherWith(fadeOut() + slideOutVertically())
            }
        ) { phase ->
            Text(
                text = when (phase) {
                    TestPhase.PING -> "Medindo Latência"
                    TestPhase.DOWNLOAD -> "Teste de Download"
                    TestPhase.UPLOAD -> "Teste de Upload"
                    TestPhase.COMPLETED -> "Teste Concluído"
                    else -> "Aguardando"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF60A5FA)
            )
        }

        Spacer(Modifier.height(24.dp))

        // Speed Display
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(280.dp)
        ) {
            // Animated Ring
            CircularProgressWithGradient(
                status = status,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = String.format("%.1f", animatedSpeed.coerceAtLeast(0f)),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Mbps",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    color = Color(0xFFCBD5E1)
                )

                if (testPhase == TestPhase.UPLOAD) {
                    Spacer(Modifier.height(8.dp))
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(24.dp)
                    )
                } else if (testPhase == TestPhase.DOWNLOAD) {
                    Spacer(Modifier.height(8.dp))
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CircularProgressWithGradient(status: RunStatus, modifier: Modifier = Modifier) {
    val progress = when(status) {
        RunStatus.RUNNING -> 0.75f
        RunStatus.DONE -> 1f
        else -> 0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 16.dp,
            trackColor = Color(0xFF1E293B),
            color = Color(0xFF3B82F6),
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun ModernMetricsGrid(
    pingMs: Int,
    jitterMs: Int,
    downloadMbps: Double,
    uploadMbps: Double,
    status: RunStatus,
    testPhase: TestPhase
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModernMetricCard(
                label = "Ping",
                value = "$pingMs",
                unit = "ms",
                icon = Icons.Default.Timer,
                color = Color(0xFF8B5CF6),
                isLoading = status == RunStatus.RUNNING && testPhase == TestPhase.PING,
                modifier = Modifier.weight(1f)
            )
            ModernMetricCard(
                label = "Jitter",
                value = "$jitterMs",
                unit = "ms",
                icon = Icons.Default.NetworkCheck,
                color = Color(0xFFEC4899),
                isLoading = status == RunStatus.RUNNING && testPhase == TestPhase.PING,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModernMetricCard(
                label = "Download",
                value = String.format("%.1f", downloadMbps),
                unit = "Mbps",
                icon = Icons.Default.ArrowDownward,
                color = Color(0xFF10B981),
                isLoading = status == RunStatus.RUNNING && testPhase == TestPhase.DOWNLOAD,
                modifier = Modifier.weight(1f)
            )
            ModernMetricCard(
                label = "Upload",
                value = String.format("%.1f", uploadMbps),
                unit = "Mbps",
                icon = Icons.Default.ArrowUpward,
                color = Color(0xFF3B82F6),
                isLoading = status == RunStatus.RUNNING && testPhase == TestPhase.UPLOAD,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModernMetricCard(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    color: Color,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(110.dp)
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = color,
                    strokeWidth = 3.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = value,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = unit,
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernActionButton(
    status: RunStatus,
    onStartTest: () -> Unit
) {
    val buttonText = when (status) {
        RunStatus.RUNNING -> "Testando"
        RunStatus.DONE -> "Testar Novamente"
        RunStatus.ERROR -> "Tentar Novamente"
        else -> "Iniciar Teste"
    }

    Button(
        onClick = onStartTest,
        enabled = status != RunStatus.RUNNING,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(12.dp, CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF3B82F6),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF475569)
        )
    ) {
        if (status == RunStatus.RUNNING) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = buttonText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ModernConnectionCard(
    clientInfo: ClientInfo?,
    serverDetails: SpeedTestServer?,
    wifiData: WifiInfoData,
    onChangeServer: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Detalhes da Conexão",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle",
                    tint = Color(0xFF94A3B8)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    serverDetails?.let { server ->
                        ModernInfoRow(
                            icon = Icons.Default.Storage,
                            label = "Servidor",
                            value = "${server.name} - ${server.city}",
                            showDivider = true
                        )
                    }

                    if (wifiData.isWifi && wifiData.ssid != null) {
                        ModernInfoRow(
                            icon = Icons.Default.Wifi,
                            label = "Rede",
                            value = wifiData.ssid!!,
                            showDivider = true
                        )
                        wifiData.linkSpeedMbps?.let {
                            ModernInfoRow(
                                icon = Icons.Default.SignalCellularAlt,
                                label = "Velocidade do Link",
                                value = "$it Mbps",
                                showDivider = true
                            )
                        }
                    }

                    clientInfo?.let {
                        ModernInfoRow(
                            icon = Icons.Default.Public,
                            label = "IP Público",
                            value = it.ipAddress,
                            showDivider = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    showDivider: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF3B82F6),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = Color(0xFF334155),
                thickness = 1.dp
            )
        }
    }
}

