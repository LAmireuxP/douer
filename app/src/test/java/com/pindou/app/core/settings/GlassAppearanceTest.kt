package com.pindou.app.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassAppearanceTest {

    @Test
    fun `越界参数被夹回可调范围`() {
        val dirty = GlassAppearance(
            alpha = 5f,
            blurDp = -3f,
            refractionDp = 999f,
            highlight = -1f,
            cornerDp = 0f,
        ).sanitized()

        assertEquals(GlassAppearance.MAX_ALPHA, dirty.alpha, 0.0001f)
        assertEquals(GlassAppearance.MIN_BLUR, dirty.blurDp, 0.0001f)
        assertEquals(GlassAppearance.MAX_REFRACTION, dirty.refractionDp, 0.0001f)
        assertEquals(GlassAppearance.MIN_HIGHLIGHT, dirty.highlight, 0.0001f)
        assertEquals(GlassAppearance.MIN_CORNER, dirty.cornerDp, 0.0001f)
    }

    @Test
    fun `合法参数原样保留`() {
        val glass = GlassAppearance(
            alpha = 0.24f,
            blurDp = 18f,
            refractionDp = 30f,
            highlight = 0.6f,
            cornerDp = 34f,
            chromatic = false,
        )
        assertEquals(glass, glass.sanitized())
    }

    @Test
    fun `表面透明度随设置单调上升且不超过上限`() {
        val low = GlassAppearance(alpha = 0.10f).surfaceAlphas(dark = false)
        val high = GlassAppearance(alpha = 0.60f).surfaceAlphas(dark = false)

        assertTrue(low.top < high.top)
        assertTrue(low.middle < high.middle)
        assertTrue(high.top <= 0.72f)
        assertTrue(high.middle <= 0.34f)
        assertTrue(high.bottom <= 0.20f)
    }

    @Test
    fun `深色主题的表面更薄`() {
        val glass = GlassAppearance(alpha = 0.40f)
        val light = glass.surfaceAlphas(dark = false)
        val dark = glass.surfaceAlphas(dark = true)

        assertTrue(dark.top < light.top)
        assertTrue(dark.middle < light.middle)
    }

    @Test
    fun `三档预设都落在合法区间`() {
        GlassAppearance.Presets.forEach { (name, preset) ->
            assertEquals(name, preset, preset.sanitized())
        }
    }

    @Test
    fun `清透比标准更透明、磨砂更不透明`() {
        assertTrue(GlassAppearance.Clear.alpha < GlassAppearance.Default.alpha)
        assertTrue(GlassAppearance.Frosted.alpha > GlassAppearance.Default.alpha)
        assertTrue(GlassAppearance.Frosted.blurDp > GlassAppearance.Clear.blurDp)
        assertFalse(GlassAppearance.Frosted.chromatic)
        assertTrue(GlassAppearance.Clear.chromatic)
    }
}
