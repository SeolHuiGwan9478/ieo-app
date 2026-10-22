package com.ieo.app.ui.splash

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.R
import com.ieo.app.ui.component.IeoWordmark
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

private val TaglineColor = Color(0xFFA3A3A3)

/**
 * 첫 화면. 검은 바탕에 로고가 나타나고, 그 아래 문구가 차례로 떠오른다.
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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center),
        ) {
            IeoWordmark(
                width = 200.dp,
                color = Color.White,
                modifier = Modifier.graphicsLayer { alpha = logoAlpha.value },
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                color = TaglineColor,
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.White,
                    disabledContentColor = Color.Black,
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
    SplashScreen(isLoggedIn = false, onStart = {}, onAutoLogin = {})
}
