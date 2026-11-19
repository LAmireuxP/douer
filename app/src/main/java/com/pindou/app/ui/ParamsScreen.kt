package com.pindou.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.pattern.FilterLevel
import com.pindou.app.core.pattern.GenerationStage
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.motion.LocalMotion
import com.pindou.app.ui.motion.Motion
import com.pindou.app.ui.motion.StaggeredAppear
import com.pindou.app.ui.motion.pressScale
import com.pindou.app.ui.params.PaletteFilter
import com.pindou.app.ui.params.ParamsMetrics
import com.pindou.app.ui.params.filterPalettes
import com.pindou.app.ui.params.groupPalettes
import com.pindou.app.ui.params.paletteKey
import com.pindou.app.ui.shadcn.Button
import com.pindou.app.ui.shadcn.ShCard
import com.pindou.app.ui.shadcn.ShChip
import com.pindou.app.ui.shadcn.ShPageHeader
import com.pindou.app.ui.shadcn.TextButton
import com.pindou.app.ui.theme.Spacing
import kotlin.math.max

/**
 * 参数设置页。
 *
 * 结构上分两条路：
 * - 窄屏（<600dp）：单列滚动，底部压一条常驻「规格条 + 生成」，
 *   改了网格/色卡立刻在条上看到豆子数和成品厘米数，不用滚回顶部核对；
 * - 宽屏（>=600dp）：左栏看图和尺寸，右栏调色卡与清理，底条横排。
 *
 * 选中态全部从 AppViewModel 推导（isAdaptive / gridW / gridH），
 * 页面不存第二份真值：否则校准页返回、或点了自适应之后，chip 会亮错档。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParamsScreen(vm: AppViewModel) {
    var paletteSheet by remember { mutableStateOf(false) }
    var textEditor by remember { mutableStateOf(false) }
    var filterName by rememberSaveable { mutableStateOf(PaletteFilter.ALL.name) }
    // 「自定义」不是能算出来的状态：36×52 既可能来自 36 档，也可能是手输。
    // 只有用户显式点过才进入，点任何固定档位就退出。
    var customMode by rememberSaveable { mutableStateOf(false) }
    val filter = PaletteFilter.fromName(filterName)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // 改图纸名称时键盘顶起整页，底条不会被盖住
            .imePadding(),
    ) {
        // 横屏手机：宽度够两栏，但高度只有 ~360dp，标题说明和 240dp 预览会把
        // 配置区整个挤到屏幕外。这里按高度再降一档密度。
        val short = maxHeight < 480.dp
        if (maxWidth >= 600.dp) {
            WideParams(
                vm = vm,
                filter = filter,
                customMode = customMode,
                short = short,
                onFilter = { filterName = it.name },
                onCustomMode = { customMode = it },
                onOpenPalettes = { paletteSheet = true },
                onEditText = { textEditor = true },
            )
        } else {
            CompactParams(
                vm = vm,
                filter = filter,
                customMode = customMode,
                short = short,
                onFilter = { filterName = it.name },
                onCustomMode = { customMode = it },
                onOpenPalettes = { paletteSheet = true },
                onEditText = { textEditor = true },
            )
        }
    }

    if (paletteSheet) {
        PaletteSheet(
            vm = vm,
            filter = filter,
            onFilter = { filterName = it.name },
            onDismiss = { paletteSheet = false },
            onPick = {
                vm.selectPalette(it)
                paletteSheet = false
            },
        )
    }

    if (textEditor) {
        TextEditorDialog(
            initial = vm.textSource.orEmpty(),
            onDismiss = { textEditor = false },
            onConfirm = {
                vm.regenerateFromText(it)
                textEditor = false
            },
        )
    }
}

// ---------------------------------------------------------------- 两种布局

@Composable
private fun CompactParams(
    vm: AppViewModel,
    filter: PaletteFilter,
    customMode: Boolean,
    short: Boolean,
    onFilter: (PaletteFilter) -> Unit,
    onCustomMode: (Boolean) -> Unit,
    onOpenPalettes: () -> Unit,
    onEditText: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                // 给底条留位：运行中会多一行阶段文字，按最高形态预留
                .padding(bottom = 152.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ParamsHeader(vm, Modifier.padding(top = Spacing.lg), short = short)
            PreviewCard(vm, height = if (short) 150.dp else 190.dp, onEditText = onEditText)
            NameField(vm)
            GridSection(vm, customMode, onCustomMode)
            PaletteSection(vm, filter, onFilter, onOpenPalettes)
            RefineSection(vm)
            TuneResultCard(vm)
        }
        CommitBar(
            vm = vm,
            compact = false,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun WideParams(
    vm: AppViewModel,
    filter: PaletteFilter,
    customMode: Boolean,
    short: Boolean,
    onFilter: (PaletteFilter) -> Unit,
    onCustomMode: (Boolean) -> Unit,
    onOpenPalettes: () -> Unit,
    onEditText: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ParamsHeader(
            vm = vm,
            modifier = Modifier
                .widthIn(max = 1120.dp)
                .fillMaxWidth()
                .padding(top = Spacing.lg),
            short = short,
        )
        Row(
            modifier = Modifier
                .widthIn(max = 1120.dp)
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // 左栏：进来的图 + 规格。右栏：选料与清理。
            Column(
                modifier = Modifier
                    .weight(0.92f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(end = Spacing.md, bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                PreviewCard(vm, height = if (short) 170.dp else 240.dp, onEditText = onEditText)
                NameField(vm)
                GridSection(vm, customMode, onCustomMode)
            }
            Column(
                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Spacing.md, bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                PaletteSection(vm, filter, onFilter, onOpenPalettes)
                RefineSection(vm)
                TuneResultCard(vm)
            }
        }
        CommitBar(
            vm = vm,
            compact = true,
            modifier = Modifier.widthIn(max = 1120.dp),
        )
    }
}

@Composable
private fun ParamsHeader(vm: AppViewModel, modifier: Modifier = Modifier, short: Boolean = false) {
    ShPageHeader(
        title = "参数设置",
        subtitle = if (short) null else "网格决定颗粒度，色卡决定能买到的豆子",
        modifier = modifier,
        onBack = { vm.backFromParams() },
    )
}

// ---------------------------------------------------------------- 预览与命名

/**
 * 预览卡：照片填满裁切，文字/像素画完整显示（像素画的方块比例不能被裁掉），
 * 角上的药丸标出这张图走的是哪条管线。
 */
