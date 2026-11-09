package com.ieo.app.ui.carlink

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.ui.component.IeoBottomSheet
import com.ieo.app.ui.component.IeoSecondaryButton
import com.ieo.app.ui.component.IeoTonalButton
import com.ieo.app.ui.component.SheetTitle
import com.ieo.app.ui.theme.TextSub

/** 내 차량이 여러 대일 때: 이 트럭이 어느 차량인지 고른다 (VEH-004) */
@Composable
fun BoxScope.CarLinkSheet(state: CarLinkUiState, actions: CarLinkActions) {
    IeoBottomSheet(item = state.choices, onDismiss = actions::dismiss) { choices ->
        SheetTitle("이 트럭은 어느 차량인가요?")
        Spacer(Modifier.height(8.dp))
        Text("한 번 고르면 이 트럭에서 로그인할 때마다 그 차량으로 시작해요", fontSize = 24.sp, color = TextSub)
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            choices.forEach { choice ->
                IeoSecondaryButton(
                    text = "${choice.plateNumber} · ${choice.spec}",
                    onClick = { actions.choose(choice.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IeoTonalButton(text = "나중에", onClick = actions::dismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
