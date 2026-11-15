package com.pindou.app.core.pattern

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PatternTransformTest {

    // 3×2：1 2 3 / 4 5 6
    private val cells = intArrayOf(1, 2, 3, 4, 5, 6)

    @Test
    fun `水平镜像按行反转`() {
        assertArrayEquals(intArrayOf(3, 2, 1, 6, 5, 4), PatternTransform.mirrorHorizontal(cells, 3, 2))
    }

    @Test
    fun `垂直镜像按行倒序`() {
        assertArrayEquals(intArrayOf(4, 5, 6, 1, 2, 3), PatternTransform.mirrorVertical(cells, 3, 2))
    }

    @Test
    fun `水平镜像两次还原`() {
        val once = PatternTransform.mirrorHorizontal(cells, 3, 2)
        assertArrayEquals(cells, PatternTransform.mirrorHorizontal(once, 3, 2))
    }

    @Test
    fun `垂直镜像两次还原`() {
        val once = PatternTransform.mirrorVertical(cells, 3, 2)
        assertArrayEquals(cells, PatternTransform.mirrorVertical(once, 3, 2))
    }

    @Test
    fun `锁定标记跟着格子一起镜像`() {
        val locks = booleanArrayOf(true, false, false, false, false, true)
        assertArrayEquals(
            booleanArrayOf(false, false, true, true, false, false),
            PatternTransform.mirrorHorizontal(locks, 3, 2),
        )
        assertArrayEquals(
            booleanArrayOf(false, false, true, true, false, false),
            PatternTransform.mirrorVertical(locks, 3, 2),
        )
    }

    @Test
    fun `尺寸不符直接拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            PatternTransform.mirrorHorizontal(intArrayOf(1, 2, 3), 2, 2)
        }
    }
}
