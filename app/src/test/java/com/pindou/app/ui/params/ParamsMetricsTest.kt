package com.pindou.app.ui.params

import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.palette.BeadSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParamsMetricsTest {

    private fun palette(brand: String, size: BeadSize): BeadPalette = BeadPalette(
        brand = brand,
        brandLabel = brand,
        beadSize = size,
        source = null,
        colors = listOf(
            BeadPalette.Entry("A1", null, 0xFF000000.toInt(), floatArrayOf(0f, 0f, 0f)),
            BeadPalette.Entry("A2", null, 0xFFFFFFFF.toInt(), floatArrayOf(100f, 0f, 0f)),
        ),
    )

    @Test
    fun `豆子数按宽高相乘`() {
        assertEquals(1_872, ParamsMetrics.beadCount(36, 52))
        assertEquals(0, ParamsMetrics.beadCount(0, 52))
        assertEquals(40_000, ParamsMetrics.beadCount(200, 200))
    }

    @Test
    fun `千分位不依赖 Locale`() {
        assertEquals("0", ParamsMetrics.formatCount(0))
        assertEquals("999", ParamsMetrics.formatCount(999))
        assertEquals("1,000", ParamsMetrics.formatCount(1_000))
        assertEquals("1,872", ParamsMetrics.formatCount(1_872))
        assertEquals("40,000", ParamsMetrics.formatCount(40_000))
    }

    @Test
    fun `成品厘米数等于格数乘珠径`() {
        val (w5, h5) = ParamsMetrics.finishedSizeCm(36, 52, 5.0)
        assertEquals(18.0, w5, 1e-9)
        assertEquals(26.0, h5, 1e-9)

        val (w26, h26) = ParamsMetrics.finishedSizeCm(36, 52, 2.6)
        assertEquals(9.36, w26, 1e-9)
        assertEquals(13.52, h26, 1e-9)

        // 非法珠径不产生 NaN/Infinity
        assertEquals(0.0 to 0.0, ParamsMetrics.finishedSizeCm(36, 52, 0.0))
    }

    @Test
    fun `厘米保留一位小数`() {
        assertEquals("18.0", ParamsMetrics.formatCm(18.0))
        assertEquals("9.4", ParamsMetrics.formatCm(9.36))
        assertEquals("13.5", ParamsMetrics.formatCm(13.52))
    }

    @Test
    fun `宽度档位按图片比例推导高度`() {
        assertEquals(27, ParamsMetrics.derivedHeight(36, 4.0 / 3.0))
        // 极端比例仍夹在 5..200，不会生成 0 行网格
        assertEquals(200, ParamsMetrics.derivedHeight(36, 0.1))
        assertEquals(5, ParamsMetrics.derivedHeight(5, 100.0))
    }

    @Test
    fun `档位归属只认宽高都对得上的组合`() {
        // 4:3 的图，36 档推出来是 36×27
        assertEquals(36, ParamsMetrics.matchGridWidth(36, 27, null, 4000, 3000))
        // 高度被手动改过就不算命中档位
        assertNull(ParamsMetrics.matchGridWidth(36, 28, null, 4000, 3000))
        // 非档位宽度
        assertNull(ParamsMetrics.matchGridWidth(37, 28, null, 4000, 3000))
    }

    @Test
    fun `像素画优先用原画比例`() {
        // 图片是 4:3，但像素画原画是 56:50；档位高度要按原画推
        val actual = ParamsMetrics.matchGridWidth(36, 32, 56 to 50, 4000, 3000)
        assertEquals(36, actual)
        assertEquals(56.0 / 50.0, ParamsMetrics.effectiveAspect(56 to 50, 4000, 3000), 1e-9)
    }

    @Test
    fun `比例偏差对称且对零维安全`() {
        assertEquals(4.0 / 3.0, ParamsMetrics.aspectSkew(4000, 3000, 100, 100), 1e-9)
        assertEquals(
            ParamsMetrics.aspectSkew(4000, 3000, 100, 100),
            ParamsMetrics.aspectSkew(3000, 4000, 100, 100),
            1e-9,
        )
        assertEquals(1.0, ParamsMetrics.aspectSkew(0, 0, 36, 36), 1e-9)
    }

    @Test
    fun `色卡分组顺序固定且全色系不混进品牌组`() {
        val all = palette("ALL-24", BeadSize.MM2_6)
        val small = palette("MARD", BeadSize.MM2_6)
        val normal = palette("HAMA", BeadSize.MM5)
        val big = palette("PERLER", BeadSize.MM8)
        val list = listOf(small, normal, big, all)

        val groups = groupPalettes(list)
        assertEquals(
            listOf(PaletteGroup.FULL, PaletteGroup.MM5, PaletteGroup.MM8, PaletteGroup.MM26),
            groups.map { it.first },
        )
        assertEquals(listOf(all), groups[0].second)
        assertEquals(listOf(small), groups[3].second)
    }

    @Test
    fun `筛选后只剩目标规格`() {
        val all = palette("ALL-24", BeadSize.MM2_6)
        val small = palette("MARD", BeadSize.MM2_6)
        val normal = palette("HAMA", BeadSize.MM5)
        val list = listOf(all, small, normal)

        assertEquals(list, filterPalettes(list, PaletteFilter.ALL))
        assertEquals(listOf(all), filterPalettes(list, PaletteFilter.FULL))
        assertEquals(listOf(normal), filterPalettes(list, PaletteFilter.MM5))
        // 2.6mm 只算品牌小豆，不含全色系
        assertEquals(listOf(small), filterPalettes(list, PaletteFilter.MM26))
        assertEquals(emptyList<BeadPalette>(), filterPalettes(list, PaletteFilter.MM8))
    }

    @Test
    fun `未知筛选名回退全部`() {
        assertEquals(PaletteFilter.ALL, PaletteFilter.fromName("banana"))
        assertEquals(PaletteFilter.MM8, PaletteFilter.fromName("MM8"))
    }

    @Test
    fun `同品牌不同珠径的色卡键不冲突`() {
        val five = palette("MARD", BeadSize.MM5)
        val eight = palette("MARD", BeadSize.MM8)
        assertNotEquals(five.paletteKey(), eight.paletteKey())
        // 同一个 brand/size/label 重建后仍然相等，LazyGrid 的 key 才能稳定复用
        assertEquals(five.paletteKey(), palette("MARD", BeadSize.MM5).paletteKey())
    }
}