@Composable
private fun PreviewCard(vm: AppViewModel, height: Dp, onEditText: () -> Unit) {
    val bitmap = vm.imagePreview ?: return
    val isText = vm.textSource != null
    val fit = isText || vm.pixelArtMode
    val badge = when {
        isText -> "文字栅格"
        vm.pixelArtMode -> "像素画"
        else -> "照片"
    }
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "导入的图片",
            modifier = Modifier.fillMaxSize(),
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
        )

        // 顶部类型标：白字压在深色药丸上，换什么底图都读得清
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Spacing.sm)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.48f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isText) MorphIcons.Type else MorphIcons.Grid,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(badge, style = MaterialTheme.typography.labelSmall, color = Color.White)
        }

        // 底部遮罩：照片再花也不会把尺寸和操作吞掉
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.52f)),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${vm.imageW} × ${vm.imageH} px",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = Color.White.copy(alpha = 0.88f),
            )
            Spacer(Modifier.weight(1f))
            PreviewAction(
                text = if (isText) "改文字" else "重新裁剪",
                onClick = { if (isText) onEditText() else vm.openCrop() },
            )
        }
    }
}

@Composable
private fun PreviewAction(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.46f))
            .pressScale(interaction, pressedScale = 0.94f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MorphIcons.Expand,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
private fun NameField(vm: AppViewModel) {
    OutlinedTextField(
        value = vm.projectName.takeIf { it != "未命名图纸" } ?: "",
        onValueChange = { vm.projectName = it },
        label = { Text("图纸名称") },
        placeholder = { Text("未命名图纸") },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------- 区块标题

/**
 * 区块抬头：图标 + 标题 + 当前值。
 * 右侧那个值是这一页的导航：不用展开卡片就知道现在多少格、什么色卡、几档过滤。
 */
@Composable
private fun SectionHead(icon: ImageVector, title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp),
            textAlign = TextAlign.End,
        )
    }
}

