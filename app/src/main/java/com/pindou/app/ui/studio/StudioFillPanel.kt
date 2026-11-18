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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pindou.app.ui.theme.Spacing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioFillPanel(session: StudioSession, onClose: () -> Unit) {
    if (session.revision < 0) return
    val engine = session.engine
    val hasSelection = engine.hasSelection
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.lg),
        ) {
            PanelHeader(
                "选区 / 渐变 / 抖动",
                if (hasSelection) "当前有选区，渐变和抖动只作用于选区内" else "没有选区：渐变和抖动作用于整个当前图层",
                onClose,
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionChip("全选") { session.act { it.selectAll() } }
                ActionChip("取消选区", enabled = hasSelection) { session.act { it.clearSelection() } }
                ActionChip("反选") { session.act { it.invertSelection() } }
                ActionChip("选当前图层") { session.act { it.selectLayerContent() } }
                ActionChip("复制") { session.act { it.copySelection() } }
                ActionChip("剪切", enabled = hasSelection) { session.act { it.cutSelection() } }
                ActionChip("粘贴", enabled = engine.canPaste) { session.act { it.pasteClipboard() } }
                ActionChip("删除选区内容", enabled = hasSelection) { session.act { it.deleteSelection() } }
                ActionChip("填充选区", enabled = hasSelection) {
                    val color = session.color
                    session.act { it.fillSelection(color, selectionOnly = true) }
                }
            }

            SectionLabel("颜色：主色 ${session.palette.colors.getOrNull(session.color)?.code ?: "-"} / 副色 ${session.palette.colors.getOrNull(session.secondaryColor)?.code ?: "-"}")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionChip("交换主副色") { session.swapColors() }
                ActionChip("用主色填满") {
                    val color = session.color
                    session.act { it.fillSelection(color, selectionOnly = false) }
                }
                ActionChip("颜色替换：主→副") {
                    val from = session.color
                    val to = session.secondaryColor
                    session.act { it.replaceColor(from, to, false) }
                }
                ActionChip("全工程替换：主→副") {
                    val from = session.color
                    val to = session.secondaryColor
                    session.act { it.replaceColor(from, to, true) }
                }
            }

            SectionLabel("渐变")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionChip("线性", selected = !session.gradientRadial) { session.gradientRadial = false }
                ActionChip("径向", selected = session.gradientRadial) { session.gradientRadial = true }
                ActionChip("抖动过渡", selected = session.gradientDithered) {
                    session.gradientDithered = !session.gradientDithered
                }
                ActionChip("铺满当前图层/选区") {
                    session.act { eng ->
                        val b = eng.selectionBounds()
                        val x0 = b?.get(0) ?: 0
                        val y0 = b?.get(1) ?: 0
                        val x1 = b?.get(2) ?: (eng.width - 1)
                        val y1 = b?.get(3) ?: (eng.height - 1)
                        eng.gradient(
                            x0, y0, x1, y1,
                            session.color, session.secondaryColor,
                            session.gradientRadial, session.gradientDithered,
                        )
                    }
                }
            }

            SectionLabel("抖动填充：主色占比 ${session.ditherDensity}%")
            Slider(
                value = session.ditherDensity.toFloat(),
                onValueChange = { session.ditherDensity = it.roundToInt() },
                valueRange = 0f..100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
            ) {
                ActionChip("用主/副色抖动铺满") {
                    val a = session.color
                    val b = session.secondaryColor
                    val density = session.ditherDensity
                    session.act { it.ditherFill(a, b, density) }
                }
            }

            SectionLabel("选区移动")
            Text(
                "选「移动选区」工具后直接拖；也可以在下面按格微调。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionChip("← 1") { session.act { it.moveSelection(-1, 0) } }
                ActionChip("→ 1") { session.act { it.moveSelection(1, 0) } }
                ActionChip("↑ 1") { session.act { it.moveSelection(0, -1) } }
                ActionChip("↓ 1") { session.act { it.moveSelection(0, 1) } }
            }
        }
    }
}
