package com.ieo.app.ui.orders

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.domain.order.ReportType
import com.ieo.app.ui.component.FilterChip
import com.ieo.app.ui.component.IeoBottomSheet
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.IeoSecondaryButton
import com.ieo.app.ui.component.IeoTonalButton
import com.ieo.app.ui.component.Keyword
import com.ieo.app.ui.component.ListRow
import com.ieo.app.ui.component.SectionTitle
import com.ieo.app.ui.component.SheetLine
import com.ieo.app.ui.component.SheetTitle
import com.ieo.app.ui.component.WhiteSection
import com.ieo.app.ui.theme.CanvasGray
import com.ieo.app.ui.theme.IeoStatus
import com.ieo.app.ui.theme.IeoTheme
import com.ieo.app.ui.theme.Orange700
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub
import com.ieo.app.ui.theme.WaitingPale
import kotlinx.coroutines.delay

private val ScreenPadding = 28.dp

/**
 * 오더 탭. 정차 중에만 목록을 보여 준다 (DSP-012). 오더를 누르면 상세가 아래에서 올라온다.
 * 상태는 [OrdersViewModel]이 정하고, 이 화면은 그리기만 한다.
 */
@Composable
fun OrdersScreen(state: OrdersUiState, actions: OrdersActions, isDriving: Boolean) {
    Box(modifier = Modifier.fillMaxSize().background(CanvasGray)) {
        if (isDriving) {
            DrivingNotice()
            return@Box
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OrderDayFilter.entries.forEach { filter ->
                    FilterChip(filter.label, selected = state.filter == filter, onClick = { actions.selectFilter(filter) })
                }
            }
            state.pending?.let { PendingStrip(it, onWithdraw = actions::withdraw, onExpired = actions::refresh) }
            OrderList(state, onOrderClick = actions::open)
        }
        IeoBottomSheet(item = state.detail, onDismiss = actions::close) { detail ->
            DetailSheet(detail, reporting = state.reporting, busy = state.isBusy, verified = state.verified, actions = actions)
        }
    }
}

@Composable
private fun DrivingNotice() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(ScreenPadding),
    ) {
        Text("주행 중에는 오더를 보여 드리지 않아요", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong)
        Spacer(Modifier.height(12.dp))
        Text("정차하면 다시 보여 드릴게요", fontSize = 24.sp, color = TextSub, textAlign = TextAlign.Center)
    }
}

/** 확정을 기다리는 내 요청: 구간, 만료까지 남은 시간, 철회 */
@Composable
private fun PendingStrip(pending: PendingRequest, onWithdraw: () -> Unit, onExpired: () -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(pending.expiresAt) {
        while (now < pending.expiresAt) {
            delay(1000)
            now = SystemClock.elapsedRealtime()
        }
        // 만료되면 서버 상태를 다시 받아 띠를 내린다
        onExpired()
    }
    val secondsLeft = ((pending.expiresAt - now) / 1000).coerceAtLeast(0)
    val waiting = IeoStatus.colors.waiting

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(WaitingPale, RoundedCornerShape(20.dp)).padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("요청 중 · ${pending.route}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextStrong)
            Text(
                "주선사 확정 기다리는 중 · %d:%02d 남음".format(secondsLeft / 60, secondsLeft % 60),
                fontSize = 24.sp,
                color = waiting,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            "철회",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = waiting,
            modifier = Modifier.clickable(onClick = onWithdraw).padding(12.dp),
        )
    }
}

@Composable
private fun OrderList(state: OrdersUiState, onOrderClick: (Long) -> Unit) {
    WhiteSection {
        SectionTitle(
            title = if (state.nearby) "근처 오더" else "모집 중인 오더",
            count = state.rows.size,
            note = if (state.nearby) "지금 위치에서 50km 안, 내 차로 실을 수 있는 것만" else "내 차로 실을 수 있는 것만",
        )
        if (state.rows.isEmpty()) {
            Text(
                if (state.isLoading) "오더를 불러오는 중이에요" else "조건에 맞는 오더가 없어요",
                fontSize = 24.sp,
                color = TextSub,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            )
        }
        state.rows.forEach { row ->
            ListRow(
                time = row.time,
                day = row.day,
                title = row.route,
                tags = listOfNotNull(
                    Keyword.vehicle(row.vehicle),
                    row.cargo?.let(Keyword::cargo),
                    row.distanceMeters?.let(Keyword::distance),
                    Keyword.payment(row.paymentType),
                    row.brokerMetrics?.let(Keyword::brokerPayment),
                ),
                subtitle = if (row.requested) "요청함" else row.brokerName,
                subtitleColor = if (row.requested) Orange700 else TextSub,
                amount = row.fare,
                amountNote = row.netProfit,
                onClick = { onOrderClick(row.id) },
            )
        }
    }
}

@Composable
private fun DetailSheet(detail: OrderDetail, reporting: Boolean, busy: Boolean, actions: OrdersActions, verified: Boolean = true) {
    SheetTitle(detail.route)
    Spacer(Modifier.height(24.dp))
    detail.lines.forEach { (label, value) -> SheetLine(label, value) }
    Spacer(Modifier.height(32.dp))
    if (reporting) {
        Text("어떤 문제가 있나요?", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextStrong)
        Spacer(Modifier.height(16.dp))
        ReportType.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                pair.forEach { type ->
                    IeoTonalButton(text = type.label, onClick = { actions.report(type) }, modifier = Modifier.weight(1f))
                }
            }
        }
        IeoSecondaryButton(text = "취소", onClick = actions::close, modifier = Modifier.fillMaxWidth())
        return
    }
    if (!verified && !detail.requested) {
        Text("서류 4가지가 승인되면 배차를 요청할 수 있어요.\n휴대폰 앱 내 정보 > 서류에서 올려 주세요", fontSize = 24.sp, lineHeight = 34.sp, color = TextSub)
        Spacer(Modifier.height(20.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IeoTonalButton(text = "신고", onClick = actions::startReport, modifier = Modifier.weight(1f))
        IeoTonalButton(text = "닫기", onClick = actions::close, modifier = Modifier.weight(1f))
        if (detail.requested) {
            IeoSecondaryButton(text = "요청 철회", onClick = actions::withdraw, modifier = Modifier.weight(2f))
        } else {
            IeoPrimaryButton(text = if (busy) "요청 중…" else "배차 요청", onClick = actions::request, modifier = Modifier.weight(2f), enabled = verified)
        }
    }
}

@Preview(name = "내비 옆 반 화면", widthDp = 728, heightDp = 1200)
@Composable
private fun OrdersScreenPreview() {
    IeoTheme {
        OrdersScreen(
            state = OrdersUiState(
                rows = listOf(
                    OrderRow(1, "17:00", "오늘", "평택 청북 → 대구 북구", "5톤 카고", "인수증", "○○물류", "47만 원", requested = true),
                    OrderRow(2, "17:30", "오늘", "오산 가장 → 광주 광산", "5톤 윙바디", "선불", "△△운송", "41만 원", requested = false),
                ),
                pending = PendingRequest("평택 청북 → 대구 북구", SystemClock.elapsedRealtime() + 492_000),
            ),
            actions = PreviewOrdersActions,
            isDriving = false,
        )
    }
}

private object PreviewOrdersActions : OrdersActions {
    override fun selectFilter(filter: OrderDayFilter) = Unit
    override fun open(orderId: Long) = Unit
    override fun close() = Unit
    override fun request() = Unit
    override fun withdraw() = Unit
    override fun startReport() = Unit
    override fun report(type: ReportType) = Unit
    override fun refresh() = Unit
}
