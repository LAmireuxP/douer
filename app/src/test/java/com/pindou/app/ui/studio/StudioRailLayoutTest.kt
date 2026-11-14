package com.pindou.app.ui.studio

import org.junit.Assert.assertEquals
import org.junit.Test

class StudioRailLayoutTest {

    @Test
    fun `未自定义时使用默认比例`() {
        assertEquals(0f, StudioRailLayout.toPixels(-1f, 1000f, 0f), 0.001f)
        assertEquals(1000f, StudioRailLayout.toPixels(-1f, 1000f, 1f), 0.001f)
        assertEquals(500f, StudioRailLayout.toPixels(-1f, 1000f, 0.5f), 0.001f)
    }

    @Test
    fun `像素转比例再转回保持位置`() {
        val max = 820f
        val px = 317f
        val f = StudioRailLayout.toFraction(px, max)
        assertEquals(px, StudioRailLayout.toPixels(f, max, 0f), 0.001f)
    }

    @Test
    fun `越界位置被夹回画布`() {
        assertEquals(0f, StudioRailLayout.clamp(-30f, 900f), 0.001f)
        assertEquals(900f, StudioRailLayout.clamp(1200f, 900f), 0.001f)
    }

    @Test
    fun `画布或浮窗尺寸为零时不产生非法值`() {
        assertEquals(0f, StudioRailLayout.toFraction(100f, 0f), 0.001f)
        assertEquals(0f, StudioRailLayout.toPixels(1f, 0f, 1f), 0.001f)
        assertEquals(0f, StudioRailLayout.clamp(10f, -5f), 0.001f)
    }
}
