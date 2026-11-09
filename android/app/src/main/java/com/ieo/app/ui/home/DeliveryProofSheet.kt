package com.ieo.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.domain.delivery.DeliveryProof
import com.ieo.app.domain.delivery.DeliveryProofStatus
import com.ieo.app.ui.common.DayTimeFormatter
import com.ieo.app.ui.component.IeoBottomSheet
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.QrCode
import com.ieo.app.ui.component.SheetTitle
import com.ieo.app.ui.theme.IeoStatus
import com.ieo.app.ui.theme.TextSub

/**
 * "인수증 받기" 창 (POD-003). 수하인이 휴대폰으로 QR을 찍어 서명하면 창 안의 글이 바뀐다.
 * 서명을 기다리는 동안 창을 닫아도 다시 누르면 같은 QR이 나온다.
 */
@Composable
fun BoxScope.DeliveryProofSheet(
    proof: DeliveryProof?,
    onDismiss: () -> Unit,
    labels: DayTimeFormatter = DayTimeFormatter(),
) {
    IeoBottomSheet(item = proof, onDismiss = onDismiss) { shown ->
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
            QrCode(content = shown.url, size = 220.dp)
            Column(modifier = Modifier.weight(1f)) {
                SheetTitle(if (shown.isConfirmed) "인수 확인을\n받았어요" else "수하인에게\nQR을 보여 주세요")
                Spacer(Modifier.height(16.dp))
                Text(statusText(shown, labels), fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, color = statusColor(shown))
                if (!shown.isConfirmed) {
                    Spacer(Modifier.height(8.dp))
                    Text("휴대폰 카메라로 찍으면 설치 없이 서명할 수 있어요", fontSize = 24.sp, color = TextSub)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        IeoPrimaryButton(text = if (shown.isConfirmed) "확인" else "닫기", onClick = onDismiss)
    }
}

@Composable
private fun statusColor(proof: DeliveryProof) = if (proof.isConfirmed) IeoStatus.colors.done else TextSub

private fun statusText(proof: DeliveryProof, labels: DayTimeFormatter): String = when (proof.status) {
    DeliveryProofStatus.PENDING -> "서명을 기다리는 중이에요"
    DeliveryProofStatus.PHOTO -> "종이 인수증 사진으로 확인했어요"
    DeliveryProofStatus.SIGNED, DeliveryProofStatus.DELIVERED -> {
        val who = proof.signerName?.let { "${it}님이 " } ?: ""
        val time = proof.signedAt?.let { " · ${labels.time(it)}" } ?: ""
        "${who}서명했어요$time"
    }
}
