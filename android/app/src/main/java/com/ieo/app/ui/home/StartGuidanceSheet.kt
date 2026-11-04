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
import com.ieo.app.ui.common.josa
import com.ieo.app.ui.component.IeoBottomSheet
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.IeoTonalButton
import com.ieo.app.ui.component.SheetLine
import com.ieo.app.ui.component.SheetTitle

/** 배차를 누르면 아래에서 올라오는 확인 창. 상차지까지 길 안내를 시작한다 */
@Composable
fun BoxScope.StartGuidanceSheet(trip: AssignedTrip?, onStart: (AssignedTrip) -> Unit, onDismiss: () -> Unit) {
    IeoBottomSheet(item = trip, onDismiss = onDismiss) { shown ->
        SheetTitle("${josa(shown.pickup.name, "으로", "로")}\n안내를 시작할까요?")
        Spacer(Modifier.height(28.dp))
        SheetLine("상차", "${shown.pickupDay} ${shown.pickupTime} · ${shown.pickup.address}")
        SheetLine("하차", shown.dropoff.name)
        SheetLine("운임", "${formatWon(shown.fare)} (${shown.paymentType})")
        SheetLine("차량·주선사", "${shown.vehicle} · ${shown.brokerName}")
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IeoTonalButton(text = "닫기", onClick = onDismiss, modifier = Modifier.weight(1f))
            IeoPrimaryButton(text = "안내 시작", onClick = { onStart(shown) }, modifier = Modifier.weight(2f))
        }
    }
}
