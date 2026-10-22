package com.ieo.app.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** 문자열을 QR 코드로 그린다. 검은 칸과 흰 바탕으로만 그려서 테마와 상관없이 잘 찍힌다. */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier, size: Dp = 240.dp) {
    val bitmap = remember(content) { createQrBitmap(content) }
    Image(
        bitmap = bitmap,
        contentDescription = "QR 코드",
        // 확대할 때 칸 경계가 흐려지지 않도록 보간하지 않는다
        filterQuality = FilterQuality.None,
        modifier = modifier.size(size),
    )
}

private fun createQrBitmap(content: String, sizePx: Int = 512): ImageBitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(sizePx * sizePx) { index ->
        if (matrix[index % sizePx, index / sizePx]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
}
