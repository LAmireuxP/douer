package com.pindou.app.core.pattern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFrameTest {

    private fun argb(r: Int, g: Int, b: Int) =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private val white = argb(255, 255, 255)
    private val red = argb(220, 40, 50)

    @Test
    fun `AI 掩码取景输出与画布对齐的掩码`() {
        val srcW = 120
        val srcH = 100
        val pixels = IntArray(srcW * srcH) { white }
        val maskData = ByteArray(srcW * srcH)
        for (y in 30 until 70) {
            for (x in 40 until 70) {
                pixels[y * srcW + x] = red
                maskData[y * srcW + x] = 1
            }
        }
        val mask = SubjectMask(srcW, srcH, maskData)

        val result = AutoFrame.applyWithMask(
            pixels = pixels,
            srcW = srcW,
            srcH = srcH,
            gridW = 2,
            gridH = 3,
            mask = mask,
        )

        assertTrue("主体包围盒应触发取景", result.trimmed)
        assertEquals(30, result.subjectW)
        assertEquals(40, result.subjectH)
        val outMask = requireNotNull(result.subjectMask)
        assertEquals(result.w, outMask.width)
        assertEquals(result.h, outMask.height)
        assertEquals(result.w * result.h, outMask.data.size)

        var minX = outMask.width
        var maxX = -1
        var minY = outMask.height
        var maxY = -1
        for (y in 0 until outMask.height) {
            for (x in 0 until outMask.width) {
                if (!outMask.isSubject(x, y)) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        assertEquals(30, maxX - minX + 1)
        assertEquals(40, maxY - minY + 1)
        assertEquals(30 * 40, outMask.subjectCount())
    }

    @Test
    fun `像素画早退时原样返回并保留掩码`() {
        // 自相关要求长边至少 2×36px；64px 会被直接判成样本不足。
        val srcW = 96
        val srcH = 96
        val colors = intArrayOf(white, red, argb(30, 90, 220), argb(40, 180, 80), argb(30, 30, 30))
        val pixels = IntArray(srcW * srcH) { i ->
            val x = i % srcW
            val y = i / srcW
            colors[((x / 8) + 3 * (y / 8)) % colors.size]
        }
        val mask = SubjectMask(srcW, srcH, ByteArray(srcW * srcH) { 1 })
        assertTrue(
            "测试图应被识别为像素画",
            PixelArtDetector.detect(pixels, srcW, srcH) != null,
        )

        val result = AutoFrame.applyWithMask(
            pixels = pixels,
            srcW = srcW,
            srcH = srcH,
            gridW = 12,
            gridH = 12,
            mask = mask,
        )

        assertFalse(result.trimmed)
        assertSame(pixels, result.pixels)
        assertSame(mask, result.subjectMask)
        assertEquals(srcW, result.w)
        assertEquals(srcH, result.h)
    }
}
