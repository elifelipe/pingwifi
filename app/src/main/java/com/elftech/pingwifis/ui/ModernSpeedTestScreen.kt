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

    // Dispara teste automaticamente se um servidor for selecionado e o teste estiver ocioso
    LaunchedEffect(serverDetails, status) {
        if (serverDetails != null && status == RunStatus.IDLE) {
            delay(800) // Pequeno delay para o usuário ver a UI antes do teste iniciar
            onStartTest()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // Fundo com gradiente suave para um visual mais moderno
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Azul escuro (topo)
                        Color(0xFF1E293B), // Azul intermediário
                        Color(0xFF0F172A)  // Azul escuro (base)
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
                progress = progress // <-- Passando o progresso para o medidor
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
                    color = Color(0xFFEF4444), // Cor de erro mais viva
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
            Spacer(modifier = Modifier.height(80.dp)) // Espaço extra no final para scroll
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
                tint = Color(0xFF3B82F6), // Azul vibrante
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
        // Mostra um indicador de carregamento enquanto busca o servidor
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
                    color = Color(0xFF94A3B8) // Cinza azulado
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
    progress: Float // <-- Recebendo o progresso
) {
    // Determina qual velocidade exibir com base na fase do teste
    val displaySpeed = when (testPhase) {
        TestPhase.DOWNLOAD -> downloadSpeed
        TestPhase.UPLOAD -> uploadSpeed
        TestPhase.COMPLETED -> downloadSpeed // Mostra o download no final
        else -> 0.0
    }

    // Anima a mudança de velocidade de forma suave
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
        // Indicador de fase com animação de fade e slide
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
            // *** CORREÇÃO APLICADA AQUI ***
            // Anima o progresso do anel de 0 a 1 (0% a 100%)
            val animatedRingProgress by animateFloatAsState(
                targetValue = when (status) {
                    RunStatus.RUNNING -> progress / 100f // Converte a porcentagem para um valor entre 0.0 e 1.0
                    RunStatus.DONE -> 1f // Completa o círculo no final
                    else -> 0f // Zera o círculo se estiver ocioso ou com erro
                },
                animationSpec = tween(400, easing = LinearEasing), // Animação suave para o preenchimento
                label = "ring_progress"
            )

            CircularProgressIndicator(
                progress = { animatedRingProgress }, // Usa o valor animado para o preenchimento
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
                    color = Color(0xFFCBD5E1) // Cinza claro
                )
                // Ícone que indica a fase atual (download/upload)
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
    // Grid 2x2 para as métricas
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
                color = Color(0xFF8B5CF6), // Roxo
                isLoading = status == RunStatus.RUNNING && testPhase == TestPhase.PING,
                modifier = Modifier.weight(1f)
            )
            ModernMetricCard(
                label = "Jitter",
                value = "$jitterMs",
                unit = "ms",
                icon = Icons.Default.NetworkCheck,
                color = Color(0xFFEC4899), // Rosa
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
                color = Color(0xFF10B981), // Verde
                isLoading = status == RunStatus.RUNNING && testPhase == TestPhase.DOWNLOAD,
                modifier = Modifier.weight(1f)
            )
            ModernMetricCard(
                label = "Upload",
                value = String.format("%.1f", uploadMbps),
                unit = "Mbps",
                icon = Icons.Default.ArrowUpward,
                color = Color(0xFF3B82F6), // Azul
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
            .shadow(8.dp, RoundedCornerShape(20.dp)), // Sombra suave
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B).copy(alpha = 0.8f) // Fundo semitransparente
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

            // Exibe um indicador de progresso enquanto a métrica está sendo testada
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
            disabledContainerColor = Color(0xFF475569) // Cor desabilitada mais clara
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
    var expanded by remember { mutableStateOf(true) } // Inicia expandido

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

