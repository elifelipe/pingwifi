package com.elftech.pingwifis.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elftech.pingwifis.data.PingResult
import com.elftech.pingwifis.data.PingStatus
import com.elftech.pingwifis.data.PingSummary
import com.elftech.pingwifis.data.model.RunStatus
import kotlinx.coroutines.delay

@Composable
fun ModernPingTestScreen(
    pingHost: String,
    onHostChange: (String) -> Unit,
    pingResults: List<PingResult>,
    pingSummary: PingSummary?,
    status: RunStatus,
    error: String?,
    onStartPing: (String, Int) -> Unit,
    onStopPing: () -> Unit
) {
    var pingCount by remember { mutableStateOf("10") }
    val listState = rememberLazyListState()

    // Auto-scroll para o último resultado
    LaunchedEffect(pingResults.size) {
        if (pingResults.isNotEmpty()) {
            listState.animateScrollToItem(pingResults.size - 1)
        }
    }

    Column(
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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        PingTestHeader()

        // Input Section
        PingInputSection(
            host = pingHost,
            onHostChange = onHostChange,
            count = pingCount,
            onCountChange = { pingCount = it },
            status = status,
            onStartPing = { onStartPing(pingHost, pingCount.toIntOrNull() ?: 10) },
            onStopPing = onStopPing
        )

        // Summary Card (aparece quando há resultados)
        AnimatedVisibility(
            visible = pingSummary != null && status == RunStatus.DONE,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            pingSummary?.let { summary ->
                PingSummaryCard(summary = summary)
            }
        }

        // Error Message
        error?.takeIf { it.isNotBlank() }?.let { errorMsg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = Color(0xFFEF4444)
                    )
                    Text(
                        text = errorMsg,
                        color = Color(0xFFFECDD3),
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Results List
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .shadow(8.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Resultados do Ping",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    if (status == RunStatus.RUNNING) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF3B82F6)
                            )
                            Text(
                                "Testando...",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (pingResults.isEmpty() && status == RunStatus.IDLE) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.NetworkPing,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color(0xFF475569)
                            )
                            Text(
                                "Digite um endereço e clique em \"Iniciar Ping\"",
                                fontSize = 14.sp,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pingResults) { result ->
                            PingResultItem(result = result)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PingTestHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.NetworkPing,
            contentDescription = null,
            tint = Color(0xFF10B981),
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Teste de Ping",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun PingInputSection(
    host: String,
    onHostChange: (String) -> Unit,
    count: String,
    onCountChange: (String) -> Unit,
    status: RunStatus,
    onStartPing: () -> Unit,
    onStopPing: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Host Input
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text("Endereço IP ou Host") },
                placeholder = { Text("Ex: 8.8.8.8 ou google.com") },
                leadingIcon = {
                    Icon(Icons.Default.Language, contentDescription = null)
                },
                enabled = status != RunStatus.RUNNING,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF475569),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFF1F5F9),
                    disabledTextColor = Color(0xFF64748B),
                    focusedLabelColor = Color(0xFF3B82F6),
                    unfocusedLabelColor = Color(0xFF94A3B8)
                ),
                singleLine = true
            )

            // Ping Count Input
            OutlinedTextField(
                value = count,
                onValueChange = { if (it.all { char -> char.isDigit() }) onCountChange(it) },
                label = { Text("Número de Pings") },
                placeholder = { Text("10") },
                leadingIcon = {
                    Icon(Icons.Default.Numbers, contentDescription = null)
                },
                enabled = status != RunStatus.RUNNING,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF475569),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFF1F5F9),
                    disabledTextColor = Color(0xFF64748B),
                    focusedLabelColor = Color(0xFF3B82F6),
                    unfocusedLabelColor = Color(0xFF94A3B8)
                ),
                singleLine = true
            )

            // Action Button
            Button(
                onClick = {
                    if (status == RunStatus.RUNNING) {
                        onStopPing()
                    } else {
                        onStartPing()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(12.dp, CircleShape),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (status == RunStatus.RUNNING) Color(0xFFEF4444) else Color(0xFF10B981),
                    contentColor = Color.White
                )
            ) {
                if (status == RunStatus.RUNNING) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Parar Ping", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar Ping", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PingSummaryCard(summary: PingSummary) {
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
            Text(
                "Estatísticas",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Grid de métricas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "Enviados",
                    value = "${summary.packetsTransmitted}",
                    icon = Icons.Default.Send,
                    color = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Recebidos",
                    value = "${summary.packetsReceived}",
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "Perda",
                    value = "${summary.packetLossPercent}%",
                    icon = Icons.Default.ErrorOutline,
                    color = if (summary.packetLossPercent > 0) Color(0xFFEF4444) else Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Jitter",
                    value = "${summary.jitterMs}ms",
                    icon = Icons.Default.ShowChart,
                    color = Color(0xFF8B5CF6),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF334155))
            Spacer(modifier = Modifier.height(16.dp))

            // Latência
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LatencyRow("Mínima", summary.minLatencyMs, Color(0xFF10B981))
                LatencyRow("Média", summary.avgLatencyMs, Color(0xFF3B82F6))
                LatencyRow("Máxima", summary.maxLatencyMs, Color(0xFFEF4444))
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun LatencyRow(label: String, value: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = label,
                fontSize = 14.sp,
                color = Color(0xFF94A3B8)
            )
        }
        Text(
            text = "${value}ms",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun PingResultItem(result: PingResult) {
    val backgroundColor = when (result.status) {
        PingStatus.SUCCESS -> Color(0xFF10B981).copy(alpha = 0.1f)
        PingStatus.TIMEOUT -> Color(0xFFEF4444).copy(alpha = 0.1f)
        PingStatus.ERROR -> Color(0xFFEF4444).copy(alpha = 0.1f)
        PingStatus.RESOLVING, PingStatus.RESOLVED -> Color(0xFF3B82F6).copy(alpha = 0.1f)
    }

    val iconColor = when (result.status) {
        PingStatus.SUCCESS -> Color(0xFF10B981)
        PingStatus.TIMEOUT -> Color(0xFFEF4444)
        PingStatus.ERROR -> Color(0xFFEF4444)
        PingStatus.RESOLVING, PingStatus.RESOLVED -> Color(0xFF3B82F6)
    }

    val icon = when (result.status) {
        PingStatus.SUCCESS -> Icons.Default.CheckCircle
        PingStatus.TIMEOUT -> Icons.Default.AccessTime
        PingStatus.ERROR -> Icons.Default.Error
        PingStatus.RESOLVING, PingStatus.RESOLVED -> Icons.Default.Info
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = result.message,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
        }
    }
}