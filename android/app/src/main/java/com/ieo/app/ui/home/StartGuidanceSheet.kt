package com.ieo.app.ui.home

import com.ieo.app.ui.theme.TextSub
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ieo.app.ui.common.formatFare
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
        SheetLine("운임", "${formatFare(shown.fare)} (${shown.paymentType})")
        SheetLine("차량·주선사", "${shown.vehicle} · ${shown.brokerName}")
        // 앱이 2창을 직접 켤 수 없어 (Pleos에 해당 API 없음) 기사님께 방법을 알려 준다
        Spacer(Modifier.height(20.dp))
        Text("안내가 시작되면 내비가 앞으로 나와요. 오른쪽 끝 ⋮를 끌면 이에오와 나란히 볼 수 있어요", fontSize = 24.sp, lineHeight = 32.sp, color = TextSub)
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IeoTonalButton(text = "닫기", onClick = onDismiss, modifier = Modifier.weight(1f))
            IeoPrimaryButton(text = "안내 시작", onClick = { onStart(shown) }, modifier = Modifier.weight(2f))
        }
    }
}
