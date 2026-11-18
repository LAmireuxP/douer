package com.pindou.app.ui.build

import android.content.res.Configuration
import com.pindou.app.ui.icon.MorphIcons
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
import com.pindou.app.core.build.ConstructionColorGuide
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
import kotlin.math.max
import kotlin.math.min

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

    // 按色号模式：每色剩余颗数、可选颜色条（纯函数，随 tick / 换页重算）
    val colorRemaining = remember(tick, state.pageIndex, cells) {
        if (cells == null) emptyMap()
        else ConstructionColorGuide.remaining(
            cells, state.done, state.width, state.height, state.pageOffset,
        )
    }
    val colorOrder = remember(colorRemaining) {
        ConstructionColorGuide.activeColors(colorRemaining)
    }
    val colorMode = state.orientation == ConstructionState.Orientation.COLOR
    val boardMode = state.orientation == ConstructionState.Orientation.BOARD
    // 画布用的活动色号：cursor 失效时退回第一个有剩余的色，但不在构图期写状态
    val activeColor = if (colorMode) {
        if ((colorRemaining[state.cursor] ?: 0) > 0) state.cursor
        else (colorOrder.firstOrNull() ?: -1)
    } else {
        -1
    }

    fun advanceColorIfNeeded() {
        if (!colorMode || cells == null) return
        val r = ConstructionColorGuide.remaining(
            cells, state.done, state.width, state.height, state.pageOffset,
        )
        if ((r[activeColor] ?: 0) > 0) return
        val next = ConstructionColorGuide.nextIncomplete(
            ConstructionColorGuide.activeColors(r), r, activeColor,
        )
        if (next >= 0) state.cursor = next else allDoneDialog = true
    }

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
            val statusLabel = when (state.orientation) {
                ConstructionState.Orientation.ROW -> "第 ${state.cursor + 1} 行"
                ConstructionState.Orientation.COLUMN -> "第 ${state.cursor + 1} 列"
                ConstructionState.Orientation.COLOR -> {
                    val code = codeList?.getOrNull(activeColor) ?: "—"
                    "色号 $code · 剩 ${colorRemaining[activeColor] ?: 0} 颗"
                }
                ConstructionState.Orientation.BOARD -> {
                    val doneInBoard = state.boardCells(state.cursor.coerceIn(0, state.boardCount - 1))
                        .count { state.isDone(it) }
                    "第 ${state.cursor + 1}/${state.boardCount} 板 · " +
                        "$doneInBoard/${state.boardCells(state.cursor.coerceIn(0, state.boardCount - 1)).size} 格"
                }
            }
            val jumpLabel = when {
                colorMode -> "下一色"
                boardMode -> "下一板"
                else -> "跳到未完成"
            }
            val bulkLabel = when {
                colorMode -> "完成该色"
                boardMode -> "完成本板"
                byRow -> "完成本行"
                else -> "完成本列"
            }

            fun jumpToNext() {
                when (state.orientation) {
                    ConstructionState.Orientation.ROW -> {
                        val next = state.nextIncompleteRow()
                        if (next < 0) {
                            allDoneDialog = true
                            return
                        }
                        state.cursor = next
                    }
                    ConstructionState.Orientation.COLUMN -> {
                        val next = state.nextIncompleteCol()
                        if (next < 0) {
                            allDoneDialog = true
                            return
                        }
                        state.cursor = next
                    }
                    ConstructionState.Orientation.COLOR -> {
                        val next = ConstructionColorGuide.nextIncomplete(
                            colorOrder, colorRemaining, activeColor,
                        )
                        if (next < 0) {
                            allDoneDialog = true
                            return
                        }
                        state.cursor = next
                    }
                    ConstructionState.Orientation.BOARD -> {
                        val next = state.nextIncompleteBoardCircular(state.cursor + 1)
                        if (next < 0) {
                            allDoneDialog = true
                            return
                        }
                        state.cursor = next
                    }
                }
                tick++
                vm.saveConstructionProgress()
            }

            fun completeCurrent() {
                when (state.orientation) {
                    ConstructionState.Orientation.ROW -> {
                        state.setRowDone(state.cursor, true)
                        state.nextIncompleteRow().takeIf { it >= 0 }?.let { state.cursor = it }
                    }
                    ConstructionState.Orientation.COLUMN -> {
                        state.setColDone(state.cursor, true)
                        state.nextIncompleteCol().takeIf { it >= 0 }?.let { state.cursor = it }
                    }
                    ConstructionState.Orientation.COLOR -> {
                        val pageSize = state.width * state.height
                        if (cells != null && activeColor >= 0) {
                            for (i in 0 until pageSize) {
                                if (cells.getOrNull(i) == activeColor && !state.isDone(i)) {
                                    state.setDone(i, true)
                                }
                            }
                        }
                        advanceColorIfNeeded()
                    }
                    ConstructionState.Orientation.BOARD -> {
                        val board = state.cursor.coerceIn(0, state.boardCount - 1)
                        state.setBoardDone(board, true)
                        // 与「下一板」同一套回绕顺序：末尾板完成后回到前面漏拼的板
                        state.nextIncompleteBoardCircular(board + 1)
                            ?.let { state.cursor = it }
                    }
                }
                tick++
                vm.saveConstructionProgress()
            }
            // 先选方向再读引导文本：方向 chip 放在「第 N 行」上面，阅读顺序才是先设定后看结果
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 三种引导方式；按色号是拼豆绘的招牌玩法，单独一颗 chip
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
                ShChip(
                    text = "按色号",
                    selected = colorMode,
                    onClick = {
                        state.updateOrientation(ConstructionState.Orientation.COLOR)
                        state.cursor = colorOrder.firstOrNull() ?: 0
                        tick++
                        vm.saveConstructionProgress()
                    },
                )
                ShChip(
                    text = "按底板",
                    selected = boardMode,
                    onClick = {
                        state.updateOrientation(ConstructionState.Orientation.BOARD)
                        state.cursor = state.nextIncompleteBoard().takeIf { it >= 0 } ?: 0
                        tick++
                        vm.saveConstructionProgress()
                    },
                )
            }

            // 色号选择条：色块 + 色号 + 剩余颗数，点一下切换当前要拼的色
            if (colorMode && colorOrder.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    colorOrder.forEach { c ->
                        ColorCountChip(
                            argb = argbList?.getOrNull(c) ?: 0xFFCCCCCC.toInt(),
                            code = codeList?.getOrNull(c) ?: "—",
                            count = colorRemaining[c] ?: 0,
                            selected = c == activeColor,
                            onClick = {
                                state.cursor = c
                                tick++
                                vm.saveConstructionProgress()
                            },
                        )
                    }
                }
            }
            if (compact) {
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    OutlinedButton(onClick = { jumpToNext() }, modifier = Modifier.weight(1f)) {
                        Text(jumpLabel)
                    }
                    Button(onClick = { completeCurrent() }, modifier = Modifier.weight(1f)) {
                        Text(bulkLabel)
                    }
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
                        statusLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { jumpToNext() }) { Text(jumpLabel) }
                    Button(onClick = { completeCurrent() }) { Text(bulkLabel) }
                }
            }
        }
    }

    // Canvas 的 draw lambda 不是 @Composable：主题色在组合阶段先读出来
    val guideAccent = ClayColors.BrandTeal
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
                            advanceColorIfNeeded()
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
                    val isSelectedColor = colorMode && colorIdx == activeColor
                    if (baseColor != null) {
                        // 已拼的格子降透明度压灰；按色号模式下非当前色整体压暗，
                        // 只留选中色可辨认，用户视线不会被别的颜色带走
                        val alpha = when {
                            !colorMode -> if (isDone) 0.28f else 1f
                            isSelectedColor -> if (isDone) 0.25f else 1f
                            else -> if (isDone) 0.06f else 0.12f
                        }
                        drawRect(
                            color = baseColor.copy(alpha = alpha),
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
                    val codeVisible = !colorMode || isSelectedColor
                    if (showCodes && codeVisible && codeList != null && !isDone && colorIdx >= 0) {
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

            // 引导高亮：按行/按列画一条带，按色号则给该色剩余格描边
            if (colorMode) {
                if (activeColor >= 0 && cells != null) {
                    val w = (cell / 12f).coerceIn(1.2f, 2.6f)
                    // 只遍历可见格：200×200 图纸双指缩放时，全量描边会拖掉帧
                    val x0 = max(0, ((-origin.x) / cell).toInt())
                    val x1 = min(state.width - 1, ((size.width - origin.x) / cell).toInt())
                    val y0 = max(0, ((-origin.y) / cell).toInt())
                    val y1 = min(state.height - 1, ((size.height - origin.y) / cell).toInt())
                    for (y in y0..y1) {
                        for (x in x0..x1) {
                            val i = y * state.width + x
                            if (cells.getOrNull(i) != activeColor || state.isDone(i)) continue
                            drawRect(
                            color = guideAccent,
                                topLeft = Offset(origin.x + x * cell, origin.y + y * cell),
                                size = Size(cell, cell),
                                style = Stroke(width = w),
                            )
                        }
                    }
                }
            } else if (boardMode) {
                val board = state.cursor.coerceIn(0, state.boardCount - 1)
                val boardsX = (state.width + state.boardSide - 1) / state.boardSide
                val x0 = (board % boardsX) * state.boardSide
                val y0 = (board / boardsX) * state.boardSide
                val x1 = minOf(state.width, x0 + state.boardSide)
                val y1 = minOf(state.height, y0 + state.boardSide)
                drawRect(
                    color = guideAccent,
                    topLeft = Offset(origin.x + x0 * cell, origin.y + y0 * cell),
                    size = Size((x1 - x0) * cell, (y1 - y0) * cell),
                    style = Stroke(width = (cell / 6f).coerceIn(2f, 6f)),
                )
            } else {
                val byRow = state.orientation == ConstructionState.Orientation.ROW
                val guideTopLeft = if (byRow) {
                    Offset(origin.x, origin.y + state.cursor * cell)
                } else {
                    Offset(origin.x + state.cursor * cell, origin.y)
                }
                val guideSize = if (byRow) Size(state.width * cell, cell) else Size(cell, state.height * cell)
                drawRect(
                    color = guideAccent,
                    topLeft = guideTopLeft,
                    size = guideSize,
                    style = Stroke(width = (cell / 12f).coerceIn(1.5f, 4f)),
                )
            }

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

/** 色号选择条上的一颗：色块 + 色号 + 剩余颗数，选中态描品牌色边。 */
@Composable
private fun ColorCountChip(
    argb: Int,
    code: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) ClayColors.BrandTeal.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surface,
            )
            .border(
                1.dp,
                if (selected) ClayColors.BrandTeal else MaterialTheme.colorScheme.outline,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(1.dp, Color.Black.copy(alpha = 0.18f), CircleShape),
        )
        Text(
            "$code $count",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
