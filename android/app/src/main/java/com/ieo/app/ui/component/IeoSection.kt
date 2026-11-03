package com.ieo.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.ui.theme.CanvasGray
import com.ieo.app.ui.theme.IeoStatus
import com.ieo.app.ui.theme.Orange50
import com.ieo.app.ui.theme.Orange700
import com.ieo.app.ui.theme.TextMid
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub
import com.ieo.app.ui.theme.White

// 목록 화면(홈, 오더, 장부)이 같이 쓰는 조각. 연회색 바탕 위에 흰 묶음을 놓고, 핵심 숫자만 크고 굵게 쓴다

/** 숫자 폭을 고정해 시각·금액 자릿수가 줄마다 맞고, 초가 바뀔 때 글자가 흔들리지 않게 한다 */
val Tnum = TextStyle(fontFeatureSettings = "tnum")

private val SectionShape = RoundedCornerShape(24.dp)

/** 흰 묶음 */
@Composable
fun WhiteSection(contentPadding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(White, SectionShape)
            .padding(start = contentPadding, top = contentPadding + 10.dp, end = contentPadding, bottom = contentPadding),
        content = content,
    )
}

/** 묶음 제목. 개수는 회색, 오른쪽 끝에 짧은 설명 */
@Composable
fun SectionTitle(title: String, count: Int? = null, note: String? = null) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong)
        count?.let {
            Spacer(Modifier.width(8.dp))
            Text("$it", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = TextSub)
        }
        note?.let {
            Spacer(Modifier.weight(1f))
            Text(it, fontSize = 18.sp, color = TextSub, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

/**
 * 목록 한 줄: [시각 칸] 구간 / 키워드 태그·설명 · 금액.
 * [time]이 null이면 시각 칸 없이 그린다 (장부의 경비 줄).
 */
@Composable
fun ListRow(
    time: String?,
    day: String?,
    title: String,
    amount: String,
    onClick: () -> Unit,
    tags: List<Keyword> = emptyList(),
    subtitle: String? = null,
    subtitleColor: Color = TextSub,
    amountNote: String? = null,
    amountNoteColor: Color = IeoStatus.colors.done,
    amountColor: Color = TextStrong,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 16.dp),
    ) {
        if (time != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.size(76.dp).background(CanvasGray, RoundedCornerShape(18.dp)),
            ) {
                Text(time, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong, style = Tnum)
                day?.let { Text(it, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextSub) }
            }
            Spacer(Modifier.width(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = TextStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (tags.isNotEmpty() || subtitle != null) {
                Spacer(Modifier.height(8.dp))
                TagLine(tags, subtitle, subtitleColor)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(amount, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = amountColor, style = Tnum)
            amountNote?.let {
                Text(it, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = amountNoteColor, style = Tnum)
            }
        }
    }
}

/** 키워드 태그를 늘어놓고, 주선사 이름 같은 남은 설명은 회색 글자로 뒤에 붙인다 */
@Composable
fun TagLine(tags: List<Keyword>, text: String? = null, textColor: Color = TextSub) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        tags.forEach { KeywordTag(it) }
        text?.let { Text(it, fontSize = 19.sp, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** 이름 · 값 한 줄 */
@Composable
fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, fontSize = 21.sp, color = TextSub, modifier = Modifier.width(130.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = TextStrong)
    }
}

/** 상태 꼬리표: 옅은 바탕에 글자. 색만으로 상태를 구분하지 않는다 (NFR-006) */
@Composable
fun StatusTag(text: String, color: Color, background: Color) {
    Text(
        text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.background(background, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun Chevron() {
    Icon(
        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = Color(0xFFB0B8C1),
        modifier = Modifier.size(30.dp),
    )
}

/** 고르는 칩 하나 (오늘·내일, 장부·미수금·경비). 고른 칩은 주황 */
@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 20.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) Orange700 else TextMid,
        modifier = Modifier
            .background(if (selected) Orange50 else White, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}
