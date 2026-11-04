package com.ieo.app

import android.Manifest
import android.car.Car
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ieo.app.core.session.SessionManager
import com.ieo.app.ui.login.LoginRoute
import com.ieo.app.ui.main.MainScreen
import com.ieo.app.ui.splash.SplashScreen
import com.ieo.app.ui.theme.IeoTheme

class MainActivity : ComponentActivity() {

    private val sensors by lazy { (application as IeoApplication).container.vehicleSensors }

    // 자동 도착(WAIT-001)에 속도와 위치가 필요하다. 거절해도 "도착했어요" 버튼으로 진행할 수 있다
    private val sensorPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) sensors.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = (application as IeoApplication).container.session
        setContent {
            IeoTheme {
                // 창 배경은 시작 화면용 주황이라, 그 위 모든 화면에 테마 바탕(흰색)을 깔아 둔다
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    IeoApp(session, onHomeShown = ::startSensors)
                }
            }
        }
    }

    /** 로그인한 뒤에 묻는다. 이미 허락했으면 바로 시작한다 */
    private fun startSensors() {
        val missing = SENSOR_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) sensors.start() else sensorPermissions.launch(missing.toTypedArray())
    }

    private companion object {
        val SENSOR_PERMISSIONS = listOf(Car.PERMISSION_SPEED, Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private enum class Screen { SPLASH, LOGIN, HOME }

/**
 * 화면 이동. 로그인 여부는 [SessionManager]가 정하고, 여기서는 그 값을 따라간다.
 * - 로그인 화면에서 로그인에 성공하면 홈으로
 * - 홈에서 로그아웃되면(리프레시 토큰 만료 포함) 로그인 화면으로
 */
@Composable
private fun IeoApp(session: SessionManager, onHomeShown: () -> Unit) {
    val isLoggedIn by session.isLoggedIn.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.SPLASH) }

    LaunchedEffect(isLoggedIn) {
        // 시작 화면은 애니메이션이 끝난 뒤 스스로 넘어간다
        if (screen != Screen.SPLASH) screen = if (isLoggedIn) Screen.HOME else Screen.LOGIN
    }

    when (screen) {
        Screen.SPLASH -> SplashScreen(
            isLoggedIn = isLoggedIn,
            onStart = { screen = Screen.LOGIN },
            onAutoLogin = { screen = Screen.HOME },
        )
        Screen.LOGIN -> {
            BackHandler { screen = Screen.SPLASH }
            LoginRoute()
        }
        Screen.HOME -> {
            LaunchedEffect(Unit) { onHomeShown() }
            MainScreen()
        }
    }
}
