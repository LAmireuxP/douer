package com.pindou.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pindou.app.core.settings.GlassAppearance
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.theme.ClayColors
import com.pindou.app.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * 底栏玻璃的外观设置。
 *
 * 拖动时只改本地 draft，松手才落盘：DataStore 的 edit 是串行事务，
 * 每移动 1px 写一次会让滑块发涩；页内顶部的预览条直接吃 draft，仍然跟手。
 * 关闭时把当前 draft 提交一次，避免「拖完直接下滑关闭」丢设置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSettingsSheet(
    current: GlassAppearance,
    onCommit: (GlassAppearance) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(current.sanitized()) }
    var dirty by remember { mutableStateOf(false) }

    fun commit(next: GlassAppearance) {
        draft = next.sanitized()
        dirty = true
    }

    fun close() {
        if (dirty) onCommit(draft)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = { close() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.md, end = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "底栏玻璃",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "只影响底部导航，不改页面配色",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { close() },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                ) {
                    Icon(MorphIcons.X, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                GlassPreview(draft)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassAppearance.Presets.forEach { (name, preset) ->
                        val selected = preset == draft
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                )
                                .clickable { commit(preset) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                GlassSection("表面") {
                    GlassSlider(
                        label = "不透明度",
                        value = draft.alpha,
                        valueText = "${(draft.alpha * 100).roundToInt()}%",
                        range = GlassAppearance.MIN_ALPHA..GlassAppearance.MAX_ALPHA,
                        onChange = { commit(draft.copy(alpha = it)) },
                    )
                    Text(
                        "越高越像磨砂，越低越能看清底下的卡片颜色",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                GlassSection("模糊与折射") {
                    GlassSlider(
                        label = "背景模糊",
                        value = draft.blurDp,
                        valueText = "${draft.blurDp.roundToInt()} dp",
                        range = GlassAppearance.MIN_BLUR..GlassAppearance.MAX_BLUR,
                        onChange = { commit(draft.copy(blurDp = it)) },
                    )
                    GlassSlider(
                        label = "边缘折射",
                        value = draft.refractionDp,
                        valueText = "${draft.refractionDp.roundToInt()} dp",
                        range = GlassAppearance.MIN_REFRACTION..GlassAppearance.MAX_REFRACTION,
                        onChange = { commit(draft.copy(refractionDp = it)) },
                    )
                }

                GlassSection("高光与形状") {
                    GlassSlider(
                        label = "边缘高光",
                        value = draft.highlight,
                        valueText = "${(draft.highlight * 100).roundToInt()}%",
                        range = GlassAppearance.MIN_HIGHLIGHT..GlassAppearance.MAX_HIGHLIGHT,
                        onChange = { commit(draft.copy(highlight = it)) },
                    )
                    GlassSlider(
                        label = "圆角",
                        value = draft.cornerDp,
                        valueText = "${draft.cornerDp.roundToInt()} dp",
                        range = GlassAppearance.MIN_CORNER..GlassAppearance.MAX_CORNER,
                        onChange = { commit(draft.copy(cornerDp = it)) },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { commit(draft.copy(chromatic = !draft.chromatic)) },
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
                            onCheckedChange = { commit(draft.copy(chromatic = it)) },
                        )
                    }
                }

                TextButton(
                    onClick = { commit(GlassAppearance.Default) },
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text("恢复默认", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(Spacing.sm))
            }
        }
    }
}

/**
 * 迷你底栏：用和真底栏同一套 alpha / 圆角 / 高光值画一条示意，
 * 让用户在松手前就看到参数效果。
 */
@Composable
private fun GlassPreview(glass: GlassAppearance) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.35f
    val alphas = glass.surfaceAlphas(dark)
    val shape = RoundedCornerShape(glass.cornerDp.dp)
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    0f to ClayColors.BrandBlue.copy(alpha = 0.45f),
                    0.5f to ClayColors.BrandPink.copy(alpha = 0.35f),
                    1f to ClayColors.BrandMint.copy(alpha = 0.40f),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .height(56.dp)
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
                    Color.White.copy(alpha = 0.25f + glass.highlight * 0.5f),
                    shape,
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("项目库", "创建", "设置").forEachIndexed { index, label ->
                val selected = index == 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .padding(horizontal = 3.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (selected) accent.copy(alpha = 0.16f)
                            else Color.Transparent,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (selected) {
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

@Composable
private fun GlassSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

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
                .height(30.dp),
        )
    }
}
