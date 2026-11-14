package com.pindou.app.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pindou.app.core.export.ExportService
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.shadcn.TextButton
import com.pindou.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class PdfPreviewState(
    val pages: List<ImageBitmap> = emptyList(),
    val error: String? = null,
)

/**
 * 应用内 PDF 预览。
 *
 * 系统里只装了夸克时，夸克会要求「所有文件访问权限」，否则对 MediaStore /
 * FileProvider URI 都报「无效的文件路径」。这里直接用 PdfRenderer 渲染，
 * 不再依赖第三方阅读器；分享按钮仍保留给网盘/聊天工具。
 */
@Composable
fun PdfPreviewDialog(
    exported: ExportService.Exported.FileReady,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    val state by produceState(
        initialValue = PdfPreviewState(),
        key1 = exported.uri,
        key2 = exported.file.name,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val local = materialize(context, exported)
                val pages = mutableListOf<ImageBitmap>()
                ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    val renderer = PdfRenderer(pfd)
                    try {
                        for (index in 0 until renderer.pageCount) {
                            val page = renderer.openPage(index)
                            try {
                                // 预览也按 2400px 宽渲染：手机上双指放大后色号仍清楚，
                                // 单页约 8~20MB，退出对话框即释放。
                                val targetW = 2400
                                val scale = targetW.toFloat() / page.width
                                val targetH = (page.height * scale).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(
                                    targetW,
                                    targetH,
                                    Bitmap.Config.ARGB_8888,
                                )
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.render(
                                    bitmap,
                                    null,
                                    null,
                                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                                )
                                pages.add(bitmap.asImageBitmap())
                            } finally {
                                page.close()
                            }
                        }
                    } finally {
                        renderer.close()
                    }
                }
                PdfPreviewState(pages = pages)
            }.getOrElse {
                PdfPreviewState(error = it.message ?: "PDF 读取失败")
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = exported.file.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onShare) { Text("分享") }
                    IconButton(onClick = onDismiss) {
                        Icon(MorphIcons.X, contentDescription = "关闭")
                    }
                }
                when {
                    state.error != null -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "PDF 预览失败：${state.error}",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    state.pages.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            CircularProgressIndicator()
                            Text("正在渲染 PDF…")
                        }
                    }
                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(state.pages) { page ->
                            Image(
                                bitmap = page,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.sm),
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * PdfRenderer 需要可 seek 的本地文件。MediaStore / FileProvider URI 都先复制到
 * cache；文件名带时间戳，不会覆盖旧导出。
 */
private fun materialize(
    context: android.content.Context,
    exported: ExportService.Exported.FileReady,
): File {
    val dir = File(context.cacheDir, "pdf-preview").apply { mkdirs() }
    val local = File(dir, exported.file.name)
    if (local.length() > 0L) return local
    context.contentResolver.openInputStream(exported.uri)?.use { input ->
        local.outputStream().use { output -> input.copyTo(output) }
    } ?: error("无法读取 PDF 文件")
    return local
}
