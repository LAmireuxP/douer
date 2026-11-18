package com.pindou.app.ui.library

import com.pindou.app.core.data.ProjectSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectLibraryFilterTest {

    private fun summary(
        id: Long,
        name: String,
        fav: Boolean = false,
        album: String = "",
    ) = ProjectSummary(
        id = id,
        name = name,
        width = 29,
        height = 29,
        paletteBrand = "MARD",
        thumbnailPath = null,
        updatedAt = 0L,
        fav = fav,
        album = album,
        pages = 1,
    )

    private val projects = listOf(
        summary(1, "小猫", fav = true, album = "宠物"),
        summary(2, "小狗", fav = false, album = "宠物"),
        summary(3, "风景", fav = true, album = ""),
        summary(4, "Cat Pixel", fav = false, album = "宠物"),
    )

    @Test
    fun `空条件返回全部且保持原顺序`() {
        val out = ProjectLibraryFilter.filter(projects, "", false, null)
        assertEquals(projects, out)
    }

    @Test
    fun `搜索词忽略大小写与首尾空白`() {
        val out = ProjectLibraryFilter.filter(projects, "  cat  ", false, null)
        assertEquals(listOf(4L), out.map { it.id })
    }

    @Test
    fun `中文搜索按子串匹配`() {
        val out = ProjectLibraryFilter.filter(projects, "宠", false, null)
        assertTrue(out.isEmpty()) // 名称里没有「宠」，只有专辑名有

        val byName = ProjectLibraryFilter.filter(projects, "小", false, null)
        assertEquals(listOf(1L, 2L), byName.map { it.id })
    }

    @Test
    fun `收藏与搜索词同时生效`() {
        val out = ProjectLibraryFilter.filter(projects, "小", true, null)
        assertEquals(listOf(1L), out.map { it.id })
    }

    @Test
    fun `专辑筛选与收藏叠加`() {
        val out = ProjectLibraryFilter.filter(projects, "", true, "宠物")
        assertEquals(listOf(1L), out.map { it.id })
    }

    @Test
    fun `不存在的专辑返回空列表`() {
        val out = ProjectLibraryFilter.filter(projects, "", false, "不存在的专辑")
        assertTrue(out.isEmpty())
    }

    @Test
    fun `空专辑名不进筛选 chips`() {
        assertEquals(listOf("宠物"), ProjectLibraryFilter.albumNames(projects))
    }

    @Test
    fun `专辑名去重并保持首次出现顺序`() {
        val list = listOf(
            summary(1, "a", album = "乙"),
            summary(2, "b", album = "甲"),
            summary(3, "c", album = "乙"),
        )
        assertEquals(listOf("乙", "甲"), ProjectLibraryFilter.albumNames(list))
    }
}
