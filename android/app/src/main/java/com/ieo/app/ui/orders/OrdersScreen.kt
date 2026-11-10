package com.ieo.app.ui.orders

import com.ieo.app.ui.theme.Orange50
import com.ieo.app.ui.theme.Orange600
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.Icons
import android.os.SystemClock
import androidx.compose.foundation.background
import java.time.Instant
import java.time.Duration
import com.ieo.app.ui.theme.Orange500
import com.ieo.app.ui.theme.CardSurface
import com.ieo.app.ui.component.KeywordTag
import com.ieo.app.domain.order.CargoCategory
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
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
import com.ieo.app.ui.component.IeoBottomSheet
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.IeoSecondaryButton
import com.ieo.app.ui.component.IeoTonalButton
import com.ieo.app.ui.component.Keyword
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
            Toolbar(state, actions)
            if (!state.verified) UnverifiedNotice()
            state.pending?.let { PendingStrip(it, onWithdraw = actions::withdraw, onExpired = actions::refresh) }
            OrderList(state, onOrderClick = actions::open)
        }
        IeoBottomSheet(item = state.detail, onDismiss = actions::close) { detail ->
            DetailSheet(detail, reporting = state.reporting, busy = state.isBusy, verified = state.verified, actions = actions)
        }
        IeoBottomSheet(item = state.draft, onDismiss = actions::closeConditions) { draft ->
            ConditionsSheet(draft, state.draftCount, actions)
        }
    }
    // 정차 화면이 보이는 동안만 1분마다 다시 받는다
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(OrdersViewModel.AUTO_REFRESH_MILLIS)
                actions.autoRefresh()
            }
        }
    }
}

