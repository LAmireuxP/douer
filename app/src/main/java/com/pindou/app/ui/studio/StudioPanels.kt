@file:OptIn(ExperimentalLayoutApi::class)

package com.pindou.app.ui.studio

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pindou.app.core.editor.SymmetryMode
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.studio.StudioBlendMode
import com.pindou.app.core.studio.StudioExport
import com.pindou.app.core.studio.StudioImageImport
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.theme.ClayColors
import com.pindou.app.ui.theme.Spacing
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 面板类型。底部动作栏打开对应的 ModalBottomSheet。 */
enum class StudioPanel { LAYERS, FRAMES, EXPORT, CANVAS, FILL }

private fun luminance(argb: Int): Int =
    (((argb shr 16) and 0xFF) * 299 + ((argb shr 8) and 0xFF) * 587 + (argb and 0xFF) * 114) / 1000

@Composable
private fun Modifier.selectedBorder(selected: Boolean, radius: Int = 8): Modifier = border(
    width = if (selected) 2.dp else 1.dp,
    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
    shape = RoundedCornerShape(radius.dp),
)

@Composable
fun StudioToolStrip(session: StudioSession, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xs, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioTool.entries.forEach { tool ->
            val selected = session.tool == tool
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        if (selected) Color.Transparent else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable {
                        session.tool = tool
                        session.clearPending()
                    }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            ) {
                Text(
                    tool.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * 浮窗顶部的双横线手柄：拖动移动，点按收起/展开。
 * 收起时在双横线下面显示一个很小的身份标记（工具图标 / 当前色点）。
 */
@Composable
private fun RailDragHeader(
    modifier: Modifier,
    collapsed: Boolean,
    marker: @Composable () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (collapsed) 42.dp else 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .width(20.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)),
            )
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .width(20.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)),
            )
            if (collapsed) {
                Spacer(Modifier.height(3.dp))
                marker()
            }
        }
        Box(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight()
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (collapsed) MorphIcons.ArrowDown else MorphIcons.ArrowUp,
                contentDescription = if (collapsed) "展开侧栏" else "收起侧栏",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

/**
 * 竖屏左侧工具浮窗：只放图标，一列到底。
 *
 * 17 个工具按 34dp 一行排，整体可滚；不再占一条横向长条，
 * 画布上下空间留给画布本身。选中工具的名字由画布下方提示行显示。
 */
@Composable
fun StudioToolRail(
    session: StudioSession,
    headerModifier: Modifier = Modifier,
    collapsed: Boolean = false,
    horizontal: Boolean = false,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!collapsed && horizontal) {
        StudioToolRailHorizontal(session, headerModifier, onToggle, modifier)
    } else {
        StudioToolRailVertical(
            session = session,
            headerModifier = headerModifier,
            collapsed = collapsed,
            onToggle = onToggle,
            modifier = modifier,
        )
    }
}

/** 贴近画布上下边、且不在左右两侧时，工具条横过来排成一行。 */
@Composable
private fun StudioToolRailHorizontal(
    session: StudioSession,
    headerModifier: Modifier,
    onToggle: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .width(310.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(bottom = 4.dp),
    ) {
        RailDragHeader(headerModifier, collapsed = false, marker = {}, onToggle = onToggle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StudioTool.entries.forEachIndexed { index, tool ->
                val selected = session.tool == tool
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            else Color.Transparent,
                        )
                        .clickable {
                            session.tool = tool
                            session.clearPending()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = tool.icon(),
                        contentDescription = tool.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp),
                    )
                }
                if (index == 3 || index == 8 || index == 11 || index == 15) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                    )
                }
            }
        }
    }
}

@Composable
fun StudioColorRail(
    session: StudioSession,
    onOpenPalette: () -> Unit,
    headerModifier: Modifier = Modifier,
    collapsed: Boolean = false,
    horizontal: Boolean = false,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!collapsed && horizontal) {
        StudioColorRailHorizontal(session, onOpenPalette, headerModifier, onToggle, modifier)
    } else {
        StudioColorRailVertical(
            session = session,
            onOpenPalette = onOpenPalette,
            headerModifier = headerModifier,
            collapsed = collapsed,
            onToggle = onToggle,
            modifier = modifier,
        )
    }
}

