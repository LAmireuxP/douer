@file:OptIn(ExperimentalLayoutApi::class)

package com.pindou.app.ui

import com.pindou.app.ui.icon.MorphIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pindou.app.ui.shadcn.ShCard
import com.pindou.app.ui.shadcn.ShChip
import com.pindou.app.ui.shadcn.ShIconTile
import com.pindou.app.ui.shadcn.ShPageHeader
import com.pindou.app.ui.shadcn.TextButton
import com.pindou.app.ui.theme.ClayColors
import com.pindou.app.ui.theme.Spacing

/**
 * 创建页：全应用唯一的新建入口。
 *
 * 照片、像素画、色号图、文字、画像素都从这里进；项目库不再重复放
 * 「导入照片 / 空白画布」，避免用户不知道到底该点哪边。
 */
@Composable
fun CreateScreen(
    vm: AppViewModel,
    onPickPhoto: () -> Unit,
    onPickPixel: () -> Unit,
    onPickChart: () -> Unit,
) {
    var textDialogOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ShPageHeader(
            title = "创建图纸",
            subtitle = "选一个来源，生成可以直接照着拼的图纸",
            modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
        )

        PhotoEntry(onClick = onPickPhoto)

        PixelEntry(
            onClick = {
                vm.startPixelModule(
                    vm.settings.defaultGridW.coerceIn(5, 500),
                    vm.settings.defaultGridH.coerceIn(5, 500),
                )
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            EntryCard(
                modifier = Modifier.weight(1f),
                title = "像素图识别",
                subtitle = "保留原格子，逐格识别",
                icon = MorphIcons.Grid,
                iconBg = ClayColors.TintMint,
                iconTint = ClayColors.BrandTeal,
                onClick = onPickPixel,
            )
            EntryCard(
                modifier = Modifier.weight(1f),
                title = "色号图识别",
                subtitle = "现成图纸，自动认格",
                icon = MorphIcons.Palette,
                iconBg = ClayColors.TintButter,
                iconTint = ClayColors.BrandOchre,
                onClick = onPickChart,
            )
        }

        TextEntry(onClick = { textDialogOpen = true })

        Spacer(Modifier.height(Spacing.xs))
        Text(
            "想自己画用「画像素」；普通照片做拼豆图纸用「导入照片」；" +
                "图片本身就是像素画、想保留原格子，用「像素图识别」。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        // 玻璃底栏叠在内容上，尾部留出滚动余量
        Spacer(Modifier.height(104.dp))
    }

    if (textDialogOpen) {
        var text by remember { mutableStateOf("") }
        var widthText by remember { mutableStateOf(vm.gridW.coerceIn(5, 200).toString()) }
        var heightText by remember { mutableStateOf(vm.gridH.coerceIn(5, 200).toString()) }
        var lockSquare by remember { mutableStateOf(true) }
        val sizeW = widthText.toIntOrNull()
        val sizeH = heightText.toIntOrNull()
        val sizeValid = sizeW != null && sizeH != null &&
            sizeW in 5..200 && sizeH in 5..200
        AlertDialog(
            onDismissRequest = { textDialogOpen = false },
            title = { Text("文字拼豆") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("输入文字（支持多行）") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        OutlinedTextField(
                            value = widthText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(3)
                                widthText = digits
                                if (lockSquare) heightText = digits
                            },
                            label = { Text("宽") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = widthText.toIntOrNull()?.let { it in 5..200 } == false,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(3)
                                heightText = digits
                                if (lockSquare) widthText = digits
                            },
                            label = { Text("高") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = heightText.toIntOrNull()?.let { it in 5..200 } == false,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(30, 40, 50, 60, 80, 100).forEach { n ->
                            ShChip(
                                text = "$n",
                                selected = sizeW == n && sizeH == n,
                                onClick = {
                                    widthText = n.toString()
                                    heightText = n.toString()
                                },
                            )
                        }
                        ShChip(
                            text = "锁定正方形",
                            selected = lockSquare,
                            onClick = {
                                lockSquare = !lockSquare
                                if (lockSquare && sizeW != null) heightText = sizeW.toString()
                            },
                        )
                    }
                    Text(
                        "成品尺寸 " + (sizeW?.toString() ?: "?") + "×" +
                            (sizeH?.toString() ?: "?") + " 粒，色卡 " +
                            "${vm.selectedPalette?.brandLabel ?: ""} 生成",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sizeValid) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val content = text.trim()
                        val w = sizeW
                        val h = sizeH
                        if (content.isNotBlank() && w != null && h != null &&
                            w in 5..200 && h in 5..200
                        ) {
                            vm.startNewProject()
                            vm.generateFromText(content, w, h)
                            textDialogOpen = false
                        }
                    },
                    enabled = text.isNotBlank() && sizeValid,
                ) { Text("生成") }
            },
            dismissButton = {
                TextButton(onClick = { textDialogOpen = false }) { Text("取消") }
            },
        )
    }

}

/** 主入口：照片生成。白卡 + 品牌淡蓝图标块 + 推荐标签，层级高于其他入口。 */
@Composable
private fun PhotoEntry(onClick: () -> Unit) {
    ShCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        "推荐",
                        style = MaterialTheme.typography.labelSmall,
                        color = ClayColors.PrimaryInk,
                    )
                }
                Text(
                    "导入照片",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                Text(
                    "自动去背景、简化配色，生成好拼的图纸",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(
                modifier = Modifier
                    .padding(start = Spacing.md)
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ClayColors.TintSky),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    MorphIcons.ImagePlus,
                    contentDescription = null,
                    tint = ClayColors.PrimaryInk,
                    modifier = Modifier.size(28.dp),
                )
            }
            Icon(
                MorphIcons.ArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = Spacing.xs)
                    .size(18.dp),
            )
        }
    }
}

/** 创建页的「画像素」模块入口：独立空白画布，不经过照片/裁剪流程。 */
@Composable
private fun PixelEntry(onClick: () -> Unit) {
    WideEntry(
        title = "画像素",
        subtitle = "图层 / 帧动画 / 选区 / 渐变 / 文字 / GIF",
        icon = MorphIcons.Brush,
        iconBg = ClayColors.TintMint,
        iconTint = ClayColors.BrandTeal,
        onClick = onClick,
    )
}

/** 文字拼豆入口：和画像素共用同一条横向卡片，尺寸完全一致。 */
@Composable
private fun TextEntry(onClick: () -> Unit) {
    WideEntry(
        title = "文字拼豆",
        subtitle = "打字排版生成图纸",
        icon = MorphIcons.Type,
        iconBg = ClayColors.TintRose,
        iconTint = ClayColors.BrandPink,
        onClick = onClick,
    )
}

@Composable
private fun WideEntry(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    ShCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.md),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                MorphIcons.ArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EntryCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    ShCard(
        modifier = modifier.height(142.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.md),
            // 图标和标题紧挨着排，不再用 SpaceBetween 把文字顶到卡片底部；
            // 两张识别卡中间不会出现一整行空白。
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ShIconTile(icon, iconBg, iconTint)
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
