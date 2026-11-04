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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.domain.wait.WaitProofLink
import com.ieo.app.ui.common.DayTimeFormatter
import com.ieo.app.ui.component.IeoBottomSheet
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.QrCode
import com.ieo.app.ui.component.SheetTitle
import com.ieo.app.ui.theme.TextSub

/**
 * 대기 증빙 링크 창 (WAIT-020). 휴대폰으로 QR을 찍어 링크를 열고 주선사에게 보낸다.
 * TODO: 알림(09)을 만들면 주선사에게 바로 보내는 버튼을 단다 (WAIT-022)
 */
@Composable
fun BoxScope.WaitProofSheet(
    link: WaitProofLink?,
    onDismiss: () -> Unit,
    labels: DayTimeFormatter = DayTimeFormatter(),
) {
    IeoBottomSheet(item = link, onDismiss = onDismiss) { shown ->
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
            QrCode(content = shown.url, size = 220.dp)
            Column(modifier = Modifier.weight(1f)) {
                SheetTitle("대기 증빙을\n휴대폰으로 받으세요")
                Spacer(Modifier.height(16.dp))
                Text("QR을 찍어 링크를 열고 주선사에게 보내 주세요.\n도착·작업 시작 시각과 대기 시간이 담겨 있어요.", fontSize = 21.sp, lineHeight = 30.sp, color = TextSub)
                Spacer(Modifier.height(12.dp))
                Text("${labels.dayTime(shown.expiresAt)}까지 열려요", fontSize = 19.sp, color = TextSub)
                Spacer(Modifier.height(8.dp))
                Text(shown.url, fontSize = 16.sp, color = TextSub)
            }
        }
        Spacer(Modifier.height(32.dp))
        IeoPrimaryButton(text = "닫기", onClick = onDismiss)
    }
}
