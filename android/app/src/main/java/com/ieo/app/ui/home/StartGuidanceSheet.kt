package com.ieo.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.ui.common.formatWon
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.IeoTonalButton
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub
import com.ieo.app.ui.theme.White

/**
 * 배차를 누르면 아래에서 올라오는 확인 창. 상차지까지 길 안내를 시작한다.
 *
 * ModalBottomSheet는 창을 하나 더 띄워서 Pleos 하단 독에 앱 아이콘이 하나 더 생긴다.
 * 그래서 앱 화면 안에 직접 그린다. 왼쪽 메뉴까지 덮도록 메인 화면 맨 위에 둔다.
 */
@Composable
fun BoxScope.StartGuidanceSheet(trip: AssignedTrip?, onStart: (AssignedTrip) -> Unit, onDismiss: () -> Unit) {
    // 닫히는 동안에도 내용을 그대로 보여 주도록 마지막 배차를 기억한다
    var shown by remember { mutableStateOf(trip) }
    if (trip != null) shown = trip

    BackHandler(enabled = trip != null, onBack = onDismiss)

    AnimatedVisibility(visible = trip != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = trip != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        shown?.let { SheetContent(it, onStart = { onStart(it) }, onDismiss = onDismiss) }
    }
}

@Composable
private fun SheetContent(trip: AssignedTrip, onStart: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            // 시트 안을 눌러도 뒤의 어두운 바탕이 눌리지 않게 막는다
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .padding(start = 36.dp, top = 40.dp, end = 36.dp, bottom = 32.dp),
    ) {
        Text(
            "${trip.pickup.name}으로\n안내를 시작할까요?",
            fontSize = 32.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextStrong,
        )
        Spacer(Modifier.height(28.dp))
        SheetLine("상차", "${trip.pickupDay} ${trip.pickupTime} · ${trip.pickup.address}")
        SheetLine("하차", trip.dropoff.name)
        SheetLine("운임", "${formatWon(trip.fare)} (${trip.paymentType})")
        SheetLine("차량·주선사", "${trip.vehicle} · ${trip.brokerName}")
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IeoTonalButton(text = "닫기", onClick = onDismiss, modifier = Modifier.weight(1f))
            IeoPrimaryButton(text = "안내 시작", onClick = onStart, modifier = Modifier.weight(2f))
        }
    }
}

@Composable
private fun SheetLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, fontSize = 21.sp, color = TextSub, modifier = Modifier.width(140.dp))
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.Medium, color = TextStrong)
    }
}
