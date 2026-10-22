package com.ieo.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.ui.component.IeoLoadingIndicator
import com.ieo.app.ui.component.IeoLoadingPanel
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.theme.IeoTheme
import kotlinx.coroutines.delay

/**
 * 디버그 빌드 전용: 공통 컴포넌트를 한 화면에서 확인한다.
 * 실행: adb shell am start --user 10 -n com.ieo.app/.debug.ComponentGalleryActivity
 */
class ComponentGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IeoTheme(darkTheme = false) {
                Gallery()
            }
        }
    }
}

@Composable
private fun Gallery() {
    var panelVisible by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.padding(48.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Text("IeoLoadingIndicator", fontSize = 24.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Sample(background = Color.White) { IeoLoadingIndicator(color = Color.Black) }
                Sample(background = Color.Black) { IeoLoadingIndicator(color = Color.White) }
                Sample(background = Color.White) { IeoLoadingIndicator(width = 48.dp, color = Color.Black) }
            }
            Text("IeoLoadingPanel", fontSize = 24.sp)
            IeoPrimaryButton(
                text = "로딩 패널 3초 보기",
                onClick = { panelVisible = true },
                modifier = Modifier.width(400.dp),
            )
        }
        IeoLoadingPanel(visible = panelVisible, message = "로그인 중")
    }

    if (panelVisible) {
        LaunchedEffect(Unit) {
            delay(3000)
            panelVisible = false
        }
    }
}

@Composable
private fun Sample(background: Color, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.width(240.dp).fillMaxHeight(0.25f).background(background),
    ) {
        content()
    }
}