/** 새로고침 · 조건 버튼, 걸어 둔 조건, 마지막 업데이트 시간 (휴대폰 오더 찾기와 같은 구성) */
@Composable
private fun Toolbar(state: OrdersUiState, actions: OrdersActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.conditions.summary.forEach { ConditionPill(it) }
            state.updatedAt?.let { UpdatedLabel(it) }
        }
        Spacer(Modifier.width(16.dp))
        ToolButton(onClick = actions::refresh) {
            Icon(Icons.Outlined.Refresh, contentDescription = "새로고침", tint = TextStrong, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.width(12.dp))
        ToolButton(onClick = actions::openConditions) {
            Icon(Icons.Outlined.Tune, contentDescription = null, tint = TextStrong, modifier = Modifier.size(30.dp))
            Text("조건", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextStrong, modifier = Modifier.padding(start = 8.dp))
            if (state.conditions.changedCount > 0) {
                Box(Modifier.padding(start = 10.dp).size(32.dp).background(Orange600, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${state.conditions.changedCount}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ToolButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.heightIn(min = 76.dp).background(CardSurface, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun ConditionPill(text: String) {
    Text(
        text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Orange700,
        modifier = Modifier.background(Orange50, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** "방금 업데이트", "3분 전 업데이트". 30초마다 다시 센다 */
@Composable
private fun UpdatedLabel(updatedAt: Instant) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(updatedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    Text(updatedLabel(updatedAt, Instant.ofEpochMilli(now)), fontSize = 24.sp, color = TextSub, modifier = Modifier.padding(vertical = 8.dp))
}

internal fun updatedLabel(updatedAt: Instant, now: Instant): String {
    val minutes = Duration.between(updatedAt, now).toMinutes()
    return if (minutes < 1) "방금 업데이트" else "${minutes}분 전 업데이트"
}

/** 조건 창: 고른 뒤 아래 버튼을 눌러야 목록이 바뀐다 */
@Composable
private fun ConditionsSheet(draft: OrderConditions, count: Int?, actions: OrdersActions) {
    SheetTitle("조건 설정")
    ConditionGroup("내 주변 거리") {
        OrderRadius.entries.forEach { r -> ConditionChip(r.label, draft.radius == r) { actions.editDraft { it.copy(radius = r) } } }
    }
    ConditionGroup("상차 날짜") {
        OrderDayFilter.entries.forEach { d -> ConditionChip(d.label, draft.day == d) { actions.editDraft { it.copy(day = d) } } }
    }
    ConditionGroup("최소 운임") {
        OrderMinFare.entries.forEach { f -> ConditionChip(f.label, draft.minFare == f) { actions.editDraft { it.copy(minFare = f) } } }
    }
    ConditionGroup("결제 방식") {
        OrderPayment.entries.forEach { p -> ConditionChip(p.label, draft.payment == p) { actions.editDraft { it.copy(payment = p) } } }
    }
    ConditionGroup("제외할 화물") {
        CargoCategory.entries.filter { it.excludable }.forEach { c ->
            ConditionChip(c.label, c in draft.excluded) {
                actions.editDraft { it.copy(excluded = if (c in it.excluded) it.excluded - c else it.excluded + c) }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IeoTonalButton(text = "초기화", onClick = actions::resetDraft, modifier = Modifier.weight(1f))
        IeoPrimaryButton(
            text = when (count) {
                null -> "오더 보기"
                0 -> "맞는 오더가 없어요"
                else -> "오더 ${count}건 보기"
            },
            onClick = actions::applyConditions,
            modifier = Modifier.weight(2f),
        )
    }
}

@Composable
private fun ConditionGroup(title: String, chips: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextSub, modifier = Modifier.padding(bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { chips() }
    }
}

/** 고른 칸은 연한 주황 바탕에 주황 테두리 (휴대폰과 같다) */
@Composable
private fun ConditionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        text,
        fontSize = 24.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) Orange700 else TextSub,
        modifier = Modifier
            .heightIn(min = 76.dp)
            .background(if (selected) Orange50 else CanvasGray, shape)
            .then(if (selected) Modifier.border(2.dp, Orange600, shape) else Modifier)
            .clickable(onClick = onClick)
            .wrapContentHeight()
            .padding(horizontal = 26.dp, vertical = 12.dp),
    )
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
            note = "내 차로 실을 수 있는 것만",
        )
        if (state.rows.isEmpty()) {
            Text(
                if (state.isLoading) "오더를 불러오는 중이에요" else "조건에 맞는 오더가 없어요",
                fontSize = 24.sp,
                color = TextSub,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            )
        }
        state.rows.forEach { row -> OrderRowView(row, onClick = { onOrderClick(row.id) }) }
    }
}

/** 한 건: 왼쪽은 상차→하차를 점과 선으로, 오른쪽은 운임·실수익·거리, 아래는 태그 (휴대폰과 같은 B안) */
@Composable
private fun OrderRowView(row: OrderRow, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Row(Modifier.weight(1f).height(IntrinsicSize.Min)) {
                Column(Modifier.width(22.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(top = 10.dp).size(14.dp).background(Orange500, CircleShape))
                    Box(Modifier.padding(vertical = 4.dp).width(3.dp).weight(1f).background(CanvasGray))
                    Box(Modifier.padding(bottom = 42.dp).size(14.dp).background(TextStrong, CircleShape))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.pickupName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextStrong)
                        if (row.isNew) {
                            Text(
                                "새로 올라옴",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(start = 10.dp).background(Orange600, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(row.pickupTime, fontSize = 24.sp, color = TextSub, modifier = Modifier.padding(bottom = 10.dp))
                    Text(row.dropoffName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextStrong)
                    Text(row.dropoffTime, fontSize = 24.sp, color = TextSub)
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 16.dp)) {
                Text(row.fare, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong)
                row.netProfit?.let { Text(it, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Orange700) }
                row.distanceMeters?.let { Text("상차지까지 ${Keyword.distanceText(it)}", fontSize = 24.sp, color = TextSub, modifier = Modifier.padding(top = 10.dp)) }
                if (row.requested) Text("요청함", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Orange700, modifier = Modifier.padding(top = 6.dp))
            }
        }
        FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOfNotNull(
                Keyword.vehicle(row.vehicle),
                row.cargo?.let(Keyword::cargo),
                Keyword.payment(row.paymentType),
                row.brokerMetrics?.let(Keyword::brokerPayment),
            ).forEach { KeywordTag(it) }
        }
    }
}

/** 서류 승인 전: 왜 주소·주선사가 가려졌고 어디서 풀 수 있는지 (휴대폰 홈 서류 카드와 같은 역할) */
@Composable
private fun UnverifiedNotice() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(Orange50, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = Orange600, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text("서류 승인 전이라 주소와 주선사를 가렸어요", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Orange700)
            Text("휴대폰 앱 내 정보 > 서류에서 4가지를 올리면 배차를 요청할 수 있어요", fontSize = 24.sp, color = TextSub)
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
        IeoTonalButton(text = if (detail.reported) "신고함" else "신고", onClick = actions::startReport, modifier = Modifier.weight(1f), enabled = !detail.reported)
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
    override fun openConditions() = Unit
    override fun editDraft(change: (OrderConditions) -> OrderConditions) = Unit
    override fun resetDraft() = Unit
    override fun closeConditions() = Unit
    override fun applyConditions() = Unit
    override fun autoRefresh() = Unit
    override fun open(orderId: Long) = Unit
    override fun close() = Unit
    override fun request() = Unit
    override fun withdraw() = Unit
    override fun startReport() = Unit
    override fun report(type: ReportType) = Unit
    override fun refresh() = Unit
}
