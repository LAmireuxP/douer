package com.pindou.app.ui.editor

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.ui.AppViewModel
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.shadcn.ShSearchField
import com.pindou.app.ui.theme.Spacing

/**
 * 编辑器色卡选择弹层。
 *
 * [query] 由调用方持有：关闭弹层再打开时保留上次搜索词，和拆分前一致。
 * [measurer]/[textStyle]/[textCache] 由 EditorScreen 跨弹层复用，
 * 避免 291 色第二次打开时重新排版。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorPaletteSheet(
    vm: AppViewModel,
    palette: BeadPalette,
    currentEntry: BeadPalette.Entry,
    safeColorIndex: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    measurer: TextMeasurer,
    textStyle: TextStyle,
    textCache: MutableMap<String, TextLayoutResult>,
    onDismiss: () -> Unit,
) {
    val filtered = remember(palette.brand, query) {
        palette.colors.withIndex().filter { (_, entry) ->
            query.isBlank() ||
                entry.code.contains(query, ignoreCase = true) ||
                entry.name?.contains(query, ignoreCase = true) == true
        }
    }
    // 色卡要在弹层里来回滑：每个色块只留一个 Canvas 节点。原来的
    // Column/Box/背景/描边/Text 五层布局，在首次滑到新行时每格都要
    // 走一遍测量和组合，正是那一下的顿挫来源。
    val primaryColor = MaterialTheme.colorScheme.primary
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(currentEntry.argb), RoundedCornerShape(10.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(10.dp),
                        ),
                )
                Column(Modifier.padding(start = Spacing.sm)) {
                    Text(currentEntry.code, style = MaterialTheme.typography.titleMedium)
                    Text(
                        currentEntry.name?.ifBlank { palette.brandLabel } ?: palette.brandLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${palette.colors.size} 色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ShSearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "搜索色号或颜色名",
                modifier = Modifier.fillMaxWidth(),
                trailing = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                MorphIcons.X,
                                contentDescription = "清空搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
            )

            if (vm.editorRecentColors.isNotEmpty()) {
                Text(
                    "最近使用",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    vm.editorRecentColors.take(8).forEach { index ->
                        val entry = palette.colors.getOrNull(index) ?: return@forEach
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(entry.argb), RoundedCornerShape(9.dp))
                                .border(
                                    width = if (index == safeColorIndex) 2.dp else 1.dp,
                                    color = if (index == safeColorIndex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = RoundedCornerShape(9.dp),
                                )
                                .clickable {
                                    vm.selectEditorColor(index)
                                    onDismiss()
                                },
                        )
                    }
                }
            }

            Text(
                if (query.isBlank()) {
                    "点击颜色后自动切回画笔"
                } else {
                    "匹配 ${filtered.size} / ${palette.colors.size} 色"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (filtered.isEmpty()) {
                // 空结果要给出可执行出口，而不是留一块空白
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "没有匹配的色号",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onQueryChange("") }) { Text("清空搜索") }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 430.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        count = filtered.size,
                        key = { filtered[it].index },
                        contentType = { "swatch" },
                    ) { pos ->
                        val (index, entry) = filtered[pos]
                        PaletteSwatch(
                            code = entry.code,
                            name = entry.name,
                            argb = entry.argb,
                            selected = index == safeColorIndex,
                            measurer = measurer,
                            textStyle = textStyle,
                            textCache = textCache,
                            primaryColor = primaryColor,
                            onClick = {
                                vm.selectEditorColor(index)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}
