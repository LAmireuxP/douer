package com.pindou.app.ui.export

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pindou.app.core.export.ExportService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private typealias ExportBlock = () -> ExportService.Exported?

class Exporter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onResult: (ExportService.Exported?) -> Unit,
) {
    var message by mutableStateOf<String?>(null)
        private set

    fun run(label: String, block: ExportBlock) {
        if (ExportService.needsLegacyStoragePermission(context)) {
            pending = label to block
            permissionLauncherHook?.invoke(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            execute(label, block)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        val next = pending
        pending = null
        when {
            granted && next != null -> execute(next.first, next.second)
            !granted -> message = "未授予存储权限，无法写入系统下载目录"
        }
    }

    private var pending: Pair<String, ExportBlock>? = null

    /** rememberExporter 挂接 ActivityResultLauncher；null 时给出明确错误而不是静默失败。 */
    internal var permissionLauncherHook: ((String) -> Unit)? = null

    private fun execute(label: String, block: ExportBlock) {
        scope.launch {
            message = "正在导出$label…"
            val out = withContext(Dispatchers.IO) { block() }
            message = when (out) {
                is ExportService.Exported.FileReady -> "$label 已保存到下载目录：${out.file.name}"
                is ExportService.Exported.Failed -> "$label 失败：${out.message}"
                null -> null
            }
            onResult(out)
        }
    }
}

/** 预览页/画像素页共用：权限申请、IO 线程、结果消息在这里收口。 */
@Composable
fun rememberExporter(
    onResult: (ExportService.Exported?) -> Unit = {},
): Exporter {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = remember { Exporter(context, scope, onResult) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> exporter.onPermissionResult(granted) }
    exporter.permissionLauncherHook = { launcher.launch(it) }
    return exporter
}
