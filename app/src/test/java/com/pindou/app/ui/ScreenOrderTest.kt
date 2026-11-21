package com.pindou.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenOrderTest {

    @Test
    fun `主流程逐级加深`() {
        assertEquals(0, ScreenOrder.rank(Screen.HOME))
        assertEquals(1, ScreenOrder.rank(Screen.CROP))
        assertEquals(1, ScreenOrder.rank(Screen.PARAMS))
        assertEquals(2, ScreenOrder.rank(Screen.PREVIEW))
        assertEquals(3, ScreenOrder.rank(Screen.EDITOR))
    }

    @Test
    fun `往前跳是前进 往回退是后退`() {
        assertTrue(ScreenOrder.isForward(Screen.HOME, Screen.CROP))
        assertTrue(ScreenOrder.isForward(Screen.PARAMS, Screen.PREVIEW))
        assertFalse(ScreenOrder.isForward(Screen.PREVIEW, Screen.PARAMS))
        assertFalse(ScreenOrder.isForward(Screen.EDITOR, Screen.HOME))
    }

    @Test
    fun `同层按前进处理`() {
        assertTrue(ScreenOrder.isForward(Screen.CROP, Screen.PARAMS))
        assertTrue(ScreenOrder.isForward(Screen.EDITOR, Screen.STUDIO))
        assertTrue(ScreenOrder.isForward(Screen.PREVIEW, Screen.CONSTRUCTION))
    }

    @Test
    fun `同屏不产生方向`() {
        assertFalse(ScreenOrder.isForward(Screen.PREVIEW, Screen.PREVIEW))
        assertFalse(ScreenOrder.isForward(Screen.HOME, Screen.HOME))
    }
}
