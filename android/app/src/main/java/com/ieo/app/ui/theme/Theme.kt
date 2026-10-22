package com.ieo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 기본색: 흑백 (design/README.md 색상 팔레트)
val Black = Color(0xFF000000)
val Gray950 = Color(0xFF121212)
val Gray900 = Color(0xFF1E1E1E)
val Gray800 = Color(0xFF2C2C2C)
val Gray500 = Color(0xFF6B6B6B)
val Gray400 = Color(0xFFA3A3A3)
val Gray100 = Color(0xFFEDEDED)
val White = Color(0xFFFFFFFF)

// 상태색: 운행 상황을 알릴 때만 사용
data class StatusColors(
    val driving: Color,
    val waiting: Color,
    val done: Color,
    val warning: Color,
)

val DarkStatus = StatusColors(
    driving = Color(0xFF60A5FA),
    waiting = Color(0xFFFBBF24),
    done = Color(0xFF4ADE80),
    warning = Color(0xFFF87171),
)

val LightStatus = StatusColors(
    driving = Color(0xFF1D4ED8),
    waiting = Color(0xFFB45309),
    done = Color(0xFF15803D),
    warning = Color(0xFFB91C1C),
)

private val DarkColors = darkColorScheme(
    primary = White,
    onPrimary = Black,
    secondary = Gray800,
    onSecondary = White,
    background = Black,
    onBackground = White,
    surface = Gray900,
    onSurface = White,
    surfaceVariant = Gray800,
    onSurfaceVariant = Gray400,
    outline = Gray800,
    error = DarkStatus.warning,
)

private val LightColors = lightColorScheme(
    primary = Black,
    onPrimary = White,
    secondary = Gray100,
    onSecondary = Black,
    background = White,
    onBackground = Black,
    surface = Gray100,
    onSurface = Black,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray500,
    outline = Gray100,
    error = LightStatus.warning,
)

@Composable
fun IeoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
