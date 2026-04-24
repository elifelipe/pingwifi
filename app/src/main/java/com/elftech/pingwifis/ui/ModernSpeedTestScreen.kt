package com.elftech.pingwifis.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elftech.pingwifis.data.model.*
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.material3.surfaceColorAtElevation

// Mapa de países para bandeiras emoji
private val countryFlags = mapOf(
    "Brazil" to "🇧🇷", "Brasil" to "🇧🇷",
    "United States" to "🇺🇸", "USA" to "🇺🇸", "US" to "🇺🇸",
    "Germany" to "🇩🇪", "Deutschland" to "🇩🇪",
    "France" to "🇫🇷", "United Kingdom" to "🇬🇧", "UK" to "🇬🇧",
    "Netherlands" to "🇳🇱", "Canada" to "🇨🇦", "Australia" to "🇦🇺",
    "Japan" to "🇯🇵", "South Korea" to "🇰🇷", "Singapore" to "🇸🇬",
    "India" to "🇮🇳", "Argentina" to "🇦🇷", "Chile" to "🇨🇱",
    "Colombia" to "🇨🇴", "Mexico" to "🇲🇽", "Spain" to "🇪🇸",
    "Italy" to "🇮🇹", "Portugal" to "🇵🇹", "Sweden" to "🇸🇪",
    "Norway" to "🇳🇴", "Finland" to "🇫🇮", "Denmark" to "🇩🇰",
    "Switzerland" to "🇨🇭", "Austria" to "🇦🇹", "Poland" to "🇵🇱",
    "Russia" to "🇷🇺", "China" to "🇨🇳", "Global" to "🌐"
)

