package com.pindou.app.core.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class SymmetryTest {

    @Test
    fun `水平对称左右镜像`() {
        assertEquals(listOf(1, 3), SymmetryMode.HORIZONTAL.centers(1, 5, 5))
        assertEquals(listOf(0, 4), SymmetryMode.HORIZONTAL.centers(0, 5, 5))
    }

    @Test
    fun `垂直对称上下镜像`() {
        assertEquals(listOf(1, 9), SymmetryMode.VERTICAL.centers(1, 4, 3))
    }

    @Test
    fun `中心对称生成四个不同中心点`() {
        assertEquals(
            listOf(0, 4, 20, 24),
            SymmetryMode.BOTH.centers(0, 5, 5),
        )
    }

    @Test
    fun `关闭对称只返回原点且中心点去重`() {
        assertEquals(listOf(2), SymmetryMode.NONE.centers(2, 5, 5))
        assertEquals(listOf(4), SymmetryMode.BOTH.centers(4, 3, 3))
    }

    @Test
    fun `旋转四份角点落四角`() {
        assertEquals(
            listOf(0, 4, 24, 20),
            SymmetryMode.ROTATE_4.centers(0, 5, 5),
        )
    }

    @Test
    fun `旋转六份产生六个落点`() {
        val points = SymmetryMode.ROTATE_6.centers(3 * 7 + 1, 7, 7)
        assertEquals(6, points.size)
        // 每个点都应在画布内且不重复
        assertEquals(points.size, points.toSet().size)
        assertEquals(true, points.all { it in 0 until 49 })
    }

    @Test
    fun `轴心点旋转后只返回一个`() {
        assertEquals(listOf(12), SymmetryMode.ROTATE_4.centers(12, 5, 5))
        assertEquals(listOf(24), SymmetryMode.ROTATE_8.centers(24, 7, 7))
    }

    @Test
    fun `奇数画布旋转不越界`() {
        val points = SymmetryMode.ROTATE_8.centers(0, 7, 7)
        assertEquals(true, points.all { it in 0 until 49 })
        assertEquals(4, points.size) // 角点八分旋转后落在四角，重复去重
    }
}
