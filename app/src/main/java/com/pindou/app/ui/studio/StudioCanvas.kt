package com.pindou.app.ui.studio

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pindou.app.core.editor.ShapeTool
import com.pindou.app.core.editor.SymmetryMode
import com.pindou.app.core.pattern.ColorLabelLayout
import com.pindou.app.core.studio.StudioEngine
import com.pindou.app.ui.theme.ClayColors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 画布变换。字段是 Compose state，捏合/平移才会触发重绘。 */
class StudioCanvasState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var canvasSize by mutableStateOf(IntSize.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }
}

private class CodeLabelCache {
    var revision: Int = -1
    var layer: Int = -1
    var mask: BooleanArray? = null
}

private const val GUTTER = 18f

private fun availSize(canvas: Offset): Offset = Offset(
    (canvas.x - GUTTER * 2f).coerceAtLeast(1f),
    (canvas.y - GUTTER * 2f).coerceAtLeast(1f),
)

private fun baseOrigin(canvas: Offset, engine: StudioEngine): Offset {
    val avail = availSize(canvas)
    val cellBase = min(avail.x / engine.width, avail.y / engine.height)
    return Offset(
        GUTTER + (avail.x - cellBase * engine.width) / 2f,
        GUTTER + (avail.y - cellBase * engine.height) / 2f,
    )
}

private fun drawOrigin(canvas: Offset, state: StudioCanvasState, engine: StudioEngine): Offset =
    baseOrigin(canvas, engine) + state.offset

private fun cellPx(canvas: Offset, state: StudioCanvasState, engine: StudioEngine): Float {
    val avail = availSize(canvas)
    return min(avail.x / engine.width, avail.y / engine.height) * state.scale
}

private fun cellAt(
    view: Offset,
    canvas: Offset,
    state: StudioCanvasState,
    engine: StudioEngine,
): Int {
    val cell = cellPx(canvas, state, engine)
    if (cell <= 0f) return -1
    val origin = drawOrigin(canvas, state, engine)
    val x = ((view.x - origin.x) / cell).toInt()
    val y = ((view.y - origin.y) / cell).toInt()
    if (x < 0 || y < 0 || x >= engine.width || y >= engine.height) return -1
    return y * engine.width + x
}

fun studioClamp(canvas: IntSize, engine: StudioEngine, state: StudioCanvasState) {
    if (canvas.width <= 0 || canvas.height <= 0) return
    val c = Offset(canvas.width.toFloat(), canvas.height.toFloat())
    val cell = cellPx(c, state, engine)
    if (cell <= 0f) return
    val avail = availSize(c)
    val base = baseOrigin(c, engine)
    val origin = drawOrigin(c, state, engine)
    val contentW = cell * engine.width
    val contentH = cell * engine.height
    val x = if (contentW <= avail.x) {
        GUTTER + (avail.x - contentW) / 2f
    } else {
        origin.x.coerceIn(c.x - GUTTER - contentW, GUTTER)
    }
    val y = if (contentH <= avail.y) {
        GUTTER + (avail.y - contentH) / 2f
    } else {
        origin.y.coerceIn(c.y - GUTTER - contentH, GUTTER)
    }
    state.offset = Offset(x - base.x, y - base.y)
}

fun studioZoomAt(
    canvas: IntSize,
    engine: StudioEngine,
    state: StudioCanvasState,
    focus: Offset,
    targetScale: Float,
) {
    if (canvas.width <= 0 || canvas.height <= 0) return
    val c = Offset(canvas.width.toFloat(), canvas.height.toFloat())
    val oldScale = state.scale
    val newScale = targetScale.coerceIn(1f, 24f)
    if (abs(newScale - oldScale) < 0.001f) return
    val origin = drawOrigin(c, state, engine)
    val k = newScale / oldScale
    val newOrigin = Offset(
        focus.x - (focus.x - origin.x) * k,
        focus.y - (focus.y - origin.y) * k,
    )
    state.scale = newScale
    state.offset = newOrigin - baseOrigin(c, engine)
    studioClamp(canvas, engine, state)
}