/** 贴近画布上下边时，色板横过来排成一行。 */
@Composable
private fun StudioColorRailHorizontal(
    session: StudioSession,
    onOpenPalette: () -> Unit,
    headerModifier: Modifier,
    onToggle: () -> Unit,
    modifier: Modifier,
) {
    val current = session.palette.colors.getOrNull(session.color)
    val secondary = session.palette.colors.getOrNull(session.secondaryColor)
    Column(
        modifier = modifier
            .width(300.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(bottom = 5.dp),
    ) {
        RailDragHeader(headerModifier, collapsed = false, marker = {}, onToggle = onToggle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(current?.argb ?: 0xFFCCCCCC.toInt()))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenPalette),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    current?.code ?: "色",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = if (luminance(current?.argb ?: 0xFFCCCCCC.toInt()) > 150) {
                        Color(0xDD111111)
                    } else {
                        Color(0xEEFFFFFF)
                    },
                )
            }
            IconButton(onClick = { session.swapColors() }, modifier = Modifier.size(28.dp)) {
                Icon(
                    MorphIcons.SwapHorizontal,
                    contentDescription = "交换主副色",
                    modifier = Modifier.size(17.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(secondary?.argb ?: 0xFFCCCCCC.toInt()))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(7.dp))
                    .clickable { session.selectColor(session.secondaryColor) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    secondary?.code ?: "-",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                    color = if (luminance(secondary?.argb ?: 0xFFCCCCCC.toInt()) > 150) {
                        Color(0xDD111111)
                    } else {
                        Color(0xEEFFFFFF)
                    },
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(22.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
            )
            session.recentColors
                .filter { it != session.color && it != session.secondaryColor }
                .take(4)
                .forEach { idx ->
                    val entry = session.palette.colors.getOrNull(idx) ?: return@forEach
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(entry.argb))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(7.dp))
                            .clickable { session.selectColor(idx) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            entry.code,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                            color = if (luminance(entry.argb) > 150) Color(0xDD111111)
                            else Color(0xEEFFFFFF),
                        )
                    }
                }
            IconButton(onClick = onOpenPalette, modifier = Modifier.size(30.dp)) {
                Icon(
                    StudioIcons.Palette,
                    contentDescription = "色卡",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * 竖屏左侧工具浮窗：只放图标，一列到底。
 */
@Composable
private fun StudioToolRailVertical(
    session: StudioSession,
    headerModifier: Modifier = Modifier,
    collapsed: Boolean = false,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            // 展开时只占画布 72% 高：留出上下拖动空间，工具多时内部自己滚动
            .then(if (collapsed) Modifier else Modifier.fillMaxHeight(0.72f))
            .width(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RailDragHeader(
            headerModifier,
            collapsed,
            marker = {
                Icon(
                    session.tool.icon(),
                    contentDescription = session.tool.label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(12.dp),
                )
            },
            onToggle = onToggle,
        )
        if (!collapsed) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                StudioTool.entries.forEachIndexed { index, tool ->
                    val selected = session.tool == tool
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else Color.Transparent,
                            )
                            .clickable {
                                session.tool = tool
                                session.clearPending()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = tool.icon(),
                            contentDescription = tool.label,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (index == 3 || index == 8 || index == 11 || index == 15) {
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                        )
                    }
                }
            }
        }
    }
}

