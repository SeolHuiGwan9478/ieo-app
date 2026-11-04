package com.ieo.app.ui.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** 설정 탭과 ViewModel을 잇는다. 탭을 열 때마다 새로 받는다 (휴대폰 앱에서 차량을 바꿨을 수 있으므로) */
@Composable
fun SettingsRoute(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }
    SettingsScreen(state = state, actions = viewModel)
}
