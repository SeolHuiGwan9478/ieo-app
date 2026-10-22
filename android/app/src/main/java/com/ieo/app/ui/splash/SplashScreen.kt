package com.ieo.app.ui.splash

import com.ieo.app.ui.theme.Orange500
import com.ieo.app.ui.theme.Orange700
import com.ieo.app.ui.theme.White
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.R
import com.ieo.app.ui.component.IeoWordmark
import com.ieo.app.ui.theme.IeoTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

/**
 * 첫 화면. 흰 바탕에 로고가 나타나고, 그 아래 문구가 차례로 떠오른다. 색은 테마(라이트)를 따른다.
 * - 로그인 전: 하단에 "시작하기" 버튼이 나타나고, 누르면 로그인 화면으로 간다.
 * - 로그인 상태: 버튼 없이 잠시 뒤 바로 다음 화면으로 넘어간다.
 */
@Composable
fun SplashScreen(
    isLoggedIn: Boolean,
    onStart: () -> Unit,
    onAutoLogin: () -> Unit,
) {
    val logoAlpha = remember { Animatable(0f) }
    val taglineAlpha = remember { Animatable(0f) }
    val taglineOffset = remember { Animatable(24f) }
    val buttonAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
        delay(150)
        val fade = async { taglineAlpha.animateTo(1f, tween(durationMillis = 800)) }
        taglineOffset.animateTo(0f, tween(durationMillis = 800, easing = FastOutSlowInEasing))
        fade.await()
        if (isLoggedIn) {
            delay(900)
            onAutoLogin()
        } else {
            delay(300)
            buttonAlpha.animateTo(1f, tween(durationMillis = 500))
        }
    }

    // 시작 화면은 주황 바탕에 흰 로고. 시스템 시작 화면(values-v31/themes.xml)과 같은 색이라 이어져 보인다
    Box(modifier = Modifier.fillMaxSize().background(Orange500)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center),
        ) {
            IeoWordmark(
                width = 200.dp,
                color = White,
                modifier = Modifier.graphicsLayer { alpha = logoAlpha.value },
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                color = White.copy(alpha = 0.9f),
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = taglineAlpha.value
                    translationY = taglineOffset.value.dp.toPx()
                },
            )
        }

        if (!isLoggedIn) {
            Button(
                onClick = onStart,
                enabled = buttonAlpha.value > 0.5f,
                shape = RoundedCornerShape(16.dp),
                // 나타나는 동안 눌리지 않게 막아 두지만, 색은 활성 상태와 같게 보이도록 한다
                colors = ButtonDefaults.buttonColors(
                    containerColor = White,
                    contentColor = Orange700,
                    disabledContainerColor = White,
                    disabledContentColor = Orange700,
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .heightIn(min = 76.dp)
                    .graphicsLayer { alpha = buttonAlpha.value },
            ) {
                Text(text = stringResource(R.string.splash_start), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun SplashScreenPreview() {
    IeoTheme {
        SplashScreen(isLoggedIn = false, onStart = {}, onAutoLogin = {})
    }
}
