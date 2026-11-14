package com.pindou.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pindou.app.core.chart.ChartRecognizer
import com.pindou.app.ui.theme.ClayColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 色号图识别校准页。
 *
 * 与像素图识别共用同一套顶栏、说明、底部面板和步进器：单指拖网格、
 * 双指缩放图片、按 5×5 大格增删行列、格子始终保持正方形。区别只在
 * 采样对象是「格子里的纯色 + 色号文字」。
 */
@Composable
fun ChartCalibrateScreen(vm: AppViewModel) {
    val bitmap = vm.chartBitmap
    val grid = vm.chartGrid
    val sample = vm.chartSample
    val imgW = vm.chartW
    val imgH = vm.chartH

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var viewPan by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var showPreview by remember(bitmap) { mutableStateOf(false) }

    val density = LocalDensity.current
    val sheetPx = if (sheetHeightPx > 0) {
        sheetHeightPx
    } else {
        with(density) { 322.dp.roundToPx() }
    }
    val canvasW = canvasSize.width.toFloat()
    val canvasH = canvasSize.height.toFloat()
    val stageBottom = (canvasH - sheetPx).coerceAtLeast(canvasH * 0.26f)
    val visibleH = (stageBottom - with(density) { 18.dp.toPx() }).coerceAtLeast(1f)
    val viewCenter = Offset(canvasW / 2f, visibleH / 2f)
    // 首次识别会把 43 列补成 45 列。取景要按「图片 + 补出来的空格」
    // 一起算，否则最右边一整块 5×5 会被屏幕边缘裁掉。
    val frameW = if (grid != null) {
        max(imgW.toFloat(), grid.cols * grid.cellW.toFloat())
    } else {
        imgW.toFloat()
    }
    val frameH = if (grid != null) {
        max(imgH.toFloat(), grid.rows * grid.cellH.toFloat())
    } else {
        imgH.toFloat()
    }
    val baseFit = if (frameW > 0f && frameH > 0f && canvasW > 0f && visibleH > 0f) {
        min(
            (canvasW - with(density) { 28.dp.toPx() }) / frameW,
            (visibleH - with(density) { 24.dp.toPx() }) / frameH,
        )
    } else {
        1f
    }

    CalibrationPage(
        title = "色号图识别",
        busy = vm.chartBusy,
        actionLabel = "生成图纸",
        actionEnabled = sample != null && (sample.filledCells > 0),
        onBack = { vm.handleBack() },
        onAction = { vm.chartToPattern() },
        guidePrimary = calibrationGuideText(
            prefix = "放大，让网格线贴住色号格；调",
            accent = "列数、行数或一格大小",
            suffix = "，格子自动保持正方形。",
        ),
        guideSecondary = "双指缩放 · 单指拖网格 · 「看图纸」预览最终效果",
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it },
        ) {
            if (bitmap == null || grid == null || imgW <= 0 || imgH <= 0) {
                CalibrationEmptyState(
                    message = "没有可校准的色号图，请重新导入",
                    onHome = { vm.backToHome() },
                )
            } else {
                val image = remember(bitmap) { bitmap.asImageBitmap() }
                // 预览和最终「生成图纸」采的是同一批格子颜色；空格外用白底，
                // 这样用户能直接看出网格有没有压偏。
                val previewBitmap = remember(sample, grid) {
                    if (sample == null ||
                        sample.cols != grid.cols || sample.rows != grid.rows ||
                        sample.argb.size != grid.cols * grid.rows
                    ) {
                        null
                    } else {
                        val pixels = IntArray(sample.argb.size) { i ->
                            if (sample.empty[i]) 0xFFFFFFFF.toInt() else sample.argb[i]
                        }
                        Bitmap.createBitmap(
                            pixels,
                            sample.cols,
                            sample.rows,
                            Bitmap.Config.ARGB_8888,
                        )
                    }
                }

                Canvas(
                    Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .background(ClayColors.Stage)
                        .calibrationGestures(
                            canvasSize = canvasSize,
                            imgW = imgW,
                            imgH = imgH,
                            baseFit = baseFit,
                            zoom = zoom,
                            pan = viewPan,
                            viewCenter = viewCenter,
                            visibleH = visibleH,
                            canvasW = canvasW,
                            onPan = { dx, dy -> vm.chartMoveBy(dx, dy) },
                            onZoom = { next, nextPan ->
                                zoom = next
                                viewPan = nextPan
                            },
                        ),
                ) {
                    val scale = baseFit * zoom
                    val drawW = imgW * scale
                    val drawH = imgH * scale
                    val left = viewCenter.x + viewPan.x - drawW / 2f
                    val top = viewCenter.y + viewPan.y - drawH / 2f
                    val right = left + drawW
                    val bottom = top + drawH
                    val originX = left + grid.offsetX.toFloat() * scale
                    val originY = top + grid.offsetY.toFloat() * scale
                    val gridW = grid.cols * grid.cellW.toFloat() * scale
                    val gridH = grid.rows * grid.cellH.toFloat() * scale

                    // 和像素图识别一样固定用白纸：色号图的 sample.paper
                    // 可能取到图纸本身的花色，不能拿它当透明区域的底色。
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(originX, originY),
                        size = Size(gridW, gridH),
                    )

                    if (showPreview && previewBitmap != null) {
                        drawImage(
                            image = previewBitmap.asImageBitmap(),
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(previewBitmap.width, previewBitmap.height),
                            dstOffset = IntOffset(
                                originX.roundToInt(),
                                originY.roundToInt(),
                            ),
                            dstSize = IntSize(
                                gridW.roundToInt().coerceAtLeast(1),
                                gridH.roundToInt().coerceAtLeast(1),
                            ),
                            filterQuality = FilterQuality.None,
                        )
                    } else {
                        drawImage(
                            image = image,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(image.width, image.height),
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(
                                drawW.roundToInt().coerceAtLeast(1),
                                drawH.roundToInt().coerceAtLeast(1),
                            ),
                            filterQuality = FilterQuality.None,
                        )
                    }

                    drawCalibrationGrid(
                        imageLeft = left,
                        imageTop = top,
                        imageRight = right,
                        imageBottom = bottom,
                        scale = scale,
                        grid = CalibrationGridGeometry(
                            cols = grid.cols,
                            rows = grid.rows,
                            cellW = grid.cellW,
                            cellH = grid.cellH,
                            offsetX = grid.offsetX,
                            offsetY = grid.offsetY,
                        ),
                        clipBottom = stageBottom,
                        clipToImage = false,
                    )
                }

                if (previewBitmap != null) {
                    CalibrationPreviewToggle(
                        showingPreview = showPreview,
                        onToggle = { showPreview = !showPreview },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 14.dp),
                    )
                }

                ChartCalibrationSheet(
                    vm = vm,
                    grid = grid,
                    sample = sample,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { sheetHeightPx = it.height },
                )
            }
        }
    }
}

