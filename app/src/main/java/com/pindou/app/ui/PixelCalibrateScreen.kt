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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.sp
import com.pindou.app.core.pattern.PixelCalibration
import com.pindou.app.core.pattern.PixelGridInspector
import com.pindou.app.ui.theme.ClayColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 像素画导入的校准页。
 *
 * 这一页的可取之处不是参数多，而是顺序明确：
 * 红线定位 → 改列数 → 改一格大小。这里保留这个顺序，把原先
 * 三列滚轮、键盘图标和识别指标压成一条操作路径，图片区不再有
 * 白色卡片边框，用户看到的是一张图和一张可操作的网格。
 */
@Composable
fun PixelCalibrateScreen(vm: AppViewModel) {
    val bitmap = vm.imagePreview
    val grid = vm.pixelCalibration
    val analysis = vm.pixelGridAnalysis
    val conversion = vm.pixelCalibrationPreview
    val imgW = vm.imageW
    val imgH = vm.imageH

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    var zoom by remember(bitmap) { mutableStateOf(1f) }
    var pan by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var showPreview by remember(bitmap) { mutableStateOf(false) }

    val density = LocalDensity.current
    val sheetPx = if (sheetHeightPx > 0) {
        sheetHeightPx
    } else {
        with(density) { 286.dp.roundToPx() }
    }
    val canvasW = canvasSize.width.toFloat()
    val canvasH = canvasSize.height.toFloat()
    val stageBottom = (canvasH - sheetPx).coerceAtLeast(canvasH * 0.26f)
    val visibleH = (stageBottom - with(density) { 18.dp.toPx() }).coerceAtLeast(1f)
    val viewCenter = Offset(canvasW / 2f, visibleH / 2f)
    // 首次识别会把 32 列补成 35 列；取景要把补出的空白和图片一起算，
    // 否则最右/最下的 5×5 大格会被屏幕边缘裁掉。
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
        title = "像素图识别",
        busy = vm.pixelCalibrationBusy,
        actionLabel = if (vm.pixelCalibrationBusy) "生成中" else "完成",
        actionEnabled = vm.pixelCalibration != null,
        onBack = { vm.handleBack() },
        onAction = { vm.confirmPixelCalibration() },
        guidePrimary = calibrationGuideText(
            prefix = "放大，让网格线贴住像素格；调",
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
                    message = "没有可校准的像素图，请重新导入",
                    onHome = { vm.backToHome() },
                )
            } else {
                val image = remember(bitmap) { bitmap.asImageBitmap() }
                // 预览直接铺「完成」时那张图：色号、空格、背景移除全部一致。
                // 网格对不上（刚拖动、还没算完）就先不显示，避免拿旧结果骗人。
                val previewBitmap = remember(conversion, grid) {
                    val argb = conversion?.previewArgb
                    if (conversion == null || conversion.grid != grid ||
                        argb == null || argb.size != grid.cols * grid.rows
                    ) {
                        null
                    } else {
                        val pixels = IntArray(argb.size) { i ->
                            if (argb[i] == 0) 0xFFFFFFFF.toInt() else argb[i]
                        }
                        Bitmap.createBitmap(
                            pixels,
                            grid.cols,
                            grid.rows,
                            Bitmap.Config.ARGB_8888,
                        )
                    }
                }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        // 画布内容放大后会超出自身的布局边界；不裁掉就会画到
                        // 顶栏上，把「完成」和标题盖住。
                        .clipToBounds()
                        .background(ClayColors.Stage)
                        .calibrationGestures(
                            canvasSize = canvasSize,
                            imgW = imgW,
                            imgH = imgH,
                            baseFit = baseFit,
                            zoom = zoom,
                            pan = pan,
                            viewCenter = viewCenter,
                            visibleH = visibleH,
                            canvasW = canvasW,
                            onPan = { dx, dy ->
                                vm.pixelCalibrationNudge(dx.toDouble(), dy.toDouble())
                            },
                            onZoom = { next, nextPan ->
                                zoom = next
                                pan = nextPan
                            },
                        ),
                ) {
                    val scale = baseFit * zoom
                    val drawW = imgW * scale
                    val drawH = imgH * scale
                    val left = viewCenter.x + pan.x - drawW / 2f
                    val top = viewCenter.y + pan.y - drawH / 2f
                    val right = left + drawW
                    val bottom = top + drawH

                    val cellPxW = grid.cellW.toFloat() * scale
                    val cellPxH = grid.cellH.toFloat() * scale
                    val originX = left + grid.offsetX.toFloat() * scale
                    val originY = top + grid.offsetY.toFloat() * scale
                    val gridW = grid.cols * cellPxW
                    val gridH = grid.rows * cellPxH

                    // 补到 5×5 的格子在原图外：先铺白纸，再盖原图，
                    // 图外的空格和完整大格就不会被深色舞台底吃掉。
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(originX, originY),
                        size = Size(gridW, gridH),
                    )

                    if (showPreview && previewBitmap != null) {
                        // 预览模式：直接铺采样后的格子颜色，空白格用白底，
                        // 用户看到的就是「完成」之后会生成的图纸。
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
                        // 不铺白色底板：图片本身就是画布，网格负责说明边界。
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
                    // 网格按可见范围逐条画，边缘格子被裁掉一部分也仍然显示；
                    // 不会再出现「不足一整块 5×5 就整块消失」。
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

                CalibrationSheet(
                    vm = vm,
                    grid = grid,
                    analysis = analysis,
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
private fun CalibrationSheet(
    vm: AppViewModel,
    grid: PixelCalibration.Grid,
    analysis: PixelGridInspector.Analysis?,
    modifier: Modifier = Modifier,
) {
    CalibrationSheetFrame(modifier) {
        CalibrationHeaderRow(
            sizeLabel = "${grid.cols} × ${grid.rows}",
            badge = when {
                vm.pixelCalibrationBusy || vm.pixelAnalysisRunning -> "识别中"
                vm.pixelRefineRunning -> "对齐中"
                vm.pixelCalibrationApplied -> "已吸附"
                vm.pixelCalibrationMethod == PixelGridInspector.Method.FALLBACK -> "手动网格"
                vm.pixelCalibrationMethod == PixelGridInspector.Method.MANUAL -> "手动"
                else -> "自动识别"
            },
            summary = buildAnalysisSummary(vm, analysis, grid.cols * grid.rows),
            redetectEnabled = !vm.pixelCalibrationBusy,
            onRedetect = { vm.pixelCalibrationRedetect() },
        )

        CalibrationNudgeRow { dx, dy ->
            vm.pixelCalibrationNudge(dx.toDouble(), dy.toDouble())
        }

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
                    vm.pixelCalibrationSetCols(
                        next.coerceIn(
                            PixelCalibration.MIN_COLS,
                            PixelCalibration.MAX_COLS,
                        ),
                    )
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
                    vm.pixelCalibrationSetRows(
                        next.coerceIn(1, PixelCalibration.MAX_COLS),
                    )
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
                    vm.pixelCalibrationSetCellSize(
                        (grid.cellW + delta * calibrationCellStep(grid.cellW))
                            .coerceIn(
                                PixelCalibration.MIN_CELL,
                                PixelCalibration.MAX_CELL,
                            )
                            .toFloat(),
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }

        CalibrationSmartRow(
            checked = vm.pixelCalibrationSmart,
            status = when {
                vm.pixelRefineRunning -> "贴合中"
                !vm.pixelCalibrationSmart -> "已关闭"
                vm.pixelCalibrationApplied -> "已吸附"
                else -> "未吸附"
            },
            onToggle = { vm.pixelCalibrationSetSmart(!vm.pixelCalibrationSmart) },
        )

        CalibrationThresholdRow(
            label = "贴合阈值",
            value = vm.pixelCalibrationThreshold,
            enabled = vm.pixelCalibrationSmart,
            onValueChange = { vm.pixelCalibrationSetThreshold(it) },
            range = 0.05f..0.90f,
        )
    }
}

private fun buildAnalysisSummary(
    vm: AppViewModel,
    analysis: PixelGridInspector.Analysis?,
    totalCells: Int,
): String {
    if (vm.pixelAnalysisRunning) return "· 分析中"
    val conversion = vm.pixelCalibrationPreview
    if (conversion != null && conversion.grid == vm.pixelCalibration) {
        // 粒数/色数按最终色号算，和成品图纸、采购清单一致
        val occupied = conversion.result.cells.filter { it >= 0 }
        return "· ${occupied.size} 粒 · ${occupied.distinct().size} 色"
    }
    return analysis?.let { "· $totalCells 粒 · ${it.colorCount} 色" } ?: ""
}
