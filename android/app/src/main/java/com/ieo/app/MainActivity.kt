package com.ieo.app

import android.Manifest
import android.car.Car
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import com.ieo.app.ui.carlink.CarLinkViewModel
import com.ieo.app.ui.login.LoginRoute
import com.ieo.app.ui.main.MainScreen
import com.ieo.app.ui.splash.SplashScreen
import com.ieo.app.ui.theme.IeoTheme

class MainActivity : ComponentActivity() {

    private val sensors by lazy { (application as IeoApplication).container.vehicleSensors }

    // 화면(MainScreen)과 같은 ViewModel. 권한을 받은 뒤 차대번호를 읽게 한다
    private val carLink: CarLinkViewModel by viewModels { CarLinkViewModel.Factory }

    // 자동 도착·떠남(WAIT-001·005)에 속도와 위치, 트럭과 내 차량 잇기(VEH-004)에 차량 정보가 필요하다.
    // 거절해도 버튼과 설정 탭의 기본 차량으로 진행할 수 있다
    private val carPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startCarFeatures()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = (application as IeoApplication).container.session
        setContent {
            IeoTheme {
                // 창 배경은 시작 화면용 주황이라, 그 위 모든 화면에 테마 바탕(흰색)을 깔아 둔다
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    IeoApp(session, onHomeShown = ::askCarPermissions)
                }
            }
        }
    }

    /** 로그인한 뒤에 묻는다. 이미 허락했으면 바로 시작한다 */
    private fun askCarPermissions() {
        val missing = (SENSOR_PERMISSIONS + PLEOS_PERMISSIONS).filterNot(::isGranted)
        if (missing.isEmpty()) startCarFeatures() else carPermissions.launch(missing.toTypedArray())
    }

    private fun startCarFeatures() {
        if (SENSOR_PERMISSIONS.all(::isGranted)) sensors.start()
        // 차량 정보·에너지 권한이 없으면 차대번호·연비를 못 읽고 조용히 넘어간다
        carLink.resolve()
    }

    private fun isGranted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        val SENSOR_PERMISSIONS = listOf(Car.PERMISSION_SPEED, Manifest.permission.ACCESS_FINE_LOCATION)

        /** Pleos 차대번호(CarInfo.getVINInfo)와 누적 연비(Fuel.getCumulativeFuelEconomy) */
        val PLEOS_PERMISSIONS = listOf("pleos.car.permission.CAR_INFO", "pleos.car.permission.CAR_ENERGY")
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
    // 로그인 상태가 바뀌면 화면 상태(ViewModel)를 통째로 비운다. 그러지 않으면 로그아웃 뒤 다른 계정으로 들어와도
    // 앞 계정의 운행·오더가 홈에 남는다 (휴대폰 앱 LoginScope와 같다)
    val scope: LoginScope = androidx.lifecycle.viewmodel.compose.viewModel()
    scope.sync(isLoggedIn)
    androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner provides scope) {
        IeoScreens(isLoggedIn, onHomeShown)
    }
}

/**
 * 로그인 상태가 바뀔 때마다 비우는 ViewModel 보관함. 화면 회전에는 살아남는다.
 * 화면 ViewModel 팩토리가 CreationExtras에서 Application을 꺼내므로 그 값을 넘겨 준다
 */
class LoginScope(application: android.app.Application) :
    androidx.lifecycle.AndroidViewModel(application),
    androidx.lifecycle.ViewModelStoreOwner,
    androidx.lifecycle.HasDefaultViewModelProviderFactory {

    override val defaultViewModelProviderFactory: androidx.lifecycle.ViewModelProvider.Factory =
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)

    override val defaultViewModelCreationExtras: androidx.lifecycle.viewmodel.CreationExtras
        get() = androidx.lifecycle.viewmodel.MutableCreationExtras().apply {
            set(androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, getApplication())
        }

    override var viewModelStore = androidx.lifecycle.ViewModelStore()
        private set
    private var loggedIn: Boolean? = null

    fun sync(now: Boolean) {
        if (loggedIn != null && loggedIn != now) {
            viewModelStore.clear()
            viewModelStore = androidx.lifecycle.ViewModelStore()
        }
        loggedIn = now
    }

    override fun onCleared() = viewModelStore.clear()
}

@Composable
private fun IeoScreens(isLoggedIn: Boolean, onHomeShown: () -> Unit) {
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
            LoginRoute(onBack = { screen = Screen.SPLASH })
        }
        Screen.HOME -> {
            LaunchedEffect(Unit) { onHomeShown() }
            MainScreen()
        }
    }
}