// ---------------------------------------------------------------- 网格

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GridSection(vm: AppViewModel, customMode: Boolean, onCustomMode: (Boolean) -> Unit) {
    val art = vm.pixelArtGrid
    val preset = when {
        customMode -> "自定义"
        vm.isAdaptive -> "自适应"
        art != null && art.first == vm.gridW && art.second == vm.gridH -> "原画"
        else -> ParamsMetrics.matchGridWidth(
            vm.gridW, vm.gridH, art, vm.imageW, vm.imageH,
        )?.toString() ?: "自定义"
    }

    StaggeredAppear(index = 0) {
        ShCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SectionHead(MorphIcons.Grid, "网格", "${vm.gridW} × ${vm.gridH}")

                if (art != null) {
                    PixelArtRow(vm, art, selected = preset == "原画")
                }

                // 数字档位和「自适应/自定义」是两条不同的轴：前者改值，后者改模式。
                // 混在一个 FlowRow 里会在窄屏把「自定义」挤到第二行单独一个，像掉了颗牙。
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    ParamsMetrics.GridWidths.forEach { width ->
                        ShChip(
                            text = width.toString(),
                            selected = preset == width.toString(),
                            onClick = {
                                onCustomMode(false)
                                vm.setGridWidthPreset(width)
                            },
                            modifier = Modifier.heightIn(min = 38.dp),
                        )
                    }
                }
                // 模式轴做成两段式，和上面的数值 chip 明确分开：
                // 数值是「选一个数」，模式是「换一种算法」。
                ModeSegments(
                    selected = preset,
                    onSelect = { mode ->
                        when (mode) {
                            "自适应" -> {
                                onCustomMode(false)
                                vm.applyAdaptiveSize()
                            }
                            "自定义" -> onCustomMode(true)
                        }
                    },
                )

                AnimatedVisibility(
                    visible = preset == "自适应",
                    enter = fadeIn(tween(Motion.FAST)) + expandVertically(tween(Motion.BASE)),
                    exit = fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.BASE)),
                ) {
                    AdaptiveRow(vm)
                }

                AnimatedVisibility(
                    visible = preset == "自定义",
                    enter = fadeIn(tween(Motion.FAST)) + expandVertically(tween(Motion.BASE)),
                    exit = fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.BASE)),
                ) {
                    CustomGridBlock(vm)
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = Spacing.xxs),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                )
                FinishedSizeBlock(vm)
            }
        }
    }
}

/** 两段式模式选择：自适应 / 自定义。选中段填主色，整条像一枚拨段开关。 */
@Composable
private fun ModeSegments(selected: String, onSelect: (String) -> Unit) {
    val motion = LocalMotion.current
    val shape = CircleShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(3.dp),
    ) {
        listOf("自适应", "自定义").forEach { mode ->
            val active = selected == mode
            val bg by animateColorAsState(
                targetValue = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = Motion.spec(tween(Motion.FAST), tween(0), motion),
                label = "modeBg",
            )
            val fg by animateColorAsState(
                targetValue = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = Motion.spec(tween(Motion.FAST), tween(0), motion),
                label = "modeFg",
            )
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(bg)
                    .pressScale(interaction, pressedScale = 0.96f)
                    .clickable(interactionSource = interaction, indication = null) {
                        onSelect(mode)
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(mode, style = MaterialTheme.typography.labelLarge, color = fg)
            }
        }
    }
}

@Composable
private fun PixelArtRow(vm: AppViewModel, art: Pair<Int, Int>, selected: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.12f else 0.06f))
            .border(
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.45f else 0.18f),
                ),
                shape,
            )
            .padding(start = Spacing.sm, end = Spacing.xxs, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "像素画原画 ${art.first}×${art.second}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                if (selected) "已按画像素 1:1 取色" else "按画像素取原色，不产生过渡色",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { vm.applyArtGrid() }) {
            Text(if (selected) "已套用" else "套用")
        }
    }
}

