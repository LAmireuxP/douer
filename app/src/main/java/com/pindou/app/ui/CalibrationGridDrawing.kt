package com.pindou.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** 两种校准网格的公共几何：列行数、格距、相对图片的偏移（源图像素）。 */
internal data class CalibrationGridGeometry(
    val cols: Int,
    val rows: Int,
    val cellW: Double,
    val cellH: Double,
    val offsetX: Double,
    val offsetY: Double,
)

/**
 * 在图片上铺校准网格。
 *
 * 关键点：以前只画「四个角都完整落在可见区」的格子，放大后可见区只剩半格
 * 时整块 5×5 都不画，网格看起来凭空消失。这里改成按可见区求边界线的
 * 起止索引，任何与可见区相交的格线都画，超出图片/画布的部分交给 clipRect。
 * 每一格仍然由横竖两条线围成，边缘只是被裁掉，不会留下悬空断线。
 */
internal fun DrawScope.drawCalibrationGrid(
    imageLeft: Float,
    imageTop: Float,
    imageRight: Float,
    imageBottom: Float,
    scale: Float,
    grid: CalibrationGridGeometry,
    clipBottom: Float,
    clipToImage: Boolean = true,
) {
    if (grid.cols <= 0 || grid.rows <= 0) return
    val cellPxW = grid.cellW.toFloat() * scale
    val cellPxH = grid.cellH.toFloat() * scale
    if (cellPxW <= 0.01f || cellPxH <= 0.01f) return

    val originX = imageLeft + grid.offsetX.toFloat() * scale
    val originY = imageTop + grid.offsetY.toFloat() * scale
    val gridRight = originX + grid.cols * cellPxW
    val gridBottom = originY + grid.rows * cellPxH

    // 像素校准默认只画在图片内；色号图首次识别会把格子补到 5×5 完整，
    // 补出来的纸白区域也要画网格，所以传入 clipToImage=false。
    val clipL = if (clipToImage) max(0f, max(imageLeft, originX)) else max(0f, originX)
    val clipT = if (clipToImage) max(0f, max(imageTop, originY)) else max(0f, originY)
    val clipR = if (clipToImage) min(size.width, min(imageRight, gridRight)) else min(size.width, gridRight)
    val clipB = if (clipToImage) min(clipBottom, min(imageBottom, gridBottom)) else min(clipBottom, gridBottom)
    if (clipR - clipL < 0.5f || clipB - clipT < 0.5f) return

    val firstCol = floor((clipL - originX) / cellPxW).toInt().coerceIn(0, grid.cols)
    val lastCol = ceil((clipR - originX) / cellPxW).toInt().coerceIn(0, grid.cols)
    val firstRow = floor((clipT - originY) / cellPxH).toInt().coerceIn(0, grid.rows)
    val lastRow = ceil((clipB - originY) / cellPxH).toInt().coerceIn(0, grid.rows)

    // 和像素图识别共用同一套灰阶：细线负责数格，每 5 格一条粗线负责
    // 圈出 5×5 大格。透明度比早期版本略高，深色图纸上也能看见。
    val minor = Color(0xFF53606D).copy(alpha = 0.30f)
    val major = Color(0xFF1E2A35).copy(alpha = 0.80f)
    val minorWidth = 0.45.dp.toPx()
    val majorWidth = 1.3.dp.toPx()

    clipRect(clipL, clipT, clipR, clipB) {
        if (cellPxW >= 4f) {
            for (k in firstCol..lastCol) {
                if (k % 5 == 0) continue
                val x = originX + k * cellPxW
                drawLine(minor, Offset(x, clipT), Offset(x, clipB), minorWidth)
            }
        }
        if (cellPxH >= 4f) {
            for (k in firstRow..lastRow) {
                if (k % 5 == 0) continue
                val y = originY + k * cellPxH
                drawLine(minor, Offset(clipL, y), Offset(clipR, y), minorWidth)
            }
        }
        for (k in firstCol..lastCol) {
            if (k % 5 != 0) continue
            val x = originX + k * cellPxW
            drawLine(major, Offset(x, clipT), Offset(x, clipB), majorWidth)
        }
        for (k in firstRow..lastRow) {
            if (k % 5 != 0) continue
            val y = originY + k * cellPxH
            drawLine(major, Offset(clipL, y), Offset(clipR, y), majorWidth)
        }
    }
}
