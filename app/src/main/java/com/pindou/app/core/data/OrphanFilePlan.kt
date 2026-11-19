package com.pindou.app.core.data

import java.io.File

/**
 * 启动时清理「删除撤销窗口内进程被杀」留下的无主文件。
 *
 * 删除是软删：DB 行立即删掉、实体暂存在内存里等 10 秒撤销；超时才删
 * 缩略图 / studio sidecar / 参考图。进程在窗口内被杀，DB 行已经没了，
 * 文件却还在，下次启动就再也关联不上。
 *
 * 两条安全线：
 * - 10 分钟内的文件不动：避免和当前会话正在写入的新文件打架；
 * - studio/ref 只看文件名里的 id 是否还在 DB：即使路径列没写成功，
 *   只要项目还在就保留，宁可留一个可恢复的文件，也不误删。
 */
object OrphanFilePlan {

    const val MIN_AGE_MS = 10 * 60 * 1000L

    fun orphans(
        thumbs: List<File>,
        studioFiles: List<File>,
        refFiles: List<File>,
        referencedPaths: Set<String>,
        projectIds: Set<Long>,
        now: Long,
        minAgeMs: Long = MIN_AGE_MS,
    ): List<File> {
        val doomed = mutableListOf<File>()

        fun oldEnough(file: File): Boolean = now - file.lastModified() >= minAgeMs

        thumbs.forEach { file ->
            if (file.absolutePath !in referencedPaths && oldEnough(file)) doomed += file
        }
        studioFiles.forEach { file ->
            val id = idFrom(file.name, "project_", ".studio")
            if (oldEnough(file) && (id == null || id !in projectIds)) doomed += file
        }
        refFiles.forEach { file ->
            val id = idFrom(file.name, "ref_", ".jpg")
            if (oldEnough(file) && (id == null || id !in projectIds)) doomed += file
        }
        return doomed
    }

    internal fun idFrom(name: String, prefix: String, suffix: String): Long? {
        if (!name.startsWith(prefix) || !name.endsWith(suffix)) return null
        val core = name.removePrefix(prefix).removeSuffix(suffix)
        if (core.isEmpty()) return null
        return core.toLongOrNull()
    }
}