@Composable
private fun ChartCalibrationSheet(
    vm: AppViewModel,
    grid: ChartRecognizer.ChartGrid,
    sample: ChartRecognizer.ChartSample?,
    modifier: Modifier = Modifier,
) {
    val smartOn = vm.chartAutoAligned || vm.chartRefineRunning
    CalibrationSheetFrame(modifier) {
        CalibrationHeaderRow(
            sizeLabel = "${grid.cols} × ${grid.rows}",
            badge = when {
                vm.chartBusy -> "采样中"
                vm.chartRefineRunning -> "对齐中"
                vm.chartAutoAligned -> "已吸附"
                vm.chartManual -> "手动网格"
                vm.chartDetected -> "自动识别"
                else -> "兜底网格"
            },
            summary = when {
                vm.chartBusy -> "· 采样中"
                sample != null -> "· ${sample.filledCells} 格 · " +
                    "${sample.cols * sample.rows - sample.filledCells} 空"
                else -> ""
            },
            redetectEnabled = !vm.chartBusy,
            onRedetect = { vm.chartRedetect() },
        )

        CalibrationNudgeRow { dx, dy -> vm.chartNudge(dx, dy) }

        CalibrationDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CalibrationBigStepper(
                step = 2,
                label = "网格列数",
                unit = null,
                value = grid.cols.toString(),
                onStep = { delta ->
                    val next = if (delta > 0) {
                        (grid.cols / 5 + 1) * 5
                    } else {
                        ((grid.cols - 1).coerceAtLeast(0) / 5) * 5
                    }
                    vm.chartSetCols(next.coerceIn(3, 400))
                },
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(58.dp)
                    .background(ClayColors.HairlineSoft),
            )
            CalibrationBigStepper(
                step = 3,
                label = "网格行数",
                unit = null,
                value = grid.rows.toString(),
                onStep = { delta ->
                    val next = if (delta > 0) {
                        (grid.rows / 5 + 1) * 5
                    } else {
                        ((grid.rows - 1).coerceAtLeast(0) / 5) * 5
                    }
                    vm.chartSetRows(next.coerceIn(3, 400))
                },
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(58.dp)
                    .background(ClayColors.HairlineSoft),
            )
            CalibrationBigStepper(
                step = 4,
                label = "一格大小",
                unit = "px",
                value = formatCalibrationCell(grid.cellW),
                onStep = { delta ->
                    vm.chartSetCellSize(
                        (grid.cellW + delta * calibrationCellStep(grid.cellW))
                            .coerceIn(4.0, 512.0)
                            .toFloat(),
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }

        CalibrationSmartRow(
            checked = smartOn,
            status = when {
                vm.chartRefineRunning -> "贴合中"
                !smartOn -> "已关闭"
                vm.chartAutoAligned -> "已吸附"
                else -> "未吸附"
            },
            onToggle = { vm.chartSetSmart(!smartOn) },
        )

        CalibrationThresholdRow(
            label = "空白阈值",
            value = vm.chartThreshold,
            enabled = !vm.chartBusy,
            onValueChange = { vm.chartSetThreshold(it) },
            range = 0.2f..0.95f,
        )
    }
}
