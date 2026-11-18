@file:OptIn(ExperimentalLayoutApi::class)
package com.pindou.app.ui.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pindou.app.core.studio.StudioExport
import com.pindou.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioExportPanel(
    session: StudioSession,
    onExportPixel: (scale: Int, transparent: Boolean) -> Unit,
    onExportGif: (scale: Int) -> Unit,
    onExportCode: () -> Unit,
    onExportSymbol: () -> Unit,
    onExportPdf: () -> Unit,
    onExportPalette: () -> Unit,
    onClose: () -> Unit,
) {
    if (session.revision < 0) return
    val engine = session.engine
    var pngScale by remember { mutableIntStateOf(8) }
    var gifScale by remember { mutableIntStateOf(2) }
    var mode by remember { mutableIntStateOf(0) }
    val effective = StudioExport.effectiveScale(engine, pngScale)
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.lg),
        ) {
            PanelHeader(
                "导出",
                "${engine.width}×${engine.height} · ${engine.usedColorCount()} 色 · ${engine.frameCount} 帧",
                onClose,
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                ActionChip("像素 PNG", selected = mode == 0) { mode = 0 }
                ActionChip("色号图", selected = mode == 1) { mode = 1 }
                ActionChip("PDF", selected = mode == 2) { mode = 2 }
                ActionChip("调色板", selected = mode == 3) { mode = 3 }
                ActionChip("GIF", selected = mode == 4) { mode = 4 }
            }

            when (mode) {
                0 -> {
                    SectionLabel("像素 PNG：透明背景，无标题 / 坐标 / 色号")
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        listOf(1, 2, 4, 8, 16, 32, 64).forEach { s ->
                            ActionChip("${s}×", selected = pngScale == s) { pngScale = s }
                        }
                    }
                    ToggleRow(
                        title = "透明背景",
                        subtitle = "关闭后铺白底，方便直接发图",
                        checked = session.transparentBackground,
                        onCheckedChange = { session.transparentBackground = it },
                    )
                    Text(
                        if (effective == pngScale) {
                            "输出 ${engine.width * pngScale}×${engine.height * pngScale}"
                        } else {
                            "输出 ${engine.width * effective}×${engine.height * effective}（受 18M 像素内存上限限制，已从 ${pngScale}× 降到 ${effective}×）"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
                    Button(
                        onClick = { onExportPixel(pngScale, session.transparentBackground) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    ) { Text("导出像素 PNG") }
                }
                1 -> {
                    SectionLabel("图纸：两行标题 / 四边坐标 / 格内色号 / 底部用量条")
                    ToggleRow(
                        "简化标号",
                        "同色区域只在最外圈标色号，内部留空；关闭则每格都标",
                        checked = session.simplifiedLabels,
                        onCheckedChange = { session.simplifiedLabels = it },
                    )
                    Text(
                        "每帧导出为一页，多帧会纵向拼成一张长图。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
                    Button(
                        onClick = onExportCode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    ) { Text("导出色号图 PNG（${engine.frameCount} 帧）") }
                    Button(
                        onClick = onExportSymbol,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    ) { Text("导出符号版 PNG") }
                }
                2 -> {
                    SectionLabel("PDF 打印版")
                    ToggleRow(
                        "简化标号",
                        "同色区域只在最外圈标色号，内部留空；关闭则每格都标",
                        checked = session.simplifiedLabels,
                        onCheckedChange = { session.simplifiedLabels = it },
                    )
                    Text(
                        "A4 自动横竖排，每帧独立分页，格内保留色号。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
                    Button(
                        onClick = onExportPdf,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    ) { Text("导出 PDF（${engine.frameCount} 页）") }
                }
                3 -> {
                    SectionLabel("调色板 + 用量")
                    Text(
                        "列出当前工程用到的所有色号、色块和颗数，方便对照买豆。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
                    Button(
                        onClick = onExportPalette,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    ) { Text("导出调色板 PNG") }
                }
                else -> {
                    SectionLabel("GIF 动画")
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        listOf(1, 2, 4, 8).forEach { s ->
                            ActionChip("${s}×", selected = gifScale == s) { gifScale = s }
                        }
                    }
                    Text(
                        "按每帧「停留时间」播放，循环。${engine.frameCount} 帧 / ${engine.width * gifScale}×${engine.height * gifScale}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
                    Button(
                        onClick = { onExportGif(gifScale) },
                        enabled = engine.frameCount > 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    ) { Text(if (engine.frameCount > 1) "导出 GIF" else "至少需要 2 帧") }
                }
            }

            Text(
                "文件保存到系统「下载」目录，不自动弹分享面板。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
    }
}
