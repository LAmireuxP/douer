package com.pindou.app.core.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GitHub Release → 更新信息。
 *
 * 国内网络直连 GitHub 经常超时，所以 API 和 APK 都优先走 gh-proxy，
 * 失败再回退官方域名。APK 最终仍由系统包管理器校验签名。
 */
object UpdateManifest {

    const val REPO = "LAmireuxP/douer"
    const val GITEE_REPO = "L_Amireux_P/douer"
    const val GITHUB_API = "https://api.github.com/repos/$REPO/releases/latest"
    const val GITEE_API = "https://gitee.com/api/v5/repos/$GITEE_REPO/releases/latest"
    const val GITHUB_RELEASES = "https://github.com/$REPO/releases"
    const val GITEE_RELEASES = "https://gitee.com/$GITEE_REPO/releases"
    const val DOMESTIC_PROXY = "https://gh-proxy.com/"

    data class ApiSource(
        val url: String,
        val releasePage: String,
        val domestic: Boolean,
    )

    @Serializable
    private data class ReleaseDto(
        val tag_name: String = "",
        val name: String? = null,
        val body: String? = null,
        val html_url: String? = null,
        val assets: List<AssetDto> = emptyList(),
    )

    @Serializable
    private data class AssetDto(
        val name: String = "",
        val browser_download_url: String = "",
        val size: Long = 0,
        val digest: String? = null,
    )

    data class UpdateInfo(
        val versionName: String,
        val notes: String,
        val sizeBytes: Long,
        val sha256: String?,
        val downloadUrl: String,
        val releaseUrl: String,
    )

    sealed interface CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult
        data object UpToDate : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Gitee 最稳（需先建同名镜像仓库）→ gh-proxy → GitHub 官方。
     * 三个源都会查，取版本号最高的那个，避免 Gitee 镜像落后时漏更新。
     */
    fun apiCandidates(): List<ApiSource> = listOf(
        ApiSource(GITEE_API, GITEE_RELEASES, domestic = true),
        ApiSource(DOMESTIC_PROXY + GITHUB_API, GITHUB_RELEASES, domestic = true),
        ApiSource(GITHUB_API, GITHUB_RELEASES, domestic = false),
    )

    fun downloadCandidates(info: UpdateInfo): List<String> =
        if (info.downloadUrl.contains("gitee.com", ignoreCase = true)) {
            listOf(info.downloadUrl)
        } else {
            listOf(
                DOMESTIC_PROXY + info.downloadUrl,
                info.downloadUrl,
            ).distinct()
        }

    fun releaseCandidates(info: UpdateInfo): List<String> =
        if (info.releaseUrl.contains("gitee.com", ignoreCase = true)) {
            listOf(info.releaseUrl)
        } else {
            listOf(
                DOMESTIC_PROXY + info.releaseUrl,
                info.releaseUrl,
            ).distinct()
        }

    fun parse(
        jsonText: String,
        currentVersion: String,
        releasePage: String = GITHUB_RELEASES,
    ): CheckResult {
        val dto = try {
            json.decodeFromString(ReleaseDto.serializer(), jsonText)
        } catch (e: Exception) {
            return CheckResult.Failed("更新源返回格式错误")
        }
        val version = dto.tag_name.removePrefix("v").ifBlank {
            dto.name?.substringAfterLast(' ')?.removePrefix("v") ?: ""
        }
        if (version.isBlank()) return CheckResult.Failed("更新源没有版本号")
        if (compareVersions(version, currentVersion) <= 0) return CheckResult.UpToDate

        val asset = dto.assets
            .filter { it.name.endsWith(".apk", ignoreCase = true) && it.browser_download_url.isNotBlank() }
            .maxByOrNull { it.size }
            ?: return CheckResult.Failed("发布页没有 APK 资产")

        val body = dto.body.orEmpty()
        val digestInBody = Regex("(?i)sha256[:\\s]+([0-9a-f]{64})")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
        return CheckResult.Available(
            UpdateInfo(
                versionName = version,
                notes = body.take(4000),
                sizeBytes = asset.size,
                sha256 = asset.digest
                    ?.removePrefix("sha256:")
                    ?.trim()
                    ?.takeIf { it.length == 64 }
                    ?: digestInBody,
                downloadUrl = asset.browser_download_url,
                releaseUrl = dto.html_url ?: releasePage,
            ),
        )
    }

    /** 数字段逐段比较：1.2.10 > 1.2.9，非数字后缀忽略。 */
    fun compareVersions(a: String, b: String): Int {
        fun parts(value: String): List<Int> = value
            .removePrefix("v")
            .split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }
            .map { it.toIntOrNull() ?: 0 }
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }
}
