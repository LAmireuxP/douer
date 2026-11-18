package com.pindou.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pindou.app.core.settings.GlassAppearance
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.theme.AppTheme
import com.pindou.app.ui.theme.AppThemes
import com.pindou.app.ui.theme.ClayColors
import com.pindou.app.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * 设置页的「外观」卡片。
 *
 * 这里不再单开一个底栏参数弹窗：主题包选择 + 底栏玻璃参数 + 预览
 * 全部收在这一张卡里。拖动只改本地 draft，松手/离开卡片时提交一次，
 * 避免 DataStore 每条目写一次导致滑块发涩。
 */
@Composable
fun AppearanceCard(
    themeMode: String,
    themeSkin: String,
    glass: GlassAppearance,
    onThemeMode: (String) -> Unit,
    onThemeSkin: (String) -> Unit,
    onGlass: (GlassAppearance) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf(glass.sanitized()) }
    var dirty by remember { mutableStateOf(false) }
    // 外部提交成功后（点「应用」或别处改了玻璃参数），draft 要跟回来，
    // 否则滑块会停在旧值上，看起来像没保存成功。
    LaunchedEffect(glass) {
        if (!dirty) draft = glass.sanitized()
    }
    LaunchedEffect(glass, dirty) {
        if (dirty && glass.sanitized() == draft) dirty = false
    }

    fun update(next: GlassAppearance) {
        draft = next.sanitized()
        dirty = true
    }

    // 切 tab / 退出设置页时把未提交的滑杆值落盘
    AutoCommitGlass(draft = draft, dirty = dirty, commit = onGlass)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("外观", style = MaterialTheme.typography.titleLarge)
                Text(
                    "主题包与底栏玻璃，改完立即生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val current = AppThemes.byId(themeSkin)
            Text(
                current.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // —— 明暗模式：和主题包分开，主题包只决定配色 ——
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
                .forEach { (mode, label) ->
                    val selected = themeMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            )
                            .clickable { onThemeMode(mode) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
        }

        // —— 主题包：2 列卡片，每张 4 色小样 + 名称 + 一句定位 ——
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppThemes.all.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { theme ->
                        ThemeTile(
                            theme = theme,
                            selected = theme.id == AppThemes.byId(themeSkin).id,
                            modifier = Modifier.weight(1f),
                        ) { onThemeSkin(theme.id) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Text(
            "主题包只改配色与圆角；图标、排版、功能不变。参考 awesome-design-md 的设计语言描述。",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // —— 底栏玻璃：预览 + 预设 + 滑杆 ——
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "底栏玻璃",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (dirty) {
                Text(
                    "未保存",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        GlassPreview(draft)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GlassAppearance.Presets.forEach { (name, preset) ->
                val selected = preset == draft
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        .clickable { update(preset) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        GlassSlider(
            label = "不透明度",
            value = draft.alpha,
            valueText = "${(draft.alpha * 100).roundToInt()}%",
            range = GlassAppearance.MIN_ALPHA..GlassAppearance.MAX_ALPHA,
            onChange = { update(draft.copy(alpha = it)) },
        )
        GlassSlider(
            label = "背景模糊",
            value = draft.blurDp,
            valueText = "${draft.blurDp.roundToInt()} dp",
            range = GlassAppearance.MIN_BLUR..GlassAppearance.MAX_BLUR,
        ) { update(draft.copy(blurDp = it)) }
        GlassSlider(
            label = "边缘折射",
            value = draft.refractionDp,
            valueText = "${draft.refractionDp.roundToInt()} dp",
            range = GlassAppearance.MIN_REFRACTION..GlassAppearance.MAX_REFRACTION,
        ) { update(draft.copy(refractionDp = it)) }
        GlassSlider(
            label = "边缘高光",
            value = draft.highlight,
            valueText = "${(draft.highlight * 100).roundToInt()}%",
            range = GlassAppearance.MIN_HIGHLIGHT..GlassAppearance.MAX_HIGHLIGHT,
        ) { update(draft.copy(highlight = it)) }
        GlassSlider(
            label = "圆角",
            value = draft.cornerDp,
            valueText = "${draft.cornerDp.roundToInt()} dp",
            range = GlassAppearance.MIN_CORNER..GlassAppearance.MAX_CORNER,
        ) { update(draft.copy(cornerDp = it)) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { update(draft.copy(chromatic = !draft.chromatic)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("色散", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "边缘带一点彩边；Android 13 以下不生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = draft.chromatic,
                onCheckedChange = { update(draft.copy(chromatic = it)) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "窗口失焦/离开设置页时自动保存",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable { update(GlassAppearance.Default) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("恢复默认", style = MaterialTheme.typography.labelSmall)
            }
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (dirty) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    )
                    .clickable {
                        if (dirty) {
                            onGlass(draft)
                            dirty = false
                        }
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "应用",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (dirty) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

/** 主题卡：四色小样 + 名称 + 一句定位 + 参考来源。 */
@Composable
private fun ThemeTile(
    theme: AppTheme,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            theme.swatch.forEach { color ->
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(color)
                        .border(0.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(5.dp)),
                )
            }
            Spacer(Modifier.weight(1f))
            if (selected) {
                Icon(
                    MorphIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            theme.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            theme.tagline,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            theme.reference,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            maxLines = 1,
        )
    }
}

/**
 * 迷你底栏：和真底栏同一套 alpha / 圆角 / 高光，松手前就能看到效果。
 * 背景用主题自带的品牌色做渐变，不同主题下预览也会跟着换色。
 */
@Composable
internal fun GlassPreview(glass: GlassAppearance) {
    val dark = MaterialTheme.colorScheme.surface.luminanceForPreview() < 0.35f
    val alphas = glass.surfaceAlphas(dark)
    val shape = RoundedCornerShape(glass.cornerDp.dp)
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    0f to ClayColors.BrandBlue.copy(alpha = 0.42f),
                    0.5f to ClayColors.BrandPink.copy(alpha = 0.32f),
                    1f to ClayColors.BrandMint.copy(alpha = 0.38f),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(50.dp)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = alphas.top),
                        0.5f to Color.White.copy(alpha = alphas.middle),
                        1f to Color.White.copy(alpha = alphas.bottom),
                    ),
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.22f + glass.highlight * 0.5f),
                    shape,
                )
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("项目库", "创建", "设置").forEachIndexed { index, label ->
                val selectedTile = index == 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (selectedTile) accent.copy(alpha = 0.16f) else Color.Transparent,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (selectedTile) {
                            if (dark) Color.White else ClayColors.PrimaryInk
                        } else {
                            if (dark) Color.White.copy(alpha = 0.6f)
                            else ClayColors.Body.copy(alpha = 0.56f)
                        },
                    )
                }
            }
        }
    }
}

private fun Color.luminanceForPreview(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
private fun GlassSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                valueText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        )
    }
}

/** 离开设置页时提交：切 tab / 按返回都会让卡片离开组合。 */
@Composable
fun AutoCommitGlass(
    draft: GlassAppearance,
    dirty: Boolean,
    commit: (GlassAppearance) -> Unit,
) {
    val draftNow by rememberUpdatedState(draft)
    val dirtyNow by rememberUpdatedState(dirty)
    val commitNow by rememberUpdatedState(commit)
    DisposableEffect(Unit) {
        onDispose { if (dirtyNow) commitNow(draftNow) }
    }
}
