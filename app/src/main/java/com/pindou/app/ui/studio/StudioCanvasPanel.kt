@file:OptIn(ExperimentalLayoutApi::class)
package com.pindou.app.ui.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pindou.app.core.editor.SymmetryMode
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.ui.theme.Spacing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioCanvasPanel(
    session: StudioSession,
    palettes: List<BeadPalette>,
    onPalette: (BeadPalette) -> Unit,
    onResetRails: () -> Unit,
    onClose: () -> Unit,
) {
    if (session.revision < 0) return
    val engine = session.engine
    var name by remember { mutableStateOf(session.name) }
    var widthText by remember(session.revision) { mutableStateOf(engine.width.toString()) }
    var heightText by remember(session.revision) { mutableStateOf(engine.height.toString()) }
    var lockAspect by remember { mutableStateOf(false) }
    val newW = widthText.toIntOrNull()
    val newH = heightText.toIntOrNull()
    val sizeOk = newW != null && newH != null && newW in 5..500 && newH in 5..500
    val hasSelection = engine.hasSelection
    val bounds = engine.selectionBounds()

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.lg),
        ) {
            PanelHeader("画布 / 工程设置", "${engine.width}×${engine.height} · ${engine.layerCount} 层 · ${engine.frameCount} 帧", onClose)

            SectionLabel("工程名称")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40); session.name = name },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
            )

            SectionLabel("色卡")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                palettes.forEach { p ->
                    ActionChip(
                        "${p.brandLabel} ${if (p.beadSize.name == "MM2_6") "2.6" else "5"}mm",
                        selected = p.brand == session.palette.brand && p.beadSize == session.palette.beadSize,
                    ) { onPalette(p) }
                }
            }

            SectionLabel("改画布尺寸")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { raw ->
                        widthText = raw.filter { it.isDigit() }.take(3)
                        if (lockAspect && newH != null && widthText.toIntOrNull() != null) {
                            val ratio = engine.height.toFloat() / engine.width
                            heightText = (widthText.toInt() * ratio).roundToInt().coerceIn(5, 500).toString()
                        }
                    },
                    label = { Text("宽") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { raw ->
                        heightText = raw.filter { it.isDigit() }.take(3)
                        if (lockAspect && widthText.toIntOrNull() != null && heightText.toIntOrNull() != null) {
                            val ratio = engine.width.toFloat() / engine.height
                            widthText = (heightText.toInt() * ratio).roundToInt().coerceIn(5, 500).toString()
                        }
                    },
                    label = { Text("高") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            SectionLabel("常用尺寸（立即应用，内容居中）")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(16, 32, 48, 64, 96, 128, 256, 500).forEach { n ->
                    ActionChip(
                        "$n×$n",
                        selected = engine.width == n && engine.height == n,
                    ) {
                        widthText = n.toString()
                        heightText = n.toString()
                        session.act { it.resizeCanvas(n, n, 0.5f, 0.5f) }
                    }
                }
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionChip("锁定比例", selected = lockAspect) { lockAspect = !lockAspect }
                ActionChip("只改画布（内容居中）", enabled = sizeOk) {
                    session.act { it.resizeCanvas(newW!!, newH!!, 0.5f, 0.5f) }
                }
                ActionChip("缩放内容", enabled = sizeOk) {
                    session.act { it.scaleContent(newW!!, newH!!) }
                }
                ActionChip("裁剪到选区", enabled = hasSelection && bounds != null) {
                    val b = bounds!!
                    session.act { it.crop(b[0], b[1], b[2] - b[0] + 1, b[3] - b[1] + 1) }
                }
                ActionChip("裁掉空白边") {
                    val b = engine.contentBounds()
                    if (b != null) {
                        session.act {
                            it.crop(b[0], b[1], b[2] - b[0] + 1, b[3] - b[1] + 1)
                        }
                    }
                }
            }

            SectionLabel("变换（所有帧同步，可撤销）")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                ActionChip("水平翻转") { session.act { it.flipHorizontal() } }
                ActionChip("垂直翻转") { session.act { it.flipVertical() } }
                ActionChip("左转 90°") { session.act { it.rotate90(false) } }
                ActionChip("右转 90°") { session.act { it.rotate90(true) } }
                ActionChip("旋转 180°") { session.act { it.rotate180() } }
                ActionChip("← 一格") { session.act { it.shift(-1, 0) } }
                ActionChip("→ 一格") { session.act { it.shift(1, 0) } }
                ActionChip("↑ 一格") { session.act { it.shift(0, -1) } }
                ActionChip("↓ 一格") { session.act { it.shift(0, 1) } }
            }

            SectionLabel("对称")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                SymmetryMode.entries.forEach { mode ->
                    ActionChip(mode.label, selected = session.symmetry == mode) {
                        session.symmetry = mode
                        session.touch()
                    }
                }
            }

            ToggleRow("显示网格", checked = session.showGrid, onCheckedChange = { session.showGrid = it })
            SectionLabel("侧栏位置")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionChip("重置两块浮窗") { onResetRails() }
            }
            Text(
                "拖住顶部双横线移动浮窗；点右侧小箭头收起/展开，收起后仍可拖动。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = 2.dp),
            )
            ToggleRow("显示中心线", checked = session.showCenterLines, onCheckedChange = { session.showCenterLines = it })
            ToggleRow("显示色号", "每格显示当前图层的色号，放大到 22px 以上才绘制", checked = session.showCodes, onCheckedChange = { session.showCodes = it })
            ToggleRow("像素完美", "滑动时去掉 L 形拐角的多余像素", checked = session.pixelPerfect, onCheckedChange = { session.pixelPerfect = it })
            ToggleRow("圆形笔刷", "关闭为方形笔刷", checked = session.circleBrush, onCheckedChange = { session.circleBrush = it })
            ToggleRow("洋葱皮", "显示上一帧残影", checked = session.onionSkin, onCheckedChange = { session.onionSkin = it })
        }
    }
}
