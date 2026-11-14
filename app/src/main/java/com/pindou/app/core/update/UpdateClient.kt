package com.pindou.app.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/** 更新检查与 APK 下载。网络调用全部在 IO 线程。 */
object UpdateClient {

    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val USER_AGENT = "Douer-Android-Updater"

    suspend fun check(currentVersion: String): UpdateManifest.CheckResult = withContext(Dispatchers.IO) {
        var lastError = "无法连接更新服务器"
        var sawUpToDate = false
        var best: UpdateManifest.UpdateInfo? = null
        for (source in UpdateManifest.apiCandidates()) {
            try {
                val text = getText(source.url)
                when (val result = UpdateManifest.parse(text, currentVersion, source.releasePage)) {
                    is UpdateManifest.CheckResult.Available -> {
                        val current = best
                        if (current == null ||
                            UpdateManifest.compareVersions(
                                result.info.versionName,
                                current.versionName,
                            ) > 0
                        ) {
                            best = result.info
                        }
                    }
                    UpdateManifest.CheckResult.UpToDate -> sawUpToDate = true
                    is UpdateManifest.CheckResult.Failed -> lastError = result.message
                }
            } catch (e: Exception) {
                lastError = e.message ?: "网络请求失败"
            }
        }
        best?.let { UpdateManifest.CheckResult.Available(it) }
            ?: if (sawUpToDate) UpdateManifest.CheckResult.UpToDate
            else UpdateManifest.CheckResult.Failed(lastError)
    }

    /**
     * 下载并校验 APK。返回最终文件；失败时删除 .part。
     * [onProgress] 的进度为 0..1，总长度未知时按 0 处理。
     */
    suspend fun download(
        info: UpdateManifest.UpdateInfo,
        destDir: File,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        destDir.mkdirs()
        destDir.listFiles()?.forEach { runCatching { it.delete() } }
        val target = File(destDir, "douer-${info.versionName}.apk")
        val temp = File(destDir, "${target.name}.part")
        var lastError = "下载失败"

        for (url in UpdateManifest.downloadCandidates(info)) {
            var connection: HttpURLConnection? = null
            try {
                connection = open(url, accept = "application/octet-stream")
                val code = connection.responseCode
                if (code !in 200..299) {
                    lastError = "下载服务器返回 HTTP $code"
                    continue
                }
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                FileOutputStream(temp).use { out ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            downloaded += n
                            onProgress(if (total > 0) downloaded.toFloat() / total else 0f)
                        }
                    }
                }
                if (info.sizeBytes > 0 && downloaded != info.sizeBytes) {
                    lastError = "下载大小不符（$downloaded / ${info.sizeBytes}）"
                    temp.delete()
                    continue
                }
                val actualSha = digest.digest().joinToString("") {
                    String.format(Locale.US, "%02x", it)
                }
                if (info.sha256 != null && !actualSha.equals(info.sha256, ignoreCase = true)) {
                    lastError = "更新包校验失败"
                    temp.delete()
                    continue
                }
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                onProgress(1f)
                return@withContext target
            } catch (e: Exception) {
                lastError = e.message ?: "下载失败"
                runCatching { temp.delete() }
            } finally {
                connection?.disconnect()
            }
        }
        throw IllegalStateException(lastError)
    }

    private fun getText(url: String): String {
        val connection = open(url, accept = "application/vnd.github+json")
        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", accept)
        }
}
