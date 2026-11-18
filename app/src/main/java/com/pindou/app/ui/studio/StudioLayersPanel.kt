@file:OptIn(ExperimentalLayoutApi::class)
package com.pindou.app.ui.studio

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.pindou.app.core.studio.StudioBlendMode
import com.pindou.app.core.studio.StudioImageImport
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.theme.Spacing
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
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
                Toast.makeText(context, "原图已铺到底层，可直接在上层点像素", Toast.LENGTH_SHORT).show()
                // 只有教程状态才收起面板并继续；手动打开图层时保持原流程
                if (tourActive) onTourDone()
            }.onFailure {
                importStatus = "导入失败：${it.message ?: "无法读取图片"}"
            }
            importing = false
        }
    }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.lg),
        ) {
            PanelHeader(
                "图层",
                "第 ${engine.frameIndex + 1} 帧 · ${engine.layerCount} 层 · 当前「${engine.activeLayer.name}」",
                onClose,
            )
            if (tourActive) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                            RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    TextButton(
                        onClick = onTourDone,
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("知道了", style = MaterialTheme.typography.labelMedium)
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
                ActionChip(
                    text = if (importing) "正在导入…" else "导入底层图片",
                    enabled = !importing,
                    attention = tourActive,
                ) { imageLauncher.launch(arrayOf("image/*")) }
                ActionChip("清除底层图", enabled = reference != null) {
                    session.act { it.clearReference() }
                }
                ActionChip("+ 新图层") { session.act { it.addLayer(false) } }
                ActionChip("复制") { session.act { it.addLayer(true) } }
                ActionChip("删除", enabled = engine.layerCount > 1) {
                    session.act { it.deleteLayer(it.activeLayerIndex) }
                }
                ActionChip("向下合并", enabled = engine.activeLayerIndex > 0) {
                    session.act { it.mergeDown(it.activeLayerIndex) }
                }
                ActionChip("压平", enabled = engine.layerCount > 1) {
                    session.act { it.flattenFrame() }
                }
                ActionChip("清空") { session.act { it.clearLayer() } }
            }
            importStatus?.let { status ->
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = 2.dp),
                )
            }

            SectionLabel("图层栈（上 = 更靠前）")
            for (index in engine.layerCount - 1 downTo 0) {
                val layer = engine.layers[index]
                val active = index == engine.activeLayerIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.sm, vertical = 1.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent,
                        )
                        .clickable { session.act { it.setActiveLayer(index) } }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { session.act { it.setLayerVisible(index, !layer.visible) } },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            if (layer.visible) MorphIcons.Eye else MorphIcons.EyeOff,
                            contentDescription = if (layer.visible) "隐藏" else "显示",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = { session.act { it.setLayerLocked(index, !layer.locked) } },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            if (layer.locked) MorphIcons.Lock else MorphIcons.LockOpen,
                            contentDescription = if (layer.locked) "解锁" else "锁定",
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                renameIndex = index
                                renameText = layer.name
                            },
                    ) {
                        Text(
                            layer.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${layer.blend.label} · ${(layer.opacity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { session.act { it.moveLayer(index, index + 1) } },
                        enabled = index < engine.layerCount - 1,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(MorphIcons.ArrowUp, contentDescription = "上移", modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = { session.act { it.moveLayer(index, index - 1) } },
                        enabled = index > 0,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(MorphIcons.ArrowDown, contentDescription = "下移", modifier = Modifier.size(17.dp))
                    }
                }
            }

            if (reference != null) {
                SectionLabel("底层原图（不参与导出）")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.sm, vertical = 1.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { session.act { it.setReferenceVisible(!reference.visible) } },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            if (reference.visible) MorphIcons.Eye else MorphIcons.EyeOff,
                            contentDescription = if (reference.visible) "隐藏原图" else "显示原图",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("底层原图", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${reference.width}×${reference.height} · 原画参考，不参与导出",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionLabel("当前图层不透明度")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = engine.activeLayer.opacity,
                    onValueChange = { v -> session.act { it.setLayerOpacity(it.activeLayerIndex, v) } },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(engine.activeLayer.opacity * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(44.dp),
                )
            }

            SectionLabel("混合模式")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                StudioBlendMode.entries.forEach { mode ->
                    ActionChip(
                        text = mode.label,
                        selected = engine.activeLayer.blend == mode,
                    ) {
                        session.act { it.setLayerBlend(it.activeLayerIndex, mode) }
                    }
                }
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
