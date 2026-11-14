package com.pindou.app.core.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GifEncoderTest {

    @Test
    fun `GIF 头尾与两帧结构`() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val a = IntArray(16) { red }
        val b = IntArray(16) { blue }
        val bytes = GifEncoder.encode(listOf(a, b), 4, 4, listOf(10, 20))

        assertEquals("GIF89a", String(bytes, 0, 6, Charsets.US_ASCII))
        assertEquals(0x3B, bytes.last().toInt() and 0xFF)
        // 两帧各一个图像描述符
        assertTrue(bytes.count { (it.toInt() and 0xFF) == 0x2C } >= 2)
        assertTrue(bytes.size > 40)
    }

    @Test
    fun `透明像素使用索引 0`() {
        val frame = IntArray(4) { 0 }
        frame[0] = 0xFFFF0000.toInt()
        val bytes = GifEncoder.encode(listOf(frame), 2, 2, listOf(10))
        assertTrue(bytes.size > 20)
        assertEquals(0x3B, bytes.last().toInt() and 0xFF)
    }

    /**
     * 手写 LZW 必须能被标准解码器读回；16 色 32×32 会让字典增长越过初始码长，
     * 只断言头尾的旧测试抓不到码长推进错拍导致的整段花屏。
     */
    @Test
    fun `GIF 解码往返保留多色帧`() {
        val w = 32
        val h = 32
        val colors = intArrayOf(
            0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(),
            0xFF0000FF.toInt(), 0xFFFFFF00.toInt(), 0xFF00FFFF.toInt(), 0xFFFF00FF.toInt(),
            0xFF112233.toInt(), 0xFF445566.toInt(), 0xFF778899.toInt(), 0xFFAABBCC.toInt(),
            0xFFDDEEFF.toInt(), 0xFF123456.toInt(), 0xFF654321.toInt(), 0xFFABCDEF.toInt(),
        )
        val a = IntArray(w * h) { i -> colors[(i * 7 + (i / w) * 3) % colors.size] }
        val b = IntArray(w * h) { i -> colors[(i * 11 + i / w) % colors.size] }
        val bytes = GifEncoder.encode(listOf(a, b), w, h, listOf(40, 80))

        val frames = GifTestDecoder.decode(bytes)
        assertEquals(2, frames.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                assertEquals(a[y * w + x], frames[0].argb[y * w + x])
                assertEquals(b[y * w + x], frames[1].argb[y * w + x])
            }
        }
    }
}
