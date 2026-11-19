package com.pindou.app.core.text

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TextBinarizerTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun `纯黑纯白原样保留`() {
        val pixels = intArrayOf(black, white)
        TextBinarizer.binarize(pixels)
        assertArrayEquals(intArrayOf(black, white), pixels)
    }

    @Test
    fun `阈值 128 向上取白`() {
        // 127 灰 → 黑；128 灰 → 白（边界值写死，避免以后改成 > 或 >= 漂移）
        val at127 = intArrayOf(argb(127, 127, 127))
        val at128 = intArrayOf(argb(128, 128, 128))
        TextBinarizer.binarize(at127)
        TextBinarizer.binarize(at128)
        assertEquals(black, at127[0])
        assertEquals(white, at128[0])
    }

    @Test
    fun `按 BT601 亮度而不是 RGB 平均`() {
        // 纯蓝平均 (0+0+255)/3 = 85 → 若按平均会判黑；BT601 亮度 29 也是黑。
        // 纯绿平均 85 → 按平均会判黑；BT601 亮度 150 → 必须判白。
        // 这条用例锁的是「绿色字母不会整块消失」。
        val green = intArrayOf(argb(0, 255, 0))
        val blue = intArrayOf(argb(0, 0, 255))
        TextBinarizer.binarize(green)
        TextBinarizer.binarize(blue)
        assertEquals(white, green[0])
        assertEquals(black, blue[0])
    }

    @Test
    fun `输出 alpha 一律不透明`() {
        val pixels = intArrayOf(0x00101010, 0x00F0F0F0)
        TextBinarizer.binarize(pixels)
        assertEquals(0xFF000000.toInt(), pixels[0])
        assertEquals(0xFFFFFFFF.toInt(), pixels[1])
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