@Composable
private fun AdaptiveRow(vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("放大倍数", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(
                "×${ParamsMetrics.formatCm(vm.adaptiveScale.toDouble())}",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = vm.adaptiveScale,
            onValueChange = { vm.updateAdaptiveScale(it) },
            valueRange = 0.5f..4f,
            steps = 13,
            // 保留 0.25 档的吸附，但把 13 个刻度点藏掉：
            // 一条 340dp 的轨道上 13 个点比滑块本身还抢眼。
            colors = SliderDefaults.colors(
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "在原画/自适应基础尺寸上整体缩放，比例不变",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CustomGridBlock(vm: AppViewModel) {
    val aspect = ParamsMetrics.effectiveAspect(vm.pixelArtGrid, vm.imageW, vm.imageH)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = vm.gridW.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let(vm::setGridWidthInput) },
                label = { Text("宽") },
                suffix = { Text("格") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = vm.gridH.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let(vm::setGridHeightInput) },
                label = { Text("高") },
                suffix = { Text("格") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "保持宽高比",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = vm.lockGridAspect, onCheckedChange = { vm.toggleGridAspectLock() })
        }
        Text(
            if (vm.lockGridAspect) {
                "改一边自动推另一边（比例 ${ParamsMetrics.formatCm(aspect)}），图案不会被拉扁"
            } else {
                "已解锁：宽高独立输入会按不同比例拉伸图案"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 成品尺寸：两根尺子，长度按当前色卡珠径换算。
 * 这是整页唯一一个「抽象格数 → 手里那块板」的换算，比单独一行豆子数更有用。
 */
@Composable
private fun FinishedSizeBlock(vm: AppViewModel) {
    val palette = vm.selectedPalette
    val (wCm, hCm) = palette?.let {
        ParamsMetrics.finishedSizeCm(vm.gridW, vm.gridH, it.beadSize.pitchMm)
    } ?: (0.0 to 0.0)
    val hasSize = palette != null && wCm > 0.0
    val maxCm = max(max(wCm, hCm), 10.0)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("成品尺寸", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text(
                if (palette != null) "${ParamsMetrics.formatCm(palette.beadSize.pitchMm)}mm 珠径" else "选色卡后换算",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SizeBar("宽", wCm, maxCm, hasSize, primary = true)
        SizeBar("高", hCm, maxCm, hasSize, primary = false)
        Text(
            if (hasSize) {
                "按圆心距算边长，实际成品四周还会多出约半颗豆的余量"
            } else {
                "珠径 5mm 时 36 格 = 18cm；2.6mm 时同样的 36 格只有 9.4cm"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SizeBar(label: String, cm: Double, maxCm: Double, hasSize: Boolean, primary: Boolean) {
    val motion = LocalMotion.current
    val fraction = if (maxCm <= 0.0) 0f else (cm / maxCm).toFloat().coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = if (hasSize) fraction else 0f,
        animationSpec = Motion.spec(Motion.Pill, tween(0), motion),
        label = "sizeBar",
    )
    val fill = if (primary) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val shape = RoundedCornerShape(5.dp)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(16.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(shape)
                .background(track),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .background(fill),
            )
            // 每 5cm 一条刻线：让「13.2cm」不再只是一个数字
            Canvas(Modifier.fillMaxSize()) {
                if (!hasSize || maxCm <= 0.0) return@Canvas
                var at = 5.0
                while (at < maxCm) {
                    val x = (at / maxCm).toFloat() * size.width
                    drawLine(
                        color = tick,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                    at += 5.0
                }
            }
        }
        Text(
            if (hasSize) "${ParamsMetrics.formatCm(cm)} cm" else "—",
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = if (hasSize) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(58.dp),
        )
    }
}

// ---------------------------------------------------------------- 色卡

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteSection(
    vm: AppViewModel,
    filter: PaletteFilter,
    onFilter: (PaletteFilter) -> Unit,
    onOpen: () -> Unit,
) {
    val palette = vm.selectedPalette
    StaggeredAppear(index = 1) {
        ShCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SectionHead(
                    icon = MorphIcons.Palette,
                    title = "色卡",
                    value = palette?.brandLabel ?: "未选择",
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    PaletteFilter.entries.forEach { entry ->
                        ShChip(
                            text = entry.label,
                            selected = filter == entry,
                            onClick = { onFilter(entry) },
                            modifier = Modifier.heightIn(min = 36.dp),
                        )
                    }
                }
                if (palette != null) {
                    PaletteTrigger(palette, onOpen)
                } else {
                    TextButton(onClick = onOpen) { Text("从 ${vm.palettes.size} 套色卡里选一套") }
                }
                Text(
                    "色号来自各家品牌色卡，生成后可在购物清单里按色号买豆",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PaletteTrigger(palette: BeadPalette, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    val edge by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        animationSpec = tween(Motion.BASE),
        label = "paletteEdge",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, edge, shape)
            .pressScale(interaction, pressedScale = 0.985f)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen),
    ) {
        // 顶部整条色带：19 套名字相近的套装，看一眼颜色比读名字快
        PaletteBand(palette, height = 8.dp)
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    palette.brandLabel,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${palette.colors.size} 色 · ${palette.beadSize.pitchMm}mm",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "更换",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = MorphIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * 色带用 Canvas 一次画完，而不是一排 Box。
 * 上百个 weight 子项不仅在 60fps 下要多测几十次，相邻子项的亚像素取整还会
 * 留下 1px 白缝——在深色主题上像一排断开的色块。
 */
@Composable
private fun PaletteBand(palette: BeadPalette, height: Dp, maxSegments: Int = 40) {
    val colors = palette.colors
    if (colors.isEmpty()) return
    // 大色卡上百色时抽稀，既保留整体色相分布，又不画上百条
    val step = max(1, (colors.size + maxSegments - 1) / maxSegments)
    val argb = colors.filterIndexed { index, _ -> index % step == 0 }
        .take(maxSegments)
        .map { it.argb }

    Canvas(Modifier.fillMaxWidth().height(height)) {
        val unit = size.width / argb.size
        argb.forEachIndexed { index, color ->
            // 每段右侧多画 1px，盖住浮点取整留下的缝
            drawRect(
                color = Color(color),
                topLeft = Offset(unit * index, 0f),
                size = androidx.compose.ui.geometry.Size(
                    width = unit + 1f,
                    height = size.height,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------- 清理

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RefineSection(vm: AppViewModel) {
    StaggeredAppear(index = 2) {
        ShCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SectionHead(MorphIcons.Sliders, "清理", vm.filterLevel.label)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    FilterLevel.entries.forEach { level ->
                        ShChip(
                            text = level.label,
                            selected = vm.filterLevel == level,
                            onClick = { vm.filterLevel = level },
                            modifier = Modifier.heightIn(min = 38.dp),
                        )
                    }
                }
                Text(
                    "等级越高杂色越少。降噪只作用于格子，眼睛、嘴、高光等高对比细节会锁定保留",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.xxs),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                )

                SwitchRow(
                    title = "限制最大颜色数",
                    subtitle = "色卡再大也只保留 N 种，合并最接近的色号",
                    checked = vm.limitColors,
                    onCheckedChange = { vm.limitColors = it },
                )
                AnimatedVisibility(
                    visible = vm.limitColors,
                    enter = fadeIn(tween(Motion.FAST)) + expandVertically(tween(Motion.BASE)),
                    exit = fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.BASE)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Slider(
                            value = vm.maxColors.toFloat(),
                            onValueChange = { vm.maxColors = it.toInt() },
                            valueRange = 4f..48f,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${vm.maxColors} 色",
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                SwitchRow(
                    title = "移除背景",
                    subtitle = "只去掉主体外的背景，主体内的同色区域保留（白底手绘图优先）",
                    checked = vm.removeBackground,
                    onCheckedChange = { vm.removeBackground = it },
                )
                AnimatedVisibility(
                    visible = vm.removeBackground,
                    enter = fadeIn(tween(Motion.FAST)) + expandVertically(tween(Motion.BASE)),
                    exit = fadeOut(tween(Motion.FAST)) + shrinkVertically(tween(Motion.BASE)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Slider(
                            value = vm.backgroundTolerance,
                            onValueChange = { vm.backgroundTolerance = it },
                            valueRange = 4f..30f,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "容差 ${vm.backgroundTolerance.toInt()}",
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                SwitchRow(
                    title = "自动贴主体取景",
                    subtitle = "四周空白先裁掉再排网格：同样的 60×60，主体能多占一倍格子",
                    checked = vm.autoFrame,
                    onCheckedChange = { vm.autoFrame = it },
                )
                SwitchRow(
                    title = "Floyd–Steinberg 抖动",
                    subtitle = "进阶选项，默认关闭（抖动图更难拼）",
                    checked = vm.dithering,
                    onCheckedChange = { vm.dithering = it },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 优选结果

@Composable
private fun TuneResultCard(vm: AppViewModel) {
    val info = vm.autoTuneInfo ?: return
    val scores = vm.autoTuneScores
    StaggeredAppear(index = 3) {
        ShCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SectionHead(MorphIcons.CircleCheck, "智能优选", "已出结果")
                Text(
                    info,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (scores.isNotEmpty()) {
                    val best = scores.maxOf { it.second }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        scores.forEach { (label, score) ->
                            val picked = score == best
                            val bg by animateColorAsState(
                                targetValue = if (picked) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                animationSpec = tween(Motion.BASE),
                                label = "tuneScoreBg",
                            )
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = bg,
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 7.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        label.removeSuffix("过滤"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "$score",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFeatureSettings = "tnum",
                                        ),
                                        color = if (picked) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 底部规格条

/**
 * 常驻底条。左边是「这版参数会得到什么」，右边是「生成」。
 * 挂在滚动区外面，2000dp 长的表单在任何位置都能一键提交。
 */
@Composable
private fun CommitBar(vm: AppViewModel, compact: Boolean, modifier: Modifier = Modifier) {
    val palette = vm.selectedPalette
    val running = vm.genState as? GenState.Running
    val error = (vm.genState as? GenState.Error)?.message
    val isText = vm.textSource != null
    val ready = palette != null && (if (isText) true else vm.imagePixels != null)
    val idle = vm.genState !is GenState.Running
    val done = vm.autoTuneInfo != null && vm.genState is GenState.Done
    val (wCm, hCm) = palette?.let {
        ParamsMetrics.finishedSizeCm(vm.gridW, vm.gridH, it.beadSize.pitchMm)
    } ?: (0.0 to 0.0)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 10.dp,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            if (running != null) {
                LinearProgressIndicator(
                    progress = { running.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (compact) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetricSummary(vm, wCm, hCm, palette)
                        Spacer(Modifier.weight(1f))
                        ActionButtons(vm, ready, idle, done, isText)
                    }
                } else {
                    MetricSummary(vm, wCm, hCm, palette)
                    ActionButtons(vm, ready, idle, done, isText, fillWidth = true)
                }

                val status = when {
                    running != null -> "${stageLabel(running.stage)} · ${(running.progress * 100).toInt()}%"
                    error != null -> "生成失败：$error"
                    else -> null
                }
                if (status != null) {
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (error != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricSummary(
    vm: AppViewModel,
    wCm: Double,
    hCm: Double,
    palette: BeadPalette?,
) {
    val beads = ParamsMetrics.beadCount(vm.gridW, vm.gridH)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${vm.gridW} × ${vm.gridH}",
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            "·",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            "${ParamsMetrics.formatCount(beads)} 粒",
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (palette != null && wCm > 0.0) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "${ParamsMetrics.formatCm(wCm)} × ${ParamsMetrics.formatCm(hCm)} cm",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionButtons(
    vm: AppViewModel,
    ready: Boolean,
    idle: Boolean,
    done: Boolean,
    isText: Boolean,
    fillWidth: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
    ) {
        // 自动优选只对图片有意义（文字模式的栅格化参数不同），文字模式隐藏
        if (!isText) {
            Button(
                onClick = { vm.autoTune() },
                enabled = ready && idle && !vm.autoTuneRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
                modifier = Modifier
                    .height(48.dp)
                    .then(if (fillWidth) Modifier.weight(1.1f) else Modifier.widthIn(min = 118.dp)),
            ) {
                if (vm.autoTuneRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text("✦ 智能优选")
                }
            }
        }
        Button(
            onClick = {
                // 优选结果已经在手上：直接进预览，不重算一遍把得分清空
                if (done) {
                    vm.openPreview()
                } else {
                    val text = vm.textSource
                    if (text != null) vm.regenerateFromText(text) else vm.generate()
                }
            },
            enabled = ready && idle,
            modifier = Modifier
                .height(48.dp)
                .then(if (fillWidth) Modifier.weight(1.3f) else Modifier.widthIn(min = 128.dp)),
        ) {
            AnimatedContent(
                targetState = done,
                transitionSpec = {
                    fadeIn(tween(Motion.FAST)) togetherWith fadeOut(tween(Motion.FAST / 2))
                },
                label = "commitLabel",
            ) { showResult ->
                Text(if (showResult) "查看图纸" else "生成图纸")
            }
        }
    }
}

private fun stageLabel(stage: GenerationStage): String = when (stage) {
    GenerationStage.EXPOSURE -> "曝光修正中"
    GenerationStage.DOWNSAMPLE -> "降采样中"
    GenerationStage.BACKGROUND -> "移除背景中"
    GenerationStage.REDUCE -> "压缩颜色中"
    GenerationStage.MAP -> "色卡映射中"
    GenerationStage.CLEAN -> "杂色清理中"
    GenerationStage.MERGE -> "相似色号合并中"
    GenerationStage.LIMIT -> "限色合并中"
    GenerationStage.DONE -> "完成"
}

// ---------------------------------------------------------------- 色卡选择弹窗

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PaletteSheet(
    vm: AppViewModel,
    filter: PaletteFilter,
    onFilter: (PaletteFilter) -> Unit,
    onDismiss: () -> Unit,
    onPick: (BeadPalette) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 先筛再分组：选了 5mm 就只剩一组，列表里不会出现空标题
    val filtered = remember(vm.palettes, filter) { filterPalettes(vm.palettes, filter) }
    val groups = remember(filtered) { groupPalettes(filtered) }
    val currentKey = vm.selectedPalette?.paletteKey()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .padding(horizontal = Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("选色卡", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    "${filtered.size} / ${vm.palettes.size} 套",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "按豆子规格筛，点整张卡切换。生成后会按这套色号出名细清单。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            FlowRow(
                modifier = Modifier.padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                PaletteFilter.entries.forEach { entry ->
                    ShChip(
                        text = entry.label,
                        selected = filter == entry,
                        onClick = { onFilter(entry) },
                        modifier = Modifier.heightIn(min = 38.dp),
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                groups.forEach { (group, list) ->
                    item(key = "head-${group.name}", span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(top = Spacing.xs)) {
                            Text(group.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                group.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(list, key = { it.paletteKey() }) { palette ->
                        PaletteTile(
                            palette = palette,
                            selected = palette.paletteKey() == currentKey,
                            onClick = { onPick(palette) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteTile(palette: BeadPalette, selected: Boolean, onClick: () -> Unit) {
    Box {
        ShCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            border = if (selected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
        ) {
            PaletteBand(palette, height = 10.dp, maxSegments = 24)
            // 固定内容高度：同一行的卡不会因为名字一行/两行而参差。
            // 56dp = 两行标题 38 + 元数据 16，一行标题时底部留白，不再是中间挖洞。
            Column(
                Modifier
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .height(56.dp),
            ) {
                Text(
                    palette.brandLabel,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${palette.colors.size} 色 · ${palette.beadSize.pitchMm}mm",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MorphIcons.Check,
                    contentDescription = "当前色卡",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 改文字

@Composable
private fun TextEditorDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("改文字") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("输入文字（支持多行）") },
                minLines = 2,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
            ) { Text("重新生成") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ---------------------------------------------------------------- 共用行

/** 开关行：标题 + 说明在左，Switch 在右，用于设置类卡片内的统一节奏 */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            // 主题的 outline 很浅，M3 默认的关闭态在白卡上几乎看不见，
            // 会被误读成「不可用」。这里把描边和滑块提到 onSurfaceVariant。
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                uncheckedIconColor = Color.Transparent,
            ),
        )
    }
}
