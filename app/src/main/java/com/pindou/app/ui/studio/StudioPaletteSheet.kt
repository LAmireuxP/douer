package com.pindou.app.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.theme.Spacing

/**
 * 色卡选择 sheet。
 *
 * 段落结构：先一行「当前色卡」摘要，然后是可选色卡列表（品牌 + 珠径 + 色数），
 * 每行右侧只有当前项打勾，不再用整行底色表达选中——浅色主题下
 * 底色和 hover 态太接近，用户分不清点没点上。
 */
@Composable
fun StudioPaletteSheet(
    session: StudioSession,
    palettes: List<BeadPalette>,
    onPalette: (BeadPalette) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = session.palette
    val sizeLabel = if (current.beadSize.name == "MM2_6") "2.6mm" else "5mm"
    StudioSheet(
        title = "选择色卡",
        subtitle = "当前：${current.brandLabel} · $sizeLabel · ${current.colors.size} 色",
        onClose = onDismiss,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .height((((palettes.size + 1) / 2) * 84).coerceIn(84, 420).dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(palettes) { p ->
                val selected =
                    p.brand == current.brand && p.beadSize == current.beadSize
                PaletteTile(
                    palette = p,
                    selected = selected,
                    onClick = {
                        onPalette(p)
                        onDismiss()
                    },
                )
            }
        }

        Text(
            "换色卡只改当前色卡索引的含义；已经画在格子的索引不会自动重映射。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun PaletteTile(
    palette: BeadPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val sizeLabel = if (palette.beadSize.name == "MM2_6") "Mini 2.6mm" else "5mm"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 三色小样：比单色块更容易认出品牌
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(0, palette.colors.size / 2, palette.colors.size - 1)
                .filter { it in palette.colors.indices }
                .forEach { i ->
                    Box(
                        modifier = Modifier
                            .height(16.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(palette.colors[i].argb)),
                    )
                }
        }
        Text(
            palette.brandLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$sizeLabel · ${palette.colors.size} 色",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (selected) {
                androidx.compose.material3.Icon(
                    MorphIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
