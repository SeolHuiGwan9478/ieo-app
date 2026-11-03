package com.ieo.app.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 로그인 화면과 ViewModel을 잇는다. [LoginScreen]은 상태만 그리고, 상태를 만드는 일은 ViewModel이 한다.
 * 로그인에 성공하면 세션이 바뀌고, 다음 화면으로 넘기는 일은 앱(MainActivity)이 한다.
 */
@Composable
fun LoginRoute(
    phoneViewModel: PhoneLoginViewModel = viewModel(factory = PhoneLoginViewModel.Factory),
    qrViewModel: QrLoginViewModel = viewModel(factory = QrLoginViewModel.Factory),
) {
    val phone by phoneViewModel.state.collectAsStateWithLifecycle()
    val qr by qrViewModel.state.collectAsStateWithLifecycle()
    LoginScreen(phone = phone, phoneActions = phoneViewModel, qr = qr, qrActions = qrViewModel)
}
