package com.pindou.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.ui.AppViewModel
import com.pindou.app.ui.theme.ClayColors
import com.pindou.app.ui.theme.Spacing

/** 色板管理：整图换品牌 + 按 ΔE 合并相近色。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EditorManageSheet(
    vm: AppViewModel,
    palette: BeadPalette,
    onDismiss: () -> Unit,
) {
    var mergeThreshold by remember { mutableFloatStateOf(3f) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("换品牌色卡", style = MaterialTheme.typography.titleLarge)
            Text(
                "全图按最近色重映射到所选品牌（仅列同规格）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                vm.palettes
                    .filter { it.beadSize == palette.beadSize }
                    .forEach { p ->
                        val selected = p.brand == palette.brand
                        TextButton(
                            onClick = {
                                vm.applyPaletteRemap(p)
                                onDismiss()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (selected) {
                                    ClayColors.PrimaryInk
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ),
                        ) { Text(if (selected) "✓${p.brandLabel}" else p.brandLabel) }
                    }
            }

            Text("合并相近色", style = MaterialTheme.typography.titleLarge)
            Text(
                "ΔE 阈值 %.1f：阈值越大合并越激进".format(mergeThreshold),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = mergeThreshold,
                onValueChange = { mergeThreshold = it },
                valueRange = 1f..10f,
            )
            Button(
                onClick = {
                    vm.mergeSimilarEditorColorsWithReport(mergeThreshold.toDouble())
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("按当前阈值合并") }
        }
    }
}
