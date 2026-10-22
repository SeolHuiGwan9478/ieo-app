package com.ieo.app.ui.component

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.ui.theme.CanvasGray
import com.ieo.app.ui.theme.TextMid

/** 차량 화면용 큰 버튼: 높이 76dp 이상, 글자 24sp (NFR-005) */
@Composable
fun IeoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 76.dp),
    ) {
        Text(text = text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

/** 보조 버튼: 흰 바탕 + 테두리. 높이 76dp 이상, 글자 24sp */
@Composable
fun IeoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
        modifier = modifier.fillMaxWidth().heightIn(min = 76.dp),
    ) {
        if (leadingIcon != null) {
            Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(text = text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

/** 회색 버튼: 닫기, 부가 기능처럼 눈에 덜 띄어야 하는 동작. 높이 76dp 이상, 글자 24sp */
@Composable
fun IeoTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CanvasGray, contentColor = TextMid),
        elevation = null,
        modifier = modifier.fillMaxWidth().heightIn(min = 76.dp),
    ) {
        Text(text = text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
