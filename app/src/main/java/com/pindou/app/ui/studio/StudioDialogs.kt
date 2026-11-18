package com.pindou.app.ui.studio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pindou.app.ui.theme.Spacing
import kotlin.math.roundToInt

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
