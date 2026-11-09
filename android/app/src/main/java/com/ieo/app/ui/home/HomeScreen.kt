package com.ieo.app.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.ui.common.formatDuration
import com.ieo.app.ui.common.formatMinutesKorean
import com.ieo.app.ui.common.formatFare
import com.ieo.app.ui.common.formatWon
import com.ieo.app.domain.site.HeightWarning
import com.ieo.app.domain.wait.WaitPolicy
import com.ieo.app.ui.component.Chevron
import com.ieo.app.ui.component.DetailLine
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.ListRow
import com.ieo.app.ui.component.SectionTitle
import com.ieo.app.ui.component.StatusTag
import com.ieo.app.ui.component.TagLine
import com.ieo.app.ui.component.Tnum
import com.ieo.app.ui.component.WhiteSection
import com.ieo.app.ui.component.Keyword
import com.ieo.app.ui.component.KeywordTag
import com.ieo.app.ui.component.IeoTonalButton
import com.ieo.app.ui.theme.CanvasGray
import com.ieo.app.ui.theme.DrivingPale
import com.ieo.app.ui.theme.IeoStatus
import com.ieo.app.ui.theme.IeoTheme
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub
import com.ieo.app.ui.theme.WaitingPale
import com.ieo.app.ui.theme.WarningPale
import com.ieo.app.ui.theme.White
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val ScreenPadding = 28.dp
private val WideLayoutMinWidth = 1000.dp

/**
 * 홈 화면. 운행 상태마다 주인공이 하나다.
 * 운행 전은 내 배차와 근처 오더, 대기 중은 대기 타이머, 주행 중은 도착 예정 시각.
 *
 * 연회색 바탕 위에 흰 묶음을 놓고, 핵심 숫자만 크고 굵게 쓴다. 구분선은 쓰지 않는다.
 * 시안: "Ieo 홈 목록 시안"의 A안 (2026-10-22)
 */
@Composable
fun HomeScreen(
    state: HomeState,
    onTripClick: (AssignedTrip) -> Unit = {},
    onOrderClick: (OrderCard) -> Unit = {},
    onUnpaidClick: () -> Unit = {},
    onRegisterTrip: () -> Unit = {},
    onStartWork: () -> Unit = {},
    onDepart: () -> Unit = {},
    onSendWaitProof: () -> Unit = {},
    onRequestDeliveryProof: () -> Unit = {},
    onArrive: () -> Unit = {},
    onResumeGuidance: () -> Unit = {},
    onNextOrderClick: (NextOrderCard) -> Unit = {},
) {
    // 내비 옆 반 화면(좁은 창)이 기본 사용 형태다. 앱 영역 전체를 쓸 때만 두 칸으로 펼친다
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(CanvasGray)) {
        val compact = maxWidth < WideLayoutMinWidth
        when (state) {
            is HomeState.Idle -> IdleHome(state, compact, onTripClick, onOrderClick, onUnpaidClick, onRegisterTrip)
            is HomeState.Waiting -> WaitingHome(state, compact, WaitActions(onStartWork, onDepart, onSendWaitProof, onRequestDeliveryProof, onNextOrderClick))
            is HomeState.Driving -> DrivingHome(state, onArrive, onResumeGuidance)
        }
    }
}

// ───────────────────────── 운행 전 ─────────────────────────

@Composable
private fun IdleHome(
    state: HomeState.Idle,
    compact: Boolean,
    onTripClick: (AssignedTrip) -> Unit,
    onOrderClick: (OrderCard) -> Unit,
    onUnpaidClick: () -> Unit,
    onRegisterTrip: () -> Unit,
) {
    if (compact) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        ) {
            TodayHeadline(state)
            state.unpaid?.let { UnpaidRow(it, onUnpaidClick) }
            MyTrips(state.myTrips, onTripClick = onTripClick, onRegisterTrip = onRegisterTrip)
            NearbyOrders(state, onOrderClick)
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize().padding(ScreenPadding),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            ) {
                TodayHeadline(state)
                state.unpaid?.let { UnpaidRow(it, onUnpaidClick) }
                MyTrips(state.myTrips, onTripClick = onTripClick, onRegisterTrip = onRegisterTrip)
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                NearbyOrders(state, onOrderClick)
            }
        }
    }
}

