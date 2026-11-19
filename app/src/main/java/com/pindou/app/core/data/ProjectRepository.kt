package com.pindou.app.core.data

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.room.Room
import androidx.room.withTransaction
import com.pindou.app.core.export.ExportRenderer
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.pattern.PatternResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** 项目仓库：Room 持久化 + 缩略图生成 */
class ProjectRepository(private val context: Context) {

    private val db: PindouDatabase by lazy {
        Room.databaseBuilder(context, PindouDatabase::class.java, "pindou.db")
            .addMigrations(
                PindouDatabase.MIGRATION_1_2,
                PindouDatabase.MIGRATION_2_3,
                PindouDatabase.MIGRATION_3_4,
            )
            .build()
    }

    private val dao get() = db.projectDao()

    fun observeAll(): Flow<List<ProjectEntity>> = dao.observeAll()

    /** 项目库列表：不读格数据 BLOB，只取卡片字段 */
    fun observeSummaries(): Flow<List<ProjectSummary>> = dao.observeSummaries()

    suspend fun get(id: Long): ProjectEntity? = withContext(Dispatchers.IO) { dao.getById(id) }

    /** 新建项目（生成图纸后调用），返回项目 id */
    suspend fun create(
        name: String,
        result: PatternResult,
        locked: BooleanArray? = null,
        pages: Int = 1,
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val thumb = renderThumbnail(result)
        dao.insert(
            ProjectEntity(
                name = name,
                width = result.width,
                height = result.height,
                beadSize = result.palette.beadSize.name,
                paletteBrand = result.palette.brand,
                cells = GridCodec.encodeCells(result.cells),
                locked = GridCodec.encodeLocks(locked ?: BooleanArray(result.cells.size)),
                pages = pages,
                thumbnailPath = thumb,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** 编辑器完成：格数据、锁、页数和尺寸一起更新；尺寸没变时保留施工进度。 */
    suspend fun updateCells(
        id: Long,
        width: Int,
        height: Int,
        cells: IntArray,
        locked: BooleanArray,
        palette: BeadPalette,
        pages: Int = 1,
        resetProgress: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // cells 为多页平铺数据时，缩略图只渲染第 1 页
        val page0 = if (pages > 1 && cells.size > width * height) {
            cells.copyOfRange(0, width * height)
        } else cells
        val thumb = renderThumbnail(PatternResult(width, height, page0, palette, 0))
        val old = dao.getById(id)?.thumbnailPath
        dao.updateCells(
            id, width, height,
            GridCodec.encodeCells(cells), GridCodec.encodeLocks(locked),
            pages, thumb, now, resetProgress,
        )
        // 旧缩略图清理（新图已生成时）
        if (thumb != null && old != null && old != thumb) {
            runCatching { File(old).delete() }
        }
    }

    /** 重新生成：尺寸、色卡、格数据全量更新，施工进度作废（图纸已变） */
    suspend fun updateContent(
        id: Long,
        width: Int,
        height: Int,
        cells: IntArray,
        locked: BooleanArray,
        palette: BeadPalette,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val thumb = renderThumbnail(PatternResult(width, height, cells, palette, 0))
        val old = dao.getById(id)?.thumbnailPath
        dao.updateContent(
            id, width, height,
            palette.beadSize.name, palette.brand,
            GridCodec.encodeCells(cells), GridCodec.encodeLocks(locked), thumb, now,
        )
        // 旧缩略图清理（新图已生成时）
        if (thumb != null && old != null && old != thumb) {
            runCatching { File(old).delete() }
        }
    }

    /** 保存施工进度 */
    suspend fun saveBuildProgress(
        id: Long,
        done: BooleanArray,
        orientation: Int,
        cursor: Int,
    ) = withContext(Dispatchers.IO) {
        dao.updateBuildProgress(
            id,
            ByteArray(done.size) { if (done[it]) 1 else 0 },
            orientation,
            cursor,
            System.currentTimeMillis(),
        )
    }

    /** 收藏/取消收藏 */
    suspend fun setFav(id: Long, fav: Boolean) = withContext(Dispatchers.IO) {
        dao.setFav(id, fav, System.currentTimeMillis())
    }

    /** 移入/移出专辑（空串 = 未分组） */
    suspend fun setAlbum(id: Long, album: String) = withContext(Dispatchers.IO) {
        dao.setAlbum(id, album.trim(), System.currentTimeMillis())
    }

    suspend fun setFavAll(ids: List<Long>, fav: Boolean) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        // SQLite 单条语句最多 999 个绑定变量；按 500 分块，选中几千份也不会崩
        ids.chunked(500).forEach { dao.setFavAll(it, fav, now) }
    }

    suspend fun setAlbumAll(ids: List<Long>, album: String) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        ids.chunked(500).forEach { dao.setAlbumAll(it, album.trim(), now) }
    }

    /** 保存参考原图路径 */
    suspend fun setRefPath(id: Long, path: String?) = withContext(Dispatchers.IO) {
        dao.setRefPath(id, path, System.currentTimeMillis())
    }

    /** 画像素分层工程 sidecar；null 表示该项目退回普通图纸编辑器。 */
    suspend fun setStudioPath(id: Long, path: String?) = withContext(Dispatchers.IO) {
        dao.setStudioPath(id, path, System.currentTimeMillis())
    }

    /** 修复旧项目缺失的宽高（格子本身未变）。 */
    suspend fun repairSize(id: Long, width: Int, height: Int) = withContext(Dispatchers.IO) {
        dao.updateSize(id, width, height, System.currentTimeMillis())
    }

    suspend fun rename(id: Long, name: String) = withContext(Dispatchers.IO) {
        dao.rename(id, name, System.currentTimeMillis())
    }

    suspend fun delete(id: Long) = deleteAll(listOf(id))

    /** 清掉参考原图并断开数据库引用；旧文件不存在时也照常把引用置空。 */
    suspend fun clearRefPath(id: Long) = withContext(Dispatchers.IO) {
        val old = dao.getById(id)?.refPath
        dao.setRefPath(id, null, System.currentTimeMillis())
        old?.let { runCatching { File(it).delete() } }
    }

    /**
     * 批量删除。数据库行放在一个事务里，文件路径先收集、事务提交后再删；
     * 文件删除失败不影响数据库行删除，避免一条坏路径卡住整批。
     */
    suspend fun deleteAll(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        val files = mutableListOf<File>()
        db.withTransaction {
            ids.forEach { id ->
                val project = dao.getById(id)
                project?.thumbnailPath?.let { files += File(it) }
                project?.studioPath?.let { files += File(it) }
                project?.refPath?.let { files += File(it) }
                dao.delete(id)
            }
        }
        files.forEach { runCatching { it.delete() } }
    }

    /**
     * 可撤销删除：只删数据库行，sidecar 文件留在磁盘上。
     *
     * 撤销窗口内缩略图/画像素工程必须还能读到，所以文件清理交给
     * [purgeDeletedFiles]；窗口结束（Snackbar 超时或被新删除顶掉）再删。
     */
    suspend fun softDelete(ids: Collection<Long>): List<ProjectEntity> = withContext(Dispatchers.IO) {
        db.withTransaction {
            ids.mapNotNull { id ->
                dao.getById(id)?.also { dao.delete(id) }
            }
        }
    }

    /** 撤销删除：带着原 id 回插，侧栏文件从未删过，缩略图立即可用。 */
    suspend fun restoreDeleted(projects: List<ProjectEntity>) = withContext(Dispatchers.IO) {
        if (projects.isEmpty()) return@withContext
        db.withTransaction {
            projects.forEach { dao.insert(it) }
        }
    }

    /** 撤销窗口结束：清理三条 sidecar 路径，失败不影响其他文件。 */
    suspend fun purgeDeletedFiles(projects: List<ProjectEntity>) = withContext(Dispatchers.IO) {
        projects.forEach { project ->
            listOfNotNull(project.thumbnailPath, project.studioPath, project.refPath)
                .forEach { path -> runCatching { File(path).delete() } }
        }
    }

    /**
     * 启动对账：清掉删除撤销窗口内进程被杀留下的无主文件。
     *
     * 只扫 thumbs / studio / refs 三个目录；studio 与 refs 按文件名里的
     * 项目 id 判断，项目还在就保留（即使路径列没写成功），避免误删可恢复数据。
     * 10 分钟内的新文件不动，防止和当前会话的写入打架。
     */
    suspend fun purgeOrphanFiles(now: Long = System.currentTimeMillis()): Int =
        withContext(Dispatchers.IO) {
            val refs = dao.fileRefs()
            val referenced = refs
                .flatMap { listOfNotNull(it.thumbnailPath, it.studioPath, it.refPath) }
                .toSet()
            val ids = refs.mapTo(mutableSetOf()) { it.id }
            val root = context.filesDir
            val doomed = OrphanFilePlan.orphans(
                thumbs = File(root, "thumbs").listFiles()?.toList().orEmpty(),
                studioFiles = File(root, "studio").listFiles()?.toList().orEmpty(),
                refFiles = File(root, "refs").listFiles()?.toList().orEmpty(),
                referencedPaths = referenced,
                projectIds = ids,
                now = now,
            )
            var deleted = 0
            doomed.forEach { file ->
                if (runCatching { file.delete() }.getOrDefault(false)) deleted++
            }
            deleted
        }

    /**
     * 导入外部图纸（.bpat）或复刻项目：全量新建一个实体。
     * 复刻时缩略图复制一份独立文件，避免删除原项目时把复刻的图也删掉。
     */
    suspend fun importProject(
        name: String,
        width: Int,
        height: Int,
        palette: BeadPalette,
        pagesCells: List<IntArray>,
        pagesLocks: List<BooleanArray>,
        album: String = "",
        thumbnailCopyFrom: String? = null,
    ): Long = withContext(Dispatchers.IO) {
        require(pagesCells.isNotEmpty()) { "页数据为空" }
        val now = System.currentTimeMillis()
        val flat = pagesCells.reduce { acc, cur -> acc + cur }
        val flatLocks = pagesLocks.reduce { acc, cur -> acc + cur }
        val thumb = thumbnailCopyFrom?.let { src ->
            runCatching {
                val dst = newThumbFile()
                File(src).copyTo(dst, overwrite = true)
                dst.absolutePath
            }.getOrNull()
        } ?: renderThumbnail(PatternResult(width, height, pagesCells[0], palette, 0))
        dao.insert(
            ProjectEntity(
                name = name,
                width = width,
                height = height,
                beadSize = palette.beadSize.name,
                paletteBrand = palette.brand,
                cells = GridCodec.encodeCells(flat),
                locked = GridCodec.encodeLocks(flatLocks),
                thumbnailPath = thumb,
                createdAt = now,
                updatedAt = now,
                pages = pagesCells.size,
                album = album,
            ),
        )
    }

    /** 生成缩略图 PNG，返回文件路径 */
    private fun renderThumbnail(result: PatternResult): String? = runCatching {
        val cellPx = when {
            result.width <= 32 -> 10
            result.width <= 64 -> 6
            else -> 4
        }
        val rendered = ExportRenderer.renderPattern(
            result,
            ExportRenderer.RenderOptions(cellPx = cellPx, showCodes = false, showGrid = false),
        )
        val bmp = createBitmap(rendered.width, rendered.height)
        bmp.setPixels(rendered.pixels, 0, rendered.width, 0, 0, rendered.width, rendered.height)

        val file = newThumbFile()
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        bmp.recycle()
        file.absolutePath
    }.getOrNull()

    /** 同一毫秒保存两次也不能撞名；时间戳后补单调纳秒。 */
    private fun newThumbFile(): File {
        val dir = File(context.filesDir, "thumbs").apply { mkdirs() }
        return File(dir, "thumb_${System.currentTimeMillis()}_${System.nanoTime()}.png")
    }
}
