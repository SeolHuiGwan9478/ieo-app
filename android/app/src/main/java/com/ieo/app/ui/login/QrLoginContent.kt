package com.ieo.app.ui.login

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.R
import com.ieo.app.ui.component.IeoLoadingIndicator
import com.ieo.app.ui.component.QrCode
import kotlinx.coroutines.delay

/** QR 유효 시간. TODO: 서버 QR 로그인(기기 인증) API가 생기면 서버가 정한 만료 시간을 쓴다 */
private const val QR_VALID_SECONDS = 300

/** 손으로 옮겨 적기 쉽도록 헷갈리는 글자(0/O, 1/I/L)를 뺀 문자 */
private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

/**
 * QR 로그인: 차량 화면에 QR을 띄우고, 기사님이 휴대폰으로 찍어 로그인을 승인한다.
 * (OAuth Device Authorization 흐름: 차량은 코드를 보여 주고, 휴대폰이 승인하면 차량이 토큰을 받는다)
 *
 * TODO: 지금은 로그인 코드를 앱에서 임시로 만든다. 서버 API가 생기면
 *       1) 서버에서 코드를 받아 QR로 보여 주고 2) 승인될 때까지 서버에 확인해 토큰을 저장한다.
 */
@Composable
fun QrLoginContent(modifier: Modifier = Modifier) {
    var loginCode by rememberSaveable { mutableStateOf(newLoginCode()) }
    var expiresAt by rememberSaveable { mutableLongStateOf(SystemClock.elapsedRealtime() + QR_VALID_SECONDS * 1000L) }

    var secondsLeft by remember(expiresAt) { mutableIntStateOf(secondsUntil(expiresAt)) }
    LaunchedEffect(expiresAt) {
        while (secondsLeft > 0) {
            delay(250)
            secondsLeft = secondsUntil(expiresAt)
        }
    }
    val expired = secondsLeft == 0

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        // QR은 테마와 상관없이 흰 바탕에 검은 칸이어야 잘 찍힌다
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(272.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        ) {
            QrCode(
                content = "ieo://login?code=$loginCode",
                size = 240.dp,
                modifier = Modifier.graphicsLayer { alpha = if (expired) 0.12f else 1f },
            )
            if (expired) {
                TextButton(onClick = {
                    loginCode = newLoginCode()
                    expiresAt = SystemClock.elapsedRealtime() + QR_VALID_SECONDS * 1000L
                }) {
                    Text(stringResource(R.string.qr_refresh), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        Text(text = stringResource(R.string.qr_instruction), fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.qr_code_label) + "  " + loginCode,
            fontSize = 20.sp,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        if (expired) {
            Text(
                text = stringResource(R.string.qr_expired),
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                IeoLoadingIndicator(width = 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.qr_waiting) + " · " + "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 8자리 로그인 코드 (예: KX7P-M4QZ) */
private fun newLoginCode(): String {
    val chars = (1..8).map { CODE_ALPHABET.random() }.joinToString("")
    return chars.take(4) + "-" + chars.drop(4)
}