/** 竖屏右侧颜色浮窗：当前色 / 副色 / 最近用色 / 色卡入口。 */
@Composable
private fun StudioColorRailVertical(
    session: StudioSession,
    onOpenPalette: () -> Unit,
    headerModifier: Modifier = Modifier,
    collapsed: Boolean = false,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = session.palette.colors.getOrNull(session.color)
    val secondary = session.palette.colors.getOrNull(session.secondaryColor)
    Column(
        modifier = modifier
            .width(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RailDragHeader(
            headerModifier,
            collapsed,
            marker = {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(current?.argb ?: 0xFFCCCCCC.toInt()))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(3.dp)),
                )
            },
            onToggle = onToggle,
        )
        if (!collapsed) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(current?.argb ?: 0xFFCCCCCC.toInt()))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
                    .clickable(onClick = onOpenPalette),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    current?.code ?: "色",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = if (luminance(current?.argb ?: 0xFFCCCCCC.toInt()) > 150) {
                        Color(0xDD111111)
                    } else {
                        Color(0xEEFFFFFF)
                    },
                    maxLines = 1,
                )
            }
            IconButton(onClick = { session.swapColors() }, modifier = Modifier.size(30.dp)) {
            Icon(
                MorphIcons.SwapHorizontal,
                contentDescription = "交换主副色",
                modifier = Modifier.size(18.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(secondary?.argb ?: 0xFFCCCCCC.toInt()))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(7.dp))
                .clickable { session.selectColor(session.secondaryColor) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                secondary?.code ?: "-",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                color = if (luminance(secondary?.argb ?: 0xFFCCCCCC.toInt()) > 150) {
                    Color(0xDD111111)
                } else {
                    Color(0xEEFFFFFF)
                },
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        )
        session.recentColors
            .filter { it != session.color && it != session.secondaryColor }
            .take(4)
            .forEach { idx ->
            val entry = session.palette.colors.getOrNull(idx)
            if (entry != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(entry.argb))
                        .border(
                            if (idx == session.color) 2.dp else 1.dp,
                            if (idx == session.color) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(7.dp),
                        )
                        .clickable { session.selectColor(idx) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        entry.code,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                        color = if (luminance(entry.argb) > 150) Color(0xDD111111)
                        else Color(0xEEFFFFFF),
                        maxLines = 1,
                    )
                }
            }
        }
        IconButton(onClick = onOpenPalette, modifier = Modifier.size(32.dp)) {
            Icon(
                StudioIcons.Palette,
                contentDescription = "色卡",
                modifier = Modifier.size(19.dp),
            )
        }
        }
    }
}

@Composable
fun StudioPaletteStrip(
    session: StudioSession,
    onOpenPalette: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = session.palette
    val current = palette.colors.getOrNull(session.color)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.xs, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenPalette)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(current?.argb ?: 0xFFCCCCCC.toInt()))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(7.dp)),
            )
            Text(
                current?.code ?: "色卡",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        IconButton(onClick = { session.swapColors() }, modifier = Modifier.size(30.dp)) {
            Icon(MorphIcons.SwapHorizontal, contentDescription = "交换主副色", modifier = Modifier.size(18.dp))
        }
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            items(palette.colors.size) { index ->
                val entry = palette.colors[index]
                val selected = session.color == index
                val secondary = session.secondaryColor == index
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(entry.argb))
                        .selectedBorder(selected, 7)
                        .clickable { session.selectColor(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (secondary && !selected) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .align(Alignment.TopEnd),
                        )
                    }
                    Text(
                        entry.code,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                        color = if (luminance(entry.argb) > 150) Color(0xDD111111) else Color(0xEEFFFFFF),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(title: String, subtitle: String? = null, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onClose) { Text("关闭") }
    }
}

