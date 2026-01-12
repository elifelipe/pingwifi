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
import androidx.compose.material3.surfaceColorAtElevation

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
    val colors = MaterialTheme.colorScheme
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
                    containerColor = colors.errorContainer
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
                        tint = colors.error
                    )
                    Text(
                        text = errorMsg,
                        color = colors.onErrorContainer,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Results List
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.surfaceColorAtElevation(2.dp)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        color = colors.onSurface
                    )
                    if (status == RunStatus.RUNNING) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colors.primary
                            )
                            Text(
                                "Testando...",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant
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
                                tint = colors.outline
                            )
                            Text(
                                "Digite um endereço e clique em \"Iniciar Ping\"",
                                fontSize = 14.sp,
                                color = colors.onSurfaceVariant,
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
    val colors = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.NetworkPing,
            contentDescription = null,
            tint = colors.secondary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Teste de Ping",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onBackground
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
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceColorAtElevation(2.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.outline,
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface,
                    disabledTextColor = colors.onSurfaceVariant,
                    focusedLabelColor = colors.primary,
                    unfocusedLabelColor = colors.onSurfaceVariant
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
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.outline,
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface,
                    disabledTextColor = colors.onSurfaceVariant,
                    focusedLabelColor = colors.primary,
                    unfocusedLabelColor = colors.onSurfaceVariant
                ),
                singleLine = true
            )

    // Action Button
            val buttonContainer = if (status == RunStatus.RUNNING) colors.error else colors.secondary
            val buttonContent = if (status == RunStatus.RUNNING) colors.onError else colors.onSecondary
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
                    .height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonContainer,
                    contentColor = buttonContent
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
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
            Text(
                "Estatísticas",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
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
                    color = colors.primary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Recebidos",
                    value = "${summary.packetsReceived}",
                    icon = Icons.Default.CheckCircle,
                    color = colors.secondary,
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
                    color = if (summary.packetLossPercent > 0) colors.error else colors.secondary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Jitter",
                    value = "${summary.jitterMs}ms",
                    icon = Icons.Default.ShowChart,
                    color = colors.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            // Latência
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LatencyRow("Mínima", summary.minLatencyMs, colors.secondary)
                LatencyRow("Média", summary.avgLatencyMs, colors.primary)
                LatencyRow("Máxima", summary.maxLatencyMs, colors.error)
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
    val colors = MaterialTheme.colorScheme

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
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
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
                color = colors.onSurface
            )
        }
    }
}

@Composable
private fun LatencyRow(label: String, value: Int, color: Color) {
    val colors = MaterialTheme.colorScheme

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
                color = colors.onSurfaceVariant
            )
        }
        Text(
            text = "${value}ms",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )
    }
}

@Composable
private fun PingResultItem(result: PingResult) {
    val colors = MaterialTheme.colorScheme
    val successColor = colors.secondary
    val warningColor = colors.error
    val infoColor = colors.primary

    val backgroundColor = when (result.status) {
        PingStatus.SUCCESS -> successColor.copy(alpha = 0.12f)
        PingStatus.TIMEOUT -> warningColor.copy(alpha = 0.12f)
        PingStatus.ERROR -> warningColor.copy(alpha = 0.12f)
        PingStatus.RESOLVING, PingStatus.RESOLVED -> infoColor.copy(alpha = 0.12f)
    }

    val iconColor = when (result.status) {
        PingStatus.SUCCESS -> successColor
        PingStatus.TIMEOUT -> warningColor
        PingStatus.ERROR -> warningColor
        PingStatus.RESOLVING, PingStatus.RESOLVED -> infoColor
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
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
