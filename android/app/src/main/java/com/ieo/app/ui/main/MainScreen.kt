package com.ieo.app.ui.main

import com.ieo.app.ui.component.IeoBannerHost
import com.ieo.app.ui.component.ListeningPill
import com.ieo.app.ui.component.ParkedOnly
import com.ieo.app.ui.component.MessageBanners
import com.ieo.app.ui.component.ShowMessage
import com.ieo.app.ui.home.PickModal
import com.ieo.app.ui.home.StopConfirmModal
import com.ieo.app.ui.home.FinishTripSheet
import com.ieo.app.ui.home.StartGuidanceSheet
import com.ieo.app.ui.home.WaitProofSheet
import androidx.compose.ui.draw.clip
import com.ieo.app.ui.theme.CardSurface
import com.ieo.app.ui.theme.TextSub
import com.ieo.app.ui.theme.Orange50
import com.ieo.app.ui.theme.Orange700
import com.ieo.app.ui.home.AssignedTrip
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
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
import com.ieo.app.ui.carlink.CarLinkSheet
import com.ieo.app.ui.carlink.CarLinkViewModel
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
    carLinkViewModel: CarLinkViewModel = viewModel(factory = CarLinkViewModel.Factory),
) {
    // 버튼을 누른 결과 같은 짧은 말. 서버 알림보다 먼저 보여 준다
    val messages = remember { MessageBanners() }
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    // SHOW_DEMO_SWITCHER로 빌드했을 때만: 서버 없이 대기·주행 화면을 확인하는 샘플
    var demo by rememberSaveable { mutableStateOf(DemoState.LIVE) }

    val liveState by homeViewModel.state.collectAsStateWithLifecycle()
    val status by homeViewModel.status.collectAsStateWithLifecycle()
    ShowMessage(status.message, messages::show, homeViewModel::messageShown)

    val homeState: HomeState = when (demo) {
        DemoState.LIVE -> liveState
        DemoState.WAITING -> SampleHomeData.waiting()
        DemoState.DRIVING -> SampleHomeData.driving()
    }

    // 이 트럭과 내 차량 잇기 (VEH-004). 차대번호는 MainActivity가 권한을 받은 뒤 읽기 시작한다
    val carLinkState by carLinkViewModel.state.collectAsStateWithLifecycle()
    ShowMessage(carLinkState.message, messages::show, carLinkViewModel::messageShown)

    // 차가 움직이면 오더·장부·설정은 막는다 (UX-003, UX-004, DSP-012)
    val moving by homeViewModel.moving.collectAsStateWithLifecycle()

    var confirmingTrip by remember { mutableStateOf<AssignedTrip?>(null) }

    // 다른 앱에서 받은 배차 등록 창. 손으로 고르고 적는 창이라 차가 움직이면 닫는다 (UX-004)
    var registering by remember { mutableStateOf(false) }
    LaunchedEffect(moving) { if (moving) registering = false }
    val registerState by registerViewModel.state.collectAsStateWithLifecycle()
    ShowMessage(registerState.message, messages::show, registerViewModel::messageShown)
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
                        onArrive = homeViewModel::arrive,
                        onResumeGuidance = homeViewModel::resumeGuidance,
                        onPickClick = { homeViewModel.openPick(it.orderId) },
                        onAddRestStopWaypoint = homeViewModel::addRestStopWaypoint,
                    )
                    Destination.ORDERS -> ParkedOnly(moving, "주행 중에는 오더를 보여 드리지 않아요") { OrdersRoute(onMessage = messages::show) }
                    Destination.LEDGER -> ParkedOnly(moving, "주행 중에는 장부를 보여 드리지 않아요") { LedgerRoute(onMessage = messages::show) }
                    Destination.SETTINGS -> ParkedOnly(moving, "주행 중에는 설정을 열지 않아요") { SettingsRoute(onMessage = messages::show) }
                }
                if (BuildConfig.SHOW_DEMO_SWITCHER && destination == Destination.HOME) {
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
        WaitProofSheet(link = status.proof, onDismiss = homeViewModel::proofShown, onSend = homeViewModel::sendProofToBroker, busy = status.isBusy)
        RegisterTripSheet(visible = registering, state = registerState, actions = registerViewModel, onDismiss = { registering = false })
        CarLinkSheet(state = carLinkState, actions = carLinkViewModel)
        // 다른 창을 쓰는 동안에는 끼어들지 않는다. QR 창은 ViewModel이 음성까지 함께 막는다
        val sheetOpen = confirmingTrip != null || registering
        PickModal(
            pick = status.pick.takeUnless { sheetOpen || demo != DemoState.LIVE },
            busy = status.isBusy,
            onRequest = homeViewModel::requestPick,
            onNext = homeViewModel::nextPick,
        )
        FinishTripSheet(
            visible = status.finishing,
            busy = status.isBusy,
            onFinish = homeViewModel::finishTrip,
            onDismiss = homeViewModel::cancelFinish,
        )
        StopConfirmModal(
            question = status.stopQuestion.takeIf { demo == DemoState.LIVE },
            parked = homeViewModel.parked.collectAsStateWithLifecycle().value,
            busy = status.isBusy,
            onConfirm = homeViewModel::confirmStopQuestion,
            onDecline = homeViewModel::declineStopQuestion,
        )
        ListeningPill(
            visible = status.listening,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        )
        IeoBannerHost(
            banner = messages.current ?: status.banners.firstOrNull(),
            onDone = { id -> if (id < 0) messages.done(id) else homeViewModel.bannerDone(id) },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun NavRail(selected: Destination, onSelect: (Destination) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.width(112.dp).fillMaxHeight().background(CardSurface).padding(vertical = 32.dp),
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
            .background(if (selected) Orange50 else CardSurface)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(top = 14.dp),
    ) {
        Icon(imageVector = item.icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.label,
            color = color,
            fontSize = 24.sp,
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
