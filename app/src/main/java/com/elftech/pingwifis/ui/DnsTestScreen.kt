package com.elftech.pingwifis.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elftech.pingwifis.data.model.DnsResult
import com.elftech.pingwifis.data.model.DnsTestState
import com.elftech.pingwifis.data.model.RunStatus
import androidx.compose.material3.surfaceColorAtElevation

@Composable
fun DnsTestScreen(
    state: DnsTestState,
    onTargetChange: (String) -> Unit,
    onStartTest: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column {
            Text(
                "Teste de DNS",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground
            )
            Text(
                "Compare a latência dos servidores DNS",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }

        // Input card
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
                    label = { Text("Domínio para testar") },
                    placeholder = { Text("google.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = {
                        focusManager.clearFocus()
                        onStartTest(state.target)
                    }),
                    leadingIcon = {
                        Icon(Icons.Default.Dns, contentDescription = null, tint = colors.primary)
                    }
                )

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onStartTest(state.target)
                    },
                    enabled = state.status != RunStatus.RUNNING && state.target.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.status == RunStatus.RUNNING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Testando…")
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Iniciar Teste", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Loading indicator
        AnimatedVisibility(visible = state.status == RunStatus.RUNNING) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = colors.primary
            )
        }

        // Results
        AnimatedVisibility(visible = state.results.isNotEmpty()) {
            DnsResultsCard(results = state.results)
        }

        // Bar chart
        AnimatedVisibility(
            visible = state.results.isNotEmpty() && state.results.any { it.resolved }
        ) {
            DnsBarChart(results = state.results)
        }

        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun DnsResultsCard(results: List<DnsResult>) {
    val colors = MaterialTheme.colorScheme
    val bestLatency = results.filter { it.resolved && it.latencyMs > 0 }.minOfOrNull { it.latencyMs }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceColorAtElevation(2.dp)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Resultados",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            results.forEach { result ->
                val isBest = result.resolved && result.latencyMs == bestLatency
                DnsResultRow(result = result, isBest = isBest)
                if (result != results.last()) {
                    HorizontalDivider(color = colors.outlineVariant, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun DnsResultRow(result: DnsResult, isBest: Boolean) {
    val colors = MaterialTheme.colorScheme

    val statusColor = when {
        !result.resolved -> colors.error
        result.latencyMs < 30 -> Color(0xFF4CAF50)
        result.latencyMs < 80 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                if (result.resolved) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        result.server.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface
                    )
                    if (isBest) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Mais rápido",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                if (result.server.address != "system") {
                    Text(
                        result.server.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
                result.resolvedIp?.let {
                    Text(
                        "→ $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            text = if (result.resolved && result.latencyMs > 0) "${result.latencyMs} ms"
                   else result.error ?: "Falhou",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (result.resolved) statusColor else colors.error,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun DnsBarChart(results: List<DnsResult>) {
    val colors = MaterialTheme.colorScheme
    val resolved = results.filter { it.resolved && it.latencyMs > 0 }
    if (resolved.isEmpty()) return

    val maxLatency = resolved.maxOf { it.latencyMs }.coerceAtLeast(1)
    val barColors = listOf(
        Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFF9C27B0)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceColorAtElevation(2.dp)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Comparação Visual",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            resolved.forEachIndexed { index, result ->
                val fraction = result.latencyMs.toFloat() / maxLatency
                val barColor = barColors.getOrElse(index) { colors.primary }

                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            result.server.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            "${result.latencyMs} ms",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = barColor
                        )
                    }
                    Spacer(Modifier.height(4.dp))

                    val animatedFraction by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(800, easing = FastOutSlowInEasing),
                        label = "dns_bar_$index"
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                    ) {
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.15f),
                            size = Size(size.width, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f)
                        )
                        drawRoundRect(
                            color = barColor,
                            size = Size(size.width * animatedFraction, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f)
                        )
                    }
                }
            }
        }
    }
}
