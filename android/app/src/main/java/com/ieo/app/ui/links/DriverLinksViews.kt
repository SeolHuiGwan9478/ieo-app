package com.ieo.app.ui.links

import androidx.compose.foundation.layout.Arrangement
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
import com.ieo.app.R
import com.ieo.app.ui.component.SectionTitle
import com.ieo.app.ui.component.StatusTag
import com.ieo.app.ui.component.TextInset
import com.ieo.app.ui.component.WhiteSection
import com.ieo.app.ui.theme.Orange50
import com.ieo.app.ui.theme.Orange700
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub

/** 설정 탭의 "단골 주선사": 받은 요청과 연결된 회사를 보여 주기만 한다. 수락·거절·해제는 휴대폰에서 */
@Composable
fun DriverLinksSection(state: DriverLinksUiState) {
    WhiteSection {
        SectionTitle(title = "단골 주선사", count = state.links.size, note = "수락과 해제는 휴대폰 앱에서 해요", icon = R.drawable.ic_menu_partner)
        if (state.invitations.isEmpty() && state.links.isEmpty()) {
            Text("연결된 회사가 없어요", fontSize = 24.sp, color = TextSub, modifier = Modifier.padding(horizontal = TextInset, vertical = 8.dp))
        }
        state.invitations.forEach { LinkLine(it, tag = "요청 받음") }
        state.links.forEach { LinkLine(it, tag = null) }
    }
}

@Composable
private fun LinkLine(row: LinkRow, tag: String?) {
    Column(modifier = Modifier.padding(start = TextInset, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(row.brokerName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextStrong)
            tag?.let { StatusTag(text = it, color = Orange700, background = Orange50) }
        }
        Spacer(Modifier.height(4.dp))
        Text(row.detail, fontSize = 24.sp, color = TextSub)
    }
}
