package com.ieo.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private const val CYCLE_MILLIS = 1200

/** 로고 심볼 좌표(design/logo/icon, 120 단위)에서 막대와 점 두 개만 잘라낸 영역 */
private const val GLYPH_LEFT = 14f
private const val GLYPH_WIDTH = 90f
private const val GLYPH_HEIGHT = 26f

/**
 * 로딩 표시. 로고의 막대·점·작은 점에 빛이 왼쪽에서 오른쪽으로 차례로 흘러간다.
 * 버튼이나 카드 안처럼 작은 자리에 쓴다. 화면 전체를 덮을 때는 [IeoLoadingPanel]을 쓴다.
 */
@Composable
fun IeoLoadingIndicator(
    modifier: Modifier = Modifier,
    width: Dp = 96.dp,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    val phase by rememberInfiniteTransition(label = "ieo-loading").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(CYCLE_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )

    Canvas(
        modifier = modifier
            .size(width = width, height = width * (GLYPH_HEIGHT / GLYPH_WIDTH))
            .semantics { contentDescription = "불러오는 중" },
    ) {
        val unit = size.width / GLYPH_WIDTH
        fun x(value: Float) = (value - GLYPH_LEFT) * unit
        val centerY = size.height / 2

        // 막대 → 점 → 작은 점 순서로 0.18 주기씩 늦게 밝아진다
        val bar = glow(phase, delay = 0f)
        val dot = glow(phase, delay = 0.18f)
        val smallDot = glow(phase, delay = 0.36f)

        val barHeight = 18 * unit
        drawRoundRect(
            color = color.copy(alpha = alphaOf(bar)),
            topLeft = Offset(x(18f), centerY - barHeight / 2),
            size = Size(42 * unit, barHeight),
            cornerRadius = CornerRadius(barHeight / 2),
        )
        drawCircle(
            color = color.copy(alpha = alphaOf(dot)),
            radius = 9 * unit * scaleOf(dot),
            center = Offset(x(76f), centerY),
        )
        drawCircle(
            color = color.copy(alpha = alphaOf(smallDot)),
            radius = 6 * unit * scaleOf(smallDot),
            center = Offset(x(98f), centerY),
        )
    }
}

/**
 * 화면 전체를 덮는 로딩 패널. 뒤 화면은 반투명하게 가리고, 터치를 막는다.
 * 예: `IeoLoadingPanel(visible = isLoading, message = "로그인 중")`
 */
@Composable
fun IeoLoadingPanel(
    visible: Boolean,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
                // 로딩 중에는 뒤 화면을 누를 수 없게 한다
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IeoLoadingIndicator(width = 120.dp)
                if (message != null) {
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = message,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 한 주기(0~1) 안에서 빛이 지나가는 정도(0~1).
 * delay만큼 늦게 시작하는 삼각파라서, 세 요소가 차례로 밝아졌다 어두워진다.
 */
private fun glow(phase: Float, delay: Float): Float {
    val distance = abs(((phase - delay) % 1f + 1f) % 1f - 0.25f)
    return (1f - distance / 0.25f).coerceIn(0f, 1f)
}

private fun alphaOf(glow: Float) = 0.25f + 0.75f * glow

private fun scaleOf(glow: Float) = 1f + 0.2f * glow

@Preview(widthDp = 400, heightDp = 200, showBackground = true)
@Composable
private fun IeoLoadingIndicatorPreview() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        IeoLoadingIndicator(color = Color.Black)
    }
}
