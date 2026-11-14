package com.pindou.app.ui.build

import android.content.res.Configuration
import com.pindou.app.ui.icon.MorphIcons
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.pindou.app.core.build.ConstructionState
import com.pindou.app.ui.AppViewModel
import com.pindou.app.ui.CellCodeDrawer.drawCellCode
import com.pindou.app.ui.GenState
import com.pindou.app.ui.GridRuler
import com.pindou.app.ui.shadcn.Button
import com.pindou.app.ui.shadcn.OutlinedButton
import com.pindou.app.ui.shadcn.ShChip
import com.pindou.app.ui.shadcn.TextButton
import com.pindou.app.ui.theme.ClayColors
import com.pindou.app.ui.theme.Spacing

/**
 * 施工模式：对照图纸逐格点掉已拼的豆子。
 * 支持按行/按列引导、整行整列标记、跳到未完成处、双指缩放平移、色号显示开关。
 */
@Composable
fun ConstructionScreen(vm: AppViewModel) {
    val state = vm.construction ?: return
    val done = (vm.genState as? GenState.Done)?.result
    val pages = remember(done, vm.projectPages) {
        vm.currentPages().ifEmpty { done?.let { listOf(it) } ?: emptyList() }
    }
    val currentPage = if (pages.isEmpty()) {
        null
    } else {
        pages.getOrNull(state.pageIndex.coerceIn(0, pages.size - 1))
    }
    val cells = currentPage?.cells ?: done?.cells
    val argbList = currentPage?.palette?.colors?.map { it.argb }
        ?: done?.palette?.colors?.map { it.argb }
    val codeList = currentPage?.palette?.colors?.map { it.code }
        ?: done?.palette?.colors?.map { it.code }
    val measurer = rememberTextMeasurer()
    val layoutCache = remember { mutableMapOf<Pair<String, Int>, TextLayoutResult>() }
    val gutter = with(LocalDensity.current) { GridRuler.GUTTER_DP.dp.toPx() }
    val rulerCache = remember { mutableMapOf<Pair<String, Int>, TextLayoutResult>() }
    val rulerTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rulerBandColor = MaterialTheme.colorScheme.surface

    var showCodes by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var allDoneDialog by remember { mutableStateOf(false) }
    // state 是普通对象（BooleanArray 原地改写），Compose 察觉不到变化；
    // 所有改动入口都在本屏，改完自增本地 tick 触发重组 + 重绘
    var tick by remember { mutableIntStateOf(0) }

    // 绘制参数计算（缩放平移联动；tap 命中计算复用同一套）
    fun cellMetrics(canvas: Offset): Pair<Float, Offset> {
        val availW = (canvas.x - gutter * 2f).coerceAtLeast(1f)
        val availH = (canvas.y - gutter * 2f).coerceAtLeast(1f)
        val base = minOf(availW / state.width, availH / state.height)
        val cell = base * scale
        val gridW = cell * state.width
        val gridH = cell * state.height
        // 平移钳制：图纸放大后不允许甩出视口
        val maxX = ((gridW - availW) / 2f).coerceAtLeast(0f)
        val maxY = ((gridH - availH) / 2f).coerceAtLeast(0f)
        val clamped = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
        val origin = Offset(
            gutter + (availW - gridW) / 2f + clamped.x,
            gutter + (availH - gridH) / 2f + clamped.y,
        )
        return cell to origin
    }

    val landscape = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE

    // 竖屏贴顶、横屏贴右，控件只做一次，避免两个方向各维护一套逻辑。
    val controlsContent: @Composable (Boolean) -> Unit = { compact ->
        Column(
            Modifier.padding(
                horizontal = if (compact) Spacing.sm else Spacing.md,
                vertical = Spacing.xs,
            ),
        ) {
            tick // 订阅：进度条与文案随格子操作刷新
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.exitConstruction() }) {
                    Icon(MorphIcons.ArrowLeft, contentDescription = "退出施工")
                }
                Text(
                    if (state.pageCount > 1) {
                        "第 ${state.pageIndex + 1}/${state.pageCount} 页 · " +
                            "${state.doneCount}/${state.total}（${(state.progress * 100).toInt()}%）"
                    } else {
                        "施工进度 ${state.doneCount}/${state.total}（${(state.progress * 100).toInt()}%）"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = if (compact) 2 else 1,
                )
                // 开关换成 chip：和其余页面同一套 shadcn 选择件，也避免大号 Switch 挤标题
                ShChip(
                    text = "色号",
                    selected = showCodes,
                    onClick = { showCodes = !showCodes },
                )
            }
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
            )
            if (state.pageCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            if (state.setPage(state.pageIndex - 1)) {
                                tick++
                                vm.saveConstructionProgress()
                            }
                        },
                        enabled = state.pageIndex > 0,
                    ) { Text("‹ 上一页") }
                    Text(
                        "第 ${state.pageIndex + 1} / ${state.pageCount} 页",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(
                        onClick = {
                            if (state.setPage(state.pageIndex + 1)) {
                                tick++
                                vm.saveConstructionProgress()
                            }
                        },
                        enabled = state.pageIndex < state.pageCount - 1,
                    ) { Text("下一页 ›") }
                }
            }
            val byRow = state.orientation == ConstructionState.Orientation.ROW
            // 先选方向再读引导文本：方向 chip 放在「第 N 行」上面，阅读顺序才是先设定后看结果
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 按行/按列二选一，用两个 chip 表达比 Switch 更直观
                ShChip(
                    text = "按行",
                    selected = state.orientation == ConstructionState.Orientation.ROW,
                    onClick = {
                        state.updateOrientation(ConstructionState.Orientation.ROW)
                        tick++
                        vm.saveConstructionProgress()
                    },
                )
                ShChip(
                    text = "按列",
                    selected = state.orientation == ConstructionState.Orientation.COLUMN,
                    onClick = {
                        state.updateOrientation(ConstructionState.Orientation.COLUMN)
                        tick++
                        vm.saveConstructionProgress()
                    },
                )
            }
            if (compact) {
                Text(
                    if (byRow) "第 ${state.cursor + 1} 行" else "第 ${state.cursor + 1} 列",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    OutlinedButton(
                        onClick = {
                            val next = if (byRow) {
                                state.nextIncompleteRow()
                            } else {
                                state.nextIncompleteCol()
                            }
                            if (next >= 0) {
                                state.cursor = next
                                tick++
                            } else {
                                allDoneDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("跳到未完成") }
                    Button(
                        onClick = {
                            if (byRow) {
                                state.setRowDone(state.cursor, true)
                                state.nextIncompleteRow().takeIf { it >= 0 }?.let {
                                    state.cursor = it
                                }
                            } else {
                                state.setColDone(state.cursor, true)
                                state.nextIncompleteCol().takeIf { it >= 0 }?.let {
                                    state.cursor = it
                                }
                            }
                            tick++
                            vm.saveConstructionProgress()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (byRow) "完成本行" else "完成本列") }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (byRow) "第 ${state.cursor + 1} 行" else "第 ${state.cursor + 1} 列",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = {
                        val next = if (byRow) {
                            state.nextIncompleteRow()
                        } else {
                            state.nextIncompleteCol()
                        }
                        if (next >= 0) {
                            state.cursor = next
                            tick++
                        } else {
                            allDoneDialog = true
                        }
                    }) { Text("跳到未完成") }
                    Button(onClick = {
                        if (byRow) {
                            state.setRowDone(state.cursor, true)
                            state.nextIncompleteRow().takeIf { it >= 0 }?.let {
                                state.cursor = it
                            }
                        } else {
                            state.setColDone(state.cursor, true)
                            state.nextIncompleteCol().takeIf { it >= 0 }?.let {
                                state.cursor = it
                            }
                        }
                        tick++
                        vm.saveConstructionProgress()
                    }) { Text(if (byRow) "完成本行" else "完成本列") }
                }
            }
        }
    }

    val canvasContent: @Composable (Modifier) -> Unit = { modifier ->
        // 网格画布：单指点击切换完成状态，双指缩放平移
        Canvas(
            modifier = modifier
                .pointerInput(state.orientation, state.pageIndex) {
                    detectTapGestures { pos ->
                        val canvas = Offset(size.width.toFloat(), size.height.toFloat())
                        val (cell, origin) = cellMetrics(canvas)
                        if (cell <= 0f) return@detectTapGestures
                        val x = ((pos.x - origin.x) / cell).toInt()
                        val y = ((pos.y - origin.y) / cell).toInt()
                        if (x in 0 until state.width && y in 0 until state.height) {
                            state.toggle(y * state.width + x)
                            tick++
                            vm.saveConstructionProgress()
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 20f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                },
        ) {
            tick // 订阅：任一格子操作触发整帧重绘
            val canvas = Offset(size.width, size.height)
            val (cell, origin) = cellMetrics(canvas)
            if (cell <= 0f) return@Canvas

            for (y in 0 until state.height) {
                for (x in 0 until state.width) {
                    val idx = y * state.width + x
                    val left = origin.x + x * cell
                    val top = origin.y + y * cell
                    val colorIdx = cells?.getOrNull(idx) ?: -1
                    val baseColorArgb = if (colorIdx >= 0) argbList?.getOrNull(colorIdx) else null
                    val baseColor = baseColorArgb?.let { Color(it) }
                    val isDone = state.isDone(idx)
                    if (baseColor != null) {
                        // 已拼的格子降透明度压灰，视觉上"褪掉"，未拼的保持醒目
                        drawRect(
                            color = if (isDone) baseColor.copy(alpha = 0.28f) else baseColor,
                            topLeft = Offset(left, top),
                            size = Size(cell, cell),
                        )
                    } else {
                        drawRect(
                            color = if ((x + y) % 2 == 0) Color(0xFFEFEFEA) else Color(0xFFE2E2DC),
                            topLeft = Offset(left, top),
                            size = Size(cell, cell),
                        )
                    }
                    if (showCodes && codeList != null && !isDone && colorIdx >= 0) {
                        drawCellCode(
                            measurer = measurer,
                            code = codeList[colorIdx],
                            left = left,
                            top = top,
                            cell = cell,
                            argb = baseColorArgb ?: 0xFFE2E2DC.toInt(),
                            layoutCache = layoutCache,
                        )
                    }
                }
            }

            // 网格线
            val lineColor = Color.Black.copy(alpha = 0.15f)
            for (x in 0..state.width) {
                val px = origin.x + x * cell
                drawLine(lineColor, Offset(px, origin.y), Offset(px, origin.y + state.height * cell), 1f)
            }
            for (y in 0..state.height) {
                val py = origin.y + y * cell
                drawLine(lineColor, Offset(origin.x, py), Offset(origin.x + state.width * cell, py), 1f)
            }

            // 当前行/列引导高亮框
            val byRow = state.orientation == ConstructionState.Orientation.ROW
            val guideTopLeft = if (byRow) {
                Offset(origin.x, origin.y + state.cursor * cell)
            } else {
                Offset(origin.x + state.cursor * cell, origin.y)
            }
            val guideSize = if (byRow) Size(state.width * cell, cell) else Size(cell, state.height * cell)
            drawRect(
                color = ClayColors.BrandTeal,
                topLeft = guideTopLeft,
                size = guideSize,
                style = Stroke(width = (cell / 12f).coerceIn(1.5f, 4f)),
            )

            with(GridRuler) {
                drawGridRuler(
                    origin = origin,
                    cell = cell,
                    cols = state.width,
                    rows = state.height,
                    gutter = gutter,
                    measurer = measurer,
                    cache = rulerCache,
                    textColor = rulerTextColor,
                    bandColor = rulerBandColor,
                )
            }
        }
    }

    if (landscape) {
        Row(modifier = Modifier.fillMaxSize()) {
            canvasContent(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                controlsContent(true)
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            controlsContent(false)
            canvasContent(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }

    if (allDoneDialog) {
        AlertDialog(
            onDismissRequest = { allDoneDialog = false },
            title = { Text("全部完成！") },
            text = {
                Text(
                    if (state.pageCount > 1) {
                        "第 ${state.pageIndex + 1} 页已经全部拼完。可以切换到下一页继续。"
                    } else {
                        "这幅图纸已经全部拼完，恭喜🎉"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { allDoneDialog = false }) { Text("好的") }
            },
        )
    }
}
