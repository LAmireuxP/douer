package com.pindou.app.core.pattern

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternTrimTest {

    @Test
    fun `裁掉四周整行整列空白`() {
        val cells = intArrayOf(
            -1, -1, -1, -1,
            -1, 1, 2, -1,
            -1, 3, 4, -1,
            -1, -1, -1, -1,
        )
        val out = PatternTrim.trimEmptyEdges(cells, 4, 4)

        assertTrue(out.trimmed)
        assertEquals(2, out.width)
        assertEquals(2, out.height)
        assertEquals(1, out.left)
        assertEquals(1, out.top)
        assertArrayEquals(intArrayOf(1, 2, 3, 4), out.cells)
    }

    @Test
    fun `只有单边空白时只裁对应方向`() {
        val cells = intArrayOf(
            -1, -1, -1,
            1, 2, -1,
            3, 4, -1,
        )
        val out = PatternTrim.trimEmptyEdges(cells, 3, 3)

        assertEquals(2, out.width)
        assertEquals(2, out.height)
        assertArrayEquals(intArrayOf(1, 2, 3, 4), out.cells)
    }

    @Test
    fun `内部空白保留`() {
        val cells = intArrayOf(
            1, -1, 2,
            3, -1, 4,
            5, 6, 7,
        )
        val out = PatternTrim.trimEmptyEdges(cells, 3, 3)

        assertFalse(out.trimmed)
        assertEquals(3, out.width)
        assertArrayEquals(cells, out.cells)
    }

    @Test
    fun `全空图纸保持原尺寸`() {
        val cells = intArrayOf(-1, -1, -1, -1)
        val out = PatternTrim.trimEmptyEdges(cells, 2, 2)

        assertFalse(out.trimmed)
        assertEquals(2, out.width)
        assertEquals(2, out.height)
        assertArrayEquals(cells, out.cells)
    }

    @Test
    fun `尺寸不匹配时原样返回`() {
        val cells = intArrayOf(1, 2, 3)
        val out = PatternTrim.trimEmptyEdges(cells, 2, 2)

        assertFalse(out.trimmed)
        assertEquals(2, out.width)
        assertArrayEquals(cells, out.cells)
    }
}
