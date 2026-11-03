package com.ieo.app.ui.orders

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** 오더 탭과 ViewModel을 잇는다. 탭을 열 때마다 목록을 새로 받는다 */
@Composable
fun OrdersRoute(isDriving: Boolean, viewModel: OrdersViewModel = viewModel(factory = OrdersViewModel.Factory)) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }
    OrdersScreen(state = state, actions = viewModel, isDriving = isDriving)
}
