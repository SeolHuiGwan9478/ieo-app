package com.ieo.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.domain.restarea.RestStop
import com.ieo.app.domain.trip.RestCheck
import com.ieo.app.domain.trip.RestLevel
import com.ieo.app.ui.common.formatMinutesKorean
import com.ieo.app.ui.common.josa
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.Keyword
import com.ieo.app.ui.component.TagLine
import com.ieo.app.ui.component.Tnum
import com.ieo.app.ui.component.WhiteSection
import com.ieo.app.ui.theme.IeoStatus
import com.ieo.app.ui.theme.IeoTag
import com.ieo.app.ui.theme.Orange50
import com.ieo.app.ui.theme.Orange500
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub

private val CardShape = RoundedCornerShape(16.dp)
private val RowShape = RoundedCornerShape(12.dp)

/** 휴게 안내 문구 (TRIP-031). 1시간 45분은 노랑, 2시간부터 빨강 */
@Composable
fun RestLine(rest: RestCheck) {
    val level = rest.level ?: return
    val advice = when (level) {
        RestLevel.SOON -> "15분 안에 쉬어 가세요"
        RestLevel.NOW -> "지금 쉬어 가세요"
        RestLevel.EXTENDED_SOON -> "15분 안에 30분 이상 쉬어 가세요"
        RestLevel.EXTENDED_NOW -> "바로 30분 이상 쉬어 가세요"
    }
    Text(
        "${formatMinutesKorean(rest.drivingFor.toMinutes())}째 운전 중 · $advice",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = if (level == RestLevel.SOON) IeoStatus.colors.waiting else IeoStatus.colors.warning,
    )
}

/**
 * 달리는 동안의 쉴 곳 카드 한 장 (TRIP-032). 휴게 안내 중이면 말한 곳을 테두리로 강조하고, 아니면 다음 화물차 라운지.
 * 달리는 동안 터치하지 않으므로 누를 곳이 없다
 */
@Composable
fun RestStopCard(state: HomeState.Driving) {
    val focus = state.restFocus
    val stop = focus ?: state.nextLounge ?: return
    val level = state.rest?.level
    val accent = when {
        focus == null || level == null -> MaterialTheme.colorScheme.outline
        level == RestLevel.SOON -> IeoStatus.colors.waiting
        else -> IeoStatus.colors.warning
    }
    Spacer(Modifier.height(20.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (focus != null) 2.dp else 1.dp, accent, CardShape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(if (focus != null) "쉬어 갈 곳" else "다음 화물차 라운지", fontSize = 20.sp, color = TextSub)
        Spacer(Modifier.height(4.dp))
        Text(
            "${stop.name} · 경로로 ${km(stop)}",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = Tnum,
        )
        Facilities(stop)
    }
}

/**
 * 세워 두었을 때 가는 길 화물차 라운지 목록과 [경유지로 안내] (TRIP-032). 터치는 세워 두었을 때만 받는다.
 * 줄을 눌러 고른 곳을 경유지로 넣는다. 처음에는 휴게 안내에서 말한 곳, 없으면 가장 가까운 라운지가 골라져 있다
 */
@Composable
fun RestStopList(state: HomeState.Driving, onAddWaypoint: (RestStop) -> Unit) {
    val lounges = state.restStops.filter { it.isLounge }.take(3)
    if (lounges.isEmpty()) return
    val suggested = state.restFocus?.takeIf { focus -> lounges.any { it.name == focus.name } } ?: lounges.first()
    var selectedName by remember(lounges.map { it.name }, suggested.name) { mutableStateOf(suggested.name) }
    val target = lounges.firstOrNull { it.name == selectedName } ?: suggested
    WhiteSection(contentPadding = 28.dp) {
        Text("가는 길 화물차 라운지 · 눌러서 고르기", fontSize = 22.sp, color = TextSub)
        Spacer(Modifier.height(8.dp))
        lounges.forEach { stop ->
            LoungeRow(stop, selected = stop.name == target.name, enabled = state.parked, onClick = { selectedName = stop.name })
        }
        Spacer(Modifier.height(16.dp))
        IeoPrimaryButton(
            text = "${josa(target.name, "을", "를")} 경유지로 안내",
            onClick = { onAddWaypoint(target) },
            enabled = state.parked,
            modifier = Modifier.fillMaxWidth(),
        )
        state.restAttribution?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, fontSize = 18.sp, color = TextSub)
        }
    }
}

@Composable
private fun ColumnScope.LoungeRow(stop: RestStop, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(if (selected) Orange50 else Color.Transparent)
            .border(if (selected) 2.dp else 0.dp, if (selected) Orange500 else Color.Transparent, RowShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stop.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Facilities(stop)
        }
        Text(km(stop), fontSize = 24.sp, color = TextSub, style = Tnum)
    }
}

/** 수면실·샤워실·세탁실만 보여 준다 */
@Composable
private fun Facilities(stop: RestStop) {
    val shown = listOf("수면실", "샤워실", "세탁실").filter { it in stop.facilities }
    if (shown.isEmpty()) return
    Spacer(Modifier.height(6.dp))
    TagLine(tags = shown.map { Keyword(it, IeoTag.Sand) })
}

private fun km(stop: RestStop) = "${(stop.aheadMeters + 500) / 1000}km"

