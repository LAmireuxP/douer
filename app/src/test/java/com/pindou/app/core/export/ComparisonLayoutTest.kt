package com.pindou.app.core.export

import org.junit.Assert.assertEquals
import org.junit.Test

class ComparisonLayoutTest {

    @Test
    fun `宽图贴宽留上下`() {
        val fit = ComparisonLayout.fit(400, 200, 200f, 200f)
        assertEquals(0f, fit.left, 1e-4f)
        assertEquals(50f, fit.top, 1e-4f)
        assertEquals(200f, fit.width, 1e-4f)
        assertEquals(100f, fit.height, 1e-4f)
    }

    @Test
    fun `高图贴高留左右`() {
        val fit = ComparisonLayout.fit(200, 400, 200f, 200f)
        assertEquals(50f, fit.left, 1e-4f)
        assertEquals(0f, fit.top, 1e-4f)
        assertEquals(100f, fit.width, 1e-4f)
        assertEquals(200f, fit.height, 1e-4f)
    }

    @Test
    fun `同比例正好铺满`() {
        val fit = ComparisonLayout.fit(100, 100, 300f, 300f)
        assertEquals(0f, fit.left, 1e-4f)
        assertEquals(0f, fit.top, 1e-4f)
        assertEquals(300f, fit.width, 1e-4f)
        assertEquals(300f, fit.height, 1e-4f)
    }

    @Test
    fun `零尺寸不产生负矩形`() {
        val fit = ComparisonLayout.fit(0, 100, 200f, 200f)
        assertEquals(0f, fit.width, 1e-4f)
        assertEquals(0f, fit.height, 1e-4f)
    }
}
