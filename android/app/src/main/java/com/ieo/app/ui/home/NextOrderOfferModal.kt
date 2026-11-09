package com.ieo.app.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.ieo.app.ui.theme.CanvasGray
import com.ieo.app.ui.theme.IeoStatus
import com.ieo.app.ui.theme.IeoTheme
import com.ieo.app.ui.theme.Orange600
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub
import com.ieo.app.ui.theme.CardSurface
import kotlinx.coroutines.delay

/** 수락을 기다리는 시간. 시간이 끝나면 거절로 치지 않고 다음 후보로 넘어간다 */
const val OFFER_SECONDS = 15

private val ModalShape = RoundedCornerShape(28.dp)

/**
 * 가운데 뜨는 배차 수락 창. 줄어드는 시간과 함께 묻는다 (REC-021, REC-022).
 * 내용을 보여 주고 묻는 이 창이 확인 단계라 수락은 한 번만 누른다.
 * 정차 중에만 넘어온다 ([HomeViewModel.nextOffer]). 시간은 앱이 보이는 동안만 재고, 다시 보이면 처음부터 잰다.
 * 추가 창은 Pleos 독에 아이콘을 하나 더 만들므로 화면 안에 직접 그린다 (IeoBottomSheet와 같은 이유)
 */
@Composable
fun BoxScope.NextOrderOfferModal(
    offer: NextOrderCard?,
    busy: Boolean,
    /** 묻는 말을 마쳤다. 이때부터 시간을 잰다 */
    asked: Boolean,
    onAccept: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onTimeout: (Long) -> Unit,
) {
    if (offer == null) return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var secondsLeft by remember(offer.orderId) { mutableIntStateOf(OFFER_SECONDS) }
    LaunchedEffect(offer.orderId, busy, asked) {
        // 수락을 보내는 동안, 음성으로 묻는 동안에는 시간을 재지 않는다
        if (busy || !asked) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            secondsLeft = OFFER_SECONDS
            while (secondsLeft > 0) {
                delay(1_000)
                secondsLeft--
            }
            onTimeout(offer.orderId)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("다음 배차 추천", fontSize = 24.sp, color = TextSub, modifier = Modifier.weight(1f))
                Text("${secondsLeft}초", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextStrong)
            }
            Spacer(Modifier.height(12.dp))
            TimeBar(secondsLeft)
            Spacer(Modifier.height(24.dp))
            Text("${offer.pickup} → ${offer.dropoff}", fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = TextStrong)
            Spacer(Modifier.height(8.dp))
            Text(
                "${offer.pickupDay} ${offer.pickupTime} 상차 · ${offer.vehicle} · ${offer.brokerName}",
                fontSize = 24.sp,
                lineHeight = 32.sp,
                color = TextSub,
            )
            Spacer(Modifier.height(12.dp))
            TagLine(
                listOfNotNull(
                    offer.cargo?.let(Keyword::cargo),
                    Keyword.emptyRun(offer.emptyMeters),
                    Keyword.longWait().takeIf { offer.longWait },
                    offer.brokerMetrics?.let(Keyword::brokerPayment),
                ),
            )
            Spacer(Modifier.height(20.dp))
            Text("실수익", fontSize = 24.sp, color = TextSub)
            Text(formatWon(offer.netProfit), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = IeoStatus.colors.done)
            Spacer(Modifier.height(4.dp))
            Text(
                "운임 ${formatWon(offer.fare)} − 기름값 ${formatWon(offer.fuelCost)}" +
                    (offer.tollFare?.let { " − 통행료 ${formatWon(it)}" } ?: " (통행료 제외)"),
                fontSize = 24.sp,
                color = TextSub,
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IeoTonalButton(text = "넘기기", onClick = { if (!busy) onSkip(offer.orderId) }, modifier = Modifier.weight(1f))
                IeoPrimaryButton(text = "수락하고 배차 요청", onClick = { if (!busy) onAccept(offer.orderId) }, modifier = Modifier.weight(2f))
            }
        }
    }
}

/** 남은 시간 막대. 1초씩 부드럽게 줄어든다 */
@Composable
private fun TimeBar(secondsLeft: Int) {
    val fraction by animateFloatAsState(
        targetValue = secondsLeft / OFFER_SECONDS.toFloat(),
        animationSpec = tween(durationMillis = 1_000, easing = LinearEasing),
        label = "offer-time",
    )
    Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(CanvasGray, RoundedCornerShape(5.dp))) {
        Box(modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Orange600, RoundedCornerShape(5.dp)))
    }
}

@Preview(widthDp = 960, heightDp = 720)
@Composable
private fun NextOrderOfferModalPreview() {
    IeoTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            NextOrderOfferModal(
                offer = SampleHomeData.unloading().nextOrders.first(),
                busy = false,
                asked = true,
                onAccept = {},
                onSkip = {},
                onTimeout = {},
            )
        }
    }
}
