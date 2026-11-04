package com.ieo.app.ui.main

import com.ieo.app.ui.home.DeliveryProofSheet
import com.ieo.app.ui.home.StartGuidanceSheet
import com.ieo.app.ui.home.WaitProofSheet
import androidx.compose.ui.draw.clip
import com.ieo.app.ui.theme.White
import com.ieo.app.ui.theme.TextSub
import com.ieo.app.ui.theme.Orange50
import com.ieo.app.ui.theme.Orange700
import com.ieo.app.ui.home.AssignedTrip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.BuildConfig
import com.ieo.app.ui.component.IeoSymbol
import com.ieo.app.ui.home.HomeScreen
import com.ieo.app.ui.home.HomeState
import com.ieo.app.ui.home.SampleHomeData
import com.ieo.app.ui.home.HomeViewModel
import com.ieo.app.ui.ledger.LedgerRoute
import com.ieo.app.ui.orders.OrdersRoute
import com.ieo.app.ui.register.RegisterTripSheet
import com.ieo.app.ui.register.RegisterTripViewModel
import com.ieo.app.ui.settings.SettingsRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class Destination(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Outlined.Home),
    ORDERS("오더", Icons.Outlined.LocalShipping),
    LEDGER("장부", Icons.AutoMirrored.Outlined.ReceiptLong),
    SETTINGS("설정", Icons.Outlined.Settings),
}

/** 로그인 후 화면: 왼쪽 세로 메뉴 + 오른쪽 본문 */
@Composable
fun MainScreen(
    homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    registerViewModel: RegisterTripViewModel = viewModel(factory = RegisterTripViewModel.Factory),
) {
    val context = LocalContext.current
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    // 디버그 빌드에서만: 서버 없이 대기·주행 화면을 확인하는 샘플
    var demo by rememberSaveable { mutableStateOf(DemoState.LIVE) }

    val liveState by homeViewModel.state.collectAsStateWithLifecycle()
    val status by homeViewModel.status.collectAsStateWithLifecycle()
    LaunchedEffect(status.message) {
        status.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            homeViewModel.messageShown()
        }
    }

    val homeState: HomeState = when (demo) {
        DemoState.LIVE -> liveState
        DemoState.WAITING -> SampleHomeData.waiting()
        DemoState.DRIVING -> SampleHomeData.driving()
    }

    var confirmingTrip by remember { mutableStateOf<AssignedTrip?>(null) }

    // 다른 앱에서 받은 배차 등록 창
    var registering by remember { mutableStateOf(false) }
    val registerState by registerViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(registerState.message) {
        registerState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            registerViewModel.messageShown()
        }
    }
    LaunchedEffect(registerState.done) {
        if (registerState.done) {
            registering = false
            homeViewModel.refresh()
            registerViewModel.doneHandled()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavRail(selected = destination, onSelect = { destination = it })
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (destination) {
                    Destination.HOME -> HomeScreen(
                        state = homeState,
                        onTripClick = { confirmingTrip = it },
                        onRegisterTrip = {
                            registering = true
                            registerViewModel.start()
                        },
                        onStartWork = homeViewModel::startWork,
                        onDepart = homeViewModel::depart,
                        onSendWaitProof = homeViewModel::sendWaitProof,
                        onRequestDeliveryProof = homeViewModel::requestDeliveryProof,
                        onArrive = homeViewModel::arrive,
                        onResumeGuidance = homeViewModel::resumeGuidance,
                    )
                    // 주행 중에는 오더 목록을 보여 주지 않는다 (DSP-012)
                    Destination.ORDERS -> OrdersRoute(isDriving = liveState is HomeState.Driving)
                    Destination.LEDGER -> LedgerRoute()
                    Destination.SETTINGS -> SettingsRoute()
                }
                if (BuildConfig.DEBUG && destination == Destination.HOME) {
                    DemoStateSwitcher(
                        selected = demo,
                        onSelect = { demo = it },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    )
                }
            }
        }
        StartGuidanceSheet(
            trip = confirmingTrip,
            onStart = { trip ->
                confirmingTrip = null
                homeViewModel.startTrip(trip.id)
            },
            onDismiss = { confirmingTrip = null },
        )
        WaitProofSheet(link = status.proof, onDismiss = homeViewModel::proofShown)
        RegisterTripSheet(visible = registering, state = registerState, actions = registerViewModel, onDismiss = { registering = false })
        DeliveryProofSheet(proof = status.deliveryProof, onDismiss = homeViewModel::closeDeliveryProof)
    }
}

@Composable
private fun NavRail(selected: Destination, onSelect: (Destination) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.width(112.dp).fillMaxHeight().background(White).padding(vertical = 32.dp),
    ) {
        IeoSymbol(size = 48.dp)
        Spacer(Modifier.height(24.dp))
        Destination.entries.forEach { item ->
            NavRailItem(item = item, selected = item == selected, onClick = { onSelect(item) })
        }
    }
}

@Composable
private fun NavRailItem(item: Destination, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Orange700 else TextSub
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Orange50 else White)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(top = 14.dp),
    ) {
        Icon(imageVector = item.icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.label,
            color = color,
            fontSize = 18.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

private enum class DemoState(val label: String) { LIVE("서버"), WAITING("대기 샘플"), DRIVING("주행 샘플") }

/** 디버그 빌드 전용: 홈 화면 상태를 바꿔 가며 확인한다 */
@Composable
private fun DemoStateSwitcher(selected: DemoState, onSelect: (DemoState) -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(4.dp),
    ) {
        Text("데모", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp))
        DemoState.entries.forEach { state ->
            Text(
                text = state.label,
                fontSize = 16.sp,
                fontWeight = if (state == selected) FontWeight.Bold else FontWeight.Normal,
                color = if (state == selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .background(
                        if (state == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(state) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
