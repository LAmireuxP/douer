package com.pindou.app.core.build

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildTimerTest {

    @Test
    fun `不足一秒不结算`() {
        assertEquals(0L, BuildTimer.settleSeconds(999))
        assertEquals(1L, BuildTimer.settleSeconds(1000))
        assertEquals(59L, BuildTimer.settleSeconds(59_900))
    }

    @Test
    fun `单次上限两小时`() {
        val eightHours = 8L * 60 * 60 * 1000
        assertEquals(7200L, BuildTimer.settleSeconds(eightHours))
        assertEquals(7200L, BuildTimer.clampSeconds(99_999))
    }

    @Test
    fun `负值归零`() {
        assertEquals(0L, BuildTimer.clampSeconds(-5))
        assertEquals(0L, BuildTimer.settleSeconds(-1000))
    }

    @Test
    fun `展示格式`() {
        assertEquals("—", BuildTimer.format(0))
        assertEquals("45 秒", BuildTimer.format(45))
        assertEquals("12 分钟", BuildTimer.format(12 * 60L))
        assertEquals("2 小时 5 分", BuildTimer.format(2 * 3600L + 5 * 60))
    }
}
