package com.pindou.app.core.pattern

import com.pindou.app.core.color.ColorConversions
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.palette.BeadSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTunerTest {

    private fun palette(vararg argb: Int): BeadPalette = BeadPalette(
        brand = "TEST", brandLabel = "测试色卡", beadSize = BeadSize.MM5, source = null,
        colors = argb.mapIndexed { i, c ->
            BeadPalette.Entry("T%02d".format(i + 1), null, c, ColorConversions.argbToLab(c))
        },
    )

    /** 8×8 双色块图：左半红右半蓝，用来跑真实管线 */
    private fun blockImage(size: Int = 8): IntArray {
        val red = 0xFFD94B4B.toInt()
        val blue = 0xFF3A55C8.toInt()
        return IntArray(size * size) { i ->
            val x = i % size
            if (x < size / 2) red else blue
        }
    }

    @Test
    fun `候选数量等于档位乘限色选项`() {
        val pixels = blockImage()
        val base = GeneratorOptions(gridW = 8, gridH = 8, filterLevel = FilterLevel.MEDIUM)
        val outcome = AutoTuner.sweep(
            pixels, 8, 8, base, palette(0xFFD94B4B.toInt(), 0xFF3A55C8.toInt()),
            levels = listOf(FilterLevel.LOW, FilterLevel.HIGH),
            maxColorsOptions = listOf(null, 4),
        )
        assertNotNull(outcome)
        assertEquals(4, outcome!!.all.size)
        // 最优必须是分数最高的那个，不能是"最后一个跑完的"
        assertEquals(outcome.all.maxOf { it.score }, outcome.best.score)
    }

    @Test
    fun `未限色时默认候选包含二十与三十色档`() {
        val base = GeneratorOptions(gridW = 29, gridH = 29)
        assertEquals(listOf<Int?>(null, 20, 30), AutoTuner.defaultColorOptions(base))
    }

    @Test
    fun `已限色时不再改动用户设定的色数`() {
        val base = GeneratorOptions(gridW = 29, gridH = 29, maxColors = 12)
        assertEquals(listOf<Int?>(12), AutoTuner.defaultColorOptions(base))
    }

    @Test
    fun `两色图在任意档位都能拿到高分`() {
        val pixels = blockImage()
        val p = palette(0xFFD94B4B.toInt(), 0xFF3A55C8.toInt())
        val r = PatternGenerator.generate(
            pixels, 8, 8, GeneratorOptions(gridW = 8, gridH = 8, filterLevel = FilterLevel.MEDIUM), p,
        )
        assertTrue("双色图还原度应接近满分，实际 ${r.quality.score}", r.quality.score >= 85)
        // 评分必须写回结果（UI 直接读 result.quality），且色数应压在 1~2 色
        assertTrue(r.quality.colorCount in 1..2)
        assertTrue("双色图平均色差应很小，实际 ${r.quality.meanDeltaE}", r.quality.meanDeltaE < 6.0)
    }

    @Test
    fun `每档只留分最高的一版且同分取色多`() {
        fun result(score: Int, colors: Int) = PatternResult(
            1, 1, IntArray(1), palette(0xFF000000.toInt()), 0,
            QualityReport(score, 0.0, 0.0, colors, 0.0, 0.0, 0.0),
        )
        val all = listOf(
            AutoTuner.Candidate(FilterLevel.LOW, null, result(80, 4)),
            AutoTuner.Candidate(FilterLevel.LOW, 20, result(85, 2)),
            AutoTuner.Candidate(FilterLevel.LOW, 30, result(85, 6)),
            AutoTuner.Candidate(FilterLevel.HIGH, null, result(90, 3)),
        )

        val best = AutoTuner.bestPerLevel(all)

        assertEquals(2, best.size)
        assertEquals(FilterLevel.LOW, best[0].filterLevel)
        // 同分 85 时取色多的一版（6 色），与 sweep 选 best 的口径一致
        assertEquals(6, best[0].result.quality.colorCount)
        assertEquals(FilterLevel.HIGH, best[1].filterLevel)
    }
}
