package com.elftech.pingwifi // Mantenha o seu pacote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dotlottie.dlplayer.Mode
import com.elftech.pingwifi.ui.NetTesterScreen
import com.elftech.pingwifi.ui.theme.PingWifiTheme
import com.elftech.pingwifi.viewmodel.NetworkViewModel
import com.lottiefiles.dotlottie.core.compose.ui.DotLottieAnimation
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PingWifiTheme(darkTheme = true) {
                // Configura a navegação do app, começando pela splash screen.
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
    // Este efeito executa o código dentro dele apenas uma vez.
    LaunchedEffect(key1 = true) {
        // Aguarda 3 segundos (3000 milissegundos). Você pode ajustar este tempo.
        delay(3000L)
        // Navega para a tela principal e remove a splash da pilha para que o usuário não possa voltar.
        navController.navigate("main") {
            popUpTo("splash") { inclusive = true }
        }
    }

    // A interface da sua splash screen com a animação Lottie centralizada.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(Color.Black) // Fundo escuro para combinar com o tema.
    ) {
        DotLottieAnimation(
            source = DotLottieSource.Url("https://lottie.host/aaa23968-7078-4261-a0ae-584240fccba3/JOOnBKAnbq.lottie"),
            autoplay = true,
            loop = true,
            speed = 1f, // VELOCIDADE AJUSTADA AQUI
            useFrameInterpolation = false,
            playMode = Mode.FORWARD
        )
    }
}
