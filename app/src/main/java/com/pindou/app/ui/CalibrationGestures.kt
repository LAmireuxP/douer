package com.pindou.app.ui

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

/**
 * 两个校准页共用的视图手势：
 * 单指只移动网格（图片固定），双指围绕手势中心缩放，缩放后夹在
 * 画布范围内。
 *
 * 手势协程只按画布尺寸/图片尺寸/可见高度重建；zoom/pan/baseFit 用
 * rememberUpdatedState 读取最新值，拖动过程中不会因为状态变化重启。
 */
@Composable
internal fun Modifier.calibrationGestures(
    canvasSize: IntSize,
    imgW: Int,
    imgH: Int,
    baseFit: Float,
    zoom: Float,
    pan: Offset,
    viewCenter: Offset,
    visibleH: Float,
    canvasW: Float,
    onPan: (dx: Float, dy: Float) -> Unit,
    onZoom: (zoom: Float, pan: Offset) -> Unit,
): Modifier {
    val density = LocalDensity.current
    val margin = with(density) { 36.dp.toPx() }
    val currentBaseFit by rememberUpdatedState(baseFit)
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)
    val currentOnPan by rememberUpdatedState(onPan)
    val currentOnZoom by rememberUpdatedState(onZoom)

    return pointerInput(canvasSize, imgW, imgH, visibleH, viewCenter) {
        if (imgW <= 0 || imgH <= 0 || canvasSize.width <= 0) return@pointerInput
        detectTransformGestures { centroid, panChange, zoomChange, _ ->
            val oldZoom = currentZoom
            if (abs(zoomChange - 1f) <= 0.001f) {
                val scale = currentBaseFit * oldZoom
                if (scale > 0f) {
                    currentOnPan(panChange.x / scale, panChange.y / scale)
                }
            } else {
                val next = (oldZoom * zoomChange).coerceIn(0.8f, 12f)
                val q = centroid - viewCenter
                var nextPan = q - (q - currentPan) * (next / oldZoom) + panChange
                val drawW = imgW * currentBaseFit * next
                val drawH = imgH * currentBaseFit * next
                val boundX = max(canvasW, drawW) / 2f + margin
                val boundY = max(visibleH, drawH) / 2f + margin
                nextPan = Offset(
                    nextPan.x.coerceIn(-boundX, boundX),
                    nextPan.y.coerceIn(-boundY, boundY),
                )
                currentOnZoom(next, nextPan)
            }
        }
    }
}
