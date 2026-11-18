package com.pindou.app.ui.studio

import android.app.Application
import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.pindou.app.core.data.ProjectRepository
import com.pindou.app.core.studio.StudioCodec
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/** 画像素与参考原图的落盘动作：纯 I/O，不持有 Compose state。 */
internal object StudioPersistence {

    /**
     * 参考原图落盘：长边压到 ≤1024px 的 JPEG，供编辑器对比叠加。
     *
     * 采样率按最长边线性缩放；scale = 1 时复用原 Bitmap，不额外分配。
     */
    suspend fun saveRefImage(
        application: Application,
        repository: ProjectRepository,
        id: Long,
        source: Bitmap,
    ): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val dir = File(application.filesDir, "refs").apply { mkdirs() }
            val file = File(dir, "ref_$id.jpg")
            val scale = min(1f, 1024f / max(source.width, source.height))
            val scaled = if (scale < 1f) {
                source.scale(
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else source
            FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            if (scaled !== source) scaled.recycle()
            repository.setRefPath(id, file.absolutePath)
            file.absolutePath
        }
    }

    /**
     * 分层工程落盘：帧拍平入库 + sidecar 写入。
     *
     * 每帧 composite 是 O(格数)；最多帧数由 StudioEngine 限制，这里不再复制整帧。
     */
    suspend fun persistStudio(
        application: Application,
        repository: ProjectRepository,
        session: StudioSession,
        studioFile: (Long) -> File,
    ): Result<Long?> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val engine = session.engine
            val width = engine.width
            val height = engine.height
            val pages = engine.frames.indices.map { engine.composite(it) }
            val pageLocks = pages.map { BooleanArray(width * height) }
            val palette = session.palette
            val existing = session.projectId
            val id = if (existing == null) {
                repository.importProject(
                    name = session.name.trim().ifBlank { "画像素" },
                    width = width,
                    height = height,
                    palette = palette,
                    pagesCells = pages,
                    pagesLocks = pageLocks,
                ).also { session.projectId = it }
            } else {
                val flat = pages.reduce { acc, cur -> acc + cur }
                val flatLocks = pageLocks.reduce { acc, cur -> acc + cur }
                repository.updateCells(
                    id = existing,
                    width = width,
                    height = height,
                    cells = flat,
                    locked = flatLocks,
                    palette = palette,
                    pages = pages.size,
                )
                existing
            }
            val file = studioFile(id)
            if (!StudioCodec.save(engine, file)) {
                return@runCatching null
            }
            repository.setStudioPath(id, file.absolutePath)
            id
        }
    }
}
