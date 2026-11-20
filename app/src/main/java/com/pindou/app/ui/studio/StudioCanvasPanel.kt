@file:OptIn(ExperimentalLayoutApi::class)

package com.pindou.app.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pindou.app.core.editor.SymmetryMode
import com.pindou.app.core.palette.BeadPalette
import kotlin.math.roundToInt

/**
 * 画布 / 工程设置 sheet。
 *
 * 旧版一页直铺：名称、色卡、尺寸、变换、对称、显示开关全挤在一起，
 * 想改个「隐藏网格」要滚过三屏。现在按三个 tab 分组：
 * 画布（名称+尺寸+色卡）→ 变换（翻转/旋转/位移/对称）→ 显示（网格/色号/笔刷）。
 * 尺寸编辑放到顶部的两列输入 + 预设格，和概念画板一致。
 */
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
    var tab by remember { mutableIntStateOf(0) }
    val newW = widthText.toIntOrNull()
    val newH = heightText.toIntOrNull()
    val sizeOk = newW != null && newH != null && newW in 5..500 && newH in 5..500
    val hasSelection = engine.hasSelection
    val bounds = engine.selectionBounds()

    StudioSheet(
        title = "画布 / 工程",
        subtitle = "${engine.width}×${engine.height} · ${engine.layerCount} 层 · ${engine.frameCount} 帧",
        onClose = onClose,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SheetPrimaryButton(
                    text = "只改画布",
                    modifier = Modifier.weight(1f),
                    enabled = sizeOk,
                ) {
                    session.act { it.resizeCanvas(newW!!, newH!!, 0.5f, 0.5f) }
                }
                SheetPrimaryButton(
                    text = "缩放内容",
                    modifier = Modifier.weight(1f),
                    enabled = sizeOk,
                ) {
                    session.act { it.scaleContent(newW!!, newH!!) }
                }
            }
        },
    ) {
        PrimaryTabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }) { CanvasTabLabel("画布") }
            Tab(selected = tab == 1, onClick = { tab = 1 }) { CanvasTabLabel("变换") }
            Tab(selected = tab == 2, onClick = { tab = 2 }) { CanvasTabLabel("显示") }
        }

        when (tab) {
            0 -> {
                SheetSection(title = "工程名称") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it.take(40)
                            session.name = name
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SheetSection(title = "尺寸（5–500）") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = widthText,
                            onValueChange = { raw ->
                                widthText = raw.filter { it.isDigit() }.take(3)
                                if (lockAspect) {
                                    val ratio = engine.height.toFloat() / engine.width
                                    widthText.toIntOrNull()?.let {
                                        heightText = (it * ratio)
                                            .roundToInt()
                                            .coerceIn(5, 500)
                                            .toString()
                                    }
                                }
                            },
                            label = { Text("宽") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { raw ->
                                heightText = raw.filter { it.isDigit() }.take(3)
                                if (lockAspect) {
                                    val ratio = engine.width.toFloat() / engine.height
                                    heightText.toIntOrNull()?.let {
                                        widthText = (it * ratio)
                                            .roundToInt()
                                            .coerceIn(5, 500)
                                            .toString()
                                    }
                                }
                            },
                            label = { Text("高") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    MiniChip("锁定比例", lockAspect) { lockAspect = !lockAspect }
                }
                SheetSection(title = "常用尺寸", hint = "点一下立刻应用，内容居中") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(16, 32, 48, 64, 96, 128, 256, 500).forEach { n ->
                            MiniChip(
                                "$n×$n",
                                engine.width == n && engine.height == n,
                            ) {
                                widthText = n.toString()
                                heightText = n.toString()
                                session.act { it.resizeCanvas(n, n, 0.5f, 0.5f) }
                            }
                        }
                    }
                }
                SheetSection(
                    title = "色卡",
                    hint = "换色卡只改索引含义，已经画下的格子不会自动重映射",
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        palettes.forEach { p ->
                            MiniChip(
                                "${p.brandLabel} ${if (p.beadSize.name == "MM2_6") "2.6" else "5"}mm",
                                p.brand == session.palette.brand &&
                                    p.beadSize == session.palette.beadSize,
                            ) { onPalette(p) }
                        }
                    }
                }
            }

            1 -> {
                SheetSection(title = "翻转 / 旋转", hint = "作用于所有帧，可撤销") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CanvasAction("水平翻转", Modifier.weight(1f)) {
                            session.act { it.flipHorizontal() }
                        }
                        CanvasAction("垂直翻转", Modifier.weight(1f)) {
                            session.act { it.flipVertical() }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CanvasAction("左转 90°", Modifier.weight(1f)) {
                            session.act { it.rotate90(false) }
                        }
                        CanvasAction("右转 90°", Modifier.weight(1f)) {
                            session.act { it.rotate90(true) }
                        }
                        CanvasAction("180°", Modifier.weight(1f)) {
                            session.act { it.rotate180() }
                        }
                    }
                }
                SheetSection(title = "整体位移", hint = "所有帧一起移动，超出画布的部分会被裁掉") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CanvasAction("← 1", Modifier.weight(1f)) { session.act { it.shift(-1, 0) } }
                        CanvasAction("→ 1", Modifier.weight(1f)) { session.act { it.shift(1, 0) } }
                        CanvasAction("↑ 1", Modifier.weight(1f)) { session.act { it.shift(0, -1) } }
                        CanvasAction("↓ 1", Modifier.weight(1f)) { session.act { it.shift(0, 1) } }
                    }
                }
                SheetSection(title = "对称绘制", hint = "画一格同时落镜像 / 旋转格；旋转适合曼陀罗、雪花") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SymmetryMode.entries.forEach { mode ->
                            MiniChip(mode.label, session.symmetry == mode) {
                                session.symmetry = mode
                                session.touch()
                            }
                        }
                    }
                }
                SheetSection(title = "裁剪") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CanvasAction(
                            "裁剪到选区",
                            Modifier.weight(1f),
                            enabled = hasSelection && bounds != null,
                        ) {
                            val b = bounds!!
                            session.act {
                                it.crop(b[0], b[1], b[2] - b[0] + 1, b[3] - b[1] + 1)
                            }
                        }
                        CanvasAction("裁掉空白边", Modifier.weight(1f)) {
                            val b = engine.contentBounds()
                            if (b != null) {
                                session.act {
                                    it.crop(b[0], b[1], b[2] - b[0] + 1, b[3] - b[1] + 1)
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                SheetSection(title = "画面辅助") {
                    SheetSwitchRow(
                        title = "显示网格",
                        checked = session.showGrid,
                        onCheckedChange = { session.showGrid = it },
                    )
                    SheetSwitchRow(
                        title = "显示中心线",
                        checked = session.showCenterLines,
                        onCheckedChange = { session.showCenterLines = it },
                    )
                    SheetSwitchRow(
                        title = "显示色号",
                        subtitle = "放大到 22px 以上才绘制",
                        checked = session.showCodes,
                        onCheckedChange = { session.showCodes = it },
                    )
                }
                SheetSection(title = "笔刷") {
                    SheetSwitchRow(
                        title = "像素完美",
                        subtitle = "滑动时去掉 L 形拐角的多余像素",
                        checked = session.pixelPerfect,
                        onCheckedChange = { session.pixelPerfect = it },
                    )
                    SheetSwitchRow(
                        title = "圆形笔刷",
                        subtitle = "关闭为方形笔刷",
                        checked = session.circleBrush,
                        onCheckedChange = { session.circleBrush = it },
                    )
                }
                SheetSection(title = "动画") {
                    SheetSwitchRow(
                        title = "洋葱皮",
                        subtitle = "显示上一帧残影",
                        checked = session.onionSkin,
                        onCheckedChange = { session.onionSkin = it },
                    )
                }
                SheetSection(
                    title = "浮窗位置",
                    hint = "拖住顶部双横线移动浮窗；点小箭头收起/展开",
                ) {
                    CanvasAction("重置两块浮窗", Modifier.fillMaxWidth()) { onResetRails() }
                }
            }
        }
    }
}

@Composable
private fun CanvasTabLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(vertical = 10.dp),
    )
}

@Composable
private fun CanvasAction(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.surface
                else Color.Transparent,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.36f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
