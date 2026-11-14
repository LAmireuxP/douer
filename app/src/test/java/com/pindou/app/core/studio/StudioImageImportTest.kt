package com.pindou.app.core.studio

import com.pindou.app.core.color.ColorConversions
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.palette.BeadSize
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class StudioImageImportTest {

    private val palette = BeadPalette(
        brand = "test",
        brandLabel = "测试",
        beadSize = BeadSize.MM5,
        source = null,
        colors = listOf(
            entry("B", 0xFF000000.toInt()),
            entry("W", 0xFFFFFFFF.toInt()),
            entry("R", 0xFFFF0000.toInt()),
        ),
    )

    private fun entry(code: String, argb: Int) = BeadPalette.Entry(
        code = code,
        name = code,
        argb = argb,
        lab = ColorConversions.argbToLab(argb),
    )

    @Test
    fun `导入像素按当前色卡量化并保留透明格`() {
        val result = StudioImageImport.quantize(
            intArrayOf(
                0xFF000000.toInt(),
                0xFFFFFFFF.toInt(),
                0x00000000,
                0xFFFF0000.toInt(),
            ),
            palette,
        )

        assertArrayEquals(intArrayOf(0, 1, -1, 2), result)
    }
}
