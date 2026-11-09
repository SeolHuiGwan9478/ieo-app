package com.ieo.app.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub
import com.ieo.app.ui.theme.CardSurface

/**
 * 아래에서 올라오는 창. [item]이 있으면 열리고 null이면 닫힌다.
 *
 * ModalBottomSheet는 창을 하나 더 띄워서 Pleos 하단 독에 앱 아이콘이 하나 더 생긴다.
 * 그래서 앱 화면 안에 직접 그린다. 왼쪽 메뉴까지 덮도록 메인 화면 맨 위에 둔다.
 */
@Composable
fun <T : Any> BoxScope.IeoBottomSheet(
    item: T?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    // 닫히는 동안에도 내용을 그대로 보여 주도록 마지막 값을 기억한다
    var shown by remember { mutableStateOf(item) }
    if (item != null) shown = item

    BackHandler(enabled = item != null, onBack = onDismiss)

    AnimatedVisibility(visible = item != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = item != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        shown?.let { value ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    // 시트 안을 눌러도 뒤의 어두운 바탕이 눌리지 않게 막는다
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .verticalScroll(rememberScrollState())
                    .padding(start = 36.dp, top = 40.dp, end = 36.dp, bottom = 32.dp),
            ) {
                content(value)
            }
        }
    }
}

/** 시트 안의 이름 · 값 한 줄 */
@Composable
fun SheetLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, fontSize = 24.sp, color = TextSub, modifier = Modifier.width(180.dp))
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = TextStrong)
    }
}

/** 시트 제목 */
@Composable
fun SheetTitle(text: String) {
    Text(text, fontSize = 32.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, color = TextStrong)
}
