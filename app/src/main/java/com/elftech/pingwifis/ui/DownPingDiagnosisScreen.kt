package com.elftech.pingwifis.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elftech.pingwifis.data.model.DownPingState
import com.elftech.pingwifis.data.model.HttpCheckResult
import com.elftech.pingwifis.data.model.RunStatus
import androidx.compose.material3.surfaceColorAtElevation

@Composable
fun DownPingDiagnosisScreen(
    state: DownPingState,
    onTargetChange: (String) -> Unit,
    onStart: (String, Int) -> Unit,
    onStop: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var pingCountText by remember { mutableStateOf("6") }
    val running = state.status == RunStatus.RUNNING

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Diagnóstico de Rede",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground
                )
            }
        }

        // Input card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceColorAtElevation(2.dp)),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = state.target,
                        onValueChange = onTargetChange,
                        label = { Text("IP ou domínio") },
                        singleLine = true,
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.onSurface,
                            unfocusedTextColor = colors.onSurface,
                            disabledTextColor = colors.onSurfaceVariant,
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.outline,
                            focusedLabelColor = colors.primary,
                            unfocusedLabelColor = colors.onSurfaceVariant,
                            cursorColor = colors.primary
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.Language, contentDescription = null, tint = colors.primary)
                        }
                    )

                    OutlinedTextField(
                        value = pingCountText,
                        onValueChange = { if (it.all(Char::isDigit)) pingCountText = it },
                        label = { Text("Quantidade de pings (3–20)") },
                        singleLine = true,
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.onSurface,
                            unfocusedTextColor = colors.onSurface,
                            disabledTextColor = colors.onSurfaceVariant,
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.outline,
                            focusedLabelColor = colors.primary,
                            unfocusedLabelColor = colors.onSurfaceVariant,
                            cursorColor = colors.primary
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.Numbers, contentDescription = null, tint = colors.primary)
                        }
                    )

                    Button(
                        onClick = {
                            if (running) onStop()
                            else onStart(state.target, pingCountText.toIntOrNull() ?: 6)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (running) colors.error else colors.primary,
                            contentColor = if (running) colors.onError else colors.onPrimary
                        )
                    ) {
                        Icon(
                            if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (running) "Parar diagnóstico" else "Executar diagnóstico",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Progress card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceColorAtElevation(2.dp)),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.primary
                        )
                    } else {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = state.progressMessage,
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Error
        state.error?.let { errorMsg ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = colors.onErrorContainer)
                        Text(
                            text = errorMsg,
                            color = colors.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Report summary
        state.report?.let { report ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceColorAtElevation(2.dp)),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Resumo",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface
                        )
                        HorizontalDivider(color = colors.outlineVariant)
                        DiagInfoRow("Destino", report.normalizedHost)
                        DiagInfoRow("IPs resolvidos", report.resolvedIps.joinToString())
                        DiagInfoRow(
                            "Pacotes perdidos",
                            "${report.pingSummary.packetLossPercent}%",
                            valueColor = if (report.pingSummary.packetLossPercent > 0) colors.error else Color(0xFF4CAF50)
                        )
                        DiagInfoRow("Latência média", "${report.pingSummary.avgLatencyMs} ms")
                    }
                }
            }

            if (report.httpChecks.isNotEmpty()) {
                item {
                    Text(
                        "HTTP / HTTPS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(report.httpChecks) { check ->
                    HttpCheckCard(check)
                }
            }

            if (report.tcpPortResults.isNotEmpty()) {
                item {
                    Text(
                        "Portas TCP",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(report.tcpPortResults.entries.toList()) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (entry.value)
                                Color(0xFF4CAF50).copy(alpha = 0.12f)
                            else
                                colors.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                if (entry.value) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (entry.value) Color(0xFF4CAF50) else colors.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Porta ${entry.key}: ${if (entry.value) "aberta" else "fechada / sem resposta"}",
                                color = colors.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // Ping events
        if (state.pingResults.isNotEmpty()) {
            item {
                Text(
                    "Eventos do ping",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(state.pingResults.takeLast(30)) { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surfaceColorAtElevation(3.dp)
                    )
                ) {
                    Text(
                        text = result.message,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = colors.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        item { Spacer(Modifier.height(60.dp)) }
    }
}

@Composable
private fun DiagInfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun HttpCheckCard(check: HttpCheckResult) {
    val colors = MaterialTheme.colorScheme
    val okColor = Color(0xFF4CAF50)
    val statusColor = if (check.ok) okColor else colors.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.10f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (check.ok) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = check.url,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = "Status: ${check.statusCode?.toString() ?: "–"}",
                color = statusColor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Tempo: ${check.latencyMs} ms",
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            if (check.message.isNotBlank()) {
                Text(
                    text = check.message,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
