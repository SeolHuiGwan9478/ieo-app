package com.ieo.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.ui.component.IeoBottomSheet
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.IeoTonalButton
import com.ieo.app.ui.component.SheetTitle
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub

/**
 * "운행 완료"를 누르면 뜨는 창 (WAIT-020). 대기 증빙을 같이 만들지 고른다. 기본은 만들지 않는다.
 * 여기서 안 만들었어도 운행이 끝난 뒤 장부에서 만들 수 있다.
 */
@Composable
fun BoxScope.FinishTripSheet(
    visible: Boolean,
    busy: Boolean,
    onFinish: (makeProof: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    IeoBottomSheet(item = Unit.takeIf { visible }, onDismiss = onDismiss) {
        var makeProof by remember { mutableStateOf(false) }
        SheetTitle("운행을 마칠까요?")
        Spacer(Modifier.height(12.dp))
        Text("하차지를 떠난 것으로 기록하고 장부에 운임을 남겨요.", fontSize = 24.sp, lineHeight = 34.sp, color = TextSub)
        Spacer(Modifier.height(24.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { makeProof = !makeProof }.padding(vertical = 8.dp),
        ) {
            Checkbox(checked = makeProof, onCheckedChange = { makeProof = it }, modifier = Modifier.scale(1.6f))
            Spacer(Modifier.width(20.dp))
            Text("대기 증빙 만들기", fontSize = 26.sp, color = TextStrong)
        }
        Text("상차지·하차지 대기 기록을 주선사에게 보낼 링크로 만들어요", fontSize = 22.sp, color = TextSub, modifier = Modifier.padding(start = 76.dp))
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IeoTonalButton(text = "취소", onClick = onDismiss, modifier = Modifier.weight(1f))
            IeoPrimaryButton(text = "운행 완료", onClick = { if (!busy) onFinish(makeProof) }, enabled = !busy, modifier = Modifier.weight(2f))
        }
    }
}
