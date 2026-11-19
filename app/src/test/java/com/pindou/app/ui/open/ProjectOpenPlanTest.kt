package com.pindou.app.ui.open

import com.pindou.app.core.data.GridCodec
import com.pindou.app.core.studio.StudioEngine
import com.pindou.app.core.studio.StudioFrame
import com.pindou.app.core.studio.StudioLayer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectOpenPlanTest {

    private fun encodedPages(pageLen: Int, pages: Int): ByteArray {
        val flat = IntArray(pageLen * pages) { index -> index % 7 }
        return GridCodec.encodeCells(flat)
    }

    @Test
    fun `尺寸相符时按页切分且不修复`() {
        val plan = ProjectOpenPlan.plan(
            width = 3,
            height = 2,
            pageCount = 2,
            encodedCells = encodedPages(6, 2),
            loadedStudio = null,
            paletteArgb = intArrayOf(0xFF000000.toInt()),
        ) as ProjectOpenPlan.Result.Ready

        assertFalse(plan.repaired)
        assertEquals(3, plan.width)
        assertEquals(2, plan.height)
        assertEquals(2, plan.pages.size)
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4, 5), plan.pages[0])
        assertArrayEquals(intArrayOf(6, 0, 1, 2, 3, 4), plan.pages[1])
    }

    @Test
    fun `页数为零按一页处理`() {
        val plan = ProjectOpenPlan.plan(
            width = 2,
            height = 2,
            pageCount = 0,
            encodedCells = encodedPages(4, 1),
            loadedStudio = null,
            paletteArgb = IntArray(0),
        ) as ProjectOpenPlan.Result.Ready

        assertEquals(1, plan.pages.size)
    }

    @Test
    fun `尺寸不符时用格子数反推并标记修复`() {
        // 库里写 40×40，实际只有 30×30 个格子
        val flat = IntArray(900) { it % 5 }
        val plan = ProjectOpenPlan.plan(
            width = 40,
            height = 40,
            pageCount = 1,
            encodedCells = GridCodec.encodeCells(flat),
            loadedStudio = null,
            paletteArgb = IntArray(0),
        ) as ProjectOpenPlan.Result.Ready

        assertTrue(plan.repaired)
        assertEquals(30, plan.width)
        assertEquals(30, plan.height)
        assertEquals(900, plan.pages[0].size)
    }

    @Test
    fun `页数无法整除时返回错误而不是猜`() {
        val plan = ProjectOpenPlan.plan(
            width = 30,
            height = 30,
            pageCount = 3,
            encodedCells = GridCodec.encodeCells(IntArray(1000)),
            loadedStudio = null,
            paletteArgb = IntArray(0),
        )

        assertTrue(plan is ProjectOpenPlan.Result.Error)
        assertTrue((plan as ProjectOpenPlan.Result.Error).message.contains("尺寸不符"))
    }

    @Test
    fun `空数据返回错误`() {
        val plan = ProjectOpenPlan.plan(
            width = 0,
            height = 0,
            pageCount = 1,
            encodedCells = ByteArray(0),
            loadedStudio = null,
            paletteArgb = IntArray(0),
        )

        assertTrue(plan is ProjectOpenPlan.Result.Error)
    }

    @Test
    fun `sidecar 尺寸相符时直接复用`() {
        val loaded = StudioEngine(
            2, 2,
            listOf(StudioFrame(mutableListOf(StudioLayer("L", IntArray(4))))),
        )
        val plan = ProjectOpenPlan.plan(
            width = 2,
            height = 2,
            pageCount = 1,
            encodedCells = encodedPages(4, 1),
            loadedStudio = loaded,
            paletteArgb = IntArray(0),
        ) as ProjectOpenPlan.Result.Ready

        assertSame(loaded, plan.studio)
    }

    @Test
    fun `sidecar 尺寸不符时按页回退建帧`() {
        val stale = StudioEngine(
            9, 9,
            listOf(StudioFrame(mutableListOf(StudioLayer("旧", IntArray(81))))),
        )
        val plan = ProjectOpenPlan.plan(
            width = 2,
            height = 2,
            pageCount = 2,
            encodedCells = encodedPages(4, 2),
            loadedStudio = stale,
            paletteArgb = IntArray(0),
        ) as ProjectOpenPlan.Result.Ready

        assertFalse(plan.studio === stale)
        assertEquals(2, plan.studio.width)
        assertEquals(2, plan.studio.height)
        assertEquals(2, plan.studio.frames.size)
        assertEquals("第 1 页", plan.studio.frames[0].layers[0].name)
        assertEquals("第 2 页", plan.studio.frames[1].layers[0].name)
        assertArrayEquals(plan.pages[0], plan.studio.frames[0].layers[0].cells)
        assertArrayEquals(plan.pages[1], plan.studio.frames[1].layers[0].cells)
    }

    @Test
    fun `单页回退的图层名是图层一`() {
        val plan = ProjectOpenPlan.plan(
            width = 2,
            height = 2,
            pageCount = 1,
            encodedCells = encodedPages(4, 1),
            loadedStudio = null,
            paletteArgb = IntArray(0),
        ) as ProjectOpenPlan.Result.Ready

        assertEquals("图层 1", plan.studio.frames[0].layers[0].name)
    }
}
