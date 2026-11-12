package com.ieo.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.domain.account.DriverPlan
import com.ieo.app.ui.theme.CanvasGray
import com.ieo.app.ui.theme.Gray400
import com.ieo.app.ui.theme.Orange100
import com.ieo.app.ui.theme.Orange600
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub
import com.ieo.app.ui.theme.White

private val STUB = 150.dp
private val NOTCH = 14.dp

/** 휴대폰 앱과 같은 티켓 모양: 떼는 선 위아래를 반원으로 파낸 둥근 사각형 (PLAN-007) */
private object TicketShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { NOTCH.toPx() }
        val x = size.width - with(density) { STUB.toPx() }
        val body = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(with(density) { 20.dp.toPx() }))) }
        val holes = Path().apply {
            addOval(Rect(Offset(x, 0f), r))
            addOval(Rect(Offset(x, size.height), r))
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, body, holes))
    }
}

/**
 * 설정의 "내 플랜" 티켓. 베이직은 바탕색(밤에도 눈부시지 않게), 프로는 주황.
 * 떼는 부분에 유료면 D-남은 날, 베이직이면 무료
 */
@Composable
fun PlanTicket(plan: DriverPlan) {
    data class Colors(val fill: Color, val ink: Color, val sub: Color, val title: Color, val dot: Color, val line: Color)
    val colors = when (plan.code) {
        "PRO" -> Colors(Orange600, White, Orange100, White, White, White)
        else -> Colors(CanvasGray, TextStrong, TextSub, TextStrong, Orange600, Gray400)
    }
    val (stubTitle, stubSub) = if (plan.paid && plan.daysLeft != null && plan.paidUntil != null) {
        "D-${plan.daysLeft}" to "${plan.paidUntil.monthValue}.${plan.paidUntil.dayOfMonth}까지"
    } else {
        "무료" to "늘 무료"
    }
    val label = if (plan.trial) "${plan.label} 체험" else plan.label
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 190.dp)
            .background(colors.fill, TicketShape)
            .drawBehind {
                val x = size.width - STUB.toPx()
                val inset = NOTCH.toPx() + 8.dp.toPx()
                drawLine(colors.line.copy(alpha = 0.7f), Offset(x, inset), Offset(x, size.height - inset), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 7.dp.toPx())))
            },
    ) {
        Column(Modifier.weight(1f).padding(start = 28.dp, end = 16.dp, top = 22.dp, bottom = 24.dp)) {
            Text("이에오 이용권", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = colors.sub)
            Text(label, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = colors.title, modifier = Modifier.padding(top = 2.dp))
            plan.highlights.take(3).forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Box(Modifier.size(8.dp).background(colors.dot, CircleShape))
                    Text(line, fontSize = 24.sp, color = colors.ink, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        Column(Modifier.width(STUB).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stubTitle, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = colors.ink)
            Text(stubSub, fontSize = 22.sp, color = colors.sub, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
