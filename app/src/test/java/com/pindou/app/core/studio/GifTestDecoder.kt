package com.pindou.app.core.studio

import java.io.ByteArrayOutputStream

/**
 * 测试专用最小 GIF 解码器：只处理 GifEncoder 会写出的结构
 * （GIF89a、局部色表、无交错、LZW 数据子块）。
 *
 * 生产代码不依赖它；它存在的目的，是让单元测试真正走一遍 LZW 解码，
 * 而不是只检查文件头尾。
 */
object GifTestDecoder {

    data class Frame(val width: Int, val height: Int, val argb: IntArray)

    fun decode(bytes: ByteArray): List<Frame> {
        require(bytes.size >= 13) { "GIF 太短" }
        require(String(bytes, 0, 6, Charsets.US_ASCII) == "GIF89a") { "不是 GIF89a" }
        var p = 6
        val width = readShort(bytes, p); p += 2
        val height = readShort(bytes, p); p += 2
        val packed = bytes[p++].toInt() and 0xFF
        p += 2
        val globalTable = if (packed and 0x80 != 0) {
            val size = 1 shl ((packed and 0x07) + 1)
            readPalette(bytes, p, size).also { p += size * 3 }
        } else {
            null
        }

        val frames = ArrayList<Frame>()
        while (p < bytes.size) {
            when (val block = bytes[p++].toInt() and 0xFF) {
                0x3B -> break
                0x21 -> {
                    p++ // extension label
                    p = skipSubBlocks(bytes, p)
                }
                0x2C -> {
                    p += 8 // left, top, width, height
                    val imagePacked = bytes[p++].toInt() and 0xFF
                    val localTable = if (imagePacked and 0x80 != 0) {
                        val size = 1 shl ((imagePacked and 0x07) + 1)
                        readPalette(bytes, p, size).also { p += size * 3 }
                    } else {
                        null
                    }
                    val table = localTable ?: globalTable ?: error("缺少色表")
                    val minCodeSize = bytes[p++].toInt() and 0xFF
                    val (data, next) = readSubBlocks(bytes, p)
                    p = next
                    val indices = lzwDecode(data, minCodeSize)
                    require(indices.size == width * height) {
                        "帧像素数不符：${indices.size} vs ${width * height}"
                    }
                    frames += Frame(
                        width,
                        height,
                        IntArray(indices.size) { table.getOrElse(indices[it]) { 0 } },
                    )
                }
                else -> error("未知 GIF 块 0x%02X".format(block))
            }
        }
        return frames
    }

    private fun lzwDecode(data: ByteArray, minCodeSize: Int): IntArray {
        val clear = 1 shl minCodeSize
        val end = clear + 1
        val dict = ArrayList<IntArray>()
        var codeSize = minCodeSize + 1
        var next = end + 1
        var prev: IntArray? = null
        val reader = BitReader(data)
        val out = ByteArrayOutputStream()

        fun reset() {
            dict.clear()
            for (i in 0 until clear) dict.add(intArrayOf(i))
            dict.add(IntArray(0))
            dict.add(IntArray(0))
            codeSize = minCodeSize + 1
            next = end + 1
            prev = null
        }
        reset()

        while (true) {
            val code = reader.read(codeSize)
            if (code < 0) break
            when {
                code == clear -> {
                    reset()
                    continue
                }
                code == end -> break
                code < dict.size -> {
                    val entry = dict[code]
                    for (v in entry) out.write(v)
                    prev?.let {
                        if (next < 4096) dict.add(it + entry[0])
                        if (next + 1 == (1 shl codeSize) && codeSize < 12) codeSize++
                        next++
                    }
                    prev = entry
                }
                code == next && prev != null -> {
                    val entry = prev!! + prev!![0]
                    for (v in entry) out.write(v)
                    if (next < 4096) dict.add(entry)
                    if (next + 1 == (1 shl codeSize) && codeSize < 12) codeSize++
                    next++
                    prev = entry
                }
                else -> error("非法 LZW 码 $code")
            }
        }
        val bytes = out.toByteArray()
        return IntArray(bytes.size) { bytes[it].toInt() and 0xFF }
    }

    private class BitReader(private val data: ByteArray) {
        private var bit = 0

        fun read(count: Int): Int {
            if (bit + count > data.size * 8) return -1
            var value = 0
            for (i in 0 until count) {
                val b = (data[bit ushr 3].toInt() ushr (bit and 7)) and 1
                value = value or (b shl i)
                bit++
            }
            return value
        }
    }

    private fun skipSubBlocks(bytes: ByteArray, start: Int): Int {
        var p = start
        while (p < bytes.size) {
            val size = bytes[p++].toInt() and 0xFF
            if (size == 0) return p
            p += size
        }
        error("GIF 子块未结束")
    }

    private fun readSubBlocks(bytes: ByteArray, start: Int): Pair<ByteArray, Int> {
        var p = start
        val out = ByteArrayOutputStream()
        while (p < bytes.size) {
            val size = bytes[p++].toInt() and 0xFF
            if (size == 0) return out.toByteArray() to p
            require(p + size <= bytes.size) { "GIF 子块越界" }
            out.write(bytes, p, size)
            p += size
        }
        error("GIF 子块未结束")
    }

    private fun readPalette(bytes: ByteArray, start: Int, size: Int): IntArray {
        require(start + size * 3 <= bytes.size) { "GIF 色表越界" }
        return IntArray(size) { i ->
            val p = start + i * 3
            (0xFF shl 24) or
                ((bytes[p].toInt() and 0xFF) shl 16) or
                ((bytes[p + 1].toInt() and 0xFF) shl 8) or
                (bytes[p + 2].toInt() and 0xFF)
        }
    }

    private fun readShort(bytes: ByteArray, start: Int): Int =
        (bytes[start].toInt() and 0xFF) or ((bytes[start + 1].toInt() and 0xFF) shl 8)
}
