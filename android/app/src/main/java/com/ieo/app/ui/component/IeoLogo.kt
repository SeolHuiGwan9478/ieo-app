package com.ieo.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ieo.app.R

/**
 * Ieo 아이콘: 둥근 사각형 안에 누운 i와 작아지는 점 두 개. (design/logo/icon)
 * 라이트 모드에서는 검정 사각형, 다크 모드에서는 흰 사각형이 되도록 테마 색을 쓴다.
 */
@Composable
fun IeoSymbol(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    background: Color = MaterialTheme.colorScheme.onBackground,
    foreground: Color = MaterialTheme.colorScheme.background,
) {
    Canvas(modifier = modifier.size(size)) {
        // 원본 SVG의 120 단위 좌표를 그대로 쓰고 실제 크기에 맞춰 늘린다
        val unit = this.size.width / 120f
        drawRoundRect(color = background, cornerRadius = CornerRadius(26 * unit))
        drawRoundRect(
            color = foreground,
            topLeft = Offset(18 * unit, 51 * unit),
            size = Size(42 * unit, 18 * unit),
            cornerRadius = CornerRadius(9 * unit),
        )
        drawCircle(color = foreground, radius = 9 * unit, center = Offset(76 * unit, 60 * unit))
        drawCircle(color = foreground, radius = 6 * unit, center = Offset(98 * unit, 60 * unit))
    }
}

/** 텍스트 로고 "ieo." */
@Composable
fun IeoWordmark(
    modifier: Modifier = Modifier,
    width: Dp = 240.dp,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Image(
        painter = painterResource(R.drawable.ieo_wordmark),
        contentDescription = stringResource(R.string.app_name),
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.width(width),
    )
}