@Composable
private fun ActionChip(
    text: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (selected) Color.Transparent else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(999.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                selected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = Spacing.md, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = 2.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioLayersPanel(session: StudioSession, onClose: () -> Unit) {
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
                    SectionLabel("图纸：单行标题 / 四边坐标 / 格内色号 / 底部用量条")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioFillPanel(session: StudioSession, onClose: () -> Unit) {
    if (session.revision < 0) return
    val engine = session.engine
    val hasSelection = engine.hasSelection
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.lg),
        ) {
            PanelHeader(
                "选区 / 渐变 / 抖动",
                if (hasSelection) "当前有选区，渐变和抖动只作用于选区内" else "没有选区：渐变和抖动作用于整个当前图层",
                onClose,
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionChip("全选") { session.act { it.selectAll() } }
                ActionChip("取消选区", enabled = hasSelection) { session.act { it.clearSelection() } }
                ActionChip("反选") { session.act { it.invertSelection() } }
                ActionChip("选当前图层") { session.act { it.selectLayerContent() } }
                ActionChip("复制") { session.act { it.copySelection() } }
                ActionChip("剪切", enabled = hasSelection) { session.act { it.cutSelection() } }
                ActionChip("粘贴", enabled = engine.canPaste) { session.act { it.pasteClipboard() } }
                ActionChip("删除选区内容", enabled = hasSelection) { session.act { it.deleteSelection() } }
                ActionChip("填充选区", enabled = hasSelection) {
                    val color = session.color
                    session.act { it.fillSelection(color, selectionOnly = true) }
                }
            }

            SectionLabel("颜色：主色 ${session.palette.colors.getOrNull(session.color)?.code ?: "-"} / 副色 ${session.palette.colors.getOrNull(session.secondaryColor)?.code ?: "-"}")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionChip("交换主副色") { session.swapColors() }
                ActionChip("用主色填满") {
                    val color = session.color
                    session.act { it.fillSelection(color, selectionOnly = false) }
                }
                ActionChip("颜色替换：主→副") {
                    val from = session.color
                    val to = session.secondaryColor
                    session.act { it.replaceColor(from, to, false) }
                }
                ActionChip("全工程替换：主→副") {
                    val from = session.color
                    val to = session.secondaryColor
                    session.act { it.replaceColor(from, to, true) }
                }
            }

            SectionLabel("渐变")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionChip("线性", selected = !session.gradientRadial) { session.gradientRadial = false }
                ActionChip("径向", selected = session.gradientRadial) { session.gradientRadial = true }
                ActionChip("抖动过渡", selected = session.gradientDithered) {
                    session.gradientDithered = !session.gradientDithered
                }
                ActionChip("铺满当前图层/选区") {
                    session.act { eng ->
                        val b = eng.selectionBounds()
                        val x0 = b?.get(0) ?: 0
                        val y0 = b?.get(1) ?: 0
                        val x1 = b?.get(2) ?: (eng.width - 1)
                        val y1 = b?.get(3) ?: (eng.height - 1)
                        eng.gradient(
                            x0, y0, x1, y1,
                            session.color, session.secondaryColor,
                            session.gradientRadial, session.gradientDithered,
                        )
                    }
                }
            }

            SectionLabel("抖动填充：主色占比 ${session.ditherDensity}%")
            Slider(
                value = session.ditherDensity.toFloat(),
                onValueChange = { session.ditherDensity = it.roundToInt() },
                valueRange = 0f..100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
            ) {
                ActionChip("用主/副色抖动铺满") {
                    val a = session.color
                    val b = session.secondaryColor
                    val density = session.ditherDensity
                    session.act { it.ditherFill(a, b, density) }
                }
            }

            SectionLabel("选区移动")
            Text(
                "选「移动选区」工具后直接拖；也可以在下面按格微调。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionChip("← 1") { session.act { it.moveSelection(-1, 0) } }
                ActionChip("→ 1") { session.act { it.moveSelection(1, 0) } }
                ActionChip("↑ 1") { session.act { it.moveSelection(0, -1) } }
                ActionChip("↓ 1") { session.act { it.moveSelection(0, 1) } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioCanvasPanel(
    session: StudioSession,
    palettes: List<BeadPalette>,
    onPalette: (BeadPalette) -> Unit,
    onResetRails: () -> Unit,
    onClose: () -> Unit,
) {
    if (session.revision < 0) return
    val engine = session.engine
    var name by remember { mutableStateOf(session.name) }
    var widthText by remember(session.revision) { mutableStateOf(engine.width.toString()) }
    var heightText by remember(session.revision) { mutableStateOf(engine.height.toString()) }
    var lockAspect by remember { mutableStateOf(false) }
    val newW = widthText.toIntOrNull()
    val newH = heightText.toIntOrNull()
    val sizeOk = newW != null && newH != null && newW in 5..500 && newH in 5..500
    val hasSelection = engine.hasSelection
    val bounds = engine.selectionBounds()

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.lg),
        ) {
            PanelHeader("画布 / 工程设置", "${engine.width}×${engine.height} · ${engine.layerCount} 层 · ${engine.frameCount} 帧", onClose)

            SectionLabel("工程名称")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40); session.name = name },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
            )

            SectionLabel("色卡")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                palettes.forEach { p ->
                    ActionChip(
                        "${p.brandLabel} ${if (p.beadSize.name == "MM2_6") "2.6" else "5"}mm",
                        selected = p.brand == session.palette.brand && p.beadSize == session.palette.beadSize,
                    ) { onPalette(p) }
                }
            }

            SectionLabel("改画布尺寸")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { raw ->
                        widthText = raw.filter { it.isDigit() }.take(3)
                        if (lockAspect && newH != null && widthText.toIntOrNull() != null) {
                            val ratio = engine.height.toFloat() / engine.width
                            heightText = (widthText.toInt() * ratio).roundToInt().coerceIn(5, 500).toString()
                        }
                    },
                    label = { Text("宽") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { raw ->
                        heightText = raw.filter { it.isDigit() }.take(3)
                        if (lockAspect && widthText.toIntOrNull() != null && heightText.toIntOrNull() != null) {
                            val ratio = engine.width.toFloat() / engine.height
                            widthText = (heightText.toInt() * ratio).roundToInt().coerceIn(5, 500).toString()
                        }
                    },
                    label = { Text("高") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            SectionLabel("常用尺寸（立即应用，内容居中）")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(16, 32, 48, 64, 96, 128, 256, 500).forEach { n ->
                    ActionChip(
                        "$n×$n",
                        selected = engine.width == n && engine.height == n,
                    ) {
                        widthText = n.toString()
                        heightText = n.toString()
                        session.act { it.resizeCanvas(n, n, 0.5f, 0.5f) }
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
                ActionChip("锁定比例", selected = lockAspect) { lockAspect = !lockAspect }
                ActionChip("只改画布（内容居中）", enabled = sizeOk) {
                    session.act { it.resizeCanvas(newW!!, newH!!, 0.5f, 0.5f) }
                }
                ActionChip("缩放内容", enabled = sizeOk) {
                    session.act { it.scaleContent(newW!!, newH!!) }
                }
                ActionChip("裁剪到选区", enabled = hasSelection && bounds != null) {
                    val b = bounds!!
                    session.act { it.crop(b[0], b[1], b[2] - b[0] + 1, b[3] - b[1] + 1) }
                }
                ActionChip("裁掉空白边") {
                    val b = engine.contentBounds()
                    if (b != null) {
                        session.act {
                            it.crop(b[0], b[1], b[2] - b[0] + 1, b[3] - b[1] + 1)
                        }
                    }
                }
            }

            SectionLabel("变换（所有帧同步，可撤销）")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                ActionChip("水平翻转") { session.act { it.flipHorizontal() } }
                ActionChip("垂直翻转") { session.act { it.flipVertical() } }
                ActionChip("左转 90°") { session.act { it.rotate90(false) } }
                ActionChip("右转 90°") { session.act { it.rotate90(true) } }
                ActionChip("旋转 180°") { session.act { it.rotate180() } }
                ActionChip("← 一格") { session.act { it.shift(-1, 0) } }
                ActionChip("→ 一格") { session.act { it.shift(1, 0) } }
                ActionChip("↑ 一格") { session.act { it.shift(0, -1) } }
                ActionChip("↓ 一格") { session.act { it.shift(0, 1) } }
            }

            SectionLabel("对称")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                SymmetryMode.entries.forEach { mode ->
                    ActionChip(mode.label, selected = session.symmetry == mode) {
                        session.symmetry = mode
                        session.touch()
                    }
                }
            }

            ToggleRow("显示网格", checked = session.showGrid, onCheckedChange = { session.showGrid = it })
            SectionLabel("侧栏位置")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionChip("重置两块浮窗") { onResetRails() }
            }
            Text(
                "拖住顶部双横线移动浮窗；点右侧小箭头收起/展开，收起后仍可拖动。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = 2.dp),
            )
            ToggleRow("显示中心线", checked = session.showCenterLines, onCheckedChange = { session.showCenterLines = it })
            ToggleRow("显示色号", "每格显示当前图层的色号，放大到 22px 以上才绘制", checked = session.showCodes, onCheckedChange = { session.showCodes = it })
            ToggleRow("像素完美", "滑动时去掉 L 形拐角的多余像素", checked = session.pixelPerfect, onCheckedChange = { session.pixelPerfect = it })
            ToggleRow("圆形笔刷", "关闭为方形笔刷", checked = session.circleBrush, onCheckedChange = { session.circleBrush = it })
            ToggleRow("洋葱皮", "显示上一帧残影", checked = session.onionSkin, onCheckedChange = { session.onionSkin = it })
        }
    }
}

