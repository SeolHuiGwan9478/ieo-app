package com.ieo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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

// 홈·목록 화면용 (A안 시안): 연회색 바탕에 흰 묶음, 글자는 진한 남회색 계열
val CanvasGray = Color(0xFFF2F4F6)   // 화면 바탕, 보조 버튼, 시각 칸
val HoverGray = Color(0xFFF9FAFB)
val TextStrong = Color(0xFF191F28)   // 제목·금액
val TextMid = Color(0xFF4E5968)      // 보조 버튼 글자
val TextSub = Color(0xFF8B95A1)      // 설명 글자
val WarningPale = Color(0xFFFDECEC)  // 미수금 아이콘 바탕
val DrivingPale = Color(0xFFE8EFFD)  // 운행 상태 꼬리표 바탕
val WaitingPale = Color(0xFFFDF1E3)  // 대기 상태 꼬리표 바탕

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

/**
 * 앱 테마. 기본은 라이트 모드(흰 바탕, 검은 글씨)다.
 * 다크 색상은 남겨 두었으니, 나중에 밤에 어둡게 바꾸려면 darkTheme에 isSystemInDarkTheme()을 넘기면 된다.
 */
@Composable
fun IeoTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}

private val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/** 상태색: 운행(파랑)·대기(노랑)·완료/수익(초록)·경고(빨강). 운행 상황을 알릴 때만 쓴다 */
object IeoStatus {
    val colors: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current
}
