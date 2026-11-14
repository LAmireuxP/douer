package com.pindou.app.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {

    private val releaseJson = """
        {
          "tag_name": "v1.2.5",
          "name": "豆儿 1.2.5",
          "body": "修复若干问题\n新增更新检查",
          "html_url": "https://github.com/LAmireuxP/pindou/releases/tag/v1.2.5",
          "assets": [
            {
              "name": "douer-1.2.5.apk",
              "browser_download_url": "https://github.com/LAmireuxP/pindou/releases/download/v1.2.5/douer-1.2.5.apk",
              "size": 2712956,
              "digest": "sha256:13bf868eed638f9ff9c6724e95a4e544b7df69ad6b3c2f0020ea43df738174ca"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `解析新版本并生成国内优先下载地址`() {
        val result = UpdateManifest.parse(releaseJson, "1.2.4")
        assertTrue(result is UpdateManifest.CheckResult.Available)
        val info = (result as UpdateManifest.CheckResult.Available).info
        assertEquals("1.2.5", info.versionName)
        assertEquals(2712956, info.sizeBytes)
        assertEquals(
            "13bf868eed638f9ff9c6724e95a4e544b7df69ad6b3c2f0020ea43df738174ca",
            info.sha256,
        )
        assertTrue(info.notes.contains("更新检查"))
        assertEquals(
            "https://gh-proxy.com/https://github.com/LAmireuxP/pindou/releases/download/v1.2.5/douer-1.2.5.apk",
            UpdateManifest.downloadCandidates(info).first(),
        )
        assertTrue(
            UpdateManifest.releaseCandidates(info).first()
                .startsWith(UpdateManifest.DOMESTIC_PROXY),
        )
    }

    @Test
    fun `同版本不提示更新`() {
        assertTrue(
            UpdateManifest.parse(releaseJson, "1.2.5") is UpdateManifest.CheckResult.UpToDate,
        )
    }

    @Test
    fun `版本号按数字段比较`() {
        assertTrue(UpdateManifest.compareVersions("1.2.10", "1.2.9") > 0)
        assertTrue(UpdateManifest.compareVersions("v1.2.4", "1.2.4") == 0)
        assertTrue(UpdateManifest.compareVersions("1.10", "1.9.9") > 0)
    }

    @Test
    fun `没有 APK 或格式错误返回失败`() {
        assertTrue(
            UpdateManifest.parse("""{"tag_name":"v1.2.5","assets":[]}""", "1.2.4")
                is UpdateManifest.CheckResult.Failed,
        )
        assertTrue(
            UpdateManifest.parse("not json", "1.2.4") is UpdateManifest.CheckResult.Failed,
        )
    }

    @Test
    fun `Gitee 资产无大小和 digest 时从正文读取 SHA256`() {
        val giteeJson = """
            {
              "tag_name": "v1.2.5",
              "body": "豆儿 1.2.5\nSHA256: 13bf868eed638f9ff9c6724e95a4e544b7df69ad6b3c2f0020ea43df738174ca",
              "assets": [
                {
                  "name": "douer-1.2.5.apk",
                  "browser_download_url": "https://gitee.com/L_Amireux_P/pindou/releases/download/v1.2.5/douer-1.2.5.apk"
                }
              ]
            }
        """.trimIndent()
        val result = UpdateManifest.parse(
            giteeJson,
            "1.2.4",
            releasePage = UpdateManifest.GITEE_RELEASES,
        )
        val info = (result as UpdateManifest.CheckResult.Available).info
        assertEquals(0, info.sizeBytes)
        assertEquals(
            "13bf868eed638f9ff9c6724e95a4e544b7df69ad6b3c2f0020ea43df738174ca",
            info.sha256,
        )
        assertEquals(
            listOf(info.downloadUrl),
            UpdateManifest.downloadCandidates(info),
        )
        assertEquals(
            listOf(UpdateManifest.GITEE_RELEASES),
            UpdateManifest.releaseCandidates(info),
        )
    }

    @Test
    fun `更新源顺序为 Gitee 优先`() {
        val sources = UpdateManifest.apiCandidates()
        assertTrue(sources.first().url.contains("gitee.com"))
        assertTrue(sources[1].url.startsWith(UpdateManifest.DOMESTIC_PROXY))
        assertTrue(sources.last().url.startsWith("https://api.github.com"))
    }
}
