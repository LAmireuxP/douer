package com.pindou.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pindou.app.core.update.UpdateClient
import com.pindou.app.core.update.UpdateInstaller
import com.pindou.app.core.update.UpdateManifest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class Available(val info: UpdateManifest.UpdateInfo) : State
        data object UpToDate : State
        data class Downloading(val progress: Float) : State
        data class Ready(
            val info: UpdateManifest.UpdateInfo,
            val file: File,
            val message: String? = null,
        ) : State
        data class Error(val message: String) : State
    }

    private val app = getApplication<Application>()
    private val prefs = app.getSharedPreferences("update_check", Application.MODE_PRIVATE)
    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    val currentVersionName: String
    val currentVersionCode: Long
    val currentVersionLabel: String

    var state by mutableStateOf<State>(State.Idle)
        private set

    init {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        currentVersionName = info.versionName.orEmpty().ifBlank { "0" }
        currentVersionCode = PackageInfoCompat.getLongVersionCode(info)
        currentVersionLabel = "$currentVersionName（$currentVersionCode）"
    }

    /**
     * [auto] 为 true 时静默检查：失败不弹错误，6 小时内不重复请求。
     * 手动检查会把错误写进 state，由设置页弹窗展示。
     */
    fun check(auto: Boolean = false) {
        if (checkJob?.isActive == true || downloadJob?.isActive == true) return
        if (auto) {
            val last = prefs.getLong(KEY_LAST_CHECK, 0L)
            if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL_MS) return
        }
        state = State.Checking
        checkJob = viewModelScope.launch {
            when (val result = UpdateClient.check(currentVersionName)) {
                is UpdateManifest.CheckResult.Available -> state = State.Available(result.info)
                UpdateManifest.CheckResult.UpToDate -> state = State.UpToDate
                is UpdateManifest.CheckResult.Failed -> {
                    state = if (auto) State.Idle else State.Error(result.message)
                }
            }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        }
    }

    fun download() {
        val available = state as? State.Available ?: return
        if (downloadJob?.isActive == true) return
        if (available.info.sha256.isNullOrBlank()) {
            state = State.Error("该更新源未提供 SHA-256 校验值，请到发布页手动下载")
            return
        }
        state = State.Downloading(0f)
        downloadJob = viewModelScope.launch {
            try {
                val dir = File(app.cacheDir, "updates")
                val file = UpdateClient.download(available.info, dir) { progress ->
                    state = State.Downloading(progress.coerceIn(0f, 1f))
                }
                state = State.Ready(available.info, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = State.Error(e.message ?: "下载失败")
            }
        }
    }

    fun install() {
        val ready = state as? State.Ready ?: return
        when (val result = UpdateInstaller.install(app, ready.file)) {
            UpdateInstaller.Result.Started ->
                state = ready.copy(message = "已打开系统安装器")
            UpdateInstaller.Result.NeedsPermission ->
                state = ready.copy(message = "请允许本应用安装未知应用，返回后再点安装")
            is UpdateInstaller.Result.Failed ->
                state = State.Error(result.message)
        }
    }

    fun dismissError() {
        if (state is State.Error) state = State.Idle
    }

    override fun onCleared() {
        checkJob?.cancel()
        downloadJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val KEY_LAST_CHECK = "last_check"
        const val AUTO_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
    }
}
