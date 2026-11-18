package com.pindou.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pindou.app.core.editor.BrushStamp
import com.pindou.app.core.editor.EditorEngine
import com.pindou.app.core.editor.SymmetryMode
import com.pindou.app.ui.AppViewModel
import com.pindou.app.ui.EditorTool
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.theme.ClayColors
import com.pindou.app.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * 撤销/重做按钮。单独抽出来是为了让「每条笔画结束 +1 的 revision」只重组这两个
 * 图标按钮，而不是整个编辑页——编辑页里有色卡网格、工具条、底栏，
 * 全量重组一次就是十几毫秒的掉帧。
 */
@Composable
internal fun UndoRedoButtons(vm: AppViewModel, engine: EditorEngine) {
    val revision = vm.editorRevision
    IconButton(
        onClick = { vm.undoEditor() },
        enabled = remember(engine, revision) { engine.canUndo },
    ) {
        Icon(MorphIcons.Undo, contentDescription = "撤销")
    }
    IconButton(
        onClick = { vm.redoEditor() },
        enabled = remember(engine, revision) { engine.canRedo },
    ) {
        Icon(MorphIcons.Redo, contentDescription = "重做")
    }
}

internal fun applyCellColor(vm: AppViewModel, engine: EditorEngine, cell: Int, color: Int) {
    val session = engine.startStroke()
    session.setCell(cell, color)
    engine.endStroke(session)
    vm.notifyEditorChanged()
}

internal fun handleTap(vm: AppViewModel, engine: EditorEngine, cell: Int, brushSize: Int) {
    if (cell < 0) return
    when (vm.editorTool) {
        EditorTool.NONE -> Unit
        EditorTool.PICKER -> {
            val color = engine.cells.getOrNull(cell) ?: return
            if (color >= 0) vm.selectEditorColor(color)
        }
        EditorTool.FILL -> {
            if (engine.floodFill(cell, vm.editorColor)) vm.notifyEditorChanged()
        }
        EditorTool.PAINT -> {
            val session = engine.startStroke()
            stampBrush(engine, cell, brushSize, session, vm.editorColor, vm.editorSymmetry)
            engine.endStroke(session)
            vm.notifyEditorChanged()
        }
        EditorTool.ERASE -> {
            if (vm.editorEraseRegion) {
                if (engine.eraseRegion(cell)) vm.notifyEditorChanged()
            } else {
                val session = engine.startStroke()
                stampBrush(engine, cell, brushSize, session, -1, vm.editorSymmetry)
                engine.endStroke(session)
                vm.notifyEditorChanged()
            }
        }
        EditorTool.PAN -> Unit
        EditorTool.SHAPE -> {
            val start = vm.editorShapeStart
            if (start == null) {
                vm.editorShapeStart = cell
            } else {
                if (engine.drawShape(
                        kind = vm.editorShape,
                        start = start,
                        end = cell,
                        color = vm.editorColor,
                        filled = vm.editorShapeFilled,
                        symmetry = vm.editorSymmetry,
                    )
                ) {
                    vm.notifyEditorChanged()
                }
                vm.editorShapeStart = null
            }
        }
    }
}

internal fun stampBrush(
    engine: EditorEngine,
    center: Int,
    brushSize: Int,
    session: EditorEngine.StrokeSession,
    color: Int,
    symmetry: SymmetryMode,
) {
    for (mirroredCenter in symmetry.centers(center, engine.width, engine.height)) {
        BrushStamp.forEachCell(engine.width, engine.height, mirroredCenter, brushSize) { index ->
            session.setCell(index, color)
        }
    }
}

internal fun handleStrokeCell(
    vm: AppViewModel,
    engine: EditorEngine,
    cell: Int,
    session: EditorEngine.StrokeSession,
    brushSize: Int,
) {
    if (cell < 0) return
    when (vm.editorTool) {
        EditorTool.NONE -> Unit
        EditorTool.PAINT ->
            stampBrush(engine, cell, brushSize, session, vm.editorColor, vm.editorSymmetry)
        EditorTool.ERASE ->
            stampBrush(engine, cell, brushSize, session, -1, vm.editorSymmetry)
        EditorTool.FILL, EditorTool.PICKER, EditorTool.PAN, EditorTool.SHAPE -> Unit
    }
}

@Composable
internal fun ColorCubeButton(code: String, argb: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(52.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(27.dp)
                .background(Color(argb), RoundedCornerShape(7.dp))
                .border(1.dp, Color.Black.copy(alpha = 0.12f), RoundedCornerShape(7.dp)),
        )
        Text(
            code,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .padding(top = 1.dp),
        )
    }
}

@Composable
internal fun EditorToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else Color.Transparent,
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.outline,
                shape,
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) {
                ClayColors.PrimaryInk
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(19.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = if (selected) {
                ClayColors.PrimaryInk
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

@Composable
internal fun CompactChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent,
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
                else MaterialTheme.colorScheme.outline,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                ClayColors.PrimaryInk
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
internal fun CanvasPill(
    icon: ImageVector,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(9999.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outline,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) ClayColors.PrimaryInk
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) ClayColors.PrimaryInk
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
internal fun ZoomControls(
    canvasSize: IntSize,
    engine: EditorEngine,
    transform: CanvasTransform,
    gutter: Float,
    modifier: Modifier = Modifier,
) {
    // derivedStateOf 只在跨过阈值时通知：拖动平移只改 offset，
    // 不会每帧让这一行重组，更不会牵动编辑页其它区域。
    val canZoomOut by remember { derivedStateOf { transform.scale > 1.01f } }
    val canZoomIn by remember { derivedStateOf { transform.scale < 19.99f } }
    val canFit by remember {
        derivedStateOf { transform.scale > 1.01f || transform.offset != Offset.Zero }
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(10.dp),
            )
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val canvas = Offset(canvasSize.width.toFloat(), canvasSize.height.toFloat())
        val focus = Offset(canvas.x / 2f, canvas.y / 2f)
        ZoomButton("−", enabled = canZoomOut) {
            zoomAt(canvas, engine, transform, focus, transform.scale / 1.5f, gutter)
        }
        ZoomPercent(transform)
        ZoomButton("+", enabled = canZoomIn) {
            zoomAt(canvas, engine, transform, focus, transform.scale * 1.5f, gutter)
        }
        ZoomButton("适应", enabled = canFit) {
            transform.reset()
        }
    }
}

/** 百分比单独成一个重组作用域：捏合时每帧只重算这一个 Text。 */
@Composable
internal fun ZoomPercent(transform: CanvasTransform) {
    Text(
        "${(transform.scale * 100).roundToInt()}%",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(44.dp),
    )
}

@Composable
internal fun ZoomButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        )
    }
}

@Composable
internal fun MenuItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.padding(start = Spacing.sm)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun CellActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasis: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        emphasis -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
    }
    val strokeColor = when {
        !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        emphasis -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(if (emphasis) 1.5.dp else 1.dp, strokeColor, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) {
                ClayColors.PrimaryInk
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            },
            modifier = Modifier.size(20.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.padding(top = 3.dp),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
