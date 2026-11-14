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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ieo.app.ui.common.formatWon
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.IeoTonalButton
import com.ieo.app.ui.component.Keyword
import com.ieo.app.ui.component.TagLine
import com.ieo.app.ui.theme.CardSurface
import com.ieo.app.ui.theme.IeoTheme
import com.ieo.app.ui.theme.Orange600
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub
import kotlinx.coroutines.delay

/** 추천 창 하나를 보여 주는 시간. 지나면 다음 추천으로 넘어간다 */
const val PICK_SECONDS = 15

private val ModalShape = RoundedCornerShape(28.dp)

/**
 * 배차 추천 창 (03). 차를 세우면 가운데에 1등부터 15초씩 차례로 뜬다. [넘기기]나 시간이 지나면 다음 추천,
 * [배차 요청]이면 요청을 보낸다. 넘긴 것은 기록하지 않고, 음성으로 묻지도 않는다.
 * 시간은 앱이 보이는 동안만 재고, 다시 보이면 처음부터 잰다. 시간 표시는 도착 확인 창과 같은 [CountdownHeader].
 * 추가 창은 Pleos 독에 아이콘을 하나 더 만들므로 화면 안에 직접 그린다
 */
@Composable
fun BoxScope.PickModal(
    pick: NextOrderCard?,
    busy: Boolean,
    onRequest: () -> Unit,
    onNext: () -> Unit,
) {
    if (pick == null) return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var secondsLeft by remember(pick.orderId) { mutableIntStateOf(PICK_SECONDS) }
    LaunchedEffect(pick.orderId, busy) {
        // 요청을 보내는 동안에는 시간을 재지 않는다
        if (busy) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            secondsLeft = PICK_SECONDS
            while (secondsLeft > 0) {
                delay(1_000)
                secondsLeft--
            }
            onNext()
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
            CountdownHeader("근처 추천", secondsLeft, PICK_SECONDS)
            Spacer(Modifier.height(24.dp))
            Text("${pick.pickup} → ${pick.dropoff}", fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = TextStrong)
            Spacer(Modifier.height(8.dp))
            Text("${pick.pickupDay} ${pick.pickupTime} 상차 · ${pick.vehicle} · ${pick.brokerName}", fontSize = 24.sp, lineHeight = 32.sp, color = TextSub)
            Spacer(Modifier.height(12.dp))
            TagLine(
                listOfNotNull(
                    Keyword.regular().takeIf { pick.forMeOnly },
                    pick.cargo?.let(Keyword::cargo),
                    Keyword.emptyRun(pick.emptyMeters),
                    Keyword.longWait().takeIf { pick.longWait },
                    pick.brokerMetrics?.let(Keyword::brokerPayment),
                ),
            )
            Spacer(Modifier.height(20.dp))
            Text("실수익", fontSize = 24.sp, color = TextSub)
            Text(formatWon(pick.netProfit), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Orange600)
            Spacer(Modifier.height(4.dp))
            Text(
                "운임 ${formatWon(pick.fare)} − 기름값 ${formatWon(pick.fuelCost)}" +
                    (pick.tollFare?.let { " − 통행료 ${formatWon(it)}" } ?: " (통행료 제외)"),
                fontSize = 24.sp,
                color = TextSub,
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IeoTonalButton(text = "넘기기", onClick = { if (!busy) onNext() }, modifier = Modifier.weight(1f))
                IeoPrimaryButton(text = "배차 요청", onClick = { if (!busy) onRequest() }, enabled = !busy, modifier = Modifier.weight(2f))
            }
        }
    }
}

@Preview(widthDp = 960, heightDp = 720)
@Composable
private fun PickModalPreview() {
    IeoTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            PickModal(pick = SampleHomeData.picks.first(), busy = false, onRequest = {}, onNext = {})
        }
    }
}
