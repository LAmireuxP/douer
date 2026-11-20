package com.pindou.app.core.pattern

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoTuneDeltaTest {

    @Test
    fun `完全一致不给标签`() {
        assertEquals("", AutoTuneDelta.label(92, 30, 92, 30))
    }

    @Test
    fun `分升色降同时列出`() {
        assertEquals("+2 分 · \u22124 色", AutoTuneDelta.label(92, 30, 94, 26))
    }

    @Test
    fun `只有色数变化`() {
        assertEquals("\u22123 色", AutoTuneDelta.label(90, 33, 90, 30))
    }

    @Test
    fun `只有分数变化`() {
        assertEquals("+1 分", AutoTuneDelta.label(89, 30, 90, 30))
    }

    @Test
    fun `基准分未知时不编造差值`() {
        assertEquals("", AutoTuneDelta.label(0, 30, 92, 24))
        assertEquals("", AutoTuneDelta.label(92, 30, 0, 24))
    }
}
