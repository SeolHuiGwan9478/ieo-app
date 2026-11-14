package com.ieo.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.ui.theme.CanvasGray
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub

/**
 * 세워 둔 동안에만 여는 화면 (UX-003, UX-004). 차가 움직이면 바로 이 안내로 바뀌고, 세우면 보던 화면으로 돌아온다.
 * 운행 중이 아니어도(빈 차로 달릴 때도) 막는다. 세워 둔 것은 기어 P나 주차 브레이크로 안다
 */
@Composable
fun ParkedOnly(moving: Boolean, title: String, content: @Composable () -> Unit) {
    if (!moving) {
        content()
        return
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().background(CanvasGray).padding(28.dp),
    ) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "기어를 P에 두거나 주차 브레이크를 채우면 다시 보여 드릴게요",
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = TextSub,
            textAlign = TextAlign.Center,
        )
    }
}
