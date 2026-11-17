package com.pindou.app.core.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstructionColorGuideTest {

    // 3×2：1 1 2 / 2 3 3，其中第 1 格已拼
    private val cells = intArrayOf(1, 1, 2, 2, 3, 3)
    private val done = booleanArrayOf(true, false, false, false, false, false)

    @Test
    fun `剩余颗数只统计未拼的有效格`() {
        val r = ConstructionColorGuide.remaining(cells, done, 3, 2, 0)
        assertEquals(1, r[1])
        assertEquals(2, r[2])
        assertEquals(2, r[3])
    }

    @Test
    fun `空格不计入统计`() {
        val withEmpty = intArrayOf(1, -1, 2, -1, 3, 3)
        val r = ConstructionColorGuide.remaining(withEmpty, BooleanArray(6), 3, 2, 0)
        assertEquals(1, r[1])
        assertEquals(1, r[2])
        assertEquals(2, r[3])
        assertFalse(r.containsKey(-1))
    }

    @Test
    fun `多页时只统计当前页`() {
        val cells2 = intArrayOf(7, 7, 7, 7, 7, 7)
        val done2 = BooleanArray(12)
        val r = ConstructionColorGuide.remaining(cells2, done2, 3, 2, 6)
        assertEquals(6, r[7])
    }

    @Test
    fun `下一个色号会绕回开头`() {
        val order = listOf(1, 2, 3)
        val remaining = mapOf(1 to 5, 2 to 0, 3 to 2)
        assertEquals(3, ConstructionColorGuide.nextIncomplete(order, remaining, 2))
        assertEquals(1, ConstructionColorGuide.nextIncomplete(order, remaining, 3))
    }

    @Test
    fun `全部拼完返回负一`() {
        val order = listOf(1, 2)
        assertTrue(ConstructionColorGuide.nextIncomplete(order, emptyMap(), 1) < 0)
    }
}
