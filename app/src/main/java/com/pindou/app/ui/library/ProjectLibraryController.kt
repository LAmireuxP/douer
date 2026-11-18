package com.pindou.app.ui.library

import com.pindou.app.core.data.GridCodec
import com.pindou.app.core.data.ProjectGridRepair
import com.pindou.app.core.data.ProjectRepository
import com.pindou.app.core.data.ProjectSummary
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.ui.chunkBooleans
import com.pindou.app.ui.chunkInts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 项目库的 CRUD / 筛选 / 复刻。列表状态仍由 VM 持有（Compose 直接读），
 * controller 只接收参数并写 repository；提示与当前项目 id 通过 [Host] 回传。
 */
class ProjectLibraryController(
    private val scope: CoroutineScope,
    private val repository: ProjectRepository,
    private val host: Host,
) {

    interface Host {
        val currentProjectId: Long?
        fun onCurrentProjectDeleted(id: Long)
        fun showLibraryToast(message: String)
        fun showLibraryError(message: String)

        /** 复刻时色卡按需加载；返回全部色卡。 */
        suspend fun ensurePalettes(): List<BeadPalette>

        fun paletteFor(brand: String, beadSize: String): BeadPalette?
    }

    fun delete(summary: ProjectSummary) {
        scope.launch {
            repository.delete(summary.id)
            if (host.currentProjectId == summary.id) host.onCurrentProjectDeleted(summary.id)
        }
    }

    /** 批量删除：Repository 负责逐条清理缩略图 / 图层 sidecar */
    fun deleteAll(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val snapshot = ids.toList()
        scope.launch {
            try {
                repository.deleteAll(snapshot)
                if (host.currentProjectId in snapshot) host.onCurrentProjectDeleted(snapshot.first())
                host.showLibraryToast("已删除 ${snapshot.size} 份图纸")
            } catch (e: Exception) {
                host.showLibraryError("批量删除失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun setFav(ids: Collection<Long>, fav: Boolean) {
        if (ids.isEmpty()) return
        val snapshot = ids.toList()
        scope.launch {
            try {
                repository.setFavAll(snapshot, fav)
                host.showLibraryToast(
                    if (fav) "已收藏 ${snapshot.size} 份图纸"
                    else "已取消收藏 ${snapshot.size} 份图纸",
                )
            } catch (e: Exception) {
                host.showLibraryError("批量收藏失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun setAlbum(ids: Collection<Long>, album: String) {
        if (ids.isEmpty()) return
        val snapshot = ids.toList()
        scope.launch {
            try {
                repository.setAlbumAll(snapshot, album)
                host.showLibraryToast(
                    if (album.isBlank()) "已移出文件夹（${snapshot.size} 份）"
                    else "已放入文件夹「${album.trim()}」（${snapshot.size} 份）",
                )
            } catch (e: Exception) {
                host.showLibraryError("批量放入文件夹失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun rename(summary: ProjectSummary, newName: String) {
        scope.launch { repository.rename(summary.id, newName) }
    }

    fun setFav(summary: ProjectSummary, fav: Boolean) {
        scope.launch { repository.setFav(summary.id, fav) }
    }

    fun setAlbum(summary: ProjectSummary, album: String) {
        scope.launch { repository.setAlbum(summary.id, album) }
    }

    /** 按搜索词/收藏/专辑过滤（库页组合使用） */
    fun filtered(
        all: List<ProjectSummary>,
        query: String,
        favOnly: Boolean,
        album: String?,
    ): List<ProjectSummary> = ProjectLibraryFilter.filter(all, query, favOnly, album)

    /** 已用到的专辑名（供筛选 chips） */
    fun albumNames(all: List<ProjectSummary>): List<String> =
        ProjectLibraryFilter.albumNames(all)

    /** 复刻项目：全量拷贝为新项目，进度清零，重新开始拼 */
    fun duplicate(summary: ProjectSummary) {
        scope.launch {
            try {
                val entity = repository.get(summary.id)
                if (entity == null) {
                    host.showLibraryError("复刻失败：项目已不存在")
                    return@launch
                }
                val loaded = host.ensurePalettes()
                val palette = host.paletteFor(entity.paletteBrand, entity.beadSize)
                    ?: loaded.first()
                val cells = GridCodec.decodeCells(entity.cells)
                val pageCount = entity.pages.coerceAtLeast(1)
                var gridWidth = entity.width
                var gridHeight = entity.height
                var pageLen = gridWidth * gridHeight
                if (cells.size != pageLen * pageCount) {
                    val repaired = ProjectGridRepair.infer(
                        entity.width, entity.height, cells.size, pageCount,
                    ) ?: error("数据尺寸不符")
                    gridWidth = repaired.width
                    gridHeight = repaired.height
                    pageLen = gridWidth * gridHeight
                    repository.repairSize(entity.id, gridWidth, gridHeight)
                }
                val pages = chunkInts(cells, pageLen)
                val locksFlat = GridCodec.decodeLocks(entity.locked)
                val locks = if (locksFlat.size == pageLen * pageCount) {
                    chunkBooleans(locksFlat, pageLen)
                } else {
                    pages.map { BooleanArray(pageLen) }
                }
                repository.importProject(
                    "${entity.name} 复刻",
                    gridWidth,
                    gridHeight,
                    palette,
                    pages,
                    locks,
                    entity.album,
                    entity.thumbnailPath,
                )
                host.showLibraryToast("已复刻「${entity.name}」")
            } catch (e: Exception) {
                host.showLibraryError("复刻失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}