/** 맨 위 한 문장: 오늘 얼마 벌었는지 */
@Composable
private fun TodayHeadline(state: HomeState.Idle) {
    Column(modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Text("${todayText()} · 운행 ${state.today.tripCount}건", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = TextSub)
        Spacer(Modifier.height(6.dp))
        Text(
            buildAnnotatedString {
                append("오늘 ")
                withStyle(SpanStyle(color = IeoStatus.colors.done)) { append(formatWon(state.today.netProfit)) }
                append(" 벌었어요")
            },
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextStrong,
            style = Tnum,
        )
    }
}

@Composable
private fun UnpaidRow(unpaid: UnpaidAlert, onClick: () -> Unit) {
    val warning = IeoStatus.colors.warning
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp).background(WarningPale, CircleShape)) {
            Text("!", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = warning)
        }
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("${unpaid.brokerName} 미수금 ${formatWon(unpaid.amount)}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextStrong)
            Text("지급일이 ${unpaid.overdueDays}일 지났어요", fontSize = 24.sp, color = warning)
        }
        Chevron()
    }
}

@Composable
private fun MyTrips(trips: List<AssignedTrip>, onTripClick: (AssignedTrip) -> Unit, onRegisterTrip: () -> Unit) {
    WhiteSection {
        SectionTitle(title = "내 배차", count = trips.size)
        if (trips.isEmpty()) {
            Text(
                "잡아 둔 배차가 없어요",
                fontSize = 24.sp,
                color = TextSub,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp),
            )
        }
        trips.forEach { trip ->
            ListRow(
                time = trip.pickupTime,
                day = trip.pickupDay,
                title = "${trip.pickup.name} → ${trip.dropoff.name}",
                tags = listOf(Keyword.vehicle(trip.vehicle), Keyword.payment(trip.paymentType)),
                subtitle = trip.brokerName,
                amount = formatFare(trip.fare),
                onClick = { onTripClick(trip) },
            )
        }
        Spacer(Modifier.height(10.dp))
        IeoTonalButton(
            text = "다른 앱에서 받은 배차 등록",
            onClick = onRegisterTrip,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun NearbyOrders(state: HomeState.Idle, onOrderClick: (OrderCard) -> Unit) {
    WhiteSection {
        SectionTitle(
            title = if (state.nearby) "근처 오더" else "모집 중인 오더",
            count = state.orders.size,
            note = if (state.nearby) "지금 위치에서 50km 안, 내 차로 실을 수 있는 것만" else "내 차로 실을 수 있는 것만",
        )
        if (state.orders.isEmpty()) {
            Text(
                if (state.nearby) "근처에 모집 중인 오더가 없어요" else "지금 모집 중인 오더가 없어요",
                fontSize = 24.sp,
                color = TextSub,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            )
        }
        state.orders.forEach { order ->
            ListRow(
                time = order.pickupTime,
                day = order.pickupDay,
                title = "${order.pickup} → ${order.dropoff}",
                tags = listOfNotNull(
                    Keyword.vehicle(order.vehicle),
                    order.cargo?.let(Keyword::cargo),
                    order.distanceMeters?.let(Keyword::distance),
                    order.brokerMetrics?.let(Keyword::brokerPayment),
                ),
                amount = formatWon(order.fare),
                amountNote = order.netProfit?.let { "실수익 ${formatWon(it)}" },
                onClick = { onOrderClick(order) },
            )
        }
    }
}

// ───────────────────────── 대기 중 ─────────────────────────

/** 대기 화면의 버튼들 */
private class WaitActions(
    val onStartWork: () -> Unit,
    val onDepart: () -> Unit,
    val onSendWaitProof: () -> Unit,
    val onRequestDeliveryProof: () -> Unit,
    val onNextOrderClick: (NextOrderCard) -> Unit,
)

@Composable
private fun WaitingHome(state: HomeState.Waiting, compact: Boolean, actions: WaitActions) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.waitStartedAt) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    // 작업을 시작하면 대기는 끝나고, 큰 숫자는 작업 시간을 센다
    val timerSeconds = (now - (state.workStartedAt ?: state.waitStartedAt)) / 1000
    val chargeableMinutes = ((state.workStartedAt ?: now) - state.chargeableFrom).coerceAtLeast(0) / 60_000

    if (compact) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        ) {
            WaitTimer(state, timerSeconds, chargeableMinutes, actions, compact = true)
            WaitDetails(state, actions.onNextOrderClick)
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(ScreenPadding),
        ) {
            Box(modifier = Modifier.weight(0.55f)) { WaitTimer(state, timerSeconds, chargeableMinutes, actions, compact = false) }
            Box(modifier = Modifier.weight(0.45f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                WaitDetails(state, actions.onNextOrderClick)
            }
        }
    }
}

