@file:OptIn(ExperimentalLayoutApi::class)
package com.pindou.app.ui.studio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.pindou.app.ui.theme.Spacing
import kotlin.math.roundToInt

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
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.lg),
        ) {
            PanelHeader(
                "帧 / 动画",
                "${engine.frameCount} 帧 · ${engine.width}×${engine.height} · 当前第 ${engine.frameIndex + 1} 帧",
                onClose,
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionChip("+ 空白帧") { session.act { it.addFrame(false) } }
                ActionChip("复制帧") { session.act { it.addFrame(true) } }
                ActionChip("删除帧", enabled = engine.frameCount > 1) {
                    session.act { it.deleteFrame(it.frameIndex) }
                }
                ActionChip("倒序") { session.act { it.reverseFrames() } }
                ActionChip(if (playing) "暂停" else "播放", selected = playing, onClick = onTogglePlay)
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = Spacing.md),
            ) {
                items(engine.frameCount) { index ->
                    val selected = index == engine.frameIndex
                    val bitmap = remember(session.revision, index) {
                        StudioExport.frameBitmap(engine, index)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { session.act { it.setFrame(index) } }
                            .padding(3.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (session.transparentBackground) Color(0xFFE8EEF6)
                                    else Color.White,
                                )
                                .selectedBorder(selected, 6)
                                .padding(2.dp),
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
                        )
                        Text(
                            "${engine.frames[index].holdMs}ms",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionLabel("当前帧停留时间：${engine.frameHold()} ms")
            Slider(
                value = engine.frameHold().toFloat(),
                onValueChange = { v -> session.act { it.setFrameHold(it.frameIndex, v.roundToInt()) } },
                valueRange = 20f..2000f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
            )

            SectionLabel("全部帧按帧率重设（GIF 用）")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(4, 6, 8, 10, 12, 15, 24).forEach { fps ->
                    ActionChip("$fps fps", selected = session.fps == fps) {
                        session.fps = fps
                        session.act { eng ->
                            val hold = (1000.0 / fps).roundToInt()
                            eng.frames.indices.forEach { eng.setFrameHold(it, hold) }
                        }
                    }
                }
            }

            ToggleRow(
                title = "洋葱皮",
                subtitle = "显示上一帧的 25% 残影，对位动画用",
                checked = session.onionSkin,
                onCheckedChange = { session.onionSkin = it; session.touch() },
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                engine.frames.indices.forEach { index ->
                    ActionChip("${index + 1}", selected = index == engine.frameIndex) {
                        session.act { it.setFrame(index) }
                    }
                }
            }
        }
    }
}
