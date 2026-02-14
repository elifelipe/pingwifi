package com.elftech.pingwifis.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elftech.pingwifis.data.model.*
import kotlinx.coroutines.delay
import kotlin.math.max
import androidx.compose.material3.surfaceColorAtElevation

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
    onStartTest: () -> Unit,
    onSelectServer: (SpeedTestServer) -> Unit
) {
    val scrollState = rememberScrollState()
    var hasAutoStarted by rememberSaveable { mutableStateOf(false) }

    // Dispara automaticamente apenas na primeira descoberta de servidor.
    LaunchedEffect(serverDetails, status, hasAutoStarted) {
        if (!hasAutoStarted && serverDetails != null && status == RunStatus.IDLE) {
            delay(800)
            onStartTest()
            hasAutoStarted = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
        ModernActionButton(status = status, onStartTest = onStartTest)
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
            isLoadingServers = isLoadingServers,
            onSelectServer = onSelectServer
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Connection card não precisa mais do "onChangeServer"
        ModernConnectionCard(
            clientInfo = clientInfo,
            serverDetails = serverDetails,
            wifiData = wifiData
        )
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSelectionCard(
    availableServers: List<SpeedTestServer>,
    selectedServer: SpeedTestServer?,
    isLoadingServers: Boolean,
    onSelectServer: (SpeedTestServer) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceColorAtElevation(2.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Servidor de teste",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (!isLoadingServers && availableServers.isNotEmpty()) expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedServer?.let { "${it.name} - ${it.city}" } ?: "Seleção automática",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    label = { Text("Servidor ativo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    singleLine = true
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableServers.forEach { server ->
                        DropdownMenuItem(
                            text = { Text("${server.name} - ${server.city}") },
                            onClick = {
                                onSelectServer(server)
                                expanded = false
                            }
                        )
                    }
                }
            }

            if (isLoadingServers) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    text = "${availableServers.size} servidores disponíveis",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

// Composable do gráfico atualizado para lidar com as duas listas e a sobreposição
@Composable
fun SpeedTestGraph(
    modifier: Modifier = Modifier,
    downloadSamples: List<Double>,
    uploadSamples: List<Double>,
    status: RunStatus,
    phase: TestPhase
) {
    Box(modifier = modifier) {
        // Cores para cada tipo de teste
        val downloadColor = MaterialTheme.colorScheme.secondary
        val uploadColor = MaterialTheme.colorScheme.primary

        // Determina o valor máximo para normalizar a altura do gráfico
        val maxDownload = downloadSamples.maxOrNull() ?: 0.0
        val maxUpload = uploadSamples.maxOrNull() ?: 0.0
        val overallMax = max(maxDownload, maxUpload).toFloat()

        // Desenha o gráfico de download (sempre, se houver dados)
        if (downloadSamples.isNotEmpty()) {
            val isVisible = status == RunStatus.DONE || (status == RunStatus.RUNNING && phase == TestPhase.DOWNLOAD)
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

        // Desenha o gráfico de upload (se houver dados)
        if (uploadSamples.isNotEmpty()) {
            val isVisible = status == RunStatus.DONE || (status == RunStatus.RUNNING && phase == TestPhase.UPLOAD)
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

// Helper Composable para desenhar um caminho de gráfico individual (evita repetição de código)
@Composable
internal fun GraphPath( // Removido 'private' para 'internal' (ou sem modificador)
    samples: List<Double>,
    color: Color,
    maxSample: Float
) {
    val brush = Brush.verticalGradient(
        colors = listOf(color.copy(alpha = 0.4f), Color.Transparent)
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (samples.size > 1) {
            val path = Path()
            val linePath = Path()
            path.moveTo(0f, size.height) // Começa no canto inferior esquerdo

            samples.forEachIndexed { index, sample ->
                val x = (index.toFloat() / (samples.size - 1).coerceAtLeast(1)) * size.width
                val y = size.height - ((sample.toFloat() / maxSample.coerceAtLeast(1f)) * size.height)

                if (index == 0) {
                    path.lineTo(x, y)
                    linePath.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                    linePath.lineTo(x, y)
                }
            }

            path.lineTo(size.width, size.height) // Fecha no canto inferior direito
            path.close()

            drawPath(path, brush) // Desenha o preenchimento
            drawPath(linePath, color, style = Stroke(width = 6f, cap = StrokeCap.Round)) // Desenha a linha
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
        TestPhase.COMPLETED -> downloadSpeed // Mostra o download no final
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(280.dp)
        ) {
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
                        if (testPhase == TestPhase.UPLOAD) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModernMetricCard(
                label = "Ping",
                value = "$pingMs",
                unit = "ms",
                icon = Icons.Default.Timer,
                color = colors.tertiary,
                isLoading = status == RunStatus.RUNNING && testPhase == TestPhase.PING,
                modifier = Modifier.weight(1f)
            )
            ModernMetricCard(
                label = "Jitter",
                value = "$jitterMs",
                unit = "ms",
                icon = Icons.Default.NetworkCheck,
                color = colors.primary,
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
                color = colors.secondary,
                isLoading = status == RunStatus.RUNNING && testPhase == TestPhase.DOWNLOAD,
                modifier = Modifier.weight(1f)
            )
            ModernMetricCard(
                label = "Upload",
                value = String.format("%.1f", uploadMbps),
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
    unit: String,
    icon: ImageVector,
    color: Color,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = modifier
            .height(110.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceColorAtElevation(2.dp)
        ),
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
                            text = value,
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
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = colors.onPrimary,
                strokeWidth = 2.5.dp
            )
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
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceColorAtElevation(2.dp)
        ),
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
                        ModernInfoRow(icon = Icons.Default.Storage, label = "Servidor", value = "${server.name} - ${server.city}", showDivider = true)
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(color = colors.outlineVariant, thickness = 1.dp)
        }
    }
}
