package com.pindou.app.core.pattern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorLabelLayoutTest {

    @Test
    fun `同一片连通同色只标一次`() {
        val cells = intArrayOf(
            1, 1, 1,
            1, 1, 1,
            1, 1, 1,
        )
        val mask = ColorLabelLayout.labelMask(cells, 3, 3)

        assertEquals(1, mask.count { it })
        assertTrue(mask[1 * 3 + 1])
    }

    @Test
    fun `两块分离的同色区域各标一次`() {
        val cells = intArrayOf(
            1, 1, 2,
            1, 1, 2,
            2, 2, 2,
        )
        val mask = ColorLabelLayout.labelMask(cells, 3, 3)

        assertEquals(2, mask.count { it })
        assertTrue(mask[0])
        // 第二块的重心在 (1.4, 1.4)，标注会落在区域内部靠重心的格子
        assertTrue(mask[5] || mask[7] || mask[8])
    }

    @Test
    fun `单格颜色保留标注`() {
        val cells = intArrayOf(
            1, 2,
            3, 4,
        )
        val mask = ColorLabelLayout.labelMask(cells, 2, 2)

        assertEquals(4, mask.count { it })
    }

    @Test
    fun `空格不标注`() {
        val cells = intArrayOf(-1, -1, -1, -1)
        val mask = ColorLabelLayout.labelMask(cells, 2, 2)

        assertFalse(mask.any { it })
    }

    @Test
    fun `尺寸不匹配时返回空掩码`() {
        val mask = ColorLabelLayout.labelMask(intArrayOf(1), 2, 2)

        assertEquals(4, mask.size)
        assertFalse(mask.any { it })
    }

    @Test
    fun `简化标号只标同色区域外圈`() {
        val cells = intArrayOf(
            1, 1, 1,
            1, 1, 1,
            1, 1, 1,
        )
        val mask = ColorLabelLayout.labelMaskOutline(cells, 3, 3)

        assertEquals(8, mask.count { it })
        assertFalse(mask[1 * 3 + 1])
        assertTrue(mask[0])
        assertTrue(mask[8])
    }

    @Test
    fun `简化标号在两格宽区域没有内部格`() {
        val cells = intArrayOf(
            1, 1,
            1, 1,
        )
        val mask = ColorLabelLayout.labelMaskOutline(cells, 2, 2)

        assertEquals(4, mask.count { it })
    }

    @Test
    fun `简化标号保留不同颜色交界`() {
        val cells = intArrayOf(
            1, 1, 1, 2, 2,
            1, 1, 1, 2, 2,
            1, 1, 1, 2, 2,
            1, 1, 1, 2, 2,
            1, 1, 1, 2, 2,
        )
        val mask = ColorLabelLayout.labelMaskOutline(cells, 5, 5)

        // 左块 3×5 只有中间一列是内部；右块 2×5 全部是边界
        assertFalse(mask[2 * 5 + 1])
        assertTrue(mask[0])
        assertTrue(mask[2 * 5 + 2])
        assertTrue(mask[2 * 5 + 3])
        assertTrue(mask[2 * 5 + 4])
    }

    @Test
    fun `简化标号不标空格`() {
        val mask = ColorLabelLayout.labelMaskOutline(intArrayOf(-1, -1, -1, -1), 2, 2)

        assertFalse(mask.any { it })
    }
}
