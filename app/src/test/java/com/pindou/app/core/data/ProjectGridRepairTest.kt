package com.pindou.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectGridRepairTest {

    @Test
    fun `正方形旧图从格子数恢复真实尺寸`() {
        val grid = ProjectGridRepair.infer(
            oldWidth = 40,
            oldHeight = 40,
            cellCount = 30 * 30,
            pages = 1,
        )
        assertEquals(ProjectGridRepair.Grid(30, 30), grid)
    }

    @Test
    fun `非正方形旧图保持长宽比例`() {
        val grid = ProjectGridRepair.infer(
            oldWidth = 44,
            oldHeight = 98,
            cellCount = 44 * 97,
            pages = 1,
        )
        assertEquals(ProjectGridRepair.Grid(44, 97), grid)
    }

    @Test
    fun `多页项目按每页格子数恢复`() {
        val grid = ProjectGridRepair.infer(
            oldWidth = 30,
            oldHeight = 30,
            cellCount = 30 * 30 * 2,
            pages = 2,
        )
        assertEquals(ProjectGridRepair.Grid(30, 30), grid)
    }

    @Test
    fun `页数无法整除时拒绝猜测`() {
        assertNull(ProjectGridRepair.infer(30, 30, 1000, 3))
    }
}
