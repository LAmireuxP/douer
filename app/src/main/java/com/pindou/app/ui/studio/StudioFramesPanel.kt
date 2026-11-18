@file:OptIn(ExperimentalLayoutApi::class)

package com.pindou.app.ui.studio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pindou.app.core.studio.StudioExport
import kotlin.math.roundToInt

/**
 * 帧 / 动画 sheet。
 *
 * 市面上的逐帧工具（Piskel、FlipaClip）都是「帧条在上、参数在下」：
 * 这里第一屏就能横向翻帧，第二屏才是播放/速度/洋葱皮。
 * 播放键做成独立的 tonal 按钮，不再和「+ 空白帧」混在一排同权重 chip 里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioFramesPanel(
    session: StudioSession,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit,
) {
    if (session.revision < 0) return
    val engine = session.engine
    var tab by remember { mutableIntStateOf(0) }

    StudioSheet(
        title = "帧 / 动画",
        subtitle = "${engine.frameCount} 帧 · ${engine.width}×${engine.height} · 当前第 ${engine.frameIndex + 1} 帧",
        onClose = onClose,
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            items(engine.frameCount) { index ->
                val selected = index == engine.frameIndex
                val bitmap = remember(session.revision, index) {
                    StudioExport.frameBitmap(engine, index)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { session.act { it.setFrame(index) } }
                        .padding(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (session.transparentBackground) Color(0xFFE8EEF6)
                                else Color.White,
                            )
                            .selectedBorder(selected, 9)
                            .padding(3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "第 ${index + 1} 帧",
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        "${engine.frames[index].holdMs}ms",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(
                onClick = onTogglePlay,
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(if (playing) "暂停预览" else "播放预览")
            }
            SmallFrameAction(
                text = "+ 空白帧",
                modifier = Modifier.weight(1f),
            ) { session.act { it.addFrame(false) } }
            SmallFrameAction(
                text = "复制帧",
                modifier = Modifier.weight(1f),
            ) { session.act { it.addFrame(true) } }
        }

        PrimaryTabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }) { TabLabel("播放") }
            Tab(selected = tab == 1, onClick = { tab = 1 }) { TabLabel("整理") }
        }

        if (tab == 0) {
            SheetSection(
                title = "当前帧停留",
                hint = "每帧单独计时；导出 GIF 时按这里的时间播放",
            ) {
                SheetSlider(
                    label = "第 ${engine.frameIndex + 1} 帧",
                    value = engine.frameHold().toFloat(),
                    valueText = "${engine.frameHold()} ms",
                    range = 20f..2000f,
                ) { v ->
                    session.act { it.setFrameHold(it.frameIndex, v.roundToInt()) }
                }
            }
            SheetSection(title = "整体帧率", hint = "点一下会把所有帧的停留时间改成同一值") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(4, 6, 8, 10, 12, 15, 24).forEach { fps ->
                        MiniChip(
                            text = "$fps fps",
                            selected = session.fps == fps,
                        ) {
                            session.fps = fps
                            session.act { eng ->
                                val hold = (1000.0 / fps).roundToInt()
                                eng.frames.indices.forEach { eng.setFrameHold(it, hold) }
                            }
                        }
                    }
                }
            }
            SheetSection(title = "辅助") {
                SheetSwitchRow(
                    title = "洋葱皮",
                    subtitle = "显示上一帧的 25% 残影，用来对位",
                    checked = session.onionSkin,
                    onCheckedChange = { session.onionSkin = it; session.touch() },
                )
            }
        } else {
            SheetSection(title = "当前帧", hint = "帧顺序决定播放顺序") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallFrameAction(
                        text = "倒序全部",
                        modifier = Modifier.weight(1f),
                    ) { session.act { it.reverseFrames() } }
                    SmallFrameAction(
                        text = "删除本帧",
                        modifier = Modifier.weight(1f),
                        enabled = engine.frameCount > 1,
                        danger = true,
                    ) { session.act { it.deleteFrame(it.frameIndex) } }
                }
            }
            SheetSection(title = "跳到帧") {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    engine.frames.indices.forEach { index ->
                        MiniChip(
                            text = "${index + 1}",
                            selected = index == engine.frameIndex,
                        ) { session.act { it.setFrame(index) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(vertical = 10.dp),
    )
}

@Composable
private fun SmallFrameAction(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (danger && enabled) MaterialTheme.colorScheme.error.copy(alpha = 0.07f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                danger -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
        )
    }
}
