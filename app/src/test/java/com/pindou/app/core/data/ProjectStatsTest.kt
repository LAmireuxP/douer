package com.pindou.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectStatsTest {

    private fun entity(
        width: Int = 4,
        height: Int = 4,
        pages: Int = 1,
        cells: IntArray = IntArray(width * height * pages) { -1 },
        done: BooleanArray? = null,
        buildSeconds: Long = 0,
        palette: String = "MARD",
    ) = ProjectEntity(
        name = "t",
        width = width,
        height = height,
        beadSize = "MM2_6",
        paletteBrand = palette,
        cells = GridCodec.encodeCells(cells),
        locked = GridCodec.encodeLocks(BooleanArray(cells.size)),
        thumbnailPath = null,
        createdAt = 0,
        updatedAt = 0,
        doneCells = done?.let { GridCodec.encodeLocks(it) },
        pages = pages,
        buildSeconds = buildSeconds,
    )

    @Test
    fun `用色数跳过空格并去重`() {
        val e = entity(cells = intArrayOf(0, 1, 1, -1, 2, -1, 2, 0, -1, -1, -1, -1, -1, -1, -1, -1))
        assertEquals(3, ProjectStats.usedColors(e))
    }

    @Test
    fun `全空项目用色数为零`() {
        assertEquals(0, ProjectStats.usedColors(entity()))
    }

    @Test
    fun `多页用色合并去重`() {
        val e = entity(pages = 2, cells = intArrayOf(0, 1, -1, -1, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1))
        assertEquals(3, ProjectStats.usedColors(e))
    }

    @Test
    fun `进度百分比按总格数算`() {
        val done = BooleanArray(16); done[0] = true; done[1] = true; done[2] = true; done[3] = true
        val e = entity(done = done)
        assertEquals(25, ProjectStats.donePercent(e))
        assertEquals(0, ProjectStats.donePercent(entity()))
    }

    @Test
    fun `底板数按 29 格一块`() {
        assertEquals(1, ProjectStats.boards(29, 29))
        assertEquals(4, ProjectStats.boards(30, 30))
        assertEquals(2, ProjectStats.boards(58, 20))
        assertEquals(0, ProjectStats.boards(0, 10))
    }

    @Test
    fun `对比行只在值不同时标差异`() {
        val a = entity(width = 4, height = 4, palette = "MARD")
        val b = entity(width = 4, height = 4, palette = "COCO")
        val rows = ProjectStats.rows(a, b) { it }
        assertTrue(rows.single { it.id == "palette" }.differs)
        assertFalse(rows.single { it.id == "size" }.differs)
        assertFalse(rows.single { it.id == "colors" }.differs)
    }
}
