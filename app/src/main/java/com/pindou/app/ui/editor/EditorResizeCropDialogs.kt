package com.pindou.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pindou.app.ui.AppViewModel
import com.pindou.app.ui.theme.Spacing

/** 编辑器「改尺寸」对话框；输入状态只属于弹窗，关闭即销毁。 */
@Composable
internal fun EditorResizeDialog(
    vm: AppViewModel,
    initialWidth: Int,
    initialHeight: Int,
    onDismiss: () -> Unit,
) {
    var w by remember { mutableStateOf(initialWidth.toString()) }
    var h by remember { mutableStateOf(initialHeight.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("改尺寸") },
        text = {
            Column {
                Text(
                    "所有页内容都会被保留；施工进度会因尺寸变化作废",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(top = Spacing.sm),
                ) {
                    OutlinedTextField(
                        value = w,
                        onValueChange = { w = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("宽") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = h,
                        onValueChange = { h = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("高") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val nw = w.toIntOrNull() ?: 0
                val nh = h.toIntOrNull() ?: 0
                if (nw in 5..500 && nh in 5..500) {
                    vm.resizeEditor(nw, nh)
                    onDismiss()
                }
            }) { Text("应用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 编辑器「裁剪图纸」对话框；左上角从 1 开始，范围不能超过当前图纸。 */
@Composable
internal fun EditorCropDialog(
    vm: AppViewModel,
    initialWidth: Int,
    initialHeight: Int,
    onDismiss: () -> Unit,
) {
    var leftText by remember { mutableStateOf("1") }
    var topText by remember { mutableStateOf("1") }
    var w by remember { mutableStateOf(initialWidth.toString()) }
    var h by remember { mutableStateOf(initialHeight.toString()) }
    val l = leftText.toIntOrNull()
    val t = topText.toIntOrNull()
    val nw = w.toIntOrNull()
    val nh = h.toIntOrNull()
    val valid = l != null && t != null && nw != null && nh != null &&
        l >= 1 && t >= 1 && nw in 5..500 && nh in 5..500 &&
        l - 1 + nw <= initialWidth && t - 1 + nh <= initialHeight
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("裁剪图纸") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "当前 ${initialWidth}×${initialHeight}，左上角从 1 开始；输入 " +
                        "(${l ?: "?"}, ${t ?: "?"}) 与宽高。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = leftText,
                        onValueChange = { leftText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("左") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = topText,
                        onValueChange = { topText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("上") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = w,
                        onValueChange = { w = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("宽") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = h,
                        onValueChange = { h = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("高") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    if (valid) {
                        "结果 ${nw}×${nh}，所有页同步裁剪与继承"
                    } else {
                        "范围无效：宽高 5..500，且不能超过当前图纸"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (l != null && t != null && nw != null && nh != null && valid) {
                        vm.cropEditor(l - 1, t - 1, nw, nh)
                        onDismiss()
                    }
                },
                enabled = valid,
            ) { Text("裁剪") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
