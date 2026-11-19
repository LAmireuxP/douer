package com.pindou.app.core.build

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstructionResumePlanTest {

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `长度完全匹配时还原布尔数组`() {
        val plan = ConstructionResumePlan.plan(
            stored = bytes(1, 0, 1, 1, 0, 1),
            pageSize = 6,
            pageCount = 1,
            orientationCode = 0,
            storedCursor = 2,
            paletteSize = 9,
            width = 2,
            height = 3,
        )

        assertArrayEquals(
            booleanArrayOf(true, false, true, true, false, true),
            plan.done,
        )
        assertEquals(ConstructionState.Orientation.ROW, plan.orientation)
        assertEquals(2, plan.cursor)
    }

    @Test
    fun `游标按行模式夹到高度减一`() {
        val plan = ConstructionResumePlan.plan(
            stored = bytes(1, 1, 1, 1, 1, 1),
            pageSize = 6,
            pageCount = 1,
            orientationCode = 0,
            storedCursor = 99,
            paletteSize = 9,
            width = 3,
            height = 2,
        )

        assertEquals(1, plan.cursor)
    }

    @Test
    fun `按列模式上限是宽度减一`() {
        val plan = ConstructionResumePlan.plan(
            stored = bytes(1, 1, 1, 1, 1, 1),
            pageSize = 6,
            pageCount = 1,
            orientationCode = 1,
            storedCursor = 99,
            paletteSize = 9,
            width = 3,
            height = 2,
        )

        assertEquals(ConstructionState.Orientation.COLUMN, plan.orientation)
        assertEquals(2, plan.cursor)
    }

    @Test
    fun `按色号模式上限是色卡数减一`() {
        val plan = ConstructionResumePlan.plan(
            stored = bytes(1, 1, 1, 1),
            pageSize = 4,
            pageCount = 1,
            orientationCode = 2,
            storedCursor = 99,
            paletteSize = 5,
            width = 2,
            height = 2,
        )

        assertEquals(ConstructionState.Orientation.COLOR, plan.orientation)
        assertEquals(4, plan.cursor)
    }

    @Test
    fun `按底板模式上限是二十九格板数减一`() {
        // 60×60 → ceil(60/29)=3，3×3-1=8
        val plan = ConstructionResumePlan.plan(
            stored = ByteArray(3600) { 1 },
            pageSize = 3600,
            pageCount = 1,
            orientationCode = 3,
            storedCursor = 99,
            paletteSize = 5,
            width = 60,
            height = 60,
        )

        assertEquals(ConstructionState.Orientation.BOARD, plan.orientation)
        assertEquals(8, plan.cursor)
    }

    @Test
    fun `只存了前几页时保留前缀其余留空`() {
        val plan = ConstructionResumePlan.plan(
            stored = bytes(1, 0, 1, 0),
            pageSize = 4,
            pageCount = 3,
            orientationCode = 0,
            storedCursor = 3,
            paletteSize = 9,
            width = 2,
            height = 2,
        )

        assertEquals(12, plan.done.size)
        assertTrue(plan.done[0])
        assertFalse(plan.done[1])
        assertTrue(plan.done[2])
        assertFalse(plan.done[3])
        // 后两页全空
        assertFalse(plan.done.drop(4).any { it })
        // 不完整恢复时游标归零
        assertEquals(0, plan.cursor)
    }

    @Test
    fun `长度不整除页大小时整份作废`() {
        val plan = ConstructionResumePlan.plan(
            stored = bytes(1, 0, 1),
            pageSize = 4,
            pageCount = 2,
            orientationCode = 0,
            storedCursor = 2,
            paletteSize = 9,
            width = 2,
            height = 2,
        )

        assertEquals(8, plan.done.size)
        assertFalse(plan.done.any { it })
        assertEquals(0, plan.cursor)
    }

    @Test
    fun `超长数组按作废处理`() {
        val plan = ConstructionResumePlan.plan(
            stored = ByteArray(20) { 1 },
            pageSize = 4,
            pageCount = 2,
            orientationCode = 0,
            storedCursor = 3,
            paletteSize = 9,
            width = 2,
            height = 2,
        )

        assertFalse(plan.done.any { it })
        assertEquals(0, plan.cursor)
    }

    @Test
    fun `空进度或空项目返回空数组`() {
        val plan = ConstructionResumePlan.plan(
            stored = null,
            pageSize = 4,
            pageCount = 2,
            orientationCode = 0,
            storedCursor = null,
            paletteSize = 9,
            width = 2,
            height = 2,
        )

        assertEquals(8, plan.done.size)
        assertFalse(plan.done.any { it })
        assertEquals(0, plan.cursor)
    }

    @Test
    fun `页尺寸为零时不抛除零`() {
        val plan = ConstructionResumePlan.plan(
            stored = bytes(1, 0),
            pageSize = 0,
            pageCount = 3,
            orientationCode = 0,
            storedCursor = 1,
            paletteSize = 9,
            width = 0,
            height = 0,
        )

        assertEquals(0, plan.done.size)
        assertEquals(0, plan.cursor)
    }
}