private fun countryFlag(countryName: String) =
    countryFlags[countryName] ?: countryFlags.entries
        .firstOrNull { countryName.contains(it.key, ignoreCase = true) }?.value ?: "🌐"

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Agora mesmo"
        diff < 3_600_000 -> "${diff / 60_000} min atrás"
        diff < 86_400_000 -> "${diff / 3_600_000} h atrás"
        else -> "${diff / 86_400_000} d atrás"
    }
}

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
    availableServers: List<SpeedTestServer>,
    downloadSpeedSamples: List<Double>,
    uploadSpeedSamples: List<Double>,
    lastTestResult: LastTestResult?,
    vpnInfo: VpnInfo,
    qualitySettings: QualitySettings,
    onStartTest: () -> Unit,
    onSelectServer: (SpeedTestServer) -> Unit,
    onUpdateQualitySettings: (QualitySettings) -> Unit
) {
    val scrollState = rememberScrollState()
    var hasAutoStarted by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(serverDetails, status, hasAutoStarted) {
        if (!hasAutoStarted && serverDetails != null && status == RunStatus.IDLE) {
            delay(800)
            onStartTest()
            hasAutoStarted = true
        }
    }

    LaunchedEffect(status) {
        when (status) {
            RunStatus.RUNNING -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            RunStatus.DONE -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(150)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // VPN warning
        AnimatedVisibility(visible = vpnInfo.isActive) {
            VpnWarningBanner()
            Spacer(Modifier.height(12.dp))
        }

        // Last test card
        lastTestResult?.let { last ->
            LastTestCard(result = last)
            Spacer(Modifier.height(16.dp))
        }

        ModernSpeedGauge(
            status = status,
            testPhase = testPhase,
            downloadSpeed = downloadMbps,
            uploadSpeed = uploadMbps,
            progress = progress
        )
        Spacer(modifier = Modifier.height(16.dp))
        SpeedTestGraph(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            downloadSamples = downloadSpeedSamples,
            uploadSamples = uploadSpeedSamples,
            status = status,
            phase = testPhase
        )
        Spacer(modifier = Modifier.height(32.dp))
        ModernMetricsGrid(
            pingMs = pingMs,
            jitterMs = jitterMs,
            downloadMbps = downloadMbps,
            uploadMbps = uploadMbps,
            status = status,
            testPhase = testPhase
        )
        Spacer(modifier = Modifier.height(24.dp))
        ModernActionButton(
            status = status,
            onStartTest = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onStartTest()
            }
        )
        error?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 12.dp),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        ServerSelectionCard(
            availableServers = availableServers,
            selectedServer = serverDetails,
            clientInfo = clientInfo,
            isLoadingServers = isLoadingServers,
            onSelectServer = onSelectServer
        )
        Spacer(modifier = Modifier.height(16.dp))
        ModernConnectionCard(
            clientInfo = clientInfo,
            serverDetails = serverDetails,
            wifiData = wifiData
        )
        Spacer(modifier = Modifier.height(16.dp))
        QualitySettingsCard(
            settings = qualitySettings,
            onUpdate = onUpdateQualitySettings
        )
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun VpnWarningBanner() {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.VpnLock,
                contentDescription = null,
                tint = colors.onErrorContainer,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    "VPN Detectada",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onErrorContainer
                )
                Text(
                    "Os resultados podem não refletir a velocidade real do Wi-Fi",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun LastTestCard(result: LastTestResult) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceColorAtElevation(1.dp)),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Último Teste",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Text(
                    formatTimeAgo(result.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniMetric(
                    label = "Down",
                    value = "%.1f".format(result.downloadMbps),
                    unit = "Mbps",
                    icon = Icons.Default.ArrowDownward,
                    color = colors.secondary,
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = "Up",
                    value = "%.1f".format(result.uploadMbps),
                    unit = "Mbps",
                    icon = Icons.Default.ArrowUpward,
                    color = colors.primary,
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = "Ping",
                    value = "${result.pingMs}",
                    unit = "ms",
                    icon = Icons.Default.Timer,
                    color = colors.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
            if (result.serverName.isNotBlank()) {
                Text(
                    result.serverName,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MiniMetric(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSelectionCard(
    availableServers: List<SpeedTestServer>,
    selectedServer: SpeedTestServer?,
    clientInfo: ClientInfo?,
    isLoadingServers: Boolean,
    onSelectServer: (SpeedTestServer) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    val serverDistance: Double? = remember(clientInfo, selectedServer) {
        val c = clientInfo ?: return@remember null
        val s = selectedServer ?: return@remember null
        if (s.lat == 0.0 && s.lon == 0.0) return@remember null
        if (c.lat == 0.0 && c.lon == 0.0) return@remember null
        haversineKm(c.lat, c.lon, s.lat, s.lon)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceColorAtElevation(2.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Servidor de teste",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (!isLoadingServers && availableServers.isNotEmpty()) expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedServer?.let {
                        "${countryFlag(it.country)} ${it.name} – ${it.city}"
                    } ?: "Seleção automática",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    label = { Text("Servidor ativo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        disabledTextColor = colors.onSurfaceVariant,
                        focusedLabelColor = colors.onSurfaceVariant,
                        unfocusedLabelColor = colors.onSurfaceVariant,
                        disabledLabelColor = colors.onSurfaceVariant,
                        focusedBorderColor = colors.outline,
                        unfocusedBorderColor = colors.outlineVariant,
                        disabledBorderColor = colors.outlineVariant,
                        focusedTrailingIconColor = colors.onSurfaceVariant,
                        unfocusedTrailingIconColor = colors.onSurfaceVariant,
                        disabledTrailingIconColor = colors.onSurfaceVariant
                    )
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableServers.forEach { server ->
                        val dist = clientInfo?.let { c ->
                            if (server.lat != 0.0 && c.lat != 0.0)
                                haversineKm(c.lat, c.lon, server.lat, server.lon)
                            else null
                        }
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("${countryFlag(server.country)} ${server.name} – ${server.city}")
                                    dist?.let {
                                        Text(
                                            "${"%.0f".format(it)} km",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = { onSelectServer(server); expanded = false }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoadingServers) {
                    LinearProgressIndicator(modifier = Modifier.weight(1f))
                } else {
                    Text(
                        text = "${availableServers.size} servidores disponíveis",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                serverDistance?.let { dist ->
                    Text(
                        text = "📍 ${"%.0f".format(dist)} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun QualitySettingsCard(
    settings: QualitySettings,
    onUpdate: (QualitySettings) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceColorAtElevation(2.dp)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Alertas de Qualidade",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Notificações ativas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface
                        )
                        Switch(
                            checked = settings.notificationsEnabled,
                            onCheckedChange = {
                                onUpdate(settings.copy(notificationsEnabled = it))
                            }
                        )
                    }
                    AnimatedVisibility(visible = settings.notificationsEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Limite mínimo de download",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                                Text(
                                    "${"%.0f".format(settings.minDownloadMbps)} Mbps",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.primary
                                )
                            }
                            Slider(
                                value = settings.minDownloadMbps,
                                onValueChange = { onUpdate(settings.copy(minDownloadMbps = it)) },
                                valueRange = 1f..100f,
                                steps = 19,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpeedTestGraph(
    modifier: Modifier = Modifier,
    downloadSamples: List<Double>,
    uploadSamples: List<Double>,
    status: RunStatus,
    phase: TestPhase
) {
    Box(modifier = modifier) {
        val downloadColor = MaterialTheme.colorScheme.secondary
        val uploadColor = MaterialTheme.colorScheme.primary

        val maxDownload = downloadSamples.maxOrNull() ?: 0.0
        val maxUpload = uploadSamples.maxOrNull() ?: 0.0
        val overallMax = max(maxDownload, maxUpload).toFloat()

        if (downloadSamples.isNotEmpty()) {
            val isVisible = status == RunStatus.DONE ||
                    (status == RunStatus.RUNNING && phase == TestPhase.DOWNLOAD)
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500))
            ) {
                GraphPath(
                    samples = downloadSamples,
                    color = downloadColor,
                    maxSample = if (status == RunStatus.DONE) overallMax else maxDownload.toFloat()
                )
            }
        }

        if (uploadSamples.isNotEmpty()) {
            val isVisible = status == RunStatus.DONE ||
                    (status == RunStatus.RUNNING && phase == TestPhase.UPLOAD)
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500))
            ) {
                GraphPath(
                    samples = uploadSamples,
                    color = uploadColor,
                    maxSample = if (status == RunStatus.DONE) overallMax else maxUpload.toFloat()
                )
            }
        }
    }
}

@Composable
internal fun GraphPath(samples: List<Double>, color: Color, maxSample: Float) {
    val brush = Brush.verticalGradient(
        colors = listOf(color.copy(alpha = 0.4f), Color.Transparent)
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (samples.size > 1) {
            val path = Path()
            val linePath = Path()
            path.moveTo(0f, size.height)

            samples.forEachIndexed { index, sample ->
                val x = (index.toFloat() / (samples.size - 1).coerceAtLeast(1)) * size.width
                val y = size.height - ((sample.toFloat() / maxSample.coerceAtLeast(1f)) * size.height)

                if (index == 0) {
                    path.lineTo(x, y); linePath.moveTo(x, y)
                } else {
                    path.lineTo(x, y); linePath.lineTo(x, y)
                }
            }

            path.lineTo(size.width, size.height)
            path.close()
            drawPath(path, brush)
            drawPath(linePath, color, style = Stroke(width = 6f, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun ModernSpeedGauge(
    status: RunStatus,
    testPhase: TestPhase,
    downloadSpeed: Double,
    uploadSpeed: Double,
    progress: Float
) {
    val colors = MaterialTheme.colorScheme
    val downloadColor = colors.secondary
    val uploadColor = colors.primary

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
                style = MaterialTheme.typography.titleMedium,
                color = colors.primary
            )
        }
        Spacer(Modifier.height(24.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
            val animatedRingProgress by animateFloatAsState(
                targetValue = when (status) {
                    RunStatus.RUNNING -> progress / 100f
                    RunStatus.DONE -> 1f
                    else -> 0f
                },
                animationSpec = tween(400, easing = LinearEasing),
                label = "ring_progress"
            )

            CircularProgressIndicator(
                progress = { animatedRingProgress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 16.dp,
                trackColor = colors.surfaceVariant,
                color = if (testPhase == TestPhase.UPLOAD) uploadColor else downloadColor,
                strokeCap = StrokeCap.Round
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = String.format("%.1f", animatedSpeed.coerceAtLeast(0f)),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground
                )
                Text(
                    text = "Mbps",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                AnimatedVisibility(
                    visible = testPhase == TestPhase.UPLOAD || testPhase == TestPhase.DOWNLOAD,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Icon(
                        if (testPhase == TestPhase.UPLOAD) Icons.Default.ArrowUpward
                        else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = if (testPhase == TestPhase.UPLOAD) uploadColor else downloadColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ModernMetricsGrid(
    pingMs: Int,
    jitterMs: Int,
    downloadMbps: Double,
    uploadMbps: Double,
    status: RunStatus,
    testPhase: TestPhase
) {
    val colors = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModernMetricCard(
                label = "Ping",
                value = "$pingMs",
                numericValue = pingMs.toFloat(),
                unit = "ms",
                icon = Icons.Default.Timer,
                color = colors.tertiary,
                isLoading = status == RunStatus.RUNNING && testPhase == TestPhase.PING,
                modifier = Modifier.weight(1f),
                isInteger = true
            )
            ModernMetricCard(
                label = "Jitter",
                value = "$jitterMs",
                numericValue = jitterMs.toFloat(),
                unit = "ms",
                icon = Icons.Default.NetworkCheck,
                color = colors.primary,
                isLoading = status == RunStatus.RUNNING && testPhase == TestPhase.PING,
                modifier = Modifier.weight(1f),
                isInteger = true
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModernMetricCard(
                label = "Download",
                value = String.format("%.1f", downloadMbps),
                numericValue = downloadMbps.toFloat(),
                unit = "Mbps",
                icon = Icons.Default.ArrowDownward,
                color = colors.secondary,
                isLoading = status == RunStatus.RUNNING && testPhase == TestPhase.DOWNLOAD,
                modifier = Modifier.weight(1f)
            )
            ModernMetricCard(
                label = "Upload",
                value = String.format("%.1f", uploadMbps),
                numericValue = uploadMbps.toFloat(),
                unit = "Mbps",
                icon = Icons.Default.ArrowUpward,
                color = colors.primary,
                isLoading = status == RunStatus.RUNNING && testPhase == TestPhase.UPLOAD,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ModernMetricCard(
    label: String,
    value: String,
    numericValue: Float? = null,
    unit: String,
    icon: ImageVector,
    color: Color,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    isInteger: Boolean = false
) {
    val colors = MaterialTheme.colorScheme

    // Contador animado: quando isLoading passa de true → false, conta de 0 ao valor final
    var animatedNum by remember { mutableFloatStateOf(0f) }
    var prevIsLoading by remember { mutableStateOf(isLoading) }

    LaunchedEffect(isLoading, numericValue) {
        if (prevIsLoading && !isLoading && numericValue != null && numericValue > 0f) {
            animatedNum = 0f
            val target = numericValue
            val steps = 50
            val stepMs = 20L
            for (i in 1..steps) {
                val t = FastOutSlowInEasing.transform(i.toFloat() / steps)
                animatedNum = target * t
                delay(stepMs)
            }
            animatedNum = target
        } else if (isLoading) {
            animatedNum = 0f
        }
        prevIsLoading = isLoading
    }

    val displayValue = when {
        numericValue != null && !isLoading && animatedNum > 0f ->
            if (isInteger) animatedNum.roundToInt().toString()
            else "%.1f".format(animatedNum)
        else -> value
    }

    Card(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceColorAtElevation(2.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }

            AnimatedContent(targetState = isLoading, label = "metric_loading") { loading ->
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = color, strokeWidth = 3.dp)
                } else {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = displayValue,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = unit,
                            fontSize = 14.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModernActionButton(status: RunStatus, onStartTest: () -> Unit) {
    val colors = MaterialTheme.colorScheme
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
            .height(56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.surfaceVariant,
            disabledContentColor = colors.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        if (status == RunStatus.RUNNING) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colors.onPrimary, strokeWidth = 2.5.dp)
            Spacer(Modifier.width(12.dp))
        } else {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(text = buttonText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ModernConnectionCard(
    clientInfo: ClientInfo?,
    serverDetails: SpeedTestServer?,
    wifiData: WifiInfoData
) {
    var expanded by remember { mutableStateOf(true) }
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceColorAtElevation(2.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle",
                    tint = colors.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    serverDetails?.let { server ->
                        ModernInfoRow(
                            icon = Icons.Default.Storage,
                            label = "Servidor",
                            value = "${countryFlag(server.country)} ${server.name} – ${server.city}, ${server.country}",
                            showDivider = true
                        )
                    }
                    if (wifiData.isWifi && wifiData.ssid != null) {
                        ModernInfoRow(icon = Icons.Default.Wifi, label = "Rede", value = wifiData.ssid!!, showDivider = true)
                        wifiData.linkSpeedMbps?.let {
                            ModernInfoRow(icon = Icons.Default.SignalCellularAlt, label = "Velocidade do Link", value = "$it Mbps", showDivider = true)
                        }
                    }
                    clientInfo?.let {
                        ModernInfoRow(icon = Icons.Default.Public, label = "IP Público", value = it.ipAddress, showDivider = true)
                        ModernInfoRow(icon = Icons.Default.Business, label = "Provedor", value = it.isp, showDivider = false)
                    }
                }
            }
        }
    }
}

@Composable
fun ModernInfoRow(icon: ImageVector, label: String, value: String, showDivider: Boolean) {
    val colors = MaterialTheme.colorScheme

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = colors.onSurface)
            }
        }
        if (showDivider) {
            HorizontalDivider(color = colors.outlineVariant, thickness = 1.dp)
        }
    }
}
