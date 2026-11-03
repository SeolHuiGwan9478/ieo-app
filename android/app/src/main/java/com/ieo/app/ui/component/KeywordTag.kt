package com.ieo.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.ui.theme.IeoTag
import com.ieo.app.ui.theme.TagColor

/** 목록에서 차량·결제·지급 같은 키워드를 한눈에 구분하도록 색을 입힌 태그 */
data class Keyword(val text: String, val color: TagColor) {

    companion object {
        /** 차량 종류: "5톤 카고", "5톤 윙" */
        fun vehicle(text: String) = Keyword(text, IeoTag.Navy)

        /** 결제 방식: 선불은 바로 받는 돈, 인수증은 서류를 내고 나중에 받는 돈 */
        fun payment(type: String) = Keyword(
            type,
            when (type) {
                "선불" -> IeoTag.Teal
                "인수증" -> IeoTag.Sand
                else -> IeoTag.Gray
            },
        )

        /** 주선사 평균 지급 소요일. 15일 이하는 빠름, 20일 초과는 늦음, 그 사이와 이력 없음은 보통 */
        fun paymentDays(days: Int?) = when {
            days == null -> Keyword("지급 이력 없음", IeoTag.Gray)
            days <= 15 -> Keyword("지급 평균 ${days}일", IeoTag.Sage)
            days > 20 -> Keyword("지급 평균 ${days}일", IeoTag.Plum)
            else -> Keyword("지급 평균 ${days}일", IeoTag.Gray)
        }
    }
}

@Composable
fun KeywordTag(keyword: Keyword, modifier: Modifier = Modifier) {
    Text(
        text = keyword.text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = keyword.color.content,
        maxLines = 1,
        modifier = modifier
            .background(keyword.color.background, RoundedCornerShape(8.dp))
            .heightIn(min = 30.dp)
            .wrapContentHeight()
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
