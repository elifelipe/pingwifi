package com.elftech.pingwifis.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.elftech.pingwifis.data.model.*
import com.elftech.pingwifis.viewmodel.EnhancedNetworkViewModel
import kotlinx.coroutines.delay
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatedNetTesterScreen(vm: EnhancedNetworkViewModel) {
    val extendedSpeed by vm.extendedSpeed.collectAsState()
    val wifi by vm.wifi.collectAsState()
    val trace by vm.trace.collectAsState()
    val serverDetails by vm.serverDetails.collectAsState()
    val clientInfo by vm.clientInfo.collectAsState()
    val isLoadingServers by vm.isLoadingServers.collectAsState()
    val availableServers by vm.availableServers.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshWifi() }

    LaunchedEffect(Unit) {
        delay(300)
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Velocidade", "Diagnóstico")
    var showServerDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PingWiFi Pro") },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { showServerDialog = true }) {
                            Icon(Icons.Default.Storage, contentDescription = "Selecionar Servidor")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Filled.Speed, contentDescription = title)
                                1 -> Icon(Icons.Filled.Search, contentDescription = title)
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> ModernSpeedTestScreen(
                    status = extendedSpeed.status,
                    testPhase = extendedSpeed.currentPhase,
                    downloadMbps = extendedSpeed.downloadMbps,
                    uploadMbps = extendedSpeed.uploadMbps,
                    progress = extendedSpeed.progressPct,
                    error = extendedSpeed.error,
                    wifiData = wifi,
                    serverDetails = serverDetails,
                    clientInfo = clientInfo,
                    pingMs = extendedSpeed.pingMs,
                    jitterMs = extendedSpeed.jitterMs,
                    isLoadingServers = isLoadingServers,
                    downloadSpeedSamples = extendedSpeed.downloadSpeedSamples,
                    uploadSpeedSamples = extendedSpeed.uploadSpeedSamples,
                    onStartTest = { vm.startSpeedTest() },
                    onChangeServer = { showServerDialog = true }
                )
                1 -> EnhancedDiagnosisTab(
                    traceState = trace,
                    onRunTraceroute = { host -> vm.runTraceroute(host) }
                )
            }
        }

        if (showServerDialog) {
            ServerSelectionDialog(
                servers = availableServers,
                currentServer = serverDetails,
                onDismiss = { showServerDialog = false },
                onSelectServer = { server ->
                    vm.changeServer(server)
                    showServerDialog = false
                }
            )
        }
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
    downloadSpeedSamples: List<Double>,
    uploadSpeedSamples: List<Double>,
    onStartTest: () -> Unit,
    onChangeServer: () -> Unit
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(serverDetails, status) {
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
            ModernHeader(clientInfo, isLoadingServers)
            Spacer(modifier = Modifier.height(24.dp))
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
                    color = Color(0xFFEF4444),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
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
        val downloadColor = Color(0xFF10B981)
        val uploadColor = Color(0xFF3B82F6)

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
private fun GraphPath(
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
    uploadSpeed: Double,
    progress: Float
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
                trackColor = Color(0xFF1E293B),
                color = if (testPhase == TestPhase.UPLOAD) Color(0xFF3B82F6) else Color(0xFF10B981),
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
                    color = Color.White
                )
                Text(
                    text = "Mbps",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    color = Color(0xFFCBD5E1)
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
                        tint = if (testPhase == TestPhase.UPLOAD) Color(0xFF3B82F6) else Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
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
                Text(text = label, fontSize = 13.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
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
                        Text(text = value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = unit, fontSize = 14.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernActionButton(status: RunStatus, onStartTest: () -> Unit) {
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
        Text(text = buttonText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.8f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Detalhes da Conexão", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle", tint = Color(0xFF94A3B8)
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
                        ModernInfoRow(icon = Icons.Default.Public, label = "IP Público", value = it.ipAddress, showDivider = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernInfoRow(icon: ImageVector, label: String, value: String, showDivider: Boolean) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, fontSize = 12.sp, color = Color(0xFF94A3B8))
                Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }
        }
        if (showDivider) {
            HorizontalDivider(color = Color(0xFF334155), thickness = 1.dp)
        }
    }
}


@Composable
fun EnhancedDiagnosisTab(
    traceState: TracerouteState,
    onRunTraceroute: (String) -> Unit
) {
    var traceHost by remember { mutableStateOf("google.com") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Traceroute", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = traceHost,
                onValueChange = { traceHost = it },
                label = { Text("Host de destino") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onRunTraceroute(traceHost) },
                enabled = traceState.status != RunStatus.RUNNING,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Iniciar Traceroute")
            }
        }

        if (traceState.status == RunStatus.RUNNING && traceState.lines.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        if (traceState.lines.isNotEmpty() || traceState.error != null) {
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        if (traceState.lines.isNotEmpty()) {
                            traceState.lines.forEach { line ->
                                Text(
                                    line,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                        traceState.error?.let {
                            Text("Erro: $it", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSelectionDialog(
    servers: List<SpeedTestServer>,
    currentServer: SpeedTestServer?,
    onDismiss: () -> Unit,
    onSelectServer: (SpeedTestServer) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .fillMaxHeight(0.7f)
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Selecionar Servidor",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (servers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Carregando servidores...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        "Escolha o servidor mais próximo:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = servers.take(10)) { server ->
                            val isSelected = server == currentServer

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                ),
                                onClick = { onSelectServer(server) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = server.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${server.city}, ${server.country}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (isSelected) {
                                        Spacer(Modifier.width(12.dp))
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Selecionado",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Fechar")
                }
            }
        }
    }
}

