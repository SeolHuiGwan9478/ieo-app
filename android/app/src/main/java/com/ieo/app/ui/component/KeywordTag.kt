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
import com.ieo.app.domain.order.BrokerMetrics
import com.ieo.app.domain.order.CargoCategory
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

        /** 지금 위치에서 상차지까지 직선거리. 1km보다 가까우면 "1km 안" */
        /** 서버가 주는 거리는 도로 거리 어림이라 "약"을 붙인다. 상세는 실제 경로로 다시 잰다 */
        fun distanceText(meters: Long): String = if (meters < 1_000) "1km 안" else "약 ${(meters + 500) / 1_000}km"

        fun distance(meters: Long) = Keyword(
            "상차지 ${distanceText(meters)}",
            IeoTag.Gray,
        )

        /** 하차지에서 다음 상차지까지 빈 차로 가는 거리 (배차 추천) */
        fun emptyRun(meters: Long) = Keyword(
            if (meters < 1_000) "공차 1km 안" else "공차 ${(meters + 500) / 1_000}km",
            IeoTag.Gray,
        )

        /** 일반이 아닌 화물 종류 (DSP-001). 위험물은 눈에 띄게 */
        fun cargo(category: CargoCategory) = Keyword(
            category.label,
            if (category == CargoCategory.HAZARDOUS) IeoTag.Plum else IeoTag.Gray,
        )

        /** 싣고 내리는 방법: "지게차", "수작업". 색으로 좋고 나쁨을 말하지 않는다 */
        fun loading(label: String) = Keyword(label, IeoTag.Gray)

        /** 상차지 평균 대기가 길었던 곳 (REC-012) */
        fun longWait() = Keyword("대기 길었던 곳", IeoTag.Plum)

        /** 주선사 평균 지급 소요일. 15일 이하는 빠름, 20일 초과는 늦음, 그 사이는 보통. 운행 20건 미만이면 기록 쌓는 중 */
        fun brokerPayment(metrics: BrokerMetrics): Keyword {
            val days = metrics.avgPaymentDays
            return when {
                !metrics.published -> Keyword("거래 기록 적음", IeoTag.Gray)
                days == null -> Keyword("지급 이력 없음", IeoTag.Gray)
                days <= 15 -> Keyword("지급 평균 ${days}일", IeoTag.Sage)
                days > 20 -> Keyword("지급 평균 ${days}일", IeoTag.Plum)
                else -> Keyword("지급 평균 ${days}일", IeoTag.Gray)
            }
        }
    }
}

@Composable
fun KeywordTag(keyword: Keyword, modifier: Modifier = Modifier) {
    Text(
        text = keyword.text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = keyword.color.content,
        maxLines = 1,
        modifier = modifier
            .background(keyword.color.background, RoundedCornerShape(8.dp))
            .heightIn(min = 40.dp)
            .wrapContentHeight()
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
