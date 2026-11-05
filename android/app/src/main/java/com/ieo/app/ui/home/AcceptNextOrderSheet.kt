package com.ieo.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ieo.app.ui.common.formatWon
import com.ieo.app.ui.component.IeoBottomSheet
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.IeoTonalButton
import com.ieo.app.ui.component.SheetLine
import com.ieo.app.ui.component.SheetTitle

/**
 * 추천 카드를 누르면 올라오는 수락 확인 창 (REC-022). 잘못 누른 수락을 막으려고 한 번 더 묻는다.
 * 수락하면 그 오더에 배차 요청이 간다. 주선사가 확정해야 배차가 된다
 */
@Composable
fun BoxScope.AcceptNextOrderSheet(order: NextOrderCard?, onAccept: (NextOrderCard) -> Unit, onDismiss: () -> Unit) {
    IeoBottomSheet(item = order, onDismiss = onDismiss) { shown ->
        SheetTitle("${shown.pickup} → ${shown.dropoff}\n수락할까요?")
        Spacer(Modifier.height(28.dp))
        SheetLine("상차", "${shown.pickupDay} ${shown.pickupTime} · ${shown.pickup}")
        SheetLine("하차", shown.dropoff)
        SheetLine("실수익", "${formatWon(shown.netProfit)} (운임 ${formatWon(shown.fare)}에서 공차 기름값을 뺀 것)")
        SheetLine("차량·주선사", "${shown.vehicle} · ${shown.brokerName}")
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IeoTonalButton(text = "닫기", onClick = onDismiss, modifier = Modifier.weight(1f))
            IeoPrimaryButton(text = "수락하고 배차 요청", onClick = { onAccept(shown) }, modifier = Modifier.weight(2f))
        }
    }
}
