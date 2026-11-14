package com.ieo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// 브랜드색: 주황 (design/README.md 색상 팔레트). Postman 주황에서 출발했다
val Orange100 = Color(0xFFFFE2D6)
val Orange500 = Color(0xFFFF6C37)  // 로고, 아이콘, 강조 표시
val Orange600 = Color(0xFFF25A22)  // 주요 버튼 바탕 (흰 굵은 글자 24sp 이상)

// 기본색: 무채색
val Black = Color(0xFF000000)
val Gray950 = Color(0xFF121212)
val Gray900 = Color(0xFF1E1E1E)
val Gray800 = Color(0xFF2C2C2C)
val Gray500 = Color(0xFF6B6B6B)
val Gray400 = Color(0xFFA3A3A3)
val Gray100 = Color(0xFFEDEDED)
val White = Color(0xFFFFFFFF)

/** 라이트·다크에서 값이 달라지는 화면 색. 화면 코드는 아래 이름(CardSurface, TextStrong 등)만 쓴다 */
private data class Palette(
    val card: Color,       // 묶음·창 바탕
    val canvas: Color,     // 화면 바탕, 보조 버튼, 시각 칸
    val textStrong: Color, // 제목·금액
    val textMid: Color,    // 보조 버튼 글자
    val textSub: Color,    // 설명 글자
    val chevron: Color,    // 목록의 › 표시
    val disabled: Color,   // 누를 수 없는 표시
    val orangeTint: Color, // 선택된 메뉴·강조 바탕
    val orangeText: Color, // 바탕 위 주황 글자
    val warningPale: Color,
    val drivingPale: Color,
    val waitingPale: Color,
)

private val LightPalette = Palette(
    card = Color(0xFFFFFFFF),
    canvas = Color(0xFFF2F4F6),
    textStrong = Color(0xFF191F28),
    textMid = Color(0xFF4E5968),
    // 어르신도 차 안 밝은 곳에서 읽히게 본문 회색과 같은 값 (흰 바탕 대비 7.4:1).
    // 예전 #8B95A1(3.0:1), #6B7684(4.6:1)는 흐렸다. 보조 글은 크기·굵기로 구분한다
    textSub = Color(0xFF4E5968),
    chevron = Color(0xFFB0B8C1),
    disabled = Color(0xFFD1D6DB),
    orangeTint = Color(0xFFFFF3EE),
    orangeText = Color(0xFFC2410C),
    warningPale = Color(0xFFFDECEC),
    drivingPale = Color(0xFFE8EFFD),
    waitingPale = Color(0xFFFEF9C3),
)

private val DarkPalette = Palette(
    card = Gray900,
    canvas = Gray950,
    textStrong = Color(0xFFF2F4F6),
    textMid = Color(0xFFC4CBD4),
    // 어두운 바탕에서도 한 단계 밝게 (예전 #9CA6B2)
    textSub = Color(0xFFB0B8C1),
    chevron = Color(0xFF6B7480),
    disabled = Color(0xFF4A4F57),
    orangeTint = Color(0xFF3A2418),
    orangeText = Color(0xFFFF9A73),
    warningPale = Color(0xFF3B1F1F),
    drivingPale = Color(0xFF1B2A44),
    waitingPale = Color(0xFF3A3010),
)

private val LocalPalette = staticCompositionLocalOf { LightPalette }

val CardSurface: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.card
val CanvasGray: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.canvas
val TextStrong: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textStrong
val TextMid: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textMid
val TextSub: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textSub
val ChevronGray: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.chevron
val DisabledGray: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.disabled
val Orange50: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.orangeTint
val Orange700: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.orangeText
val WarningPale: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.warningPale
val DrivingPale: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.drivingPale
val WaitingPale: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.waitingPale

