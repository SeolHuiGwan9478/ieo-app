package com.ieo.app.ui.main

import com.ieo.app.ui.home.StartGuidanceSheet
import androidx.compose.ui.draw.clip
import com.ieo.app.ui.theme.White
import com.ieo.app.ui.theme.TextSub
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.CanvasGray
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.time.LocalTime
import com.ieo.app.ui.home.toSummary
import com.ieo.app.ui.home.AssignedTrip
import com.ieo.app.data.navi.NaviProgress
import com.ieo.app.data.navi.NaviGuide
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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

private enum class Destination(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Outlined.Home),
    ORDERS("오더", Icons.Outlined.LocalShipping),
    LEDGER("장부", Icons.AutoMirrored.Outlined.ReceiptLong),
    SETTINGS("설정", Icons.Outlined.Settings),
}

/** 로그인 후 화면: 왼쪽 세로 메뉴 + 오른쪽 본문 */
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    // TODO: 운행 도메인이 생기면 실제 운행 상태로 바꾼다. 지금은 샘플 데이터를 보여 준다
    var demo by rememberSaveable { mutableStateOf(DemoState.IDLE) }

    // 차량 내비 연결. 배차에서 길 안내를 시작하면 내비가 알려 주는 진행 상황을 홈에 보여 준다
    val naviGuide = remember { NaviGuide(context) }
    DisposableEffect(naviGuide) {
        naviGuide.connect()
        onDispose { naviGuide.release() }
    }
    val guidance by naviGuide.progress.collectAsState()
    var guidingTrip by remember { mutableStateOf<AssignedTrip?>(null) }
    // 내비에서 안내를 끄면 홈으로 돌아온다
    LaunchedEffect(guidance) {
        if (guidance == null) guidingTrip = null
    }
    LaunchedEffect(guidingTrip) {
        if (guidingTrip == null) return@LaunchedEffect
        while (true) {
            delay(10_000)
            naviGuide.refresh()
        }
    }

    val homeState: HomeState = guidingTrip?.let { guidingHomeState(it, guidance) } ?: when (demo) {
        DemoState.IDLE -> SampleHomeData.idle()
        DemoState.WAITING -> SampleHomeData.waiting()
        DemoState.DRIVING -> SampleHomeData.driving()
    }

    var confirmingTrip by remember { mutableStateOf<AssignedTrip?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavRail(selected = destination, onSelect = { destination = it })
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (destination) {
                    Destination.HOME -> HomeScreen(
                        state = homeState,
                        onTripClick = { confirmingTrip = it },
                    )
                    else -> ComingSoon(destination.label)
                }
                if (BuildConfig.DEBUG && destination == Destination.HOME) {
                    DemoStateSwitcher(
                        selected = demo,
                        onSelect = {
                            if (guidingTrip != null) naviGuide.stopGuidance()
                            guidingTrip = null
                            demo = it
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    )
                }
            }
        }
        StartGuidanceSheet(
            trip = confirmingTrip,
            onStart = { trip ->
                confirmingTrip = null
                if (naviGuide.startGuidance(trip.pickup)) {
                    guidingTrip = trip
                } else {
                    Toast.makeText(context, "차량 내비에 연결하지 못했습니다", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { confirmingTrip = null },
        )
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
    val color = if (selected) TextStrong else TextSub
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) CanvasGray else White)
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

@Composable
private fun ComingSoon(title: String) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Text(text = "$title 화면은 준비 중입니다", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private enum class DemoState(val label: String) { IDLE("운행 전"), WAITING("대기 중"), DRIVING("주행 중") }

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

/** 내비 진행 상황으로 주행 중 화면 상태를 만든다. 아직 경로 정보가 없으면 값을 비워 둔다 */
private fun guidingHomeState(trip: AssignedTrip, progress: NaviProgress?): HomeState.Driving {
    val remainSeconds = progress?.remainSeconds
    return HomeState.Driving(
        trip = trip.toSummary(),
        destinationName = trip.pickup.name,
        isPickup = true,
        etaText = remainSeconds?.let { LocalTime.now().plusSeconds(it.toLong()).format(DateTimeFormatter.ofPattern("HH:mm")) },
        progress = progress?.fraction,
        remainingKm = progress?.remainMeters?.let { (it + 500) / 1000 },
        remainingMinutes = remainSeconds?.let { (it + 59) / 60 },
        etaShared = false,
        recommendation = null,
    )
}
