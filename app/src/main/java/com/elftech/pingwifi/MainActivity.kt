package com.elftech.pingwifi

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.elftech.pingwifi.ui.NetTesterScreen
import com.elftech.pingwifi.ui.theme.PingWifiTheme
import com.elftech.pingwifi.viewmodel.NetworkViewModel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PingWifiTheme(darkTheme = true) {
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
            SplashScreen(navController = navController)
        }
        composable("main") {
            val vm: NetworkViewModel = viewModel()
            NetTesterScreen(vm)
        }
    }
}

@Composable
fun SplashScreen(navController: NavController) {
    // Navegação após 2.5 segundos
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
            .background(Color.Black)
    ) {
        // Animação customizada de rede Wi-Fi
        WiFiAnimation()
    }
}

@Composable
fun WiFiAnimation() {
    // Estados de animação
    val infiniteTransition = rememberInfiniteTransition(label = "wifi")

    // Animação de pulso para as ondas
    val waveAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waves"
    )

    // Animação de rotação
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Animação de escala
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(250.dp)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                // Desenha animação Wi-Fi customizada
                drawWiFiAnimation(
                    waveProgress = waveAnimation,
                    rotation = rotation,
                    scale = scale
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Texto com animação
        Text(
            text = "PingWiFi",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Indicador de carregamento
        LoadingDots()
    }
}

@Composable
fun LoadingDots() {
    var dotCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dotCount = (dotCount + 1) % 4
        }
    }

    val dots = when (dotCount) {
        1 -> "."
        2 -> ".."
        3 -> "..."
        else -> ""
    }

    Text(
        text = "Carregando$dots",
        fontSize = 16.sp,
        color = Color.White.copy(alpha = 0.7f)
    )
}

private fun DrawScope.drawWiFiAnimation(
    waveProgress: Float,
    rotation: Float,
    scale: Float
) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val baseRadius = size.minDimension / 4

    // Cor principal com gradiente
    val primaryColor = Color(0xFF4CAF50)
    val secondaryColor = Color(0xFF2196F3)

    // Desenha ponto central (roteador)
    rotate(degrees = rotation, pivot = Offset(centerX, centerY)) {
        drawCircle(
            color = primaryColor,
            radius = 10f * scale,
            center = Offset(centerX, centerY)
        )
    }

    // Desenha ondas Wi-Fi
    for (i in 1..3) {
        val waveRadius = baseRadius * i * (1 + waveProgress * 0.5f)
        val alpha = (1f - waveProgress) * 0.5f / i

        drawCircle(
            color = primaryColor.copy(alpha = alpha),
            radius = waveRadius,
            center = Offset(centerX, centerY),
            style = Stroke(
                width = 3f * (4 - i)
            )
        )
    }

    // Desenha pontos orbitando (dispositivos conectados)
    for (i in 0..2) {
        val angle = rotation + (i * 120f)
        val orbitRadius = baseRadius * 2f * scale
        val x = centerX + cos(Math.toRadians(angle.toDouble())).toFloat() * orbitRadius
        val y = centerY + sin(Math.toRadians(angle.toDouble())).toFloat() * orbitRadius

        drawCircle(
            color = secondaryColor,
            radius = 6f,
            center = Offset(x, y)
        )

        // Linha de conexão
        drawLine(
            color = secondaryColor.copy(alpha = 0.3f),
            start = Offset(centerX, centerY),
            end = Offset(x, y),
            strokeWidth = 1f
        )
    }
}