// 키워드 태그(차량·결제·지급): 주황과 잘 어울리는 색 중에서 키워드 성격에 맞게 고른다.
// 상태색과 헷갈리지 않게 모두 옅은 바탕 + 진한 글자로 쓴다
class TagColor(
    private val lightBackground: Color,
    private val lightContent: Color,
    private val darkBackground: Color,
    private val darkContent: Color,
) {
    val background: Color
        @Composable @ReadOnlyComposable get() = if (LocalPalette.current === DarkPalette) darkBackground else lightBackground
    val content: Color
        @Composable @ReadOnlyComposable get() = if (LocalPalette.current === DarkPalette) darkContent else lightContent
}

object IeoTag {
    val Navy = TagColor(Color(0xFFE8ECF4), Color(0xFF1E2A44), Color(0xFF25304A), Color(0xFFC5D2EE))   // 차량: 단단한 장비
    val Teal = TagColor(Color(0xFFD5F5F0), Color(0xFF0F766E), Color(0xFF123F3A), Color(0xFF7FE3D3))   // 선불: 바로 받는 돈
    val Sand = TagColor(Color(0xFFF5EBDD), Color(0xFF7C5A2E), Color(0xFF40331F), Color(0xFFE8CFA6))   // 인수증: 종이 서류
    val Sage = TagColor(Color(0xFFE6EFE2), Color(0xFF3F5F31), Color(0xFF2A3A24), Color(0xFFB9D8A6))   // 지급이 빠른 주선사 (15일 이하)
    val Plum = TagColor(Color(0xFFF3E6F5), Color(0xFF74307F), Color(0xFF3E2544), Color(0xFFE6B8EE))   // 지급이 늦은 주선사 (20일 초과)
    val Gray = TagColor(LightPalette.canvas, LightPalette.textMid, Gray800, DarkPalette.textMid)      // 보통이거나 판단할 근거가 없을 때
    val Mine = TagColor(LightPalette.orangeTint, LightPalette.orangeText, DarkPalette.orangeTint, DarkPalette.orangeText) // 단골 주선사의 지정 오더
}

// 상태색: 운행 상황을 알릴 때만 사용
data class StatusColors(
    val driving: Color,
    val waiting: Color,
    val done: Color,
    val warning: Color,
)

val DarkStatus = StatusColors(
    driving = Color(0xFF60A5FA),
    waiting = Color(0xFFFACC15),
    done = Color(0xFF4ADE80),
    warning = Color(0xFFF87171),
)

val LightStatus = StatusColors(
    driving = Color(0xFF1D4ED8),
    // 대기는 브랜드 주황과 헷갈리지 않도록 노랑 계열로 둔다
    waiting = Color(0xFFA16207),
    done = Color(0xFF15803D),
    warning = Color(0xFFB91C1C),
)

private val DarkColors = darkColorScheme(
    // 흰 글자가 잘 보이도록 라이트와 같은 버튼 주황을 쓴다
    primary = Orange600,
    onPrimary = White,
    secondary = Gray800,
    onSecondary = White,
    background = Gray950,
    onBackground = DarkPalette.textStrong,
    surface = Gray900,
    onSurface = DarkPalette.textStrong,
    surfaceVariant = Gray800,
    onSurfaceVariant = Gray400,
    outline = Gray800,
    error = DarkStatus.warning,
)

private val LightColors = lightColorScheme(
    primary = Orange600,
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

/** 앱 테마. 시스템의 라이트·다크 설정을 따른다 (UX-006) */
@Composable
fun IeoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus,
        LocalPalette provides palette,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = IeoTypography,
        ) {
            // 색을 따로 정하지 않은 글자·아이콘도 바탕에 맞는 색으로 나오게 한다
            CompositionLocalProvider(LocalContentColor provides palette.textStrong, content = content)
        }
    }
}

/** 글자가 24sp라 기본 줄 간격(24sp)이면 두 줄일 때 윗줄과 붙는다 */
private val IeoTypography = Typography().let { it.copy(bodyLarge = it.bodyLarge.copy(lineHeight = 32.sp)) }

private val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/** 상태색: 운행(파랑)·대기(노랑)·완료/수익(초록)·경고(빨강). 운행 상황을 알릴 때만 쓴다. 브랜드 주황은 상태를 뜻하지 않는다 */
object IeoStatus {
    val colors: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current
}
