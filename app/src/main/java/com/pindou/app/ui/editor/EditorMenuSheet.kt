package com.pindou.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pindou.app.core.editor.ColorReplaceOps
import com.pindou.app.core.editor.EditorEngine
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.ui.AppViewModel
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.theme.Spacing
import kotlin.math.roundToInt

/** 编辑器右上角「⋯」菜单：参考图、画像素、色板、页面与导出操作。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorMenuSheet(
    vm: AppViewModel,
    palette: BeadPalette,
    engine: EditorEngine,
    onDismiss: () -> Unit,
    onOpenManage: () -> Unit,
    onOpenReplace: (Int) -> Unit,
    onOpenResize: () -> Unit,
    onOpenCrop: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "更多操作",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("参考图对比", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (vm.refBitmap == null) "当前图纸没有原图" else "在原图上对位涂色",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = vm.showRefOverlay,
                    onCheckedChange = { vm.toggleRefOverlay() },
                    enabled = vm.refBitmap != null,
                )
            }
            if (vm.showRefOverlay && vm.refBitmap != null) {
                Text(
                    "透明度 ${(vm.refOpacity * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = vm.refOpacity,
                    onValueChange = { vm.updateRefOpacity(it) },
                    valueRange = 0.05f..1f,
                )
            }
            MenuItemRow(
                icon = MorphIcons.Brush,
                title = "在画像素中编辑",
                subtitle = "保留当前所有页，转成帧/图层工作区",
            ) {
                onDismiss()
                vm.editInStudio()
            }
            MenuItemRow(
                icon = MorphIcons.PaintBucket,
                title = "色板管理",
                subtitle = "换品牌 / 合并相近色",
            ) {
                onDismiss()
                onOpenManage()
            }
            MenuItemRow(
                icon = MorphIcons.Palette,
                title = "替换相似颜色",
                subtitle = "同色系并成一个色号，减少换豆",
            ) {
                onDismiss()
                // 默认源色取「当前画笔色」；画笔停在一个没用过的色号上时，
                // 直接跳到用量最少的已用色——否则一进来就是「0 格 / 没有可替换的格子」，
                // 用户还得自己翻色卡找哪个色真正在图上。
                val cur = vm.editorColor.coerceIn(0, palette.colors.lastIndex)
                val use = ColorReplaceOps.usage(engine.pages, palette.colors.size)
                val source = if (use.getOrElse(cur) { 0 } > 0) {
                    cur
                } else {
                    use.indices.filter { use[it] > 0 }.minByOrNull { use[it] } ?: cur
                }
                onOpenReplace(source)
            }
            MenuItemRow(
                icon = MorphIcons.Copy,
                title = "复制当前页",
                subtitle = "新页继承当前页内容与锁定状态",
            ) {
                vm.addEditorPage(true)
                onDismiss()
            }
            MenuItemRow(
                icon = MorphIcons.Trash,
                title = "删除当前页",
                subtitle = if (engine.pageCount <= 1) "至少保留一页" else "可撤销",
                enabled = engine.pageCount > 1,
            ) {
                vm.removeEditorPage(engine.pageIndex)
                onDismiss()
            }
            MenuItemRow(
                icon = MorphIcons.SwapHorizontal,
                title = "改尺寸",
                subtitle = "所有页内容居中保留",
            ) {
                onDismiss()
                onOpenResize()
            }
            MenuItemRow(
                icon = MorphIcons.Grid,
                title = "裁剪图纸",
                subtitle = "按坐标裁掉四周多余的格子",
            ) {
                onDismiss()
                onOpenCrop()
            }
            MenuItemRow(
                icon = MorphIcons.FlipHorizontal,
                title = "水平翻转",
                subtitle = "左右镜像当前所有页，可撤销",
            ) {
                vm.flipEditorHorizontal()
                onDismiss()
            }
            MenuItemRow(
                icon = MorphIcons.SwapHorizontal,
                title = "垂直翻转",
                subtitle = "上下镜像当前所有页，可撤销",
            ) {
                vm.flipEditorVertical()
                onDismiss()
            }
            MenuItemRow(
                icon = MorphIcons.RotateCcw,
                title = "左转 90°",
                subtitle = "宽高互换，所有页一起旋转",
            ) {
                vm.rotateEditor(clockwise = false)
                onDismiss()
            }
            MenuItemRow(
                icon = MorphIcons.RotateCw,
                title = "右转 90°",
                subtitle = "宽高互换，所有页一起旋转",
            ) {
                vm.rotateEditor(clockwise = true)
                onDismiss()
            }
            MenuItemRow(
                icon = MorphIcons.Download,
                title = "导出 .bpat 图纸",
                subtitle = "包含全部页、色卡与锁定状态",
            ) {
                onDismiss()
                vm.exportBpatFile()
            }
        }
    }
}
