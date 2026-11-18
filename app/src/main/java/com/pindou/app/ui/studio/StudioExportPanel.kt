@file:OptIn(ExperimentalLayoutApi::class)

package com.pindou.app.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pindou.app.core.studio.StudioExport
import com.pindou.app.ui.icon.MorphIcons
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment

private enum class ExportFormat(val label: String, val hint: String) {
    PNG("像素 PNG", "透明底，无文字"),
    CODE("色号图", "带坐标与色号"),
    PDF("PDF", "A4 打印分页"),
    PALETTE("调色板", "色号 + 用量"),
    GIF("GIF", "多帧动画"),
}

/**
 * 导出 sheet。
 *
 * 参照 Procreate / Pixel Studio 的导出流程：先选格式（横向五格），
 * 再看到该格式的全部参数，最后只有一个整宽主按钮。
 * 旧版把五种格式做成同一排 chip、下面再接按钮，视觉上分不出「选择」和「执行」。
 */
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
    var format by remember { mutableStateOf(ExportFormat.PNG) }
    val effective = StudioExport.effectiveScale(engine, pngScale)

    StudioSheet(
        title = "导出",
        subtitle = "${engine.width}×${engine.height} · ${engine.usedColorCount()} 色 · ${engine.frameCount} 帧 · 保存在系统「下载」",
        onClose = onClose,
        footer = {
            when (format) {
                ExportFormat.PNG -> SheetPrimaryButton(
                    text = "导出像素 PNG",
                    icon = MorphIcons.Download,
                ) { onExportPixel(pngScale, session.transparentBackground) }
                ExportFormat.CODE -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SheetPrimaryButton(
                        text = "导出色号图 PNG（${engine.frameCount} 帧）",
                        icon = MorphIcons.Download,
                    ) { onExportCode() }
                    SheetPrimaryButton(
                        text = "导出符号版 PNG",
                        icon = MorphIcons.Grid,
                    ) { onExportSymbol() }
                }
                ExportFormat.PDF -> SheetPrimaryButton(
                    text = "导出 PDF（${engine.frameCount} 页）",
                    icon = MorphIcons.Download,
                ) { onExportPdf() }
                ExportFormat.PALETTE -> SheetPrimaryButton(
                    text = "导出调色板 PNG",
                    icon = MorphIcons.Palette,
                ) { onExportPalette() }
                ExportFormat.GIF -> SheetPrimaryButton(
                    text = if (engine.frameCount > 1) "导出 GIF" else "至少需要 2 帧",
                    icon = MorphIcons.Download,
                    enabled = engine.frameCount > 1,
                ) { onExportGif(gifScale) }
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExportFormat.entries.forEach { item ->
                FormatTile(
                    format = item,
                    selected = format == item,
                    modifier = Modifier.weight(1f),
                ) { format = item }
            }
        }

        when (format) {
            ExportFormat.PNG -> {
                SheetSection(
                    title = "尺寸倍率",
                    hint = if (effective == pngScale) {
                        "输出 ${engine.width * pngScale}×${engine.height * pngScale} 像素"
                    } else {
                        "受 18M 像素内存上限限制，实际按 ${effective}× 导出"
                    },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(1, 2, 4, 8, 16, 32, 64).forEach { s ->
                            MiniChip("${s}×", pngScale == s) { pngScale = s }
                        }
                    }
                }
                SheetSection(title = "背景") {
                    SheetSwitchRow(
                        title = "透明背景",
                        subtitle = "关闭后铺白底，方便直接发图",
                        checked = session.transparentBackground,
                        onCheckedChange = { session.transparentBackground = it },
                    )
                }
            }

            ExportFormat.CODE -> {
                SheetSection(
                    title = "标号方式",
                    hint = "每帧一页；多帧会在同一张长图里纵向拼接",
                ) {
                    SheetSwitchRow(
                        title = "简化标号",
                        subtitle = "同色区域只在最外圈标色号，内部留空",
                        checked = session.simplifiedLabels,
                        onCheckedChange = { session.simplifiedLabels = it },
                    )
                }
                ExportPreviewNote("两行标题 · 四边坐标 · 格内色号 · 底部用量条")
            }

            ExportFormat.PDF -> {
                SheetSection(
                    title = "打印排版",
                    hint = "A4 自动横竖排，每帧独立分页，格内保留色号",
                ) {
                    SheetSwitchRow(
                        title = "简化标号",
                        subtitle = "同色区域只在最外圈标色号",
                        checked = session.simplifiedLabels,
                        onCheckedChange = { session.simplifiedLabels = it },
                    )
                }
                ExportPreviewNote("${engine.frameCount} 页 · 按格子实际毫米尺寸排版")
            }

            ExportFormat.PALETTE -> {
                ExportPreviewNote("列出当前工程用到的所有色号、色块和颗数，方便对照买豆。")
            }

            ExportFormat.GIF -> {
                SheetSection(
                    title = "尺寸倍率",
                    hint = "${engine.frameCount} 帧 / ${engine.width * gifScale}×${engine.height * gifScale} 像素",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(1, 2, 4, 8).forEach { s ->
                            MiniChip("${s}×", gifScale == s) { gifScale = s }
                        }
                    }
                }
                SheetSection(title = "播放", hint = "按每帧「停留时间」播放并循环") {
                    Text(
                        "帧率在「帧」面板里调；这里只负责把当前帧序导出。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatTile(
    format: ExportFormat,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            format.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            format.hint,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ExportPreviewNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