/**
 * 画像素主画布。
 *
 * 合成结果先写成 1 格 = 1 像素的 Bitmap，再按 FilterQuality.None 放大绘制；
 * 这样图层透明度/混合模式只算一次，屏幕上不是每帧几千个 drawRect。
 */
@Composable
fun StudioCanvas(
    session: StudioSession,
    state: StudioCanvasState,
    onTextAt: (Int) -> Unit,
    onPickColor: (Int) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val engine = session.engine
    val palette = session.palette

    val currentBitmap = remember(engine, engine.width, engine.height) {
        Bitmap.createBitmap(engine.width, engine.height, Bitmap.Config.ARGB_8888)
    }
    val currentImage = remember(currentBitmap) { currentBitmap.asImageBitmap() }
    val onionBitmap = remember(engine, engine.width, engine.height) {
        Bitmap.createBitmap(engine.width, engine.height, Bitmap.Config.ARGB_8888)
    }
    val onionImage = remember(onionBitmap) { onionBitmap.asImageBitmap() }
    val lastRender = remember(engine, engine.width, engine.height) { intArrayOf(-1, -1) }
    // 原图引用不是 Compose state；revision 变化时重新取一次，否则导入完成后
    // 画布可能仍持有导入前的 null 引用。
    val reference = remember(session.revision, engine.frameIndex) { engine.reference }
    val referenceBitmap = remember(reference) {
        reference?.let { ref ->
            Bitmap.createBitmap(ref.width, ref.height, Bitmap.Config.ARGB_8888).apply {
                setPixels(ref.argb, 0, ref.width, 0, 0, ref.width, ref.height)
            }
        }
    }
    val referenceImage = remember(referenceBitmap) { referenceBitmap?.asImageBitmap() }

    val checkerPaint = remember {
        val tile = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(256) { i ->
            val x = i % 16
            val y = i / 16
            if ((x / 8 + y / 8) % 2 == 0) 0xFFF2F5FA.toInt() else 0xFFFFFFFF.toInt()
        }
        tile.setPixels(pixels, 0, 16, 0, 0, 16, 16)
        Paint().apply {
            shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }
    val codePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }
    }
    val codeLabelCache = remember { CodeLabelCache() }
    val rulerPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8A96A3.toInt()
            textAlign = Paint.Align.CENTER
        }
    }
    val selectionPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x332F80ED
        }
    }

    // 手势中的临时预览
    var dragStart by remember { mutableStateOf<Int?>(null) }
    var dragEnd by remember { mutableStateOf<Int?>(null) }
    var lassoPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var gradientFrom by remember { mutableStateOf<Offset?>(null) }
    var gradientTo by remember { mutableStateOf<Offset?>(null) }
    var movePreview by remember { mutableStateOf(Offset.Zero) }
    var moving by remember { mutableStateOf(false) }
    // 手势进行中不画色号：83×57 有几千个格，逐帧 text layout 会拖慢落笔；
    // 抬手后 finally 恢复，画布上的色号马上回来。
    val interacting = remember { mutableStateOf(false) }

    val textAtState by rememberUpdatedState(onTextAt)
    val pickState by rememberUpdatedState(onPickColor)
    val messageState by rememberUpdatedState(onMessage)

    Canvas(
        modifier = modifier
            .onSizeChanged { state.canvasSize = it }
            .pointerInput(session.tool, engine, session.brushSize, session.pixelPerfect, session.symmetry) {
                awaitEachGesture {
                    val canvas = Offset(size.width.toFloat(), size.height.toFloat())
                    val down = awaitFirstDown(requireUnconsumed = false)
                    interacting.value = true
                    val start = down.position
                    val startCell = cellAt(start, canvas, state, engine)
                    var last = start
                    var lastCell = startCell
                    var moved = false
                    var transforming = false
                    var stroke: StudioEngine.Stroke? = null
                    var prevDist = 0f
                    var prevCentroid = Offset.Zero
                    var lastBump = 0L
                    var moveDx = 0
                    var moveDy = 0
                    val lasso = ArrayList<Offset>()
                    dragStart = startCell
                    dragEnd = startCell
                    if (session.tool == StudioTool.GRADIENT) gradientFrom = start
                    moving = false
                    movePreview = Offset.Zero

                    fun bumpLive() {
                        val now = System.nanoTime()
                        if (now - lastBump >= 16_000_000L) {
                            lastBump = now
                            session.touch()
                        }
                    }

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                if (!transforming) {
                                    transforming = true
                                    stroke?.let { engine.endStroke(it); session.touch() }
                                    stroke = null
                                }
                                val a = pressed[0].position
                                val b = pressed[1].position
                                val centroid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                                val dist = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
                                if (prevDist > 0f && dist > 0f) {
                                    studioZoomAt(
                                        state.canvasSize, engine, state, centroid,
                                        state.scale * (dist / prevDist),
                                    )
                                }
                                if (prevCentroid != Offset.Zero) {
                                    state.offset += centroid - prevCentroid
                                    studioClamp(state.canvasSize, engine, state)
                                }
                                prevDist = dist
                                prevCentroid = centroid
                            } else if (pressed.size == 1) {
                                val pos = pressed[0].position
                                val total = hypot(
                                    (pos.x - start.x).toDouble(),
                                    (pos.y - start.y).toDouble(),
                                ).toFloat()
                                if (!moved && total > viewConfiguration.touchSlop) {
                                    moved = true
                                    if (session.tool == StudioTool.BRUSH || session.tool == StudioTool.ERASER) {
                                        stroke = engine.beginStroke()
                                    }
                                    if (session.tool == StudioTool.MOVE) moving = true
                                }
                                when (session.tool) {
                                    StudioTool.PAN -> if (moved) {
                                        state.offset += pos - last
                                        studioClamp(state.canvasSize, engine, state)
                                    }
                                    StudioTool.BRUSH, StudioTool.ERASER -> {
                                        val s = stroke
                                        if (s != null && lastCell >= 0) {
                                            val cellNow = cellAt(pos, canvas, state, engine)
                                            if (cellNow >= 0) {
                                                val color = if (session.tool == StudioTool.ERASER) -1 else session.color
                                                s.lineTo(
                                                    lastCell, cellNow, color,
                                                    session.brushSize, session.circleBrush,
                                                    session.pixelPerfect,
                                                )
                                                if (session.symmetry != SymmetryMode.NONE) {
                                                    val froms = session.symmetry.centers(lastCell, engine.width, engine.height)
                                                    val tos = session.symmetry.centers(cellNow, engine.width, engine.height)
                                                    for (i in 1 until min(froms.size, tos.size)) {
                                                        s.lineTo(
                                                            froms[i], tos[i], color,
                                                            session.brushSize, session.circleBrush,
                                                            session.pixelPerfect,
                                                        )
                                                    }
                                                }
                                                bumpLive()
                                            }
                                        }
                                    }
                                    StudioTool.SELECT_RECT -> dragEnd = cellAt(pos, canvas, state, engine)
                                    StudioTool.SELECT_LASSO -> {
                                        lastCell = cellAt(pos, canvas, state, engine)
                                        if (lastCell >= 0) {
                                            val p = Offset(lastCell % engine.width + 0.5f, lastCell / engine.width + 0.5f)
                                            if (lasso.isEmpty() || hypot(
                                                    (lasso.last().x - p.x).toDouble(),
                                                    (lasso.last().y - p.y).toDouble(),
                                                ) >= 0.8
                                            ) lasso.add(p)
                                        }
                                        lassoPoints = lasso
                                    }
                                    StudioTool.GRADIENT -> {
                                        gradientTo = pos
                                        val idx = cellAt(pos, canvas, state, engine)
                                        dragEnd = idx
                                    }
                                    StudioTool.MOVE -> {
                                        val idx = cellAt(pos, canvas, state, engine)
                                        if (idx >= 0 && startCell >= 0) {
                                            moveDx = idx % engine.width - startCell % engine.width
                                            moveDy = idx / engine.width - startCell / engine.width
                                            val cell = cellPx(canvas, state, engine)
                                            movePreview = Offset(moveDx * cell, moveDy * cell)
                                        }
                                    }
                                    else -> Unit
                                }
                                last = pos
                                lastCell = cellAt(pos, canvas, state, engine)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                            if (pressed.isEmpty()) {
                                val endCell = cellAt(last, canvas, state, engine)
                                when (session.tool) {
                                    StudioTool.BRUSH, StudioTool.ERASER -> {
                                        if (moved) {
                                            stroke?.let { engine.endStroke(it); session.touch() }
                                            stroke = null
                                        } else if (endCell >= 0) {
                                            // 单击也要落笔：画一个笔刷点（含对称）
                                            val tap = engine.beginStroke()
                                            val color = if (session.tool == StudioTool.ERASER) -1 else session.color
                                            tap.setBrush(endCell, color, session.brushSize, session.circleBrush)
                                            for (c in session.symmetry.centers(endCell, engine.width, engine.height)) {
                                                if (c != endCell) {
                                                    tap.setBrush(c, color, session.brushSize, session.circleBrush)
                                                }
                                            }
                                            engine.endStroke(tap)
                                            session.touch()
                                        }
                                    }
                                    StudioTool.FILL -> if (!moved && endCell >= 0) {
                                        if (engine.floodFill(endCell, session.color)) session.touch()
                                    }
                                    StudioTool.PICKER -> if (!moved && endCell >= 0) {
                                        val idx = engine.activeLayer.cells.getOrNull(endCell) ?: -1
                                        if (idx >= 0) {
                                            pickState(idx)
                                            messageState("取色：${palette.colors.getOrNull(idx)?.code ?: idx}")
                                        }
                                    }
                                    StudioTool.LINE, StudioTool.RECT, StudioTool.ELLIPSE -> {
                                        if (startCell >= 0 && endCell >= 0 && moved) {
                                            val kind = when (session.tool) {
                                                StudioTool.LINE -> ShapeTool.LINE
                                                StudioTool.RECT -> ShapeTool.RECT
                                                else -> ShapeTool.ELLIPSE
                                            }
                                            val ok = engine.drawShape(
                                                kind, startCell, endCell, session.color,
                                                filled = session.shapeFilled && kind != ShapeTool.LINE,
                                                symmetry = session.symmetry,
                                                brushSize = session.brushSize,
                                                circleBrush = session.circleBrush,
                                            )
                                            if (ok) session.touch()
                                        }
                                    }
                                    StudioTool.POLYGON -> if (!moved && endCell >= 0) {
                                        val pts = session.pendingPoints
                                        if (pts.size >= 6) {
                                            val fx = pts[0]
                                            val fy = pts[1]
                                            val nearStart = abs(endCell % engine.width - fx) <= 1 &&
                                                abs(endCell / engine.width - fy) <= 1
                                            if (nearStart) {
                                                if (engine.drawPolygon(
                                                        pts.toIntArray(), session.color,
                                                        session.shapeFilled,
                                                        session.brushSize, session.circleBrush,
                                                    )
                                                ) {
                                                    session.pendingPoints = emptyList()
                                                    session.touch()
                                                }
                                            } else {
                                                session.pendingPoints = pts + intArrayOf(
                                                    endCell % engine.width, endCell / engine.width,
                                                ).toList()
                                                session.touch()
                                            }
                                        } else {
                                            session.pendingPoints = pts + intArrayOf(
                                                endCell % engine.width, endCell / engine.width,
                                            ).toList()
                                            session.touch()
                                        }
                                    }
                                    StudioTool.CURVE -> if (!moved && endCell >= 0) {
                                        val pts = session.pendingPoints + intArrayOf(
                                            endCell % engine.width, endCell / engine.width,
                                        ).toList()
                                        if (pts.size >= 6) {
                                            if (engine.drawCurve(
                                                    pts.take(6).toIntArray(), session.color,
                                                    session.brushSize, session.circleBrush,
                                                )
                                            ) session.touch()
                                            session.pendingPoints = emptyList()
                                        } else {
                                            session.pendingPoints = pts
                                            session.touch()
                                        }
                                    }
                                    StudioTool.GRADIENT -> if (moved && startCell >= 0 && endCell >= 0) {
                                        if (engine.gradient(
                                                startCell % engine.width, startCell / engine.width,
                                                endCell % engine.width, endCell / engine.width,
                                                session.color, session.secondaryColor,
                                                session.gradientRadial, session.gradientDithered,
                                            )
                                        ) session.touch()
                                    }
                                    StudioTool.DITHER -> if (!moved) {
                                        if (engine.ditherFill(
                                                session.color, session.secondaryColor,
                                                session.ditherDensity,
                                            )
                                        ) session.touch()
                                    }
                                    StudioTool.SELECT_RECT -> {
                                        if (startCell >= 0 && endCell >= 0) {
                                            if (engine.selectRect(
                                                    startCell % engine.width, startCell / engine.width,
                                                    endCell % engine.width, endCell / engine.width,
                                                )
                                            ) session.touch()
                                        }
                                    }
                                    StudioTool.SELECT_LASSO -> {
                                        if (lasso.size >= 3) {
                                            val xs = IntArray(lasso.size) { lasso[it].x.toInt() }
                                            val ys = IntArray(lasso.size) { lasso[it].y.toInt() }
                                            if (engine.selectLasso(xs, ys)) session.touch()
                                        }
                                    }
                                    StudioTool.MAGIC_WAND -> if (!moved && endCell >= 0) {
                                        if (engine.selectMagicWand(
                                                endCell, tolerance = 8, useComposite = true,
                                            )
                                        ) session.touch()
                                    }
                                    StudioTool.MOVE -> {
                                        if (moved && (moveDx != 0 || moveDy != 0)) {
                                            if (engine.moveSelection(moveDx, moveDy)) session.touch()
                                        }
                                        moving = false
                                        movePreview = Offset.Zero
                                    }
                                    StudioTool.TEXT -> if (!moved && endCell >= 0) textAtState(endCell)
                                    StudioTool.PAN -> Unit
                                }
                                dragStart = null
                                dragEnd = null
                                lassoPoints = emptyList()
                                gradientFrom = null
                                gradientTo = null
                                break
                            }
                        }
                    } finally {
                        if (stroke != null) {
                            engine.endStroke(stroke)
                            stroke = null
                        }
                        dragStart = null
                        dragEnd = null
                        lassoPoints = emptyList()
                        gradientFrom = null
                        gradientTo = null
                        moving = false
                        movePreview = Offset.Zero
                        interacting.value = false
                        session.touch()
                    }
                }
            },
    ) {
        val revision = session.revision
        val canvas = Offset(size.width, size.height)
        val cell = cellPx(canvas, state, engine)
        if (cell <= 0f) return@Canvas
        val origin = drawOrigin(canvas, state, engine)

        drawRect(ClayColors.Stage)

        // 棋盘格只铺画布范围，用 BitmapShader 平铺，不按格循环
        drawIntoCanvas { c ->
            val rect = android.graphics.RectF(
                origin.x, origin.y,
                origin.x + engine.width * cell,
                origin.y + engine.height * cell,
            )
            if (session.transparentBackground) {
                c.nativeCanvas.drawRect(rect, checkerPaint)
            } else {
                c.nativeCanvas.drawRect(rect, Paint().apply { color = 0xFFFFFFFF.toInt() })
            }
        }

        if (referenceImage != null && reference != null && reference.visible) {
            drawImage(
                image = referenceImage,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(reference.width, reference.height),
                dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
                dstSize = IntSize(
                    (engine.width * cell).roundToInt().coerceAtLeast(1),
                    (engine.height * cell).roundToInt().coerceAtLeast(1),
                ),
                filterQuality = FilterQuality.Medium,
            )
        }

        if (lastRender[0] != revision || lastRender[1] != engine.frameIndex) {
            val pixels = engine.renderArgb(engine.frameIndex)
            currentBitmap.setPixels(pixels, 0, engine.width, 0, 0, engine.width, engine.height)
            if (session.onionSkin && engine.frameIndex > 0) {
                val prev = engine.renderArgb(engine.frameIndex - 1)
                onionBitmap.setPixels(prev, 0, engine.width, 0, 0, engine.width, engine.height)
            }
            lastRender[0] = revision
            lastRender[1] = engine.frameIndex
        }

        if (session.onionSkin && engine.frameIndex > 0) {
            drawImage(
                image = onionImage,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(engine.width, engine.height),
                dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
                dstSize = IntSize(
                    (engine.width * cell).roundToInt().coerceAtLeast(1),
                    (engine.height * cell).roundToInt().coerceAtLeast(1),
                ),
                alpha = 0.25f,
                filterQuality = FilterQuality.None,
            )
        }

        drawImage(
            image = currentImage,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(engine.width, engine.height),
            dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
            dstSize = IntSize(
                (engine.width * cell).roundToInt().coerceAtLeast(1),
                (engine.height * cell).roundToInt().coerceAtLeast(1),
            ),
            filterQuality = FilterQuality.None,
        )

        if (session.showGrid && cell >= 2.5f) {
            val line = Color.Black.copy(alpha = 0.13f)
            for (x in 0..engine.width) {
                drawLine(
                    line,
                    Offset(origin.x + x * cell, origin.y),
                    Offset(origin.x + x * cell, origin.y + engine.height * cell),
                    1f,
                )
            }
            for (y in 0..engine.height) {
                drawLine(
                    line,
                    Offset(origin.x, origin.y + y * cell),
                    Offset(origin.x + engine.width * cell, origin.y + y * cell),
                    1f,
                )
            }
        }

        if (session.showCenterLines) {
            val c = Color(0xFF2F80ED).copy(alpha = 0.55f)
            val cx = origin.x + engine.width * cell / 2f
            val cy = origin.y + engine.height * cell / 2f
            drawLine(c, Offset(cx, origin.y), Offset(cx, origin.y + engine.height * cell), 1.5f)
            drawLine(c, Offset(origin.x, cy), Offset(origin.x + engine.width * cell, cy), 1.5f)
        }

        // 对称轴
        when (session.symmetry) {
            SymmetryMode.NONE -> Unit
            SymmetryMode.HORIZONTAL -> {
                val cx = origin.x + engine.width * cell / 2f
                drawLine(Color(0xFFE05B8A), Offset(cx, origin.y), Offset(cx, origin.y + engine.height * cell), 2f)
            }
            SymmetryMode.VERTICAL -> {
                val cy = origin.y + engine.height * cell / 2f
                drawLine(Color(0xFFE05B8A), Offset(origin.x, cy), Offset(origin.x + engine.width * cell, cy), 2f)
            }
            SymmetryMode.BOTH -> {
                val cx = origin.x + engine.width * cell / 2f
                val cy = origin.y + engine.height * cell / 2f
                drawLine(Color(0xFFE05B8A), Offset(cx, origin.y), Offset(cx, origin.y + engine.height * cell), 1.5f)
                drawLine(Color(0xFFE05B8A), Offset(origin.x, cy), Offset(origin.x + engine.width * cell, cy), 1.5f)
            }
        }

        // 色号
        if (session.showCodes && !interacting.value && cell >= 15f) {
            val paint = palette.colors
            codePaint.textSize = max(6f, cell * 0.32f)
            // 只画当前视口内的格：200×200 放大后如果每次都遍历 4 万格，
            // 静止画面也会被几千次 drawText 拖慢。
            val firstX = max(0, (-origin.x / cell).toInt())
            val lastX = min(engine.width - 1, ((size.width - origin.x) / cell).toInt() + 1)
            val firstY = max(0, (-origin.y / cell).toInt())
            val lastY = min(engine.height - 1, ((size.height - origin.y) / cell).toInt() + 1)
            val cells = engine.activeLayer.cells
            if (codeLabelCache.revision != revision || codeLabelCache.layer != engine.activeLayerIndex) {
                codeLabelCache.mask = ColorLabelLayout.labelMask(cells, engine.width, engine.height)
                codeLabelCache.revision = revision
                codeLabelCache.layer = engine.activeLayerIndex
            }
            val labels = codeLabelCache.mask
            drawIntoCanvas { c ->
                for (y in firstY..lastY) {
                    for (x in firstX..lastX) {
                        val idx = cells[y * engine.width + x]
                        if (idx < 0) continue
                        if (labels == null || !labels[y * engine.width + x]) continue
                        val entry = paint.getOrNull(idx) ?: continue
                        val argb = entry.argb
                        val lum = ((argb shr 16 and 0xFF) * 299 + (argb shr 8 and 0xFF) * 587 + (argb and 0xFF) * 114) / 1000
                        codePaint.color = if (lum > 150) 0xCC111111.toInt() else 0xEEFFFFFF.toInt()
                        c.nativeCanvas.drawText(
                            entry.code,
                            origin.x + (x + 0.5f) * cell,
                            origin.y + (y + 0.62f) * cell,
                            codePaint,
                        )
                    }
                }
            }
        }

        // 选区：按行合并成 run，避免每格一次 drawRect
        val selection = engine.selection
        if (selection != null) {
            val shift = if (moving) movePreview else Offset.Zero
            for (y in 0 until engine.height) {
                var x = 0
                while (x < engine.width) {
                    if (!selection[y * engine.width + x]) {
                        x++
                        continue
                    }
                    val startX = x
                    while (x < engine.width && selection[y * engine.width + x]) x++
                    val left = origin.x + startX * cell + shift.x
                    val top = origin.y + y * cell + shift.y
                    drawRect(
                        Color(0x332F80ED),
                        topLeft = Offset(left, top),
                        size = Size((x - startX) * cell, cell),
                    )
                    drawLine(Color(0xFF2F80ED), Offset(left, top), Offset(left + (x - startX) * cell, top), 1f)
                    drawLine(Color(0xFF2F80ED), Offset(left, top + cell), Offset(left + (x - startX) * cell, top + cell), 1f)
                    drawLine(Color(0xFF2F80ED), Offset(left, top), Offset(left, top + cell), 1f)
                    drawLine(
                        Color(0xFF2F80ED),
                        Offset(left + (x - startX) * cell, top),
                        Offset(left + (x - startX) * cell, top + cell),
                        1f,
                    )
                }
            }
        }

        // 拖拽中的矩形选区/形状预览
        val ds = dragStart
        val de = dragEnd
        val previewRect = session.tool == StudioTool.SELECT_RECT ||
            session.tool == StudioTool.RECT ||
            session.tool == StudioTool.ELLIPSE ||
            session.tool == StudioTool.LINE
        if (previewRect && ds != null && de != null && ds >= 0 && de >= 0) {
            val x0 = min(ds % engine.width, de % engine.width)
            val x1 = max(ds % engine.width, de % engine.width)
            val y0 = min(ds / engine.width, de / engine.width)
            val y1 = max(ds / engine.width, de / engine.width)
            if (session.tool == StudioTool.LINE) {
                drawLine(
                    Color(0xFF2F80ED),
                    Offset(origin.x + (ds % engine.width + 0.5f) * cell, origin.y + (ds / engine.width + 0.5f) * cell),
                    Offset(origin.x + (de % engine.width + 0.5f) * cell, origin.y + (de / engine.width + 0.5f) * cell),
                    2f,
                )
            } else {
                drawRect(
                    Color(0x222F80ED),
                    topLeft = Offset(origin.x + x0 * cell, origin.y + y0 * cell),
                    size = Size((x1 - x0 + 1) * cell, (y1 - y0 + 1) * cell),
                )
                drawRect(
                    Color(0xFF2F80ED),
                    topLeft = Offset(origin.x + x0 * cell, origin.y + y0 * cell),
                    size = Size((x1 - x0 + 1) * cell, (y1 - y0 + 1) * cell),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2f),
                )
            }
        }

        // 套索轨迹
        if (lassoPoints.isNotEmpty()) {
            val path = androidx.compose.ui.graphics.Path()
            for ((i, p) in lassoPoints.withIndex()) {
                val px = origin.x + p.x * cell
                val py = origin.y + p.y * cell
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, Color(0xFF2F80ED), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }

        // 渐变拖拽线
        val gf = gradientFrom
        val gt = gradientTo
        if (gf != null && gt != null) {
            drawLine(Color(0xFF2F80ED), gf, gt, 2f)
        }

        // 多边形/曲线待落点
        if (session.pendingPoints.size >= 2) {
            val pts = session.pendingPoints
            for (i in pts.indices step 2) {
                val x = origin.x + (pts[i] + 0.5f) * cell
                val y = origin.y + (pts[i + 1] + 0.5f) * cell
                drawCircle(Color(0xFF2F80ED), radius = 4f, center = Offset(x, y))
            }
            for (i in 2 until pts.size step 2) {
                drawLine(
                    Color(0xAA2F80ED),
                    Offset(origin.x + (pts[i - 2] + 0.5f) * cell, origin.y + (pts[i - 1] + 0.5f) * cell),
                    Offset(origin.x + (pts[i] + 0.5f) * cell, origin.y + (pts[i + 1] + 0.5f) * cell),
                    1.5f,
                )
            }
        }

        // 四周坐标尺：列号从左到右，行号从下到上；首格标 1，之后每 5 格标 5/10/15…
        if (cell >= 6f) {
            rulerPaint.textSize = 10.dp.toPx()
            val pad = rulerPaint.textSize + 2f
            val topY = (origin.y - 4f).coerceAtLeast(pad)
            val bottomY = (origin.y + engine.height * cell + pad).coerceAtMost(size.height - 2f)
            val leftX = (origin.x - 4f).coerceAtLeast(pad)
            val rightX = (origin.x + engine.width * cell + 4f).coerceAtMost(size.width - 2f)
            drawIntoCanvas { c ->
                rulerPaint.textAlign = Paint.Align.CENTER
                for (x in 0 until engine.width) {
                    if (x != 0 && (x + 1) % 5 != 0) continue
                    val cx = origin.x + (x + 0.5f) * cell
                    c.nativeCanvas.drawText((x + 1).toString(), cx, topY, rulerPaint)
                    c.nativeCanvas.drawText((x + 1).toString(), cx, bottomY, rulerPaint)
                }
                for (y in 0 until engine.height) {
                    val rowNumber = engine.height - y
                    if (rowNumber != 1 && rowNumber % 5 != 0) continue
                    val cy = origin.y + (y + 0.62f) * cell
                    rulerPaint.textAlign = Paint.Align.RIGHT
                    c.nativeCanvas.drawText(rowNumber.toString(), leftX, cy, rulerPaint)
                    rulerPaint.textAlign = Paint.Align.LEFT
                    c.nativeCanvas.drawText(rowNumber.toString(), rightX, cy, rulerPaint)
                }
            }
        }
    }
}
