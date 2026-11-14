package com.pindou.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pindou.app.core.display.RefreshRateController
import com.pindou.app.core.update.UpdateManifest
import com.pindou.app.ui.shadcn.ShCard
import com.pindou.app.ui.shadcn.ShChip
import com.pindou.app.ui.shadcn.ShPageHeader
import com.pindou.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    var paletteMenu by remember { mutableStateOf(false) }
    val settings = vm.settings
    val updateVm: UpdateViewModel = viewModel()
    var updateDialog by remember { mutableStateOf(false) }

    // 设置页打开时静默检查一次；UpdateViewModel 内部有 6 小时节流
    LaunchedEffect(Unit) { updateVm.check(auto = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ShPageHeader(
            title = "设置",
            subtitle = "默认参数只影响新建的图纸",
            modifier = Modifier.padding(top = Spacing.lg),
        )

        // 默认参数
        ShCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("默认网格尺寸", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    listOf(29 to 29, 48 to 48, 58 to 58, 78 to 78).forEach { (w, h) ->
                        ShChip(
                            text = "${w}×${h}",
                            selected = settings.defaultGridW == w && settings.defaultGridH == h,
                            onClick = {
                                vm.viewModelScope.launch { vm.settingsStore.setDefaultGrid(w, h) }
                            },
                        )
                    }
                }

                Text(
                    "默认色卡",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
                val brands = vm.palettes.map { it.brand to it.brandLabel }.distinct()
                val currentLabel = brands.firstOrNull { it.first == settings.defaultPaletteBrand }?.second
                    ?: settings.defaultPaletteBrand
                ExposedDropdownMenuBox(
                    expanded = paletteMenu,
                    onExpandedChange = { paletteMenu = it },
                ) {
                    OutlinedTextField(
                        value = currentLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("打开新图纸时预选") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paletteMenu) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = paletteMenu, onDismissRequest = { paletteMenu = false }) {
                        brands.forEach { (brand, label) ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    vm.viewModelScope.launch { vm.settingsStore.setDefaultPalette(brand) }
                                    paletteMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // 外观
        ShCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("主题", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (mode, label) ->
                        ShChip(
                            text = label,
                            selected = settings.themeMode == mode,
                            onClick = {
                                vm.viewModelScope.launch { vm.settingsStore.setThemeMode(mode) }
                            },
                        )
                    }
                }
            }
        }

        // 刷新率
        ShCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("屏幕刷新率", style = MaterialTheme.typography.titleLarge)
                val view = LocalView.current
                val rates = remember(view) { RefreshRateController.supportedRates(view.display) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ShChip(
                        text = "跟随系统",
                        selected = settings.refreshMode == RefreshRateController.SYSTEM,
                        onClick = {
                            vm.viewModelScope.launch {
                                vm.settingsStore.setRefreshMode(RefreshRateController.SYSTEM)
                            }
                        },
                    )
                    rates.forEach { hz ->
                        ShChip(
                            text = "$hz Hz",
                            selected = settings.refreshMode == hz.toString(),
                            onClick = {
                                vm.viewModelScope.launch {
                                    vm.settingsStore.setRefreshMode(hz.toString())
                                }
                            },
                        )
                    }
                }
                var liveHz by remember { mutableStateOf(view.display?.refreshRate ?: 0f) }
                LaunchedEffect(view) {
                    while (true) {
                        liveHz = view.display?.refreshRate ?: liveHz
                        delay(500)
                    }
                }
                Text(
                    "面板当前 ${liveHz.roundToInt()} Hz · 仅在本应用内生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // AI 实验
        ShCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("AI 主体分割", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "实验功能 · 用端侧模型把主体和背景分开",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.aiSubjectEnabled,
                        onCheckedChange = { enabled ->
                            vm.viewModelScope.launch {
                                vm.settingsStore.setAiSubjectEnabled(enabled)
                            }
                        },
                    )
                }
                Text(
                    "开启后自动抠主体、按主体取景，背景不会再把主体边缘吃掉。" +
                        "首次使用需下载模型；设备不支持时自动回退传统算法，不影响生成。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                vm.aiMaskStatus?.let { status ->
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // 统计
        ShCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("生成统计", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                    Text(
                        "${vm.stats.total}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        " 张图纸",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
                    )
                }
                Text(
                    "从 1.2.3 起统计，只计算真正保存进项目库的新图纸",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.xxs),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                StatRow("图片生成", vm.stats.photo)
                StatRow("像素画导入", vm.stats.pixel)
                StatRow("色号图识别", vm.stats.chart)
                StatRow("文字拼豆", vm.stats.text)
                StatRow("空白画布", vm.stats.blank)
                StatRow(".bpat 导入", vm.stats.bpat)
            }
        }

        // 版本更新
        ShCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("版本更新", style = MaterialTheme.typography.titleLarge)
                Text(
                    "当前版本 ${updateVm.currentVersionLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    updateStatusText(updateVm.state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        updateDialog = true
                        when (val state = updateVm.state) {
                            is UpdateViewModel.State.Available -> updateVm.download()
                            is UpdateViewModel.State.Ready -> updateVm.install()
                            else -> updateVm.check()
                        }
                    },
                    enabled = updateVm.state !is UpdateViewModel.State.Checking &&
                        updateVm.state !is UpdateViewModel.State.Downloading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xxs),
                ) {
                    Text(updateButtonText(updateVm.state))
                }
            }
        }

        // 关于
        ShCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("关于", style = MaterialTheme.typography.titleLarge)
                Text(
                    "本项目以 AGPL-3.0 开源",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "源码：github.com/LAmireuxP/douer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Gitee：gitee.com/L_Amireux_P/douer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val context = LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val intent = Intent(
                                Intent.ACTION_SENDTO,
                                Uri.parse("mailto:li.amireux@qq.com"),
                            ).apply {
                                putExtra(Intent.EXTRA_SUBJECT, "豆儿反馈")
                            }
                            runCatching { context.startActivity(intent) }
                                .onFailure {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("反馈邮箱", "li.amireux@qq.com"),
                                    )
                                    Toast.makeText(context, "邮箱已复制", Toast.LENGTH_SHORT).show()
                                }
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("反馈通道", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "li.amireux@qq.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // 玻璃底栏叠在内容上，尾部留出滚动余量
        Row(modifier = Modifier.padding(bottom = 104.dp)) {}
    }

    if (updateDialog) {
        UpdateDialog(
            state = updateVm.state,
            onDismiss = { updateDialog = false },
            onDownload = { updateVm.download() },
            onInstall = { updateVm.install() },
            onRetry = { updateVm.check() },
        )
    }
}

