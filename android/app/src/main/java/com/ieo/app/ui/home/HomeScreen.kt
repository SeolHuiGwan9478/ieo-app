package com.ieo.app.ui.home

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
import com.ieo.app.ui.common.formatWon
import com.ieo.app.domain.wait.WaitPolicy
import com.ieo.app.ui.component.IeoPrimaryButton
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
private val SectionShape = RoundedCornerShape(24.dp)

/** 숫자 폭을 고정해 시각·금액 자릿수가 줄마다 맞고, 초가 바뀔 때 글자가 흔들리지 않게 한다 */
private val Tnum = TextStyle(fontFeatureSettings = "tnum")

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
    onArrive: () -> Unit = {},
    onResumeGuidance: () -> Unit = {},
) {
    // 내비 옆 반 화면(좁은 창)이 기본 사용 형태다. 앱 영역 전체를 쓸 때만 두 칸으로 펼친다
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(CanvasGray)) {
        val compact = maxWidth < WideLayoutMinWidth
        when (state) {
            is HomeState.Idle -> IdleHome(state, compact, onTripClick, onOrderClick, onUnpaidClick, onRegisterTrip)
            is HomeState.Waiting -> WaitingHome(state, compact, WaitActions(onStartWork, onDepart, onSendWaitProof))
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
        Text("${todayText()} · 운행 ${state.today.tripCount}건", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = TextSub)
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
            Text("${unpaid.brokerName} 미수금 ${formatWon(unpaid.amount)}", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = TextStrong)
            Text("지급일이 ${unpaid.overdueDays}일 지났어요", fontSize = 19.sp, color = warning)
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
                fontSize = 21.sp,
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
                amount = formatWon(trip.fare),
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
        SectionTitle(title = "근처 오더", count = state.orders.size, note = "${state.baseLocation} 기준")
        state.orders.forEach { order ->
            ListRow(
                time = order.pickupTime,
                day = order.pickupDay,
                title = "${order.pickup} → ${order.dropoff}",
                tags = listOf(Keyword.vehicle(order.vehicle), Keyword.paymentDays(order.brokerPaymentDays)),
                amount = formatWon(order.fare),
                amountNote = "실수익 ${formatWon(order.netProfit)}",
                onClick = { onOrderClick(order) },
            )
        }
    }
}

/** 목록 한 줄: [시각 칸] 구간 / 키워드 태그·설명 · 금액 */
@Composable
private fun ListRow(
    time: String,
    day: String,
    title: String,
    amount: String,
    onClick: () -> Unit,
    tags: List<Keyword> = emptyList(),
    subtitle: String? = null,
    amountNote: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.size(76.dp).background(CanvasGray, RoundedCornerShape(18.dp)),
        ) {
            Text(time, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong, style = Tnum)
            Text(day, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextSub)
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = TextStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            TagLine(tags, subtitle)
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(amount, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong, style = Tnum)
            amountNote?.let {
                Text(it, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = IeoStatus.colors.done, style = Tnum)
            }
        }
    }
}

// ───────────────────────── 대기 중 ─────────────────────────

/** 대기 화면의 버튼들 */
private class WaitActions(val onStartWork: () -> Unit, val onDepart: () -> Unit, val onSendWaitProof: () -> Unit)

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
            WaitTimer(state, timerSeconds, chargeableMinutes, actions)
            WaitDetails(state)
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(ScreenPadding),
        ) {
            Box(modifier = Modifier.weight(0.55f)) { WaitTimer(state, timerSeconds, chargeableMinutes, actions) }
            Box(modifier = Modifier.weight(0.45f)) { WaitDetails(state) }
        }
    }
}

@Composable
private fun WaitTimer(state: HomeState.Waiting, timerSeconds: Long, chargeableMinutes: Long, actions: WaitActions) {
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
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IeoTonalButton(text = "대기 증빙 보내기", onClick = actions.onSendWaitProof, modifier = Modifier.weight(1f))
            when {
                !state.isWorking -> IeoPrimaryButton(text = "$work 시작", onClick = actions.onStartWork, modifier = Modifier.weight(1f))
                // 하차지를 떠나면 운행이 끝난다
                state.isPickup -> IeoPrimaryButton(text = "상차 끝, 출발", onClick = actions.onDepart, modifier = Modifier.weight(1f))
                else -> IeoPrimaryButton(text = "운행 완료", onClick = actions.onDepart, modifier = Modifier.weight(1f))
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
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = TextSub)
}

