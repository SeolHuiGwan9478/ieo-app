package com.ieo.app.ui.home

import androidx.compose.foundation.background
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
 * 도착 확인 창 (WAIT-007). 음성으로 묻는 것과 같은 말을 보여 주고, 말로도 버튼으로도 대답할 수 있다.
 * 버튼은 세워 두었을 때(기어 P 또는 주차 브레이크)만 눌린다. 달리는 중이면 말로 대답하라고 알려 준다.
 * 시간 표시는 수락 창과 같은 [CountdownHeader]를 쓴다.
 * 시간은 ViewModel이 재고(15초 뒤 창을 닫는다), 여기서는 남은 초만 보여 준다.
 * 추가 창은 Pleos 독에 아이콘을 하나 더 만들므로 화면 안에 직접 그린다 (NextOrderOfferModal과 같은 이유)
 */
@Composable
fun BoxScope.ArrivalConfirmModal(
    question: ArrivalQuestion?,
    parked: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
) {
    if (question == null) return
    var secondsLeft by remember(question.stopName, question.startLabel) { mutableIntStateOf(HomeViewModel.ARRIVAL_CONFIRM_SECONDS) }
    LaunchedEffect(question.stopName, question.startLabel, question.asked) {
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
            CountdownHeader("도착 확인", secondsLeft, HomeViewModel.ARRIVAL_CONFIRM_SECONDS)
            Spacer(Modifier.height(24.dp))
            Text("${question.stopName}에 도착하셨어요?", fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = TextStrong)
            Spacer(Modifier.height(8.dp))
            Text("${question.startLabel}부터 대기로 기록할게요", fontSize = 24.sp, lineHeight = 32.sp, color = TextSub)
            // 대답은 자유롭게 말해도 서버가 알아듣는다 (UX-025). 버튼이 잠긴 까닭만 알려 준다
            if (!parked) {
                Spacer(Modifier.height(16.dp))
                Text("달리는 중이라 말로 대답해 주세요", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = IeoStatus.colors.waiting)
            }
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IeoTonalButton(text = "아직이에요", onClick = { if (!busy) onDecline() }, enabled = parked, modifier = Modifier.weight(1f))
                IeoPrimaryButton(text = "도착 맞아요", onClick = { if (!busy) onConfirm() }, enabled = parked, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Preview(widthDp = 960, heightDp = 720)
@Composable
private fun ArrivalConfirmModalPreview() {
    IeoTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            ArrivalConfirmModal(
                question = ArrivalQuestion(stopName = "대구 성서 하차지", startLabel = "14:00", asked = true),
                parked = false,
                busy = false,
                onConfirm = {},
                onDecline = {},
            )
        }
    }
}
