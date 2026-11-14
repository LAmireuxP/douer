package com.pindou.app.core.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** 调起系统安装器；不静默安装，未知来源权限交给用户确认。 */
object UpdateInstaller {

    sealed interface Result {
        data object Started : Result
        data object NeedsPermission : Result
        data class Failed(val message: String) : Result
    }

    fun install(context: Context, file: File): Result {
        if (!file.isFile) return Result.Failed("更新包不存在，请重新下载")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val opened = runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
            return if (opened) {
                Result.NeedsPermission
            } else {
                Result.Failed("请到系统设置中允许本应用安装未知应用")
            }
        }

        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }.getOrElse {
            return Result.Failed("无法生成安装文件地址")
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            Result.Started
        }.getOrElse {
            Result.Failed("没有找到系统安装器")
        }
    }
}