@Composable
private fun WaitTimer(state: HomeState.Waiting, timerSeconds: Long, chargeableMinutes: Long, actions: WaitActions, compact: Boolean) {
    val waiting = IeoStatus.colors.waiting
    val work = if (state.isPickup) "상차" else "하차"
    WhiteSection(contentPadding = 36.dp) {
        StatusTag(text = if (state.isWorking) "$work 중" else "대기 중", color = waiting, background = WaitingPale)
        Spacer(Modifier.height(16.dp))
        Text(state.stopName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextStrong)
        Text(
            text = formatDuration(timerSeconds),
            fontSize = 104.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextStrong,
            style = Tnum,
            letterSpacing = (-3).sp,
        )
        WaitFeeLine(state.policy, chargeableMinutes, waitEnded = state.isWorking)
        state.departingInSeconds?.let { seconds ->
            Spacer(Modifier.height(12.dp))
            Text(
                "${if (state.isPickup) "상차지" else "하차지"}를 떠나고 있어요 · ${formatDuration(seconds).takeLast(5)} 뒤 ${if (state.isPickup) "출발" else "운행 완료"}로 기록해요",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = waiting,
            )
        }
        Spacer(Modifier.height(36.dp))
        // 반 화면에서 버튼 3개를 한 줄에 두면 글자가 깨지므로 하차지에서는 주 버튼을 아랫줄로 내린다
        val stacked = compact && !state.isPickup
        val proofButtons: @Composable RowScope.() -> Unit = {
            IeoTonalButton(text = "대기 증빙 보내기", onClick = actions.onSendWaitProof, modifier = Modifier.weight(1f))
            // 인수증은 하차지에서만 받는다 (POD-003)
            if (!state.isPickup) {
                IeoTonalButton(text = "인수증 받기", onClick = actions.onRequestDeliveryProof, modifier = Modifier.weight(1f))
            }
        }
        val mainButton: @Composable (Modifier) -> Unit = { modifier ->
            when {
                !state.isWorking -> IeoPrimaryButton(text = "$work 시작", onClick = actions.onStartWork, modifier = modifier)
                // 하차지를 떠나면 운행이 끝난다
                state.isPickup -> IeoPrimaryButton(text = "상차 끝, 출발", onClick = actions.onDepart, modifier = modifier)
                else -> IeoPrimaryButton(text = "운행 완료", onClick = actions.onDepart, modifier = modifier)
            }
        }
        if (stacked) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = proofButtons)
            Spacer(Modifier.height(12.dp))
            mainButton(Modifier)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                proofButtons()
                mainButton(Modifier.weight(1f))
            }
        }
    }
}

/** [chargeableMinutes]: 대기비를 세기 시작한 뒤 흐른 분. [waitEnded]면 작업을 시작해 대기가 끝났다 */
@Composable
private fun WaitFeeLine(policy: WaitPolicy?, chargeableMinutes: Long, waitEnded: Boolean) {
    val amount = policy?.chargeableAmount(chargeableMinutes) ?: 0L
    val text = when {
        policy == null -> "대기비 기준이 없어 시간만 기록해요"
        waitEnded && amount == 0L -> "무료 대기 시간 안에 끝났어요"
        waitEnded -> "대기비 ${formatWon(amount)}을 청구할 수 있어요"
        amount == 0L -> "${formatMinutesKorean(policy.minutesUntilCharged(chargeableMinutes))} 뒤부터 대기비가 붙어요"
        else -> "대기비 ${formatWon(amount)}을 청구할 수 있어요"
    }
    Text(text, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = TextSub)
}

@Composable
private fun WaitDetails(state: HomeState.Waiting, onNextOrderClick: (NextOrderCard) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.nextOrders.isNotEmpty()) NextOrders(state.nextOrders, onNextOrderClick)
        WhiteSection {
            SectionTitle(title = "이번 운행")
            DetailLine("구간", state.trip.route)
            DetailLine("운임", "${formatFare(state.trip.fare)} (${state.trip.paymentType})")
            DetailLine("주선사", state.trip.brokerName)
        }
        state.site?.let { site -> SiteSection(site, isPickup = state.isPickup) }
    }
}

