package com.pindou.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemesTest {

    @Test
    fun `八套主题 id 唯一且可回查`() {
        assertEquals(8, AppThemes.all.size)
        assertEquals(AppThemes.all.size, AppThemes.all.map { it.id }.distinct().size)
        AppThemes.all.forEach { theme ->
            assertEquals(theme, AppThemes.byId(theme.id))
        }
    }

    @Test
    fun `未知 id 回落到默认主题`() {
        assertEquals(AppThemes.Douer, AppThemes.byId(null))
        assertEquals(AppThemes.Douer, AppThemes.byId(""))
        assertEquals(AppThemes.Douer, AppThemes.byId("nope"))
    }

    @Test
    fun `每套主题的浅色画布都比正文更亮`() {
        AppThemes.all.forEach { theme ->
            val p = theme.light
            assertTrue("${theme.id} 画布应比正文亮", p.canvas.luminance() > p.ink.luminance())
            assertTrue("${theme.id} 卡面应比正文亮", p.surfaceCard.luminance() > p.ink.luminance())
        }
    }

    @Test
    fun `每套主题的深色画布都比正文更暗`() {
        AppThemes.all.forEach { theme ->
            val p = theme.dark
            assertTrue("${theme.id} 深色画布应比正文暗", p.canvas.luminance() < p.ink.luminance())
            assertTrue("${theme.id} 深色卡面应比画布亮一档", p.surfaceCard.luminance() >= p.canvas.luminance())
        }
    }

    @Test
    fun `主按钮文字与底色有足够对比`() {
        AppThemes.all.forEach { theme ->
            listOf(theme.light, theme.dark).forEach { p ->
                val ratio = contrast(p.primary, p.onPrimary)
                assertTrue(
                    "${theme.id} 主按钮对比度不足：%.2f".format(ratio),
                    ratio >= 2.5f,
                )
            }
        }
    }

    @Test
    fun `每套主题都提供四色小样`() {
        AppThemes.all.forEach { theme ->
            assertNotNull(theme.swatch)
            assertEquals(4, theme.swatch.size)
        }
    }

    @Test
    fun `胶囊主题的按钮圆角明显大于紧凑主题`() {
        assertTrue(AppThemes.Spotify.style.pillButtons)
        assertTrue(AppThemes.Airbnb.style.pillButtons)
        assertTrue(AppThemes.Spotify.style.button.value > 100f)
        assertTrue(AppThemes.Linear.style.button.value < 20f)
    }

    /** WCAG 相对亮度对比度，只用于断言按钮可读性。 */
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05f) / (lo + 0.05f)
    }
}
