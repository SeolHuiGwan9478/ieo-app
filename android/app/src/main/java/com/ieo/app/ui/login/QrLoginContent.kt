package com.ieo.app.ui.login

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
import androidx.compose.runtime.remember
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

/**
 * QR 로그인: 차량 화면에 QR을 띄우고, 기사님이 휴대폰으로 찍어 로그인을 승인한다.
 * (OAuth Device Authorization 흐름: 차량은 코드를 보여 주고, 휴대폰이 승인하면 차량이 토큰을 받는다)
 * 상태는 [QrLoginViewModel]이 정하고, 이 화면은 그리기만 한다.
 */
@Composable
fun QrLoginContent(
    state: QrLoginUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val waiting = state as? QrLoginUiState.Waiting
    var secondsLeft by remember(waiting?.expiresAt) { mutableIntStateOf(secondsUntil(waiting?.expiresAt ?: 0L)) }
    LaunchedEffect(waiting?.expiresAt) {
        while (waiting != null && secondsLeft > 0) {
            delay(250)
            secondsLeft = secondsUntil(waiting.expiresAt)
        }
    }
    // 서버가 만료를 알려 주기 전이라도 시간이 다 되면 만료로 보여 준다
    val ended = (state !is QrLoginUiState.Waiting && state !is QrLoginUiState.Loading) || (waiting != null && secondsLeft == 0)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        // QR은 테마와 상관없이 흰 바탕에 검은 칸이어야 잘 찍힌다
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(272.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        ) {
            when {
                waiting != null -> QrCode(
                    content = waiting.qrContent,
                    size = 240.dp,
                    modifier = Modifier.graphicsLayer { alpha = if (ended) 0.12f else 1f },
                )
                state is QrLoginUiState.Loading -> IeoLoadingIndicator(width = 56.dp)
            }
            if (ended) {
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.qr_refresh), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        Text(text = stringResource(R.string.qr_instruction), fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.qr_code_label) + "  " + (waiting?.code ?: "----"),
            fontSize = 20.sp,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        val problem = when {
            state is QrLoginUiState.Rejected -> stringResource(R.string.qr_rejected)
            state is QrLoginUiState.Failed -> state.message
            ended -> stringResource(R.string.qr_expired)
            else -> null
        }
        if (problem != null) {
            Text(text = problem, fontSize = 20.sp, color = MaterialTheme.colorScheme.error)
        } else if (waiting != null) {
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
