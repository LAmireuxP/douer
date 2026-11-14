package com.pindou.app.core.palette

import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

class PaletteAssetsTest {

    private fun palette(
        brand: String,
        size: BeadSize,
        colorCount: Int,
    ): BeadPalette = BeadPalette(
        brand = brand,
        brandLabel = brand,
        beadSize = size,
        source = null,
        colors = List(colorCount) { i ->
            BeadPalette.Entry(
                code = "C$i",
                name = null,
                argb = 0xFF000000.toInt() or i,
                lab = floatArrayOf(i.toFloat(), 0f, 0f),
            )
        },
    )

    @Test
    fun `同品牌按豆子规格选色卡`() {
        val s = palette("Artkal", BeadSize.MM5, 176)
        val mini = palette("Artkal", BeadSize.MM2_6, 221)

        assertSame(mini, PaletteAssets.pick(listOf(s, mini), "Artkal", "MM2_6"))
        assertSame(s, PaletteAssets.pick(listOf(s, mini), "Artkal", "MM5"))
    }

    @Test
    fun `规格对不上时退回同品牌第一套`() {
        val s = palette("Artkal", BeadSize.MM5, 176)
        val other = palette("MARD", BeadSize.MM5, 221)

        assertSame(s, PaletteAssets.pick(listOf(other, s), "Artkal", "MM2_6"))
    }

    @Test
    fun `品牌不存在返回 null`() {
        val s = palette("Artkal", BeadSize.MM5, 176)

        assertNull(PaletteAssets.pick(listOf(s), "COCO", "MM5"))
    }
}
