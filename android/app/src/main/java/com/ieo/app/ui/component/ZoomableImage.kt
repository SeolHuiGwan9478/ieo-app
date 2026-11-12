package com.ieo.app.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale

/** 두 번 누르면 이만큼 확대 */
private const val DOUBLE_TAP_SCALE = 2f

/**
 * 두 번 누르면 2배로 보는 사진. 다시 두 번 누르면 원래대로. 확대한 채로 끌어 옮긴다.
 * 차 화면에서는 두 손가락 벌리기가 낯선 분이 많아 두 번 누르기만 둔다. 누른 곳이 손가락 아래에 남게 확대한다
 */
@Composable
fun ZoomableImage(bitmap: ImageBitmap, contentDescription: String, modifier: Modifier = Modifier) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.clipToBounds()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        fun clamp(value: Offset, s: Float): Offset {
            val maxX = width * (s - 1) / 2
            val maxY = height * (s - 1) / 2
            return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
        }
        Image(
            bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap) {
                    detectTapGestures(onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_SCALE
                            offset = clamp((Offset(width / 2, height / 2) - tap) * (DOUBLE_TAP_SCALE - 1), DOUBLE_TAP_SCALE)
                        }
                    })
                }
                .pointerInput(bitmap) {
                    detectDragGestures { change, drag ->
                        if (scale > 1f) {
                            change.consume()
                            offset = clamp(offset + drag, scale)
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
