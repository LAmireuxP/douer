package com.pindou.app.core.studio

import java.io.ByteArrayOutputStream

/**
 * 最小 GIF89a 编码器：每帧一张局部色表 + LZW，无第三方依赖。
 *
 * 画像素里的 GIF 导出是「按帧序列循环播放」，这里保持同样语义：
 * - 透明格 = 透明索引 0；
 * - 颜色数 ≤ 255 时用精确色表；
 * - 超过 255 色时用 6×7×6 的固定色表量化（像素画颜色多但面积小，够用）。
 */
object GifEncoder {

    /** [frames] 每帧为 width×height 的 ARGB，alpha<8 视为透明。 */
    fun encode(
        frames: List<IntArray>,
        width: Int,
        height: Int,
        delaysCs: List<Int>,
        loop: Boolean = true,
    ): ByteArray {
        require(frames.isNotEmpty()) { "没有可导出的帧" }
        require(width in 1..4096 && height in 1..4096) { "GIF 尺寸越界" }
        require(frames.all { it.size == width * height }) { "帧尺寸不一致" }

        val out = ByteArrayOutputStream()
        writeAscii(out, "GIF89a")
        writeShort(out, width)
        writeShort(out, height)
        // 无全局色表，颜色分辨率 8 位
        out.write(0x70)
        out.write(0)
        out.write(0)

        if (loop) {
            out.write(0x21)
            out.write(0xFF)
            out.write(0x0B)
            writeAscii(out, "NETSCAPE2.0")
            out.write(0x03)
            out.write(0x01)
            writeShort(out, 0) // 无限循环
            out.write(0)
        }

        for ((frameIdx, frame) in frames.withIndex()) {
            val built = buildPalette(frame)
            val palette = built.colors
            val bits = tableBits(palette.size)
            val tableSize = 1 shl bits
            val delay = (delaysCs.getOrElse(frameIdx) { 10 }).coerceIn(2, 65535)

            // 图形控制扩展：透明索引 0，处置方式 2（恢复背景）
            out.write(0x21)
            out.write(0xF9)
            out.write(0x04)
            out.write(0x09) // disposal=2, transparent=1
            writeShort(out, delay)
            out.write(0) // 透明色索引
            out.write(0)

            // 图像描述符 + 局部色表
            out.write(0x2C)
            writeShort(out, 0)
            writeShort(out, 0)
            writeShort(out, width)
            writeShort(out, height)
            out.write(0x80 or (bits - 1))

            for (i in 0 until tableSize) {
                val c = palette.getOrElse(i) { 0 }
                out.write((c shr 16) and 0xFF)
                out.write((c shr 8) and 0xFF)
                out.write(c and 0xFF)
            }

            val indices = IntArray(frame.size) { i ->
                if ((frame[i] ushr 24) < 8) 0 else paletteIndexOf(built, frame[i])
            }
            writeLzw(out, indices, maxOf(2, bits))
        }

        out.write(0x3B)
        return out.toByteArray()
    }

    private data class BuiltPalette(
        val colors: IntArray,
        val exact: Boolean,
        val indexByColor: Map<Int, Int>? = null,
    )

    /** 返回色表，索引 0 固定为透明占位。 */
    private fun buildPalette(frame: IntArray): BuiltPalette {
        val colors = LinkedHashSet<Int>()
        for (argb in frame) {
            if ((argb ushr 24) < 8) continue
            colors.add(argb or (0xFF shl 24))
            if (colors.size > 255) break
        }
        if (colors.size <= 255) {
            val table = ArrayList<Int>(colors.size + 1)
            table.add(0)
            table.addAll(colors)
            val colors = table.toIntArray()
            val map = HashMap<Int, Int>(colors.size * 2)
            for (i in colors.indices) map[colors[i]] = i
            return BuiltPalette(colors, exact = true, indexByColor = map)
        }
        // 超过 255 色：6×7×6 固定表（252 色），与索引计算保持一致
        val table = IntArray(253)
        var idx = 1
        for (r in 0 until 6) {
            for (g in 0 until 7) {
                for (b in 0 until 6) {
                    val rr = r * 255 / 5
                    val gg = g * 255 / 6
                    val bb = b * 255 / 5
                    table[idx++] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
                }
            }
        }
        return BuiltPalette(table, exact = false)
    }

    private fun paletteIndexOf(built: BuiltPalette, argb: Int): Int {
        if (built.exact) {
            val found = built.indexByColor?.get(argb)
            if (found != null) return found
            // 理论上不会走到：buildPalette 已收集全部颜色
            return 1
        }
        val r = ((argb shr 16) and 0xFF) * 6 / 256
        val g = ((argb shr 8) and 0xFF) * 7 / 256
        val b = (argb and 0xFF) * 6 / 256
        return 1 + r * 42 + g * 6 + b
    }

    private fun tableBits(colors: Int): Int {
        var bits = 1
        while ((1 shl bits) < colors) bits++
        return bits.coerceIn(1, 8)
    }

    private fun writeLzw(out: ByteArrayOutputStream, indices: IntArray, minCodeSize: Int) {
        out.write(minCodeSize)
        val clear = 1 shl minCodeSize
        val end = clear + 1
        var codeSize = minCodeSize + 1
        var next = end + 1
        val dict = HashMap<Int, Int>(512)
        val bits = BitWriter()

        fun reset() {
            dict.clear()
            next = end + 1
            codeSize = minCodeSize + 1
        }

        bits.write(clear, codeSize)
        if (indices.isEmpty()) {
            bits.write(end, codeSize)
            bits.flush(out)
            return
        }
        var prefix = indices[0]
        for (i in 1 until indices.size) {
            val k = indices[i]
            val key = (prefix shl 8) or k
            val code = dict[key]
            if (code != null) {
                prefix = code
            } else {
                bits.write(prefix, codeSize)
                if (next < 4096) {
                    dict[key] = next++
                    // GIF LZW 的编码端码表比解码端多一项，所以码长要等
                    // next 越过 2^codeSize 才拓宽；提前一拍会让后面全部错位。
                    if (next > (1 shl codeSize) && codeSize < 12) codeSize++
                } else {
                    bits.write(clear, codeSize)
                    reset()
                }
                prefix = k
            }
        }
        bits.write(prefix, codeSize)
        bits.write(end, codeSize)
        bits.flush(out)
    }

    private class BitWriter {
        private var acc = 0
        private var count = 0
        private val buffer = ByteArrayOutputStream()

        fun write(code: Int, bits: Int) {
            acc = acc or (code shl count)
            count += bits
            while (count >= 8) {
                buffer.write(acc and 0xFF)
                acc = acc ushr 8
                count -= 8
            }
        }

        fun flush(out: ByteArrayOutputStream) {
            if (count > 0) {
                buffer.write(acc and 0xFF)
                acc = 0
                count = 0
            }
            val bytes = buffer.toByteArray()
            var offset = 0
            while (offset < bytes.size) {
                val n = minOf(255, bytes.size - offset)
                out.write(n)
                out.write(bytes, offset, n)
                offset += n
            }
            out.write(0)
        }
    }

    private fun writeAscii(out: ByteArrayOutputStream, text: String) {
        out.write(text.toByteArray(Charsets.US_ASCII))
    }

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }
}
