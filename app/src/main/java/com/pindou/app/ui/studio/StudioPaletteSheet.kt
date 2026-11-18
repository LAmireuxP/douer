package com.pindou.app.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.theme.Spacing

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
