@file:OptIn(ExperimentalLayoutApi::class)

package com.pindou.app.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 选区 / 渐变 / 抖动 sheet。
 *
 * 旧版一屏里有 30 多个 chip，找「填充选区」要往下翻两屏。现在按任务分三段：
 * 选区（选中/修改）→ 填充（纯色/渐变/抖动）→ 移动微调；
 * 每段只用一行主动作 + 一排次要动作，主次分明。
 */
@Composable
fun StudioFillPanel(session: StudioSession, onClose: () -> Unit) {
    if (session.revision < 0) return
    val engine = session.engine
    val hasSelection = engine.hasSelection
    val primaryCode = session.palette.colors.getOrNull(session.color)?.code ?: "-"
    val secondaryCode = session.palette.colors.getOrNull(session.secondaryColor)?.code ?: "-"

    StudioSheet(
        title = "选区 / 填充",
        subtitle = if (hasSelection) {
            "当前有选区 · 填充和渐变只作用于选区内"
        } else {
            "没有选区 · 填充和渐变作用于整个当前图层"
        },
        onClose = onClose,
    ) {
        SheetSection(
            title = "选区",
            hint = "长按格子不会选中；用下面四个动作建立/放弃选区",
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniChip("全选", false) { session.act { it.selectAll() } }
                MiniChip("反选", false) { session.act { it.invertSelection() } }
                MiniChip("选当前图层", false) { session.act { it.selectLayerContent() } }
                MiniChip("取消选区", false, enabled = hasSelection) {
                    session.act { it.clearSelection() }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SecondaryAction("复制", Modifier.weight(1f), enabled = hasSelection) {
                    session.act { it.copySelection() }
                }
                SecondaryAction("剪切", Modifier.weight(1f), enabled = hasSelection) {
                    session.act { it.cutSelection() }
                }
                SecondaryAction("粘贴", Modifier.weight(1f), enabled = engine.canPaste) {
                    session.act { it.pasteClipboard() }
                }
                SecondaryAction(
                    "删除内容",
                    Modifier.weight(1f),
                    enabled = hasSelection,
                    danger = true,
                ) {
                    session.act { it.deleteSelection() }
                }
            }
        }

        SheetSection(
            title = "填充",
            hint = "主色 $primaryCode · 副色 $secondaryCode",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        val color = session.color
                        session.act { it.fillSelection(color, selectionOnly = hasSelection) }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(
                        horizontal = 8.dp,
                        vertical = 0.dp,
                    ),
                ) {
                    Text(
                        if (hasSelection) "主色填充选区" else "主色填满图层",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                SecondaryAction(
                    "交换主副色",
                    Modifier.weight(1f),
                ) { session.swapColors() }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniChip("该色 → 副色", false) {
                    val from = session.color
                    val to = session.secondaryColor
                    session.act { it.replaceColor(from, to, false) }
                }
                MiniChip("全工程替换", false) {
                    val from = session.color
                    val to = session.secondaryColor
                    session.act { it.replaceColor(from, to, true) }
                }
            }
        }

        SheetSection(title = "渐变", hint = "先在画布上拖出方向，或用下面的按钮直接铺满") {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniChip("线性", !session.gradientRadial) { session.gradientRadial = false }
                MiniChip("径向", session.gradientRadial) { session.gradientRadial = true }
                MiniChip("抖动过渡", session.gradientDithered) {
                    session.gradientDithered = !session.gradientDithered
                }
            }
            SecondaryAction("铺满当前图层/选区", Modifier.fillMaxWidth()) {
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

        SheetSection(title = "抖动填充", hint = "两种颜色按 Bayer 矩阵交错，主色占比越低越像副色") {
            SheetSlider(
                label = "主色占比",
                value = session.ditherDensity.toFloat(),
                valueText = "${session.ditherDensity}%",
                range = 0f..100f,
            ) { session.ditherDensity = it.roundToInt() }
            SecondaryAction("按比例抖动铺满", Modifier.fillMaxWidth()) {
                val a = session.color
                val b = session.secondaryColor
                val density = session.ditherDensity
                session.act { it.ditherFill(a, b, density) }
            }
        }

        SheetSection(
            title = "选区移动",
            hint = "选「移动选区」工具后可直接拖；这里按格微调",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SecondaryAction("← 1", Modifier.weight(1f), enabled = hasSelection) {
                    session.act { it.moveSelection(-1, 0) }
                }
                SecondaryAction("→ 1", Modifier.weight(1f), enabled = hasSelection) {
                    session.act { it.moveSelection(1, 0) }
                }
                SecondaryAction("↑ 1", Modifier.weight(1f), enabled = hasSelection) {
                    session.act { it.moveSelection(0, -1) }
                }
                SecondaryAction("↓ 1", Modifier.weight(1f), enabled = hasSelection) {
                    session.act { it.moveSelection(0, 1) }
                }
            }
        }
    }
}

/** 白底描边的次要动作；四列排布时文字可能截断，固定 1 行。 */
@Composable
private fun SecondaryAction(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Transparent)
            .border(
                1.dp,
                if (danger && enabled) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.36f)
                danger -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
        )
    }
}
