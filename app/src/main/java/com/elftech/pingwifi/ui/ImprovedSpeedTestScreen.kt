package com.elftech.pingwifi.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elftech.pingwifi.data.model.ClientInfo
import com.elftech.pingwifi.data.model.RunStatus
import com.elftech.pingwifi.data.model.SpeedTestServer
import com.elftech.pingwifi.data.model.TestPhase
import com.elftech.pingwifi.model.WifiInfoData
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ImprovedSpeedTestScreen(
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
    onStartTest: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Dispara o teste automaticamente na primeira abertura
    LaunchedEffect(Unit) {
        delay(500)
        onStartTest()
    }

    // Fundo com gradiente
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0D1B2A),
            Color(0xFF1B263B)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Header()
        Spacer(modifier = Modifier.height(32.dp))

        SpeedGauge(
            status = status,
            testPhase = testPhase,
            downloadSpeed = downloadMbps,
            uploadSpeed = uploadMbps
        )

        Spacer(modifier = Modifier.height(32.dp))

        MetricsGrid(
            pingMs = pingMs,
            jitterMs = jitterMs,
            downloadMbps = downloadMbps,
            uploadMbps = uploadMbps,
            status = status
        )

        Spacer(modifier = Modifier.height(32.dp))

        ActionButton(status = status, onStartTest = onStartTest)

        error?.let {
            Text(
                text = "Erro: $it",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        ConnectionInfo(clientInfo, serverDetails, wifiData)
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Wifi,
            contentDescription = "PingWifi Logo",
            tint = Color(0xFFE0E1DD),
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "PingWiFi",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun SpeedGauge(
    status: RunStatus,
    testPhase: TestPhase,
    downloadSpeed: Double,
    uploadSpeed: Double
) {
    val speedToShow = when (testPhase) {
        TestPhase.DOWNLOAD -> downloadSpeed
        TestPhase.UPLOAD -> uploadSpeed
        TestPhase.COMPLETED -> downloadSpeed
        else -> 0.0
    }

    val animatedSpeed by animateFloatAsState(
        targetValue = speedToShow.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "speed"
    )

    val gaugeAngle by animateFloatAsState(
        targetValue = (animatedSpeed.coerceIn(0f, 150f) / 150f) * 270f - 135f,
        animationSpec = tween(500, easing = LinearOutSlowInEasing),
        label = "gauge"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = when (testPhase) {
                TestPhase.PING -> "A testar latência..."
                TestPhase.DOWNLOAD -> "Velocidade de Descarga"
                TestPhase.UPLOAD -> "Velocidade de Envio"
                TestPhase.COMPLETED -> "Resultado Final"
                else -> "A preparar..."
            },
            color = Color(0xFF778DA9),
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(280.dp)
        ) {
            GaugeCanvas(angle = gaugeAngle)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%.1f", animatedSpeed),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Mbps",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    color = Color(0xFFE0E1DD)
                )
            }
        }
    }
}

@Composable
private fun GaugeCanvas(angle: Float) {
    val gaugeBrush = Brush.sweepGradient(
        colors = listOf(Color(0xFF415A77), Color(0xFF00B4D8), Color(0xFF90E0EF)),
        center = Offset(LocalDensity.current.run { 140.dp.toPx() }, LocalDensity.current.run { 140.dp.toPx() })
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawArc(
            color = Color(0xFF1B263B),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 20f, cap = StrokeCap.Round)
        )
        drawArc(
            brush = gaugeBrush,
            startAngle = 135f,
            sweepAngle = angle + 135f, // Offset to start from the beginning
            useCenter = false,
            style = Stroke(width = 20f, cap = StrokeCap.Round)
        )
        rotate(degrees = angle, pivot = center) {
            drawLine(
                color = Color.White,
                start = Offset(center.x, center.y + 20f),
                end = Offset(center.x, center.y - (size.minDimension / 2.2f)),
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )
        }
        drawCircle(color = Color.White, radius = 10f, center = center)
    }
}

@Composable
private fun MetricsGrid(
    pingMs: Int,
    jitterMs: Int,
    downloadMbps: Double,
    uploadMbps: Double,
    status: RunStatus
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                label = "Latência",
                value = "$pingMs",
                unit = "ms",
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1f),
                isLoading = status == RunStatus.RUNNING && pingMs == 0
            )
            MetricCard(
                label = "Jitter",
                value = "$jitterMs",
                unit = "ms",
                icon = Icons.Default.NetworkCheck,
                modifier = Modifier.weight(1f),
                isLoading = status == RunStatus.RUNNING && jitterMs == 0
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                label = "Descarga",
                value = String.format("%.1f", downloadMbps),
                unit = "Mbps",
                icon = Icons.Default.ArrowDownward,
                modifier = Modifier.weight(1f),
                isLoading = status == RunStatus.RUNNING && downloadMbps == 0.0
            )
            MetricCard(
                label = "Envio",
                value = String.format("%.1f", uploadMbps),
                unit = "Mbps",
                icon = Icons.Default.ArrowUpward,
                modifier = Modifier.weight(1f),
                isLoading = status == RunStatus.RUNNING && uploadMbps == 0.0
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isLoading: Boolean
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF415A77).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF90E0EF),
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(text = label, color = Color(0xFFE0E1DD), fontSize = 14.sp)
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(top = 4.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = value,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = unit,
                            color = Color(0xFFE0E1DD),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(status: RunStatus, onStartTest: () -> Unit) {
    val buttonText = when (status) {
        RunStatus.RUNNING -> "A testar..."
        RunStatus.DONE -> "Testar Novamente"
        else -> "Iniciar Teste"
    }

    Button(
        onClick = onStartTest,
        enabled = status != RunStatus.RUNNING,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(8.dp, CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF00B4D8),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF778DA9)
        )
    ) {
        if (status == RunStatus.RUNNING) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(text = buttonText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ConnectionInfo(
    clientInfo: ClientInfo?,
    serverDetails: SpeedTestServer?,
    wifiData: WifiInfoData
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1B263B))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Detalhes da Ligação", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expandir",
                    tint = Color.White
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                InfoRow("Cliente", "${clientInfo?.city}, ${clientInfo?.country} (${clientInfo?.ipAddress})")
                Divider(color = Color(0xFF415A77), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                InfoRow("Servidor", "${serverDetails?.name} - ${serverDetails?.city}, ${serverDetails?.country}")
                if (wifiData.isWifi) {
                    Divider(color = Color(0xFF415A77), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                    InfoRow("Rede Wi-Fi", "${wifiData.ssid} (${wifiData.linkSpeedMbps} Mbps)")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFFE0E1DD), fontSize = 14.sp)
        Text(
            text = value ?: "A carregar...",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}