/** 하차지에서 이어서 할 배차 후보. 실수익 높은 순. 누르면 수락 확인 창 (REC-021, 022) */
@Composable
private fun NextOrders(orders: List<NextOrderCard>, onClick: (NextOrderCard) -> Unit) {
    WhiteSection {
        SectionTitle(title = "다음 배차 추천", count = orders.size, note = "하차 끝나고 바로 실을 수 있는 것만, 실수익 높은 순")
        orders.forEach { order ->
            ListRow(
                time = order.pickupTime,
                day = order.pickupDay,
                title = order.route,
                tags = listOfNotNull(
                    Keyword.vehicle(order.vehicle),
                    order.cargo?.let(Keyword::cargo),
                    Keyword.emptyRun(order.emptyMeters),
                    Keyword.longWait().takeIf { order.longWait },
                    order.brokerMetrics?.let(Keyword::brokerPayment),
                ),
                subtitle = order.brokerName,
                amount = formatWon(order.netProfit),
                amountNote = "실수익",
                onClick = { onClick(order) },
            )
        }
    }
}

/** 다른 차주들이 남긴 상하차지 정보. 아무도 남기지 않았으면 그렇게 알려 준다 (SITE-007) */
@Composable
private fun SiteSection(site: SiteInfo, isPickup: Boolean) {
    WhiteSection {
        SectionTitle(title = if (isPickup) "상차지 정보" else "하차지 정보")
        site.heightLimitMeters?.let { DetailLine("높이 제한", "${it}m${heightNote(site.heightWarning)}") }
        site.forklift?.let { DetailLine("지게차", if (it) "있음" else "없음") }
        site.parking?.let { DetailLine("주차", if (it) "가능" else "어려움") }
        site.averageWaitMinutes?.let { DetailLine("평균 대기", formatMinutesKorean(it.toLong())) }
        site.averageWorkMinutes?.let { DetailLine("평균 작업", formatMinutesKorean(it.toLong())) }
        site.notes.forEach { note ->
            Text("“$note”", fontSize = 24.sp, color = TextSub, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        }
        if (site.isEmpty) {
            Text("아직 남겨진 정보가 없어요", fontSize = 24.sp, color = TextSub)
        }
    }
}

private fun heightNote(warning: HeightWarning) = when (warning) {
    HeightWarning.BLOCKED -> " · 내 차는 못 들어가요"
    HeightWarning.CAUTION -> " · 여유 0.2m 미만"
    HeightWarning.OK, HeightWarning.UNKNOWN -> ""
}

// ───────────────────────── 주행 중 ─────────────────────────

/**
 * 주행 중에는 버튼을 두지 않는다. 도착 시각과 진행 상황만 크게 보여 주고, 다음 배차는 음성으로 안내한다.
 * 내비 안내가 꺼졌을 때만(도착해서 끄거나, 앱을 다시 켰거나, 내비가 요청을 받지 못했을 때) "길 안내 다시"·"도착했어요" 버튼을 보여 준다.
 */
@Composable
private fun DrivingHome(state: HomeState.Driving, onArrive: () -> Unit, onResumeGuidance: () -> Unit) {
    val status = IeoStatus.colors
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(ScreenPadding)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 880.dp).verticalScroll(rememberScrollState()),
        ) {
            WhiteSection(contentPadding = 36.dp) {
                StatusTag(
                    text = if (state.isPickup) "상차지로 가는 중" else "하차지로 가는 중",
                    color = status.driving,
                    background = DrivingPale,
                )
                Spacer(Modifier.height(18.dp))
                Text("${state.destinationName}까지", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextSub)
                if (state.etaText != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(state.etaText, fontSize = 104.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong, letterSpacing = (-3).sp, style = Tnum)
                        Text(" 도착", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong, modifier = Modifier.padding(bottom = 20.dp))
                    }
                } else if (!state.naviActive) {
                    // 앱을 다시 켰거나 내비가 요청을 받지 못했다. 기다려도 채워지지 않으므로 할 일을 알려 준다
                    Text("길 안내가 꺼져 있어요", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong, modifier = Modifier.padding(vertical = 20.dp))
                } else {
                    Text("경로 확인 중", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = TextSub, modifier = Modifier.padding(vertical = 28.dp))
                }
                if (state.naviActive || state.etaText != null) {
                    Spacer(Modifier.height(16.dp))
                    ProgressBar(progress = state.progress ?: 0f, color = status.driving)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("남은 거리", state.remainingKm?.let { "${it}km" } ?: "-")
                        Stat("남은 시간", state.remainingMinutes?.let { formatMinutesKorean(it.toLong()) } ?: "-")
                        Stat("진행", state.progress?.let { "${(it * 100).toInt()}%" } ?: "-")
                    }
                }
                state.arrivingInSeconds?.let { seconds ->
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "${if (state.isPickup) "상차지" else "하차지"}에 멈췄어요 · ${formatDuration(seconds).takeLast(5)} 뒤 대기를 시작해요",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = IeoStatus.colors.waiting,
                    )
                }
                if (state.etaShared) {
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = status.done, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("주선사·수하인에게 도착 시각을 알렸어요", fontSize = 24.sp, color = TextSub)
                    }
                }
            }
            WhiteSection {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.trip.route, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        TagLine(
                            tags = listOf(Keyword.vehicle(state.trip.vehicle), Keyword.payment(state.trip.paymentType)),
                            text = state.trip.brokerName,
                        )
                    }
                    Text(formatFare(state.trip.fare), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong, style = Tnum)
                }
            }
            state.recommendation?.let { rec ->
                WhiteSection {
                    SectionTitle(title = "다음 배차 추천")
                    ListRow(
                        time = rec.pickupTime,
                        day = "상차",
                        title = rec.route,
                        subtitle = "기어 P로 세우면 수락할 수 있어요",
                        amount = "실수익",
                        amountNote = formatWon(rec.netProfit),
                        onClick = {},
                    )
                }
            }
            if (!state.naviActive) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 경로를 모르면 다시 안내가 먼저고, 안내를 마친 뒤라면 도착 처리가 먼저다
                    if (state.etaText == null) {
                        IeoTonalButton(text = "도착했어요", onClick = onArrive, modifier = Modifier.weight(1f))
                        IeoPrimaryButton(text = "길 안내 다시", onClick = onResumeGuidance, modifier = Modifier.weight(1f))
                    } else {
                        IeoTonalButton(text = "길 안내 다시", onClick = onResumeGuidance, modifier = Modifier.weight(1f))
                        IeoPrimaryButton(text = "도착했어요", onClick = onArrive, modifier = Modifier.weight(1f))
                    }
                }
            } else if (state.recommendation == null) {
                Text(
                    "안내를 끝내려면 내비에서 안내 종료를 눌러 주세요",
                    fontSize = 24.sp,
                    color = TextSub,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RowScope.Stat(label: String, value: String) {
    Column(modifier = Modifier.weight(1f).background(CanvasGray, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(label, fontSize = 24.sp, color = TextSub)
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong, style = Tnum, maxLines = 1)
    }
}

private const val PROGRESS_GLIDE_MILLIS = 10_000

@Composable
private fun ProgressBar(progress: Float, color: Color) {
    // 내비 값은 10초쯤마다 온다. 다음 값이 올 때까지 같은 속도로 움직여 멈춤 없이 차오르게 한다
    val shown by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = PROGRESS_GLIDE_MILLIS, easing = LinearEasing),
        label = "trip-progress",
    )
    Box(modifier = Modifier.fillMaxWidth().height(12.dp).background(CanvasGray, CircleShape)) {
        Box(modifier = Modifier.fillMaxWidth(shown).height(12.dp).background(color, CircleShape))
    }
}

// ───────────────────────── 공통 조각 ─────────────────────────

private fun todayText(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))

@Preview(name = "내비 옆 반 화면", widthDp = 728, heightDp = 1200)
@Composable
private fun IdleHomeCompactPreview() {
    IeoTheme { HomeScreen(SampleHomeData.idle()) }
}

@Preview(name = "내비 옆 반 화면", widthDp = 728, heightDp = 1200)
@Composable
private fun WaitingHomeCompactPreview() {
    IeoTheme { HomeScreen(SampleHomeData.waiting()) }
}

@Preview(name = "내비 옆 반 화면", widthDp = 728, heightDp = 1200)
@Composable
private fun DrivingHomeCompactPreview() {
    IeoTheme { HomeScreen(SampleHomeData.driving()) }
}

@Preview(name = "하차지 대기 + 다음 배차 추천", widthDp = 1280, heightDp = 720)
@Composable
private fun UnloadingHomePreview() {
    IeoTheme { HomeScreen(SampleHomeData.unloading()) }
}

@Preview(widthDp = 1600, heightDp = 1000)
@Composable
private fun IdleHomeWidePreview() {
    IeoTheme { HomeScreen(SampleHomeData.idle()) }
}
