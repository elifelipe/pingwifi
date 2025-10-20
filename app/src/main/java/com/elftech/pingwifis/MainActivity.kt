package com.elftech.pingwifis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.elftech.pingwifis.ui.UpdatedNetTesterScreen
import com.elftech.pingwifis.ui.theme.EnhancedPingWifiTheme
import com.elftech.pingwifis.viewmodel.EnhancedNetworkViewModel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EnhancedPingWifiTheme(darkTheme = true) {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            ModernSplashScreen(navController = navController)
        }
        composable("main") {
            val vm: EnhancedNetworkViewModel = viewModel()
            UpdatedNetTesterScreen(vm)
        }
    }
}

@Composable
fun ModernSplashScreen(navController: NavController) {
    LaunchedEffect(key1 = true) {
        delay(2500L)
        navController.navigate("main") {
            popUpTo("splash") { inclusive = true }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ModernWiFiAnimation()
            Spacer(modifier = Modifier.height(40.dp))
            Text(text = "PingWiFi", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Teste de Velocidade Real", fontSize = 16.sp, color = Color(0xFF94A3B8))
            Spacer(modifier = Modifier.height(32.dp))
            LoadingIndicator()
        }
    }
}

@Composable
fun ModernWiFiAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "wifi")
    val waveProgress by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "wave")
    val rotation by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(animation = tween(3000, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "rotation")
    val scale by infiniteTransition.animateFloat(initialValue = 0.95f, targetValue = 1.05f, animationSpec = infiniteRepeatable(animation = tween(1000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "scale")

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val baseRadius = size.minDimension / 5
            drawCircle(color = Color(0xFF3B82F6), radius = 12f * scale, center = Offset(centerX, centerY))
            for (i in 1..3) {
                val waveRadius = baseRadius * i * (1 + waveProgress * 0.4f)
                val alpha = (1f - waveProgress) * 0.6f / i
                drawCircle(color = Color(0xFF3B82F6).copy(alpha = alpha), radius = waveRadius, center = Offset(centerX, centerY), style = Stroke(width = 4f * (4 - i), cap = StrokeCap.Round))
            }
            for (i in 0..2) {
                val angle = rotation + (i * 120f)
                val orbitRadius = baseRadius * 2.5f
                val x = centerX + cos(Math.toRadians(angle.toDouble())).toFloat() * orbitRadius
                val y = centerY + sin(Math.toRadians(angle.toDouble())).toFloat() * orbitRadius
                drawCircle(color = Color(0xFF10B981), radius = 8f, center = Offset(x, y))
                drawLine(color = Color(0xFF10B981).copy(alpha = 0.3f), start = Offset(centerX, centerY), end = Offset(x, y), strokeWidth = 2f)
            }
        }
    }
}

@Composable
fun LoadingIndicator() {
    var dotCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dotCount = (dotCount + 1) % 4
        }
    }
    val dots = ".".repeat(dotCount)
    Text(text = "Iniciando$dots", fontSize = 14.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
}
