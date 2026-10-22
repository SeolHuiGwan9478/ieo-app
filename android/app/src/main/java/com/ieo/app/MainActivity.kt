package com.ieo.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ieo.app.data.SessionStore
import com.ieo.app.ui.home.HomeScreen
import com.ieo.app.ui.login.LoginScreen
import com.ieo.app.ui.splash.SplashScreen
import com.ieo.app.ui.theme.IeoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IeoTheme {
                IeoApp()
            }
        }
    }
}

private enum class Screen { SPLASH, LOGIN, HOME }

@Composable
private fun IeoApp() {
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    var screen by rememberSaveable { mutableStateOf(Screen.SPLASH) }
    // TODO: 서버 인증 API(/api/auth/*)가 구현되면 실제 요청으로 바꾼다
    val notReady = { Toast.makeText(context, R.string.not_ready, Toast.LENGTH_SHORT).show() }

    when (screen) {
        Screen.SPLASH -> SplashScreen(
            isLoggedIn = session.isLoggedIn(),
            onStart = { screen = Screen.LOGIN },
            onAutoLogin = { screen = Screen.HOME },
        )
        Screen.LOGIN -> {
            BackHandler { screen = Screen.SPLASH }
            LoginScreen(
                onRequestCode = { },
                onLogin = { _, _ -> notReady() },
            )
        }
        Screen.HOME -> HomeScreen()
    }
}
