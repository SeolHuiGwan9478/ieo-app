package com.ieo.app.ui.ledger

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** 장부 탭과 ViewModel을 잇는다. 탭을 열 때마다 새로 받는다 (운행이 끝나면 장부가 늘어나므로) */
@Composable
fun LedgerRoute(viewModel: LedgerViewModel = viewModel(factory = LedgerViewModel.Factory)) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }
    LedgerScreen(state = state, actions = viewModel)
}
