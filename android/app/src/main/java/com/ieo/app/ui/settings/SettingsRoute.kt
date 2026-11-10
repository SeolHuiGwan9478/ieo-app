package com.ieo.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ieo.app.ui.component.ShowMessage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ieo.app.ui.links.DriverLinksSection
import com.ieo.app.ui.links.DriverLinksViewModel

/** 설정 탭과 ViewModel을 잇는다. 탭을 열 때마다 새로 받는다 (휴대폰 앱에서 차량을 바꿨을 수 있으므로) */
@Composable
fun SettingsRoute(
    onMessage: (String) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    // 홈의 요청 창과 같은 ViewModel (Activity 범위). 메시지는 MainScreen이 띄운다
    linksViewModel: DriverLinksViewModel = viewModel(factory = DriverLinksViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val linksState by linksViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.refresh()
        linksViewModel.refresh()
    }
    ShowMessage(state.message, onMessage, viewModel::messageShown)
    SettingsScreen(
        state = state,
        actions = viewModel,
        linkCount = linksState.links.size,
        linksSection = { DriverLinksSection(linksState, linksViewModel) },
    )
}
