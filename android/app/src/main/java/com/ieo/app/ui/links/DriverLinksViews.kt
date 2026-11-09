package com.ieo.app.ui.links

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.ui.common.josa
import com.ieo.app.ui.component.IeoBottomSheet
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.IeoTonalButton
import com.ieo.app.ui.component.SectionTitle
import com.ieo.app.ui.component.SheetTitle
import com.ieo.app.ui.component.StatusTag
import com.ieo.app.ui.component.WhiteSection
import com.ieo.app.ui.theme.Orange50
import com.ieo.app.ui.theme.Orange700
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub

/** 받은 연결 요청 창. 수락이 위치 공유 동의라서 동의 문구를 꼭 보여 준다 */
@Composable
fun BoxScope.LinkInvitationSheet(invitation: LinkRow?, busy: Boolean, actions: DriverLinksActions) {
    IeoBottomSheet(item = invitation, onDismiss = actions::later) { shown ->
        SheetTitle("${shown.brokerName}에서\n단골 차주로 연결을 요청했어요")
        Spacer(Modifier.height(16.dp))
        Text(DriverLinksViewModel.consentText(shown.brokerName), fontSize = 24.sp, lineHeight = 32.sp, color = TextStrong)
        Spacer(Modifier.height(8.dp))
        Text(shown.detail, fontSize = 24.sp, color = TextSub)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IeoTonalButton(text = "나중에", onClick = actions::later, modifier = Modifier.weight(1f))
            IeoTonalButton(text = "거절", onClick = { if (!busy) actions.decline() }, modifier = Modifier.weight(1f))
            IeoPrimaryButton(text = "수락", onClick = actions::accept, enabled = !busy, modifier = Modifier.weight(2f))
        }
    }
}

/** 해제 확인 창 */
@Composable
fun BoxScope.UnlinkSheet(link: LinkRow?, busy: Boolean, actions: DriverLinksActions) {
    IeoBottomSheet(item = link, onDismiss = actions::closeUnlink) { shown ->
        SheetTitle("${josa(shown.brokerName, "과", "와")}\n연결을 해제할까요?")
        Spacer(Modifier.height(12.dp))
        Text(
            "위치 공유와 새 지정 배차가 끝나요. 이미 잡은 배차는 그대로예요",
            fontSize = 24.sp,
            color = TextSub,
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IeoTonalButton(text = "취소", onClick = actions::closeUnlink, modifier = Modifier.weight(1f))
            IeoPrimaryButton(text = "해제", onClick = actions::unlink, enabled = !busy, modifier = Modifier.weight(2f))
        }
    }
}

/** 설정 탭의 "단골 주선사": 받은 요청과 연결된 회사 */
@Composable
fun DriverLinksSection(state: DriverLinksUiState, actions: DriverLinksActions) {
    WhiteSection {
        SectionTitle(title = "단골 주선사", count = state.links.size, note = "연결된 회사 일을 하는 동안 위치를 공유해요")
        if (state.invitations.isEmpty() && state.links.isEmpty()) {
            Text("연결된 회사가 없어요", fontSize = 24.sp, color = TextSub, modifier = Modifier.padding(8.dp))
        }
        state.invitations.forEach { invitation ->
            LinkLine(invitation, tag = "요청 받음", button = "보기", onClick = { actions.open(invitation.id) })
        }
        state.links.forEach { link ->
            LinkLine(link, tag = null, button = "해제", onClick = { actions.confirmUnlink(link.id) })
        }
    }
}

@Composable
private fun LinkLine(row: LinkRow, tag: String?, button: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(row.brokerName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextStrong)
                tag?.let { StatusTag(text = it, color = Orange700, background = Orange50) }
            }
            Spacer(Modifier.height(4.dp))
            Text(row.detail, fontSize = 24.sp, color = TextSub)
        }
        IeoTonalButton(text = button, onClick = onClick, modifier = Modifier.weight(0.4f))
    }
}
