package com.ieo.app.ui.orders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ieo.app.ui.component.ShowMessage
import androidx.lifecycle.viewmodel.compose.viewModel

/** 오더 탭과 ViewModel을 잇는다. 탭을 열 때마다 목록을 새로 받는다 */
@Composable
fun OrdersRoute(onMessage: (String) -> Unit, viewModel: OrdersViewModel = viewModel(factory = OrdersViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    ShowMessage(state.message, onMessage, viewModel::messageShown)
    OrdersScreen(state = state, actions = viewModel)
}