@Composable
private fun StatRow(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun updateStatusText(state: UpdateViewModel.State): String = when (state) {
    UpdateViewModel.State.Idle -> "国内优先 Gitee / 加速节点，失败自动切回 GitHub"
    UpdateViewModel.State.Checking -> "正在检查更新…"
    is UpdateViewModel.State.Available ->
        "发现新版本 v${state.info.versionName} · ${formatUpdateSize(state.info.sizeBytes)}"
    UpdateViewModel.State.UpToDate -> "已是最新版本"
    is UpdateViewModel.State.Downloading ->
        "正在下载 ${(state.progress * 100).roundToInt()}%"
    is UpdateViewModel.State.Ready -> state.message ?: "更新包已下载，等待安装"
    is UpdateViewModel.State.Error -> "检查失败：${state.message}"
}

private fun updateButtonText(state: UpdateViewModel.State): String = when (state) {
    is UpdateViewModel.State.Available -> "下载并安装 v${state.info.versionName}"
    is UpdateViewModel.State.Ready -> "安装更新"
    is UpdateViewModel.State.Downloading -> "下载中 ${(state.progress * 100).roundToInt()}%"
    UpdateViewModel.State.Checking -> "正在检查…"
    is UpdateViewModel.State.Error -> "重试检查"
    else -> "检查更新"
}

private fun formatUpdateSize(bytes: Long): String = when {
    bytes <= 0 -> "大小未知"
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

private fun openReleasePage(context: Context) {
    val urls = listOf(
        UpdateManifest.DOMESTIC_PROXY + UpdateManifest.GITHUB_RELEASES,
        UpdateManifest.GITHUB_RELEASES,
    )
    for (url in urls) {
        val ok = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (ok) return
    }
    Toast.makeText(context, "没有可打开浏览器的应用", Toast.LENGTH_SHORT).show()
}

@Composable
private fun UpdateDialog(
    state: UpdateViewModel.State,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val downloading = state is UpdateViewModel.State.Downloading
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = {
            Text(
                when (state) {
                    UpdateViewModel.State.Checking -> "检查更新"
                    is UpdateViewModel.State.Available -> "发现新版本 v${state.info.versionName}"
                    UpdateViewModel.State.UpToDate -> "已是最新版本"
                    is UpdateViewModel.State.Downloading -> "正在下载更新"
                    is UpdateViewModel.State.Ready -> "更新已下载"
                    is UpdateViewModel.State.Error -> "更新失败"
                    UpdateViewModel.State.Idle -> "检查更新"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                when (state) {
                    UpdateViewModel.State.Checking -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("正在连接更新服务器…")
                    }
                    is UpdateViewModel.State.Available -> {
                        Text(
                            "v${state.info.versionName} · ${formatUpdateSize(state.info.sizeBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (state.info.notes.isBlank()) {
                            Text("本次更新没有附加说明")
                        } else {
                            Text(state.info.notes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    UpdateViewModel.State.UpToDate -> Text("当前版本已是最新。")
                    is UpdateViewModel.State.Downloading -> {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${(state.progress * 100).roundToInt()}%")
                    }
                    is UpdateViewModel.State.Ready -> {
                        Text(state.message ?: "更新包已下载，点击安装。")
                    }
                    is UpdateViewModel.State.Error -> {
                        Text(state.message)
                        Text(
                            "可以重试，或打开发布页手动下载。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    UpdateViewModel.State.Idle -> Text("点击检查更新。")
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateViewModel.State.Available -> {
                    Button(onClick = onDownload) { Text("下载并安装") }
                }
                is UpdateViewModel.State.Ready -> {
                    Button(onClick = onInstall) { Text("安装") }
                }
                is UpdateViewModel.State.Error -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        TextButton(onClick = { openReleasePage(context) }) { Text("发布页") }
                        Button(onClick = onRetry) { Text("重试") }
                    }
                }
                UpdateViewModel.State.Idle -> {
                    Button(onClick = onRetry) { Text("检查") }
                }
                else -> {
                    TextButton(onClick = onDismiss) {
                        Text(if (state is UpdateViewModel.State.Downloading) "隐藏" else "知道了")
                    }
                }
            }
        },
        dismissButton = if (downloading) {
            null
        } else {
            {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}
