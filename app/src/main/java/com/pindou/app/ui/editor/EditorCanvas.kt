package com.pindou.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import com.pindou.app.core.editor.EditorEngine
import com.pindou.app.core.editor.RunRects
import com.pindou.app.ui.CellCodeDrawer
import com.pindou.app.ui.CellCodeDrawer.drawCellCode
import com.pindou.app.ui.EditorTool
import com.pindou.app.ui.GridRuler
import com.pindou.app.ui.theme.ClayColors
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun EditorCanvas(
    engine: EditorEngine,
    paletteCodes: List<String>,
    paletteArgb: List<Int>,
    transform: CanvasTransform,
    gutter: Float,
    showCodes: Boolean,
    showGrid: Boolean,
    revisionProvider: () -> Int,
    tool: EditorTool,
    brushSize: Int,
    refImage: ImageBitmap?,
    refAlpha: Float,
    highlightCell: Int?,
    onTap: (Int) -> Unit,
    onStrokeCell: (Int, EditorEngine.StrokeSession) -> Unit,
    onLongPress: (Int) -> Unit,
    onLongPressCommit: (Int) -> Unit,
    onStrokeEnd: () -> Unit,
) {
    // pointerInput 的 block 只在 key 变化时重建，会一直持有首次组合的 lambda。
    // 用 rememberUpdatedState 把回调换成可变引用，避免旧闭包写旧状态。
    val tapState by rememberUpdatedState(onTap)
    val strokeCellState by rememberUpdatedState(onStrokeCell)
    val longPressState by rememberUpdatedState(onLongPress)
    val longPressCommitState by rememberUpdatedState(onLongPressCommit)
    val strokeEndState by rememberUpdatedState(onStrokeEnd)

    val measurer = rememberTextMeasurer()
    val rulerTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rulerBandColor = MaterialTheme.colorScheme.surface
    // Canvas 的 draw lambda 不是 @Composable，主题色必须在这里先读出来。
    val pickerAccent = ClayColors.BrandTeal
    val highlightAccent = ClayColors.BrandBlue
    val rulerCache = remember {
        mutableMapOf<Pair<String, Int>, androidx.compose.ui.text.TextLayoutResult>()
    }
    // 换色卡后同色号可能对应不同明暗，旧布局的文字黑白会错；按色卡重建
    val layoutCache = remember(paletteArgb) {
        mutableMapOf<Pair<String, Int>, androidx.compose.ui.text.TextLayoutResult>()
    }
    val lockPath = remember {
        Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, 0.30f)
            lineTo(-0.30f, 0f)
            close()
        }
    }
    val lockColor = Color(0xE6F59E0B)
    val lockOutline = Color(0xE6FFFFFF)

    // 交互中每帧只失效「绘制」、不触发重组：这两个 state 只在 Canvas 的
    // draw lambda 里读，Compose 只会重录显示列表，不会重新执行整个编辑页。
    // gestureActive 覆盖画笔、平移、双指缩放：手指一落下就隐藏色号，
    // 动画期间每帧省掉上千次 text layout + drawText，抬手立刻恢复。
    val liveTick = remember { mutableIntStateOf(0) }
    val gestureActive = remember { mutableStateOf(false) }
    val cellColors = remember(paletteArgb) { Array(paletteArgb.size) { Color(paletteArgb[it]) } }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(tool, engine, brushSize) {
                awaitEachGesture {
                    val canvas = Offset(size.width.toFloat(), size.height.toFloat())
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    val startCell = cellAt(start, canvas, transform, engine, gutter)
                    var last = start
                    var lastCell = startCell
                    var moved = false
                    var longPressed = false
                    var isTransform = false
                    var stroke: EditorEngine.StrokeSession? = null
                    var prevDist = 0f
                    var prevCentroid = Offset.Zero
                    // 笔画中的重绘节流：指针事件比屏幕刷新密，全跟会把 UI 线程塞满
                    var lastBumpNs = 0L

                    // finally 保证取消/异常也能恢复色号，不会留下「滑动后没字」的状态
                    try {
                        // 长按与拖动竞争：超时前没有指针事件 = 长按
                        var pending = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            awaitPointerEvent()
                        }
                        if (pending == null) {
                            longPressed = true
                            longPressState(startCell)
                        }

                        while (true) {
                            val event = pending ?: awaitPointerEvent()
                            pending = null
                            val pressed = event.changes.filter { it.pressed }

                            if (pressed.size >= 2) {
                                if (!isTransform) {
                                    isTransform = true
                                    gestureActive.value = true
                                    stroke?.let {
                                        engine.endStroke(it)
                                        strokeEndState()
                                    }
                                    stroke = null
                                }
                                val a = pressed[0].position
                                val b = pressed[1].position
                                val centroid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                                val dist = hypot(
                                    (a.x - b.x).toDouble(),
                                    (a.y - b.y).toDouble(),
                                ).toFloat()
                                if (prevDist > 0f && dist > 0f) {
                                    zoomAt(
                                        canvas,
                                        engine,
                                        transform,
                                        centroid,
                                        transform.scale * (dist / prevDist),
                                        gutter,
                                    )
                                }
                                if (prevCentroid != Offset.Zero) {
                                    transform.offset += centroid - prevCentroid
                                    clampTransform(canvas, engine, transform, gutter)
                                }
                                prevDist = dist
                                prevCentroid = centroid
                            } else if (pressed.size == 1) {
                                val change = pressed[0]
                                val pos = change.position
                                val totalMove = hypot(
                                    (pos.x - start.x).toDouble(),
                                    (pos.y - start.y).toDouble(),
                                ).toFloat()

                                if (!isTransform && !moved && totalMove > viewConfiguration.touchSlop) {
                                    moved = true
                                    gestureActive.value = true
                                    if (!longPressed &&
                                        (tool == EditorTool.PAINT || tool == EditorTool.ERASE)
                                    ) {
                                        stroke = engine.startStroke()
                                    }
                                }

                                // 未选工具时单指只平移画布，不落笔：新手进图先看，
                                // 手一滑也不会把颜色涂到格子上。
                                if (!isTransform &&
                                    (tool == EditorTool.PAN || tool == EditorTool.NONE) &&
                                    moved
                                ) {
                                    transform.offset += pos - last
                                    clampTransform(canvas, engine, transform, gutter)
                                } else if (!isTransform && moved && !longPressed &&
                                    (tool == EditorTool.PAINT || tool == EditorTool.ERASE)
                                ) {
                                    val session = stroke ?: engine.startStroke().also { stroke = it }
                                    val cellNow = cellPx(canvas, transform, engine, gutter)
                                    val stepPx = max(2f, cellNow / 2f)
                                    val distance = hypot(
                                        (pos.x - last.x).toDouble(),
                                        (pos.y - last.y).toDouble(),
                                    ).toFloat()
                                    val steps = max(1, (distance / stepPx).toInt())
                                    for (i in 1..steps) {
                                        val t = i.toFloat() / steps
                                        val p = Offset(
                                            last.x + (pos.x - last.x) * t,
                                            last.y + (pos.y - last.y) * t,
                                        )
                                        strokeCellState(
                                            cellAt(p, canvas, transform, engine, gutter),
                                            session,
                                        )
                                    }
                                    // 8ms ≈ 120Hz 上限：画到哪儿立刻能看到，又不会每个
                                    // 指针事件都重录一遍整块画布
                                    val now = System.nanoTime()
                                    if (now - lastBumpNs >= 8_000_000L) {
                                        lastBumpNs = now
                                        liveTick.intValue++
                                    }
                                }

                                lastCell = cellAt(pos, canvas, transform, engine, gutter)
                                last = pos
                            }

                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }

                            if (pressed.isEmpty()) {
                                if (longPressed) {
                                    // 长按后抬起：交给上层开菜单，避免弹窗被这次抬起关掉。
                                    // 手指漂移超过 1.5 倍 touchSlop 视为误触，不弹菜单。
                                    val drift = hypot(
                                        (last.x - start.x).toDouble(),
                                        (last.y - start.y).toDouble(),
                                    ).toFloat()
                                    if (drift <= viewConfiguration.touchSlop * 1.5f) {
                                        longPressCommitState(startCell)
                                    }
                                }
                                if (!isTransform && !longPressed &&
                                    tool != EditorTool.PAN && tool != EditorTool.NONE
                                ) {
                                    val totalMove = hypot(
                                        (start.x - last.x).toDouble(),
                                        (start.y - last.y).toDouble(),
                                    )
                                    if (totalMove < viewConfiguration.touchSlop) tapState(lastCell)
                                }
                                stroke?.let {
                                    engine.endStroke(it)
                                    strokeEndState()
                                }
                                break
                            }
                        }
                    } finally {
                        gestureActive.value = false
                        liveTick.intValue++
                    }
                }
            },
    ) {
        // 只读不用的两个 state：draw 阶段读它们，笔画进行中每帧才会自动重绘
        revisionProvider()
        liveTick.intValue
        val interacting = gestureActive.value
        val canvas = Offset(size.width, size.height)
        val cell = cellPx(canvas, transform, engine, gutter)
        if (cell <= 0f) return@Canvas
        val origin = drawOrigin(canvas, transform, engine, gutter)

        val firstX = max(0, (-origin.x / cell).toInt())
        val firstY = max(0, (-origin.y / cell).toInt())
        val lastX = min(engine.width - 1, ((size.width - origin.x) / cell).toInt() + 1)
        val lastY = min(engine.height - 1, ((size.height - origin.y) / cell).toInt() + 1)

        val gridW = engine.width
        val cells = engine.cells

        // 空格底色整块铺一次。逐格绘制会让 60×60 的背景产生 3600 次
        // drawRect，帧时间耗在空区域上。
        drawRect(
            color = EmptyCellColor,
            topLeft = Offset(origin.x + firstX * cell, origin.y + firstY * cell),
            size = Size((lastX - firstX + 1) * cell, (lastY - firstY + 1) * cell),
        )

        // 有色格压成「同色矩形」再画：60×60 从 3600 次 drawRect 降到几百次，
        // 滑动上色每帧都要重录显示列表，这一步是帧时间的大头。
        RunRects.forEach(cells, gridW, firstX, lastX, firstY, lastY) { x0, x1, y0, y1, c ->
            drawRect(
                color = cellColors.getOrElse(c) { Color(0xFFCCCCCC) },
                topLeft = Offset(origin.x + x0 * cell, origin.y + y0 * cell),
                size = Size((x1 - x0 + 1) * cell, (y1 - y0 + 1) * cell),
            )
        }

        // 锁定标记单独一遍：合并矩形之后格子边界已经不可见，标记必须按格画。
        // 没有锁定格时整段跳过，滑动上色就完全不碰这一块。
        val locks = engine.locked
        var hasLock = false
        for (y in firstY..lastY) {
            val row = y * gridW
            for (x in firstX..lastX) {
                if (locks[row + x]) {
                    hasLock = true
                    break
                }
            }
            if (hasLock) break
        }
        if (hasLock) {
            for (y in firstY..lastY) {
                val row = y * gridW
                for (x in firstX..lastX) {
                    if (locks[row + x]) {
                        drawLockMark(lockPath, lockColor, lockOutline, cell, origin, x, y)
                    }
                }
            }
        }

        if (refImage != null && refAlpha > 0f) {
            drawImage(
                image = refImage,
                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                srcSize = androidx.compose.ui.unit.IntSize(refImage.width, refImage.height),
                dstOffset = androidx.compose.ui.unit.IntOffset(origin.x.toInt(), origin.y.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(
                    (engine.width * cell).toInt().coerceAtLeast(1),
                    (engine.height * cell).toInt().coerceAtLeast(1),
                ),
                alpha = refAlpha,
            )
        }

        if (showGrid) {
            val lineColor = Color.Black.copy(alpha = 0.14f)
            for (x in firstX..lastX + 1) {
                val px = origin.x + x * cell
                drawLine(
                    lineColor,
                    Offset(px, origin.y + firstY * cell),
                    Offset(px, origin.y + (lastY + 1) * cell),
                    1f,
                )
            }
            for (y in firstY..lastY + 1) {
                val py = origin.y + y * cell
                drawLine(
                    lineColor,
                    Offset(origin.x + firstX * cell, py),
                    Offset(origin.x + (lastX + 1) * cell, py),
                    1f,
                )
            }
        }

        // 手指按住画布时不画色号：几千个 text layout + drawText 每帧重排会直接掉帧，
        // 抬手后立刻恢复（interacting 只在 draw 阶段读，不会触发整页重组）。
        if (showCodes && !interacting && cell >= CellCodeDrawer.MIN_CELL_PX) {
            for (y in firstY..lastY) {
                for (x in firstX..lastX) {
                    val idx = y * engine.width + x
                    val colorIdx = engine.cells[idx]
                    if (colorIdx < 0) continue
                    val code = paletteCodes.getOrNull(colorIdx) ?: continue
                    val argb = paletteArgb.getOrNull(colorIdx) ?: 0xFFCCCCCC.toInt()
                    drawCellCode(
                        measurer = measurer,
                        code = code,
                        left = origin.x + x * cell,
                        top = origin.y + y * cell,
                        cell = cell,
                        argb = argb,
                        layoutCache = layoutCache,
                    )
                }
            }
        }

        if (tool == EditorTool.PICKER) {
            drawRect(
                color = pickerAccent.copy(alpha = 0.5f),
                topLeft = Offset.Zero,
                size = Size(size.width, 6f),
            )
        }

        // 长按菜单打开时把目标格圈出来，避免对话框挡住后不知道改的是哪一格
        if (highlightCell != null && highlightCell in engine.cells.indices) {
            val hx = highlightCell % engine.width
            val hy = highlightCell / engine.width
            val left = origin.x + hx * cell
            val top = origin.y + hy * cell
            val stroke = (cell * 0.10f).coerceIn(2f, 6f)
            drawRect(
                color = highlightAccent.copy(alpha = 0.95f),
                topLeft = Offset(left - stroke / 2f, top - stroke / 2f),
                size = Size(cell + stroke, cell + stroke),
                style = Stroke(width = stroke),
            )
        }

        // 坐标尺最后画，盖住平移时贴到边缘的网格；刻度与点击共用同一 origin/cell
        with(GridRuler) {
            drawGridRuler(
                origin = origin,
                cell = cell,
                cols = engine.width,
                rows = engine.height,
                gutter = gutter,
                measurer = measurer,
                cache = rulerCache,
                textColor = rulerTextColor,
                bandColor = rulerBandColor,
            )
        }
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLockMark(
    path: Path,
    fill: Color,
    outline: Color,
    cell: Float,
    origin: Offset,
    x: Int,
    y: Int,
) {
    val left = origin.x + x * cell
    val top = origin.y + y * cell
    withTransform({
        translate(left + cell, top)
        scale(cell, cell, pivot = Offset.Zero)
    }) {
        drawPath(path, fill)
        drawPath(path, outline, style = Stroke(width = 0.10f))
    }
}
