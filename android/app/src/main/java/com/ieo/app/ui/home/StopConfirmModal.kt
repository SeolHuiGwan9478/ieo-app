package com.ieo.app.ui.home

import androidx.compose.foundation.background
import com.ieo.app.ui.theme.Orange600
import com.ieo.app.ui.theme.CanvasGray
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.IeoTonalButton
import com.ieo.app.ui.theme.CardSurface
import com.ieo.app.ui.theme.IeoStatus
import com.ieo.app.ui.theme.IeoTheme
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub
import kotlinx.coroutines.delay

private val ModalShape = RoundedCornerShape(28.dp)

/**
 * 도착·떠남 확인 창 (WAIT-007, 005). 음성으로 묻는 것과 같은 말을 보여 주고, 말로도 버튼으로도 대답할 수 있다.
 * 버튼은 세워 두었을 때(기어 P 또는 주차 브레이크)만 눌린다. 달리는 중이면 말로 대답하라고 알려 준다.
 * 시간 표시는 수락 창과 같은 [CountdownHeader]를 쓴다.
 * 시간은 ViewModel이 재고(15초 뒤 창을 닫는다), 여기서는 남은 초만 보여 준다.
 * 추가 창은 Pleos 독에 아이콘을 하나 더 만들므로 화면 안에 직접 그린다 (NextOrderOfferModal과 같은 이유)
 */
@Composable
fun BoxScope.StopConfirmModal(
    question: StopQuestion?,
    parked: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
) {
    if (question == null) return
    var secondsLeft by remember(question.kind, question.stopName, question.timeLabel) { mutableIntStateOf(HomeViewModel.ARRIVAL_CONFIRM_SECONDS) }
    LaunchedEffect(question.kind, question.stopName, question.timeLabel, question.asked) {
        if (!question.asked) return@LaunchedEffect
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.45f))
            // 뒤의 화면이 눌리지 않게 막는다
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .background(CardSurface, ModalShape)
                .padding(32.dp),
        ) {
            CountdownHeader(question.title, secondsLeft, HomeViewModel.ARRIVAL_CONFIRM_SECONDS)
            Spacer(Modifier.height(24.dp))
            Text(question.question, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = TextStrong)
            Spacer(Modifier.height(8.dp))
            Text(question.detail, fontSize = 24.sp, lineHeight = 32.sp, color = TextSub)
            // 대답은 자유롭게 말해도 서버가 알아듣는다 (UX-025). 버튼이 잠긴 까닭만 알려 준다
            if (!parked) {
                Spacer(Modifier.height(16.dp))
                Text("달리는 중이라 말로 대답해 주세요", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = IeoStatus.colors.waiting)
            }
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IeoTonalButton(text = question.declineLabel, onClick = { if (!busy) onDecline() }, enabled = parked, modifier = Modifier.weight(1f))
                IeoPrimaryButton(text = question.confirmLabel, onClick = { if (!busy) onConfirm() }, enabled = parked, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 창 위쪽의 제목과 남은 초, 줄어드는 막대. 시간을 재는 창(수락 창, 도착 확인 창)이 같은 모양을 쓴다 */
@Composable
fun CountdownHeader(title: String, secondsLeft: Int, totalSeconds: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 24.sp, color = TextSub, modifier = Modifier.weight(1f))
        Text("${secondsLeft}초", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextStrong)
    }
    Spacer(Modifier.height(12.dp))
    TimeBar(secondsLeft, totalSeconds)
}

/** 남은 시간 막대. 1초씩 부드럽게 줄어든다 */
@Composable
private fun TimeBar(secondsLeft: Int, totalSeconds: Int) {
    val fraction by animateFloatAsState(
        targetValue = secondsLeft / totalSeconds.toFloat(),
        animationSpec = tween(durationMillis = 1_000, easing = LinearEasing),
        label = "offer-time",
    )
    Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(CanvasGray, RoundedCornerShape(5.dp))) {
        Box(modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Orange600, RoundedCornerShape(5.dp)))
    }
}

@Preview(widthDp = 960, heightDp = 720)
@Composable
private fun StopConfirmModalPreview() {
    IeoTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            StopConfirmModal(
                question = StopQuestion(StopQuestionKind.ARRIVE, stopName = "대구 성서 하차지", isPickup = false, timeLabel = "14:00", asked = true),
                parked = false,
                busy = false,
                onConfirm = {},
                onDecline = {},
            )
        }
    }
}
