package com.pindou.app.core.palette

import org.junit.Assert.assertEquals
import org.junit.Test

class BeadSizeTest {
    @Test
    fun `豆子规格覆盖常见三档`() {
        assertEquals(5.0, BeadSize.MM5.pitchMm, 1e-9)
        assertEquals(8.0, BeadSize.MM8.pitchMm, 1e-9)
        assertEquals(2.6, BeadSize.MM2_6.pitchMm, 1e-9)
    }

    @Test
    fun `标签解析支持新规格`() {
        assertEquals(BeadSize.MM8, BeadSize.fromTag("8mm"))
        assertEquals(BeadSize.MM8, BeadSize.fromTag("MM8"))
        assertEquals(BeadSize.MM8, BeadSize.fromTag("8.0"))
        assertEquals(BeadSize.MM5, BeadSize.fromTag("5mm"))
        assertEquals(BeadSize.MM2_6, BeadSize.fromTag("2.6mm"))
    }

    @Test
    fun `未知标签回退 5mm`() {
        assertEquals(BeadSize.MM5, BeadSize.fromTag("banana"))
    }
}
