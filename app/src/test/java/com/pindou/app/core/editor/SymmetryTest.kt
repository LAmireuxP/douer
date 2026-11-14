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
}