@Composable
fun StudioTextDialog(
    origin: Int,
    session: StudioSession,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("拼豆") }
    var fontSize by remember { mutableIntStateOf(10) }
    val engine = session.engine
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("落字") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(80) },
                    label = { Text("文字（支持换行）") },
                    minLines = 2,
                    maxLines = 4,
                )
                Text(
                    "字高 ${fontSize} 格 · 点击位置是左上角",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = { fontSize = it.roundToInt() },
                    valueRange = 3f..40f,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val mask = StudioTextRasterizer.rasterize(text, fontSize)
                    if (mask != null) {
                        session.act {
                            it.stampMask(
                                mask.cells, mask.width, mask.height,
                                origin % engine.width, origin / engine.width,
                                session.color,
                            )
                        }
                    }
                    onDismiss()
                },
            ) { Text("落字") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun StudioDiscardDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("不保存退出？") },
        text = { Text("当前画像素工作区的修改会丢失。已经保存过的项目不受影响。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("不保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("继续画") } },
    )
}

@Composable
fun StudioFlattenDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("转为普通图纸？") },
        text = {
            Text(
                "每一帧会拍平成一页，图层、混合模式和帧结构不再保留；" +
                    "拍平后的格子、色号和尺寸不变，之后可以在普通编辑器里继续改。",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("转为普通图纸") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("继续分层") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioPaletteSheet(
    session: StudioSession,
    palettes: List<BeadPalette>,
    onPalette: (BeadPalette) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.lg),
        ) {
            PanelHeader(
                "选择色卡",
                "当前：${session.palette.brandLabel} · ${if (session.palette.beadSize.name == "MM2_6") "2.6mm" else "5mm"} · ${session.palette.colors.size} 色",
                onDismiss,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                palettes.forEach { p ->
                    val selected = p.brand == session.palette.brand && p.beadSize == session.palette.beadSize
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPalette(p)
                                onDismiss()
                            }
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else Color.Transparent,
                            )
                            .padding(horizontal = Spacing.md, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(p.colors.first().argb)),
                        )
                        Column(Modifier.weight(1f).padding(start = Spacing.sm)) {
                            Text("${p.brandLabel} ${if (p.beadSize.name == "MM2_6") "Mini 2.6mm" else "5mm"}", style = MaterialTheme.typography.bodyMedium)
                            Text("${p.colors.size} 色", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected) Icon(MorphIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(
                "换色卡只改当前色卡索引的含义；已经画在格子的索引不会自动重映射。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
    }
}
