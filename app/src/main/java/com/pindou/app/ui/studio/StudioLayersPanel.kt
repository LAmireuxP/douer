@file:OptIn(ExperimentalLayoutApi::class)

package com.pindou.app.ui.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pindou.app.core.studio.StudioBlendMode
import com.pindou.app.core.studio.StudioImageImport
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.theme.Spacing
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 图层 sheet。
 *
 * 和旧版的差别是信息分层：顶部先给「导入底层 / 新建 / 复制」三个最常用动作，
 * 中间是图层栈（每行右侧的上下箭头换成了独立的「上移/下移」），
 * 底部才是破坏性动作（删除/合并/压平/清空）和当前层属性。
 * 这样打开面板第一眼就是图层列表，不用先越过两排 chip。
 */
@Composable
fun StudioLayersPanel(
    session: StudioSession,
    tourActive: Boolean = false,
    onTourDone: () -> Unit = {},
    onClose: () -> Unit,
) {
    if (session.revision < 0) return
    val engine = session.engine
    val reference = engine.reference
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var renameIndex by remember { mutableIntStateOf(-1) }
    var renameText by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null || importing) return@rememberLauncherForActivityResult
        importing = true
        importStatus = "正在把图片铺到底层…"
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    StudioImageImport.decodeReference(
                        resolver = context.contentResolver,
                        uri = uri,
                        canvasWidth = engine.width,
                        canvasHeight = engine.height,
                    )
                }
            }.onSuccess { imported ->
                session.act { it.importReferenceImage(imported) }
                importStatus = "已导入底层原图；软件不会自动转像素，请在上层手动画"
                // 只有教程状态才收起面板并继续；手动打开图层时保持原流程
                if (tourActive) onTourDone()
            }.onFailure {
                importStatus = "导入失败：${it.message ?: "无法读取图片"}"
            }
            importing = false
        }
    }

    StudioSheet(
        title = "图层",
        subtitle = "第 ${engine.frameIndex + 1} 帧 · ${engine.layerCount} 层 · 当前「${engine.activeLayer.name}」",
        onClose = onClose,
    ) {
        if (tourActive) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        MorphIcons.ImagePlus,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(20.dp),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    ) {
                        Text(
                            "下一步：导入底层图片",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "点下面被高亮的「导入底层图片」选一张照片。" +
                                "照片只铺在绘制层下面做参考，不会自动转成像素，也不参与 PNG/PDF/GIF 导出。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onTourDone,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 0.dp,
                        ),
                    ) {
                        Text("知道了", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        SheetSection(
            title = "添加",
            hint = if (reference != null) "底层已铺一张参考图 · 不参与导出" else "底层图只做参考，不会自动转成像素",
        ) {
            SheetPrimaryButton(
                text = if (importing) "正在导入…" else "导入底层图片",
                icon = MorphIcons.ImagePlus,
                enabled = !importing,
                onClick = { imageLauncher.launch(arrayOf("image/*")) },
            )
            if (importStatus != null) {
                Text(
                    importStatus!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallSheetAction(
                    text = "+ 新图层",
                    modifier = Modifier.weight(1f),
                ) { session.act { it.addLayer(false) } }
                SmallSheetAction(
                    text = "复制图层",
                    modifier = Modifier.weight(1f),
                ) { session.act { it.addLayer(true) } }
                SmallSheetAction(
                    text = "清除底图",
                    modifier = Modifier.weight(1f),
                    enabled = reference != null,
                ) { session.act { it.clearReference() } }
            }
        }

        SheetSection(title = "图层栈（上 = 更靠前）") {
            for (index in engine.layerCount - 1 downTo 0) {
                val layer = engine.layers[index]
                val active = index == engine.activeLayerIndex
                SheetRow(
                    title = layer.name,
                    subtitle = "${layer.blend.label} · ${(layer.opacity * 100).roundToInt()}%",
                    selected = active,
                    onClick = { session.act { it.setActiveLayer(index) } },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    session.act { it.setLayerVisible(index, !layer.visible) }
                                },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    if (layer.visible) MorphIcons.Eye else MorphIcons.EyeOff,
                                    contentDescription = if (layer.visible) "隐藏" else "显示",
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    session.act { it.setLayerLocked(index, !layer.locked) }
                                },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    if (layer.locked) MorphIcons.Lock else MorphIcons.LockOpen,
                                    contentDescription = if (layer.locked) "解锁" else "锁定",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    renameIndex = index
                                    renameText = layer.name
                                },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    MorphIcons.Type,
                                    contentDescription = "重命名",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallSheetAction(
                    text = "上移",
                    modifier = Modifier.weight(1f),
                    enabled = engine.activeLayerIndex < engine.layerCount - 1,
                ) {
                    val i = engine.activeLayerIndex
                    session.act { it.moveLayer(i, i + 1) }
                }
                SmallSheetAction(
                    text = "下移",
                    modifier = Modifier.weight(1f),
                    enabled = engine.activeLayerIndex > 0,
                ) {
                    val i = engine.activeLayerIndex
                    session.act { it.moveLayer(i, i - 1) }
                }
                SmallSheetAction(
                    text = "向下合并",
                    modifier = Modifier.weight(1f),
                    enabled = engine.activeLayerIndex > 0,
                ) {
                    val i = engine.activeLayerIndex
                    session.act { it.mergeDown(i) }
                }
            }
        }

        if (reference != null) {
            SheetSection(title = "底层原图（不参与导出）") {
                SheetRow(
                    title = "底层原图",
                    subtitle = "${reference.width}×${reference.height} · 原画参考",
                    icon = MorphIcons.ImagePlus,
                    onClick = { session.act { it.setReferenceVisible(!reference.visible) } },
                    trailing = {
                        Icon(
                            if (reference.visible) MorphIcons.Eye else MorphIcons.EyeOff,
                            contentDescription = if (reference.visible) "隐藏原图" else "显示原图",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }

        SheetSection(title = "当前图层") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "不透明度",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(engine.activeLayer.opacity * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Slider(
                value = engine.activeLayer.opacity,
                onValueChange = { v ->
                    session.act { it.setLayerOpacity(it.activeLayerIndex, v) }
                },
                valueRange = 0f..1f,
                modifier = Modifier.height(32.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StudioBlendMode.entries.forEach { mode ->
                    MiniChip(
                        text = mode.label,
                        selected = engine.activeLayer.blend == mode,
                    ) {
                        session.act { it.setLayerBlend(it.activeLayerIndex, mode) }
                    }
                }
            }
        }

        SheetSection(title = "整理", hint = "删除和压平都会影响整帧，操作可撤销") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallSheetAction(
                    text = "压平",
                    modifier = Modifier.weight(1f),
                    enabled = engine.layerCount > 1,
                ) { session.act { it.flattenFrame() } }
                SmallSheetAction(
                    text = "清空图层",
                    modifier = Modifier.weight(1f),
                ) { session.act { it.clearLayer() } }
                SmallSheetAction(
                    text = "删除图层",
                    modifier = Modifier.weight(1f),
                    enabled = engine.layerCount > 1,
                    danger = true,
                ) { session.act { it.deleteLayer(it.activeLayerIndex) } }
            }
        }
    }

    if (renameIndex >= 0) {
        AlertDialog(
            onDismissRequest = { renameIndex = -1 },
            title = { Text("图层名称") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(40) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        session.act { it.renameLayer(renameIndex, renameText) }
                        renameIndex = -1
                    },
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { renameIndex = -1 }) { Text("取消") } },
        )
    }
}

/** 三列以内的次要动作：白底、1dp 边、40dp 高。 */
@Composable
private fun SmallSheetAction(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (danger && enabled) MaterialTheme.colorScheme.error.copy(alpha = 0.07f)
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 属性小胶囊：混合模式、帧率这类互斥短选项。 */
@Composable
internal fun MiniChip(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                selected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
