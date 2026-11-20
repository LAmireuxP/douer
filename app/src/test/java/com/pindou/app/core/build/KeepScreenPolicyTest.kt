package com.pindou.app.core.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepScreenPolicyTest {

    @Test
    fun `常亮模式一直保持`() {
        assertTrue(KeepScreenPolicy.shouldKeep("on", 0))
        assertTrue(KeepScreenPolicy.shouldKeep("on", 100L * 60 * 60 * 1000))
        assertNull(KeepScreenPolicy.limitFor("on"))
    }

    @Test
    fun `关模式从不保持`() {
        assertFalse(KeepScreenPolicy.shouldKeep("off", 0))
        assertFalse(KeepScreenPolicy.shouldKeep("off", 1))
    }

    @Test
    fun `限时到点翻转`() {
        val ten = 10L * 60 * 1000
        assertTrue(KeepScreenPolicy.shouldKeep("10", ten - 1))
        assertFalse(KeepScreenPolicy.shouldKeep("10", ten))
        assertFalse(KeepScreenPolicy.shouldKeep("10", ten + 1))
    }

    @Test
    fun `非法值回退到 30 分钟`() {
        assertEquals("30", KeepScreenPolicy.normalize("abc"))
        assertEquals(30, KeepScreenPolicy.limitFor(""))
        assertTrue(KeepScreenPolicy.shouldKeep("abc", 29L * 60 * 1000))
        assertFalse(KeepScreenPolicy.shouldKeep("abc", 30L * 60 * 1000))
    }

    @Test
    fun `设置页选项含关且顺序固定`() {
        assertEquals(listOf("on", "10", "30", "60", "off"), KeepScreenPolicy.OPTIONS)
        assertEquals("关", KeepScreenPolicy.label("off"))
        assertEquals("30 分钟", KeepScreenPolicy.label("wat"))
    }
}