@Composable
private fun WaitDetails(state: HomeState.Waiting) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WhiteSection {
            SectionTitle(title = "이번 운행")
            DetailLine("구간", state.trip.route)
            DetailLine("운임", "${formatWon(state.trip.fare)} (${state.trip.paymentType})")
            DetailLine("주선사", state.trip.brokerName)
        }
        state.site?.let { site ->
            WhiteSection {
                SectionTitle(title = if (state.isPickup) "상차지 정보" else "하차지 정보")
                site.heightLimitMeters?.let { DetailLine("높이 제한", "${it}m") }
                site.forklift?.let { DetailLine("지게차", if (it) "있음" else "없음") }
                site.averageWaitMinutes?.let { DetailLine("평균 대기", formatMinutesKorean(it.toLong())) }
            }
        }
    }
}

// ───────────────────────── 주행 중 ─────────────────────────

/**
 * 주행 중에는 버튼을 두지 않는다. 도착 시각과 진행 상황만 크게 보여 주고, 다음 배차는 음성으로 안내한다.
 * 내비 안내가 꺼졌을 때만(도착해서 끄거나, 앱을 다시 켰을 때) "다시 안내"·"도착했어요" 버튼을 보여 준다.
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
                } else {
                    Text("경로 확인 중", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = TextSub, modifier = Modifier.padding(vertical = 28.dp))
                }
                Spacer(Modifier.height(16.dp))
                ProgressBar(progress = state.progress ?: 0f, color = status.driving)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("남은 거리", state.remainingKm?.let { "${it}km" } ?: "-")
                    Stat("남은 시간", state.remainingMinutes?.let { formatMinutesKorean(it.toLong()) } ?: "-")
                    Stat("진행", state.progress?.let { "${(it * 100).toInt()}%" } ?: "-")
                }
                if (state.etaShared) {
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = status.done, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("주선사·수하인에게 도착 시각을 알렸어요", fontSize = 20.sp, color = TextSub)
                    }
                }
            }
            WhiteSection {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.trip.route, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        TagLine(
                            tags = listOf(Keyword.vehicle(state.trip.vehicle), Keyword.payment(state.trip.paymentType)),
                            text = state.trip.brokerName,
                        )
                    }
                    Text(formatWon(state.trip.fare), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong, style = Tnum)
                }
            }
            state.recommendation?.let { rec ->
                WhiteSection {
                    SectionTitle(title = "다음 배차 추천")
                    ListRow(
                        time = rec.pickupTime,
                        day = "상차",
                        title = rec.route,
                        subtitle = "\"응\"이라고 말하면 자세히 알려 드려요",
                        amount = "실수익",
                        amountNote = formatWon(rec.netProfit),
                        onClick = {},
                    )
                }
            }
            if (!state.naviActive) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IeoTonalButton(text = "길 안내 다시", onClick = onResumeGuidance, modifier = Modifier.weight(1f))
                    IeoPrimaryButton(text = "도착했어요", onClick = onArrive, modifier = Modifier.weight(1f))
                }
            } else if (state.recommendation == null) {
                Text(
                    "안내를 끝내려면 내비에서 안내 종료를 눌러 주세요",
                    fontSize = 18.sp,
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
        Text(label, fontSize = 18.sp, color = TextSub)
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong, style = Tnum, maxLines = 1)
    }
}

@Composable
private fun ProgressBar(progress: Float, color: Color) {
    Box(modifier = Modifier.fillMaxWidth().height(12.dp).background(CanvasGray, CircleShape)) {
        Box(modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(12.dp).background(color, CircleShape))
    }
}

// ───────────────────────── 공통 조각 ─────────────────────────

/** 키워드 태그를 늘어놓고, 주선사 이름 같은 남은 설명은 회색 글자로 뒤에 붙인다 */
@Composable
private fun TagLine(tags: List<Keyword>, text: String? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        tags.forEach { KeywordTag(it) }
        text?.let { Text(it, fontSize = 19.sp, color = TextSub, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun WhiteSection(contentPadding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(White, SectionShape)
            .padding(start = contentPadding, top = contentPadding + 10.dp, end = contentPadding, bottom = contentPadding),
        content = content,
    )
}

@Composable
private fun SectionTitle(title: String, count: Int? = null, note: String? = null) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong)
        count?.let {
            Spacer(Modifier.width(8.dp))
            Text("$it", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = TextSub)
        }
        note?.let {
            Spacer(Modifier.weight(1f))
            Text(it, fontSize = 18.sp, color = TextSub, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, fontSize = 21.sp, color = TextSub, modifier = Modifier.width(130.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = TextStrong)
    }
}

/** 상태 꼬리표: 옅은 바탕에 글자. 색만으로 상태를 구분하지 않는다 (NFR-006) */
@Composable
private fun StatusTag(text: String, color: Color, background: Color) {
    Text(
        text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.background(background, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = Color(0xFFB0B8C1),
        modifier = Modifier.size(30.dp),
    )
}

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

@Preview(widthDp = 1600, heightDp = 1000)
@Composable
private fun IdleHomeWidePreview() {
    IeoTheme { HomeScreen(SampleHomeData.idle()) }
}
