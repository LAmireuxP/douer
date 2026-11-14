package com.pindou.app.core.pattern

/**
 * 主体掩码：1 = 前景主体，0 = 背景。
 *
 * 用 ByteArray 而不是 BooleanArray：掩码会在 AutoFrame 输出画布上重建、
 * 也会跨线程传给背景移除，紧凑存储减少一次大图拷贝的内存峰值。
 */
data class SubjectMask(
    val width: Int,
    val height: Int,
    val data: ByteArray,
) {
    fun isSubject(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= width || y >= height) return false
        val i = y * width + x
        return i < data.size && data[i].toInt() != 0
    }

    fun subjectCount(): Int = data.count { it.toInt() != 0 }
}
