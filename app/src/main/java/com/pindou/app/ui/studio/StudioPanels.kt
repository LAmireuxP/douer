package com.pindou.app.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.studio.StudioBlendMode
import com.pindou.app.ui.icon.MorphIcons
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
internal fun Modifier.selectedBorder(selected: Boolean, radius: Int = 8): Modifier = border(
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
@Suppress("ModifierParameter")
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
@Suppress("ModifierParameter")
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

@Suppress("ModifierParameter")
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
@Suppress("ModifierParameter")
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
@Suppress("ModifierParameter")
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
@Suppress("ModifierParameter")
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

