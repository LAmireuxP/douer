@file:OptIn(ExperimentalLayoutApi::class)
package com.pindou.app.ui.editor
import com.pindou.app.ui.icon.MorphIcons
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import com.pindou.app.core.editor.BrushStamp
import com.pindou.app.core.editor.ColorReplaceOps
import com.pindou.app.core.editor.EditorEngine
import com.pindou.app.core.editor.RunRects
import com.pindou.app.core.editor.SymmetryMode
import com.pindou.app.ui.AppViewModel
import com.pindou.app.ui.CellCodeDrawer
import com.pindou.app.ui.CellCodeDrawer.drawCellCode
import com.pindou.app.ui.EditorTool
import com.pindou.app.ui.GridRuler
import com.pindou.app.ui.shadcn.ShSearchField
import com.pindou.app.ui.theme.ClayColors
import com.pindou.app.ui.theme.Spacing
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AppViewModel) {
    val engine = vm.editor ?: return
    val palette = vm.selectedPalette ?: return
    if (palette.colors.isEmpty()) return

    val safeColorIndex = vm.editorColor.coerceIn(0, palette.colors.lastIndex)
    val currentEntry = palette.colors[safeColorIndex]
    // 色号/ARGB 只跟色卡有关，别在每次重组（每条笔画都会重组一次）时 map 一遍
    val paletteCodes = remember(palette) { palette.colors.map { it.code } }
    val paletteArgb = remember(palette) { palette.colors.map { it.argb } }
    // 色卡弹层关掉后仍然保留文本测量结果：291 色第二次打开、来回滑都不再
    // 重新排版色号。放在 if (showPaletteSheet) 里面会随弹层一起销毁。
    val swatchMeasurer = rememberTextMeasurer(cacheSize = 128)
    val swatchTextStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurface,
    )
    val swatchTextCache = remember(swatchTextStyle) {
        mutableMapOf<String, TextLayoutResult>()
    }

    var showPaletteSheet by remember { mutableStateOf(false) }
    var showManageSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }
    var showCropDialog by remember { mutableStateOf(false) }
    var showReplaceSheet by remember { mutableStateOf(false) }
    var replaceSource by remember { mutableIntStateOf(0) }
    var colorOpReport by remember { mutableStateOf<List<ColorReplaceOps.Move>>(emptyList()) }
    var longPressCell by remember { mutableStateOf<Int?>(null) }
    // 长按时只记下格子并高亮，等手指抬起再弹菜单：
    // 弹窗若在按下的中途创建，抬起事件会落到新窗口上被当成「点外部」直接关掉。
    var cellSheetOpen by remember { mutableStateOf(false) }
    var paletteQuery by remember { mutableStateOf("") }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val transform = remember { CanvasTransform() }
    val haptic = LocalHapticFeedback.current
    // 坐标带宽度：画布几何、点击命中、缩放都以它为左上边界
    val gutter = with(LocalDensity.current) { GridRuler.GUTTER_DP.dp.toPx() }

    LaunchedEffect(vm.editorTool) {
        if (vm.editorTool != EditorTool.SHAPE) vm.editorShapeStart = null
    }

    LaunchedEffect(engine.pageIndex, engine.width, engine.height) {
        transform.reset()
    }

    val landscape = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE

    // 顶栏：竖屏常规密度，横屏压到 40dp 左右，把高度留给画布。
    val headerContent: @Composable (Boolean) -> Unit = { compact ->
        // 撤销裁剪会同时改 engine.width/height，但它们不是 Compose 状态；
        // 这里订阅 revision，尺寸文字才能和画布在同一帧刷新。
        val gridLabel = remember(vm.editorRevision) {
            "${engine.width}×${engine.height}"
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Spacing.xs,
                    vertical = if (compact) 1.dp else Spacing.xxs,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { vm.exitEditor() },
                modifier = if (compact) Modifier.size(36.dp) else Modifier,
            ) {
                Icon(
                    MorphIcons.ArrowLeft,
                    contentDescription = "返回",
                    modifier = if (compact) Modifier.size(20.dp) else Modifier,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp),
            ) {
                Text(
                    if (vm.pixelModuleActive) "画像素 · $gridLabel" else gridLabel,
                    style = if (compact) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (vm.pixelModuleActive) {
                        "像素画布 · ${palette.brandLabel} · 第 ${engine.pageIndex + 1}/${engine.pageCount} 页"
                    } else {
                        "${palette.brandLabel} · 第 ${engine.pageIndex + 1}/${engine.pageCount} 页"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                UndoRedoButtons(vm, engine)
                IconButton(
                    onClick = { showMenu = true },
                    modifier = if (compact) Modifier.size(36.dp) else Modifier,
                ) {
                    Icon(
                        MorphIcons.MoreVertical,
                        contentDescription = "更多",
                        modifier = if (compact) Modifier.size(20.dp) else Modifier,
                    )
                }
                Button(
                    onClick = { vm.finishEditor() },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(
                        horizontal = if (compact) 12.dp else 16.dp,
                        vertical = 0.dp,
                    ),
                    modifier = Modifier.height(if (compact) 30.dp else 36.dp),
                ) {
                    Text("完成", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    // 画布本体竖屏、横屏共用，只由调用方决定尺寸和位置。
    val canvasContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .onSizeChanged { canvasSize = it },
        ) {
            EditorCanvas(
                engine = engine,
                paletteCodes = paletteCodes,
                paletteArgb = paletteArgb,
                transform = transform,
                gutter = gutter,
                showCodes = vm.editorShowCodes,
                showGrid = vm.editorShowGrid,
                // 传 lambda 而不是 Int：Int 参数会让整个编辑页在每条笔画结束时
                // 重组一次（色卡条、最近颜色、工具条全部重算）；lambda 只在 draw
                // 阶段读，revision 变化只失效画布。
                revisionProvider = { vm.editorRevision },
                tool = vm.editorTool,
                brushSize = vm.editorBrushSize,
                refImage = vm.refBitmap?.takeIf { vm.showRefOverlay }?.asImageBitmap(),
                refAlpha = vm.refOpacity,
                highlightCell = vm.editorShapeStart ?: longPressCell,
                onTap = { cell -> handleTap(vm, engine, cell, vm.editorBrushSize) },
                onStrokeCell = { cell, session ->
                    handleStrokeCell(vm, engine, cell, session, vm.editorBrushSize)
                },
                onLongPress = { cell ->
                    if (cell >= 0) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        longPressCell = cell
                    }
                },
                onLongPressCommit = { cell ->
                    // 手指抬起才真正打开菜单，此时没有进行中的手势
                    if (cell >= 0 && longPressCell == cell) cellSheetOpen = true
                },
                onStrokeEnd = { vm.notifyEditorChanged() },
            )

            if (vm.refBitmap != null) {
                CanvasPill(
                    icon = MorphIcons.Eye,
                    text = if (vm.showRefOverlay) {
                        "原图 ${(vm.refOpacity * 100).roundToInt()}%"
                    } else {
                        "原图"
                    },
                    selected = vm.showRefOverlay,
                    onClick = { vm.toggleRefOverlay() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }

            // transform 的读取全部收进 ZoomControls：捏合/拖动时只重组这一行，
            // 避免整页重算。
            ZoomControls(
                canvasSize = canvasSize,
                engine = engine,
                transform = transform,
                gutter = gutter,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            )

            // 改色结果条：贴在画布顶上，不挡画布手势，6 秒自动收
            if (colorOpReport.isNotEmpty()) {
                ColorOpReportBar(
                    moves = colorOpReport,
                    palette = palette,
                    onUndo = {
                        vm.undoEditor()
                        colorOpReport = emptyList()
                    },
                    onDismiss = { colorOpReport = emptyList() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp),
                )
            }
        }
    }

    // 右侧控制栏和底部控制区共用一份，横屏只把外边距收紧。
    val controlsContent: @Composable (Boolean) -> Unit = { compact ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 0.dp),
        ) {
        // 页码行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { vm.switchEditorPage(engine.pageIndex - 1) },
                    enabled = engine.pageIndex > 0,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        MorphIcons.ChevronLeft,
                        contentDescription = "上一页",
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    "第 ${engine.pageIndex + 1}/${engine.pageCount} 页",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { vm.switchEditorPage(engine.pageIndex + 1) },
                    enabled = engine.pageIndex < engine.pageCount - 1,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        MorphIcons.ChevronRight,
                        contentDescription = "下一页",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            TextButton(
                onClick = { vm.addEditorPage(false) },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text("+ 新页", style = MaterialTheme.typography.labelMedium)
            }
        }

        // 工具条：当前色 + 画笔/油漆桶/橡皮/吸管/移动
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorCubeButton(
                code = currentEntry.code,
                argb = currentEntry.argb,
                onClick = { showPaletteSheet = true },
            )
            EditorToolButton(
                MorphIcons.Brush,
                "画笔",
                vm.editorTool == EditorTool.PAINT,
                modifier = Modifier.weight(1f),
            ) { vm.editorTool = EditorTool.PAINT }
            EditorToolButton(
                MorphIcons.PaintBucket,
                "填充",
                vm.editorTool == EditorTool.FILL,
                modifier = Modifier.weight(1f),
            ) { vm.editorTool = EditorTool.FILL }
            EditorToolButton(
                MorphIcons.Eraser,
                "橡皮",
                vm.editorTool == EditorTool.ERASE,
                modifier = Modifier.weight(1f),
            ) { vm.editorTool = EditorTool.ERASE }
            EditorToolButton(
                MorphIcons.Pipette,
                "吸管",
                vm.editorTool == EditorTool.PICKER,
                modifier = Modifier.weight(1f),
            ) { vm.editorTool = EditorTool.PICKER }
            EditorToolButton(
                MorphIcons.Hand,
                "移动",
                vm.editorTool == EditorTool.PAN,
                modifier = Modifier.weight(1f),
            ) { vm.editorTool = EditorTool.PAN }
            EditorToolButton(
                MorphIcons.Grid,
                "形状",
                vm.editorTool == EditorTool.SHAPE,
                modifier = Modifier.weight(1f),
            ) {
                vm.editorTool = EditorTool.SHAPE
                vm.editorShapeStart = null
            }
        }

        // 笔刷尺寸 + 显示开关。横屏控制栏只有 248dp，拆两行避免挤出屏幕。
        if (compact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "笔刷",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BrushStamp.sizes.forEach { size ->
                    CompactChip(
                        text = "${size}×${size}",
                        selected = vm.editorBrushSize == size,
                        onClick = { vm.editorBrushSize = size },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "显示",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompactChip(
                    text = "色号",
                    selected = vm.editorShowCodes,
                    onClick = { vm.editorShowCodes = !vm.editorShowCodes },
                )
                CompactChip(
                    text = "网格",
                    selected = vm.editorShowGrid,
                    onClick = { vm.editorShowGrid = !vm.editorShowGrid },
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "笔刷",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BrushStamp.sizes.forEach { size ->
                    CompactChip(
                        text = "${size}×${size}",
                        selected = vm.editorBrushSize == size,
                        onClick = { vm.editorBrushSize = size },
                    )
                }
                Spacer(Modifier.weight(1f))
                CompactChip(
                    text = "色号",
                    selected = vm.editorShowCodes,
                    onClick = { vm.editorShowCodes = !vm.editorShowCodes },
                )
                CompactChip(
                    text = "网格",
                    selected = vm.editorShowGrid,
                    onClick = { vm.editorShowGrid = !vm.editorShowGrid },
                )
            }
        }

        // 对称绘制：画像素里的镜像轴，画笔/橡皮落笔时同步补镜像格
        if (vm.editorTool == EditorTool.PAINT || vm.editorTool == EditorTool.ERASE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "对称",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SymmetryMode.entries.forEach { mode ->
                    CompactChip(
                        text = mode.label,
                        selected = vm.editorSymmetry == mode,
                        onClick = { vm.editorSymmetry = mode },
                    )
                }
            }
        }

        if (vm.editorTool == EditorTool.SHAPE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "形状",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                com.pindou.app.core.editor.ShapeTool.entries.forEach { shape ->
                    CompactChip(
                        text = shape.label,
                        selected = vm.editorShape == shape,
                        onClick = {
                            vm.editorShape = shape
                            vm.editorShapeStart = null
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                CompactChip(
                    text = "填充",
                    selected = vm.editorShapeFilled,
                    onClick = { vm.editorShapeFilled = !vm.editorShapeFilled },
                )
            }
        }

        // 区域擦除是橡皮的子模式，选别的工具时不占版面
        if (vm.editorTool == EditorTool.ERASE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactChip(
                    text = "区域擦除",
                    selected = vm.editorEraseRegion,
                    onClick = { vm.editorEraseRegion = !vm.editorEraseRegion },
                )
                Text(
                    if (vm.editorEraseRegion) {
                        "点按擦掉整块同色区域 · 划动仍是方形橡皮"
                    } else {
                        "开启后点按整块同色区域一起擦除"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            when (vm.editorTool) {
                EditorTool.NONE -> "先选一个工具再编辑 · 单指拖动画布 · 双指缩放"
                EditorTool.PAINT -> "点按/划动上色 · 双指缩放 · 长按单格菜单"
                EditorTool.ERASE -> if (vm.editorEraseRegion) {
                    "区域擦除：点按整块同色区域 · 双指缩放"
                } else {
                    "擦除 ${vm.editorBrushSize}×${vm.editorBrushSize} · 双指缩放 · 长按单格菜单"
                }
                EditorTool.FILL -> "点按填充同色连通区域 · 双指缩放"
                EditorTool.PICKER -> "点按格子取色，取完自动切回画笔"
                EditorTool.PAN -> "单指拖动画布 · 双指缩放 · 点「适应」回到全图"
                EditorTool.SHAPE -> if (vm.editorShapeStart == null) {
                    "点第一格定起点 · 再点第二格画${vm.editorShape.label}"
                } else {
                    "已定起点 · 点第二格画${vm.editorShape.label} · 换工具取消"
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (compact) 3 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .padding(bottom = 2.dp),
        )
        }
    }

    if (landscape) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            headerContent(true)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                canvasContent(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = Spacing.xs, bottom = Spacing.xs),
                )
                Column(
                    modifier = Modifier
                        .width(248.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(end = Spacing.xs)
                        .padding(bottom = Spacing.sm),
                ) {
                    controlsContent(true)
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            headerContent(false)
            canvasContent(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = Spacing.xs),
            )
            controlsContent(false)
        }
    }

    // 色卡选择
    if (showPaletteSheet) {
        EditorPaletteSheet(
            vm = vm,
            palette = palette,
            currentEntry = currentEntry,
            safeColorIndex = safeColorIndex,
            query = paletteQuery,
            onQueryChange = { paletteQuery = it },
            measurer = swatchMeasurer,
            textStyle = swatchTextStyle,
            textCache = swatchTextCache,
            onDismiss = { showPaletteSheet = false },
        )
    }

    // ⋮ 更多
    if (showMenu) {
        EditorMenuSheet(
            vm = vm,
            palette = palette,
            engine = engine,
            onDismiss = { showMenu = false },
            onOpenManage = { showManageSheet = true },
            onOpenReplace = {
                replaceSource = it
                showReplaceSheet = true
            },
            onOpenResize = { showResizeDialog = true },
            onOpenCrop = { showCropDialog = true },
        )
    }

    // 改尺寸
    if (showResizeDialog) {
        EditorResizeDialog(
            vm = vm,
            initialWidth = engine.width,
            initialHeight = engine.height,
            onDismiss = { showResizeDialog = false },
        )
    }

    // 裁剪图纸
    if (showCropDialog) {
        EditorCropDialog(
            vm = vm,
            initialWidth = engine.width,
            initialHeight = engine.height,
            onDismiss = { showCropDialog = false },
        )
    }

    // 色板管理
    if (showManageSheet) {
        EditorManageSheet(
            vm = vm,
            palette = palette,
            onDismiss = { showManageSheet = false },
        )
    }

    // 替换相似颜色
    if (showReplaceSheet) {
        ColorReplaceSheet(
            vm = vm,
            palette = palette,
            engine = engine,
            initialSource = replaceSource,
            onDismiss = { showReplaceSheet = false },
            onReport = { colorOpReport = it },
        )
    }
    // 长按单格：底部弹层，动作区在手指可达范围内
    if (cellSheetOpen && longPressCell != null) {
        val cell = longPressCell!!
        if (cell !in engine.cells.indices) {
            cellSheetOpen = false
            longPressCell = null
            return
        }
        val colorIdx = engine.cells[cell]
        val cellEntry = palette.colors.getOrNull(colorIdx)
        val isLocked = engine.locked[cell]
        val sameColorCount = if (colorIdx >= 0) engine.cells.count { it == colorIdx } else 0
        // 连通块大小只跟格子内容有关：按格子 + 修订号缓存，避免每次重组都跑一遍 BFS
        val blockCount = remember(cell, vm.editorRevision) {
            if (colorIdx >= 0) engine.connectedRegionSize(cell) else 0
        }
        val cellX = cell % engine.width + 1
        val cellY = cell / engine.width + 1
        // 确认态跟着格子走：换一格后不能把上一格的「再点一次」带过来
        var replaceArmed by remember(cell) { mutableStateOf(false) }
        val closeSheet = {
            cellSheetOpen = false
            longPressCell = null
        }
        ModalBottomSheet(onDismissRequest = {
            cellSheetOpen = false
            longPressCell = null
        }) {
            Column(
                Modifier
                    .padding(horizontal = Spacing.md)
                    .padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (cellEntry != null) Color(cellEntry.argb) else Color(0xFFF2F2F3),
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cellEntry == null) {
                            Text(
                                "空",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = Spacing.sm),
                    ) {
                        Text(
                            if (cellEntry != null) cellEntry.code else "空格",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            (cellEntry?.name?.takeIf { it.isNotBlank() }?.plus(" · ") ?: "") +
                                "第 $cellY 行 $cellX 列",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            if (isLocked) "已锁定" else "本页 $sameColorCount 格",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CellActionTile(
                        icon = MorphIcons.PaintBucket,
                        title = "填充此格",
                        subtitle = "用 ${currentEntry.code}",
                        enabled = !isLocked && colorIdx != safeColorIndex,
                        modifier = Modifier.weight(1f),
                    ) {
                        applyCellColor(vm, engine, cell, safeColorIndex)
                        closeSheet()
                    }
                    CellActionTile(
                        icon = MorphIcons.Eraser,
                        title = "清除此格",
                        subtitle = "留空",
                        enabled = !isLocked && colorIdx >= 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        applyCellColor(vm, engine, cell, -1)
                        closeSheet()
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CellActionTile(
                        icon = MorphIcons.Pipette,
                        title = "取此格颜色",
                        subtitle = if (cellEntry != null) "切到 ${cellEntry.code}" else "空格无颜色",
                        enabled = cellEntry != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        vm.selectEditorColor(colorIdx)
                        closeSheet()
                    }
                    CellActionTile(
                        icon = MorphIcons.Brush,
                        title = "整块换色",
                        subtitle = if (blockCount > 0) {
                            "连通 $blockCount 格 → ${currentEntry.code}"
                        } else {
                            "空格无连通区"
                        },
                        enabled = !isLocked && colorIdx >= 0 &&
                            colorIdx != safeColorIndex && blockCount > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (engine.floodFill(cell, safeColorIndex)) {
                            vm.notifyEditorChanged()
                        }
                        closeSheet()
                    }
                }

                // 一整页同色可能是几百格，误触代价大：先点亮，再点一次才执行
                val needConfirm = sameColorCount >= 100
                CellActionTile(
                    icon = MorphIcons.SwapHorizontal,
                    title = if (replaceArmed) "再点一次执行" else "同色替换整页",
                    subtitle = if (replaceArmed) {
                        "将改 $sameColorCount 格 → ${currentEntry.code}"
                    } else {
                        "本页 $sameColorCount 格 → ${currentEntry.code}"
                    },
                    emphasis = replaceArmed,
                    enabled = !isLocked && colorIdx >= 0 && colorIdx != safeColorIndex,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (needConfirm && !replaceArmed) {
                        replaceArmed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else {
                        if (engine.replaceColorOnPage(colorIdx, safeColorIndex)) {
                            vm.notifyEditorChanged()
                        }
                        closeSheet()
                    }
                }

                CellActionTile(
                    icon = if (isLocked) MorphIcons.LockOpen else MorphIcons.Lock,
                    title = if (isLocked) "解锁此格" else "锁定此格",
                    subtitle = "锁定的格子不会被画笔、填充或替换改动",
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    engine.setLocked(cell, !isLocked)
                    vm.notifyEditorChanged()
                    closeSheet()
                }

                CellActionTile(
                    icon = MorphIcons.Palette,
                    title = "替换相似颜色",
                    subtitle = if (cellEntry != null) {
                        "整图 ${cellEntry.code} 及相近色 → 指定色"
                    } else {
                        "空格没有颜色"
                    },
                    enabled = cellEntry != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val seed = colorIdx
                    closeSheet()
                    if (seed >= 0) {
                        replaceSource = seed
                        showReplaceSheet = true
                    }
                }
            }
        }
    }
}
