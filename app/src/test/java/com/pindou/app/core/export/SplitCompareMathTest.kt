package com.pindou.app.core.export

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitCompareMathTest {

    @Test
    fun `中点取一半`() {
        assertEquals(0.5f, SplitCompareMath.fractionFor(500f, 1000f), 1e-4f)
        assertEquals(500f, SplitCompareMath.dividerX(0.5f, 1000f), 1e-4f)
    }

    @Test
    fun `拖到左边缘停在左极限`() {
        assertEquals(0.08f, SplitCompareMath.fractionFor(-40f, 1000f), 1e-4f)
        assertEquals(0.08f, SplitCompareMath.fractionFor(0f, 1000f), 1e-4f)
    }

    @Test
    fun `拖到右边缘停在右极限`() {
        assertEquals(0.92f, SplitCompareMath.fractionFor(1000f, 1000f), 1e-4f)
        assertEquals(0.92f, SplitCompareMath.fractionFor(5000f, 1000f), 1e-4f)
    }

    @Test
    fun `零宽度退回中线`() {
        assertEquals(0.5f, SplitCompareMath.fractionFor(120f, 0f), 1e-4f)
    }

    @Test
    fun `越界比例绘制时同样被夹住`() {
        assertEquals(80f, SplitCompareMath.dividerX(-1f, 1000f), 1e-4f)
        assertEquals(920f, SplitCompareMath.dividerX(2f, 1000f), 1e-4f)
    }
}
