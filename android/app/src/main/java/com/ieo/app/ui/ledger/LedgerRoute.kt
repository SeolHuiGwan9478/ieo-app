package com.ieo.app.ui.ledger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ieo.app.ui.component.ShowMessage
import com.ieo.app.ui.home.WaitProofSheet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

/** 장부 탭과 ViewModel을 잇는다. 탭을 열 때마다 새로 받는다 (운행이 끝나면 장부가 늘어나므로) */
@Composable
fun LedgerRoute(onMessage: (String) -> Unit, viewModel: LedgerViewModel = viewModel(factory = LedgerViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    ShowMessage(state.message, onMessage, viewModel::messageShown)
    Box(modifier = Modifier.fillMaxSize()) {
        LedgerScreen(state = state, actions = viewModel)
        WaitProofSheet(link = state.proof, onDismiss = viewModel::proofShown, onSend = viewModel::sendWaitProof, busy = state.isBusy)
    }
}
