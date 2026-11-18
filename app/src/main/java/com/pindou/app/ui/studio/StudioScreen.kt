package com.pindou.app.ui.studio

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pindou.app.core.pattern.CodeLabelMode
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberUpdatedState
import com.pindou.app.core.editor.SymmetryMode
import com.pindou.app.core.export.ExportService
import com.pindou.app.ui.export.rememberExporter
import com.pindou.app.core.studio.StudioExport
import com.pindou.app.ui.AppViewModel
import com.pindou.app.ui.PdfPreviewDialog
import com.pindou.app.ui.Screen
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.theme.ClayColors
import com.pindou.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 画像素工作区。
 *
 * 和旧 EditorScreen 分开的原因：旧编辑器是单页单层，所有状态都在
 * AppViewModel 里；画像素要同时管理帧、图层、选区、动画预览和独立导出，
 * 继续塞进旧页面会让一次笔画触发整页重组。这里画布只订阅 session.revision，
 * 顶栏/面板各自订阅自己的状态。
 */
@Composable
fun StudioScreen(vm: AppViewModel) {
    val session = vm.studio ?: return
    val engine = session.engine
    val palette = session.palette
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    // 顶栏的色数、撤销/重做可用状态和底栏选区状态都存在可变引擎里；
    // 在页面顶层读一次 revision，让这些非 Compose 状态能随笔画/图层操作刷新。
    // 画布本身仍只在自己的 draw 阶段读 revision，不会额外重绘两遍。
    val revision = session.revision
    val usedColorCount = remember(revision) { engine.usedColorCount() }
    val context = LocalContext.current
    val canvasState = remember { StudioCanvasState() }
    val tourAnchors = remember { StudioTourAnchors() }
    var panel by remember { mutableStateOf<StudioPanel?>(null) }
    var paletteSheet by remember { mutableStateOf(false) }
    var discardDialog by remember { mutableStateOf(false) }
    var flattenDialog by remember { mutableStateOf(false) }
    var textOrigin by remember { mutableIntStateOf(-1) }
    var moreMenu by remember { mutableStateOf(false) }
    var tutorialStep by remember { mutableIntStateOf(-1) }
    var layersTour by remember { mutableStateOf(false) }
    // 从「导入底层图片」这一步打开图层面板时记住下一站；面板关掉后教程继续，
    // 不会因为中途去选图片就把后面工具/颜色/缩放的说明吞掉。
    var tourResumeStep by remember { mutableIntStateOf(-1) }
    var pdfPreview by remember { mutableStateOf<ExportService.Exported.FileReady?>(null) }
    val exporter = rememberExporter { out -> if (out is ExportService.Exported.FileReady &&
        out.file.extension.equals("pdf", true)
    ) pdfPreview = out }
    var exportMsg by remember { mutableStateOf<String?>(null) }
    val exporterMessage = exporter.message
    if (exporterMessage != null && exportMsg != exporterMessage) exportMsg = exporterMessage

    LaunchedEffect(exportMsg) {
        if (exportMsg != null) {
            delay(4_500)
            exportMsg = null
        }
    }

    LaunchedEffect(engine.width, engine.height) {
        canvasState.reset()
    }

    LaunchedEffect(vm.settings.studioTutorialSeen) {
        if (vm.settings.studioTutorialSeen == null && tutorialStep < 0) {
            tutorialStep = 0
        }
    }

    // 动画预览：按每帧停留时间切帧；面板关掉也继续播放
    LaunchedEffect(session.playing, engine.frameCount) {
        while (session.playing) {
            val hold = engine.frameHold().coerceIn(20, 10_000)
            delay(hold.toLong())
            if (!session.playing) break
            session.act { it.setFrame((it.frameIndex + 1) % it.frameCount) }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { tourAnchors.root = it.boundsInWindow() },
    ) {
    Column(Modifier.fillMaxSize()) {
        // ---------------- 顶栏 ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { vm.exitStudio() },
                modifier = Modifier.size(38.dp),
            ) {
                Icon(MorphIcons.ArrowLeft, contentDescription = "返回", modifier = Modifier.size(22.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "画像素 · ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        "${engine.width}×${engine.height}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier
                            .onGloballyPositioned {
                                tourAnchors.canvasSize = it.boundsInWindow()
                            }
                            .clip(RoundedCornerShape(5.dp))
                            .clickable { panel = StudioPanel.CANVAS }
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
                Text(
                    "第 ${engine.frameIndex + 1}/${engine.frameCount} 帧 · ${engine.layerCount} 层 · ${palette.brandLabel} · ${usedColorCount} 色",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = { session.undo() },
                enabled = engine.canUndo(),
                modifier = Modifier.size(34.dp),
            ) {
                Icon(MorphIcons.Undo, contentDescription = "撤销", modifier = Modifier.size(19.dp))
            }
            IconButton(
                onClick = { session.redo() },
                enabled = engine.canRedo(),
                modifier = Modifier.size(34.dp),
            ) {
                Icon(MorphIcons.Redo, contentDescription = "重做", modifier = Modifier.size(19.dp))
            }
            Box {
                IconButton(onClick = { moreMenu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(MorphIcons.MoreVertical, contentDescription = "更多", modifier = Modifier.size(19.dp))
                }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("保存并预览") },
                        onClick = { moreMenu = false; vm.finishStudio(Screen.PREVIEW) },
                    )
                    DropdownMenuItem(
                        text = { Text("保存并返回项目库") },
                        onClick = { moreMenu = false; vm.finishStudio(Screen.HOME) },
                    )
                    DropdownMenuItem(
                        text = { Text("导出…") },
                        onClick = { moreMenu = false; panel = StudioPanel.EXPORT },
                    )
                    DropdownMenuItem(
                        text = { Text("新手教程") },
                        onClick = { moreMenu = false; tutorialStep = 0 },
                    )
                    DropdownMenuItem(
                        text = { Text("画布设置…") },
                        onClick = { moreMenu = false; panel = StudioPanel.CANVAS },
                    )
                    DropdownMenuItem(
                        text = { Text("转为普通图纸…") },
                        onClick = { moreMenu = false; flattenDialog = true },
                    )
                    DropdownMenuItem(
                        text = { Text("不保存退出") },
                        onClick = { moreMenu = false; discardDialog = true },
                    )
                }
            }
            Button(
                onClick = { vm.finishStudio(Screen.PREVIEW) },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Text("完成", style = MaterialTheme.typography.labelLarge)
            }
        }

        // ---------------- 画布 ----------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { tourAnchors.canvas = it.boundsInWindow() }
                .padding(horizontal = Spacing.xs, vertical = 2.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ClayColors.Stage)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        ) {
            StudioCanvas(
                session = session,
                state = canvasState,
                onTextAt = { textOrigin = it },
                onPickColor = { session.selectColor(it) },
                onMessage = { exportMsg = it },
                modifier = Modifier.fillMaxSize(),
            )

            if (portrait) {
                // 两块浮窗可以在画布内自由拖动：位置用 0..1 比例保存，
                // 换屏幕/横竖屏后仍落在可见范围。
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val canvasW = constraints.maxWidth.toFloat()
                    val canvasH = constraints.maxHeight.toFloat()
                    FloatingStudioPanel(
                        canvasW = canvasW,
                        canvasH = canvasH,
                        modifier = Modifier.onGloballyPositioned {
                            tourAnchors.toolRail = it.boundsInWindow()
                        },
                        xFraction = session.toolX,
                        yFraction = session.toolY,
                        defaultX = 0f,
                        defaultY = 0.5f,
                        onCommit = { fx, fy ->
                            vm.setStudioRailPositions(fx, fy, session.colorX, session.colorY)
                        },
                    ) { header ->
                        StudioToolRail(
                            session,
                            headerModifier = header,
                            collapsed = session.toolCollapsed,
                            horizontal = dockHorizontal(session.toolX, session.toolY, 0f),
                            onToggle = {
                                val next = !session.toolCollapsed
                                session.toolCollapsed = next
                                vm.setStudioRailCollapsed(next, session.colorCollapsed)
                            },
                        )
                    }
                    FloatingStudioPanel(
                        canvasW = canvasW,
                        canvasH = canvasH,
                        modifier = Modifier.onGloballyPositioned {
                            tourAnchors.colorRail = it.boundsInWindow()
                        },
                        xFraction = session.colorX,
                        yFraction = session.colorY,
                        defaultX = 1f,
                        defaultY = 0.5f,
                        onCommit = { fx, fy ->
                            vm.setStudioRailPositions(session.toolX, session.toolY, fx, fy)
                        },
                    ) { header ->
                        StudioColorRail(
                            session = session,
                            onOpenPalette = { paletteSheet = true },
                            headerModifier = header,
                            collapsed = session.colorCollapsed,
                            horizontal = dockHorizontal(session.colorX, session.colorY, 1f),
                            onToggle = {
                                val next = !session.colorCollapsed
                                session.colorCollapsed = next
                                vm.setStudioRailCollapsed(session.toolCollapsed, next)
                            },
                        )
                    }
                }
            }

            // 缩放控制
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .onGloballyPositioned { tourAnchors.zoom = it.boundsInWindow() }
                    .padding(6.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val center = androidx.compose.ui.geometry.Offset(
                    canvasState.canvasSize.width / 2f,
                    canvasState.canvasSize.height / 2f,
                )
                TextButton(
                    onClick = {
                        studioZoomAt(canvasState.canvasSize, engine, canvasState, center, canvasState.scale / 1.5f)
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.height(28.dp),
                ) { Text("−", style = MaterialTheme.typography.labelMedium) }
                Text(
                    "${(canvasState.scale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp),
                )
                TextButton(
                    onClick = {
                        studioZoomAt(canvasState.canvasSize, engine, canvasState, center, canvasState.scale * 1.5f)
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.height(28.dp),
                ) { Text("+", style = MaterialTheme.typography.labelMedium) }
                TextButton(
                    onClick = { canvasState.reset() },
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.height(28.dp),
                ) { Text("适应", style = MaterialTheme.typography.labelSmall) }
            }

            if (exportMsg != null) {
                Text(
                    exportMsg!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = ClayColors.OnDark,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(ClayColors.Ink.copy(alpha = 0.92f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // ---------------- 工具条 ----------------
        // 竖屏用左右两侧的浮窗；横屏保留横向工具条，避免画布宽度被两侧吃掉。
        if (!portrait) StudioToolStrip(session)

        // 工具上下文：笔刷大小 / 形状填充 / 待落点
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xs, vertical = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (session.tool) {
                StudioTool.BRUSH, StudioTool.ERASER, StudioTool.LINE, StudioTool.RECT,
                StudioTool.ELLIPSE, StudioTool.POLYGON, StudioTool.CURVE -> {
                    Text(
                        "笔刷",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf(1, 2, 3, 5, 8).forEach { size ->
                        SmallChip("$size", session.brushSize == size) { session.brushSize = size }
                    }
                    SmallChip("方", !session.circleBrush) { session.circleBrush = false }
                    SmallChip("圆", session.circleBrush) { session.circleBrush = true }
                    SmallChip("填充", session.shapeFilled) { session.shapeFilled = !session.shapeFilled }
                    SmallChip("色号", session.showCodes) {
                        session.showCodes = !session.showCodes
                        session.touch()
                    }
                }
                StudioTool.GRADIENT, StudioTool.DITHER -> {
                    SmallChip("线性", !session.gradientRadial) { session.gradientRadial = false }
                    SmallChip("径向", session.gradientRadial) { session.gradientRadial = true }
                    SmallChip("抖动", session.gradientDithered) { session.gradientDithered = !session.gradientDithered }
                    SmallChip("填充设置…", false) { panel = StudioPanel.FILL }
                }
                StudioTool.SELECT_RECT, StudioTool.SELECT_LASSO, StudioTool.MAGIC_WAND,
                StudioTool.MOVE -> {
                    SmallChip("全选", false) { session.act { it.selectAll() } }
                    SmallChip("取消", false) { session.act { it.clearSelection() } }
                    SmallChip("反选", false) { session.act { it.invertSelection() } }
                    SmallChip("复制", false) { session.act { it.copySelection() } }
                    SmallChip("粘贴", false) { session.act { it.pasteClipboard() } }
                    SmallChip("删除", false) { session.act { it.deleteSelection() } }
                    SmallChip("选区设置…", false) { panel = StudioPanel.FILL }
                }
                else -> {
                    SmallChip("对称：${session.symmetry.label}", session.symmetry != SymmetryMode.NONE) {
                        session.symmetry = when (session.symmetry) {
                            SymmetryMode.NONE -> SymmetryMode.HORIZONTAL
                            SymmetryMode.HORIZONTAL -> SymmetryMode.VERTICAL
                            SymmetryMode.VERTICAL -> SymmetryMode.BOTH
                            SymmetryMode.BOTH -> SymmetryMode.NONE
                        }
                    }
                    SmallChip("网格", session.showGrid) { session.showGrid = !session.showGrid }
                    SmallChip("色号", session.showCodes) { session.showCodes = !session.showCodes }
                }
            }
            if (session.pendingPoints.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "待落点 ${session.pendingPoints.size / 2}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (session.tool == StudioTool.POLYGON && session.pendingPoints.size >= 6) {
                    SmallChip("闭合", true) {
                        val color = session.color
                        session.act {
                            it.drawPolygon(
                                session.pendingPoints.toIntArray(), color,
                                session.shapeFilled, session.brushSize, session.circleBrush,
                            )
                        }
                        session.pendingPoints = emptyList()
                        session.touch()
                    }
                }
                SmallChip("取消点", false) { session.clearPending() }
            }
        }

        Text(
            "${session.tool.label} · " + when (session.tool) {
                StudioTool.BRUSH -> "点按/拖动上色 · 双指缩放 · 笔刷 ${session.brushSize}"
                StudioTool.ERASER -> "点按/拖动擦成透明 · 双指缩放"
                StudioTool.FILL -> "点按填充连通同色区域"
                StudioTool.PICKER -> "点按格子取色，取完自动切回画笔"
                StudioTool.LINE -> "拖出直线"
                StudioTool.RECT -> "拖出矩形，可选描边/填充"
                StudioTool.ELLIPSE -> "拖出椭圆，可选描边/填充"
                StudioTool.POLYGON -> "逐点点击，点回起点或按「闭合」落形"
                StudioTool.CURVE -> "点 3 个控制点画贝塞尔曲线"
                StudioTool.GRADIENT -> "拖出渐变方向 · 用主副色"
                StudioTool.DITHER -> "点按用主副色抖动铺满选区/图层"
                StudioTool.TEXT -> "点画布决定落字左上角"
                StudioTool.SELECT_RECT -> "拖出矩形选区"
                StudioTool.SELECT_LASSO -> "手指圈出任意形状"
                StudioTool.MAGIC_WAND -> "点按选中连通同色区域（跨图层）"
                StudioTool.MOVE -> "拖动选区内容，松手落位"
                StudioTool.PAN -> "单指平移 · 双指缩放 · 点「适应」回到全图"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.md),
        )

        // ---------------- 调色板 ----------------
        if (!portrait) {
            StudioPaletteStrip(session, onOpenPalette = { paletteSheet = true })
        }

        // ---------------- 底部动作栏 ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { tourAnchors.bottomBar = it.boundsInWindow() }
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BottomAction(
                "图层",
                "第 ${engine.layerCount} 层",
                Modifier
                    .weight(1f)
                    .onGloballyPositioned { tourAnchors.layersAction = it.boundsInWindow() },
            ) {
                panel = StudioPanel.LAYERS
            }
            BottomAction("帧", "${engine.frameCount} 帧", Modifier.weight(1f)) {
                panel = StudioPanel.FRAMES
            }
            BottomAction("选区/填充", if (engine.hasSelection) "有选区" else "无选区", Modifier.weight(1f)) {
                panel = StudioPanel.FILL
            }
            BottomAction("画布", "${engine.width}×${engine.height}", Modifier.weight(1f)) {
                panel = StudioPanel.CANVAS
            }
            BottomAction("导出", "PNG/GIF/PDF", Modifier.weight(1f)) {
                panel = StudioPanel.EXPORT
            }
        }
    }

    when (panel) {
        StudioPanel.LAYERS -> StudioLayersPanel(
            session = session,
            tourActive = layersTour,
            onTourDone = {
                val next = tourResumeStep
                layersTour = false
                tourResumeStep = -1
                panel = null
                if (next >= 0) tutorialStep = next
            },
        ) {
            val next = tourResumeStep
            tourResumeStep = -1
            panel = null
            layersTour = false
            if (next >= 0) tutorialStep = next
        }
        StudioPanel.FRAMES -> StudioFramesPanel(
            session = session,
            playing = session.playing,
            onTogglePlay = { session.playing = !session.playing },
            onClose = { panel = null },
        )
        StudioPanel.EXPORT -> StudioExportPanel(
            session = session,
            onExportPixel = { scale, transparent ->
                exporter.run("像素 PNG") {
                    StudioExport.exportPixelPng(context, engine, engine.frameIndex, scale, transparent, session.name)
                }
            },
            onExportGif = { scale ->
                exporter.run("GIF") {
                    StudioExport.exportAnimation(context, engine, scale, session.name)
                }
            },
            onExportCode = {
                exporter.run("色号图") {
                    StudioExport.exportCodeChart(
                        context, engine, palette, session.name,
                        symbol = false,
                        codeLabels = if (session.simplifiedLabels) CodeLabelMode.OUTLINE else CodeLabelMode.FULL,
                    )
                }
            },
            onExportSymbol = {
                exporter.run("符号版") {
                    StudioExport.exportCodeChart(context, engine, palette, session.name, symbol = true)
                }
            },
            onExportPdf = {
                exporter.run("PDF") {
                    StudioExport.exportPdf(
                        context, engine, palette, session.name,
                        codeLabels = if (session.simplifiedLabels) CodeLabelMode.OUTLINE else CodeLabelMode.FULL,
                    )
                }
            },
            onExportPalette = {
                exporter.run("调色板") {
                    StudioExport.exportPalette(context, engine, palette, session.name)
                }
            },
            onClose = { panel = null },
        )
        StudioPanel.CANVAS -> StudioCanvasPanel(
            session = session,
            palettes = vm.palettes,
            onPalette = { vm.setStudioPalette(it) },
            onResetRails = { vm.resetStudioRailPositions() },
            onClose = { panel = null },
        )
        StudioPanel.FILL -> StudioFillPanel(session) { panel = null }
        null -> Unit
    }

    if (paletteSheet) {
        StudioPaletteSheet(
            session = session,
            palettes = vm.palettes,
            onPalette = { vm.setStudioPalette(it) },
            onDismiss = { paletteSheet = false },
        )
    }

    if (textOrigin >= 0) {
        StudioTextDialog(
            origin = textOrigin,
            session = session,
            onDismiss = { textOrigin = -1 },
        )
    }

    if (discardDialog) {
        StudioDiscardDialog(
            onDismiss = { discardDialog = false },
            onConfirm = {
                discardDialog = false
                vm.discardStudio()
            },
        )
    }

    if (flattenDialog) {
        StudioFlattenDialog(
            onDismiss = { flattenDialog = false },
            onConfirm = {
                flattenDialog = false
                vm.flattenStudioToEditor()
            },
        )
    }

    pdfPreview?.let { file ->
        PdfPreviewDialog(
            exported = file,
            onDismiss = { pdfPreview = null },
            onShare = { ExportService.shareFile(context, file) },
        )
    }

    if (tutorialStep >= 0) {
        StudioTourOverlay(
            step = tutorialStep,
            anchors = tourAnchors,
            canvasLabel = "${engine.width}×${engine.height}",
            onStepChange = { tutorialStep = it },
            onOpenLayerImport = {
                tourResumeStep = tutorialStep + 1
                tutorialStep = -1
                panel = StudioPanel.LAYERS
                layersTour = true
            },
            onDismiss = {
                tutorialStep = -1
                vm.markStudioTutorialSeen()
            },
        )
    }
    }
}

/**
 * 画布内的自由浮窗。
 *
 * 位置存 0..1 比例：拖动时用像素跟手，松手换算成比例并持久化；
 * 画布或浮窗尺寸变化时，按比例重新落位，不会被甩到屏幕外。
 */
private fun dockHorizontal(xFraction: Float, yFraction: Float, defaultX: Float): Boolean {
    val fx = if (xFraction < 0f) defaultX else xFraction
    val fy = if (yFraction < 0f) 0.5f else yFraction
    val nearTopBottom = fy <= 0.18f || fy >= 0.82f
    val nearSide = fx <= 0.18f || fx >= 0.82f
    return nearTopBottom && !nearSide
}

@Composable
private fun FloatingStudioPanel(
    canvasW: Float,
    canvasH: Float,
    modifier: Modifier = Modifier,
    xFraction: Float,
    yFraction: Float,
    defaultX: Float,
    defaultY: Float,
    onCommit: (Float, Float) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var dragX by remember { mutableStateOf<Float?>(null) }
    var dragY by remember { mutableStateOf<Float?>(null) }

    val maxX = (canvasW - panelSize.width).coerceAtLeast(0f)
    val maxY = (canvasH - panelSize.height).coerceAtLeast(0f)
    val baseX = StudioRailLayout.toPixels(xFraction, maxX, defaultX)
    val baseY = StudioRailLayout.toPixels(yFraction, maxY, defaultY)
    val x = dragX ?: baseX
    val y = dragY ?: baseY
    val xNow by rememberUpdatedState(x)
    val yNow by rememberUpdatedState(y)
    val onCommitNow by rememberUpdatedState(onCommit)

    val headerModifier = Modifier.pointerInput(maxX, maxY) {
        var startX = 0f
        var startY = 0f
        detectDragGestures(
            onDragStart = {
                startX = xNow
                startY = yNow
                dragX = startX
                dragY = startY
            },
            onDragCancel = {
                dragX = null
                dragY = null
            },
            onDragEnd = {
                val fx = StudioRailLayout.toFraction(dragX ?: startX, maxX)
                val fy = StudioRailLayout.toFraction(dragY ?: startY, maxY)
                dragX = null
                dragY = null
                onCommitNow(fx, fy)
            },
        ) { change, amount ->
            change.consume()
            dragX = StudioRailLayout.clamp((dragX ?: startX) + amount.x, maxX)
            dragY = StudioRailLayout.clamp((dragY ?: startY) + amount.y, maxY)
        }
    }

    Box(
        modifier = Modifier
            .onSizeChanged { panelSize = it }
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .then(modifier),
    ) {
        content(headerModifier)
    }
}

@Composable
private fun SmallChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (selected) Color.Transparent else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BottomAction(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
