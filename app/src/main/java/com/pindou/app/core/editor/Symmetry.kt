package com.pindou.app.core.editor

/**
 * 绘制对称轴。索引以「格」为单位，镜像后返回去重后的中心点。
 *
 * 画像素里的对称模式是全局开关，这里沿用同一思路：画笔/橡皮每落一笔，
 * 同时在镜像位置补一笔；方形笔刷本身对称，所以镜像中心点即可。
 */
enum class SymmetryMode(val label: String) {
    NONE("无"),
    HORIZONTAL("水平"),
    VERTICAL("垂直"),
    BOTH("中心");

    fun centers(index: Int, width: Int, height: Int): List<Int> {
        if (this == NONE || width <= 0 || height <= 0 || index !in 0 until width * height) {
            return listOf(index)
        }
        val x = index % width
        val y = index / width
        val mx = width - 1 - x
        val my = height - 1 - y
        val out = LinkedHashSet<Int>()
        out.add(index)
        if (this == HORIZONTAL || this == BOTH) out.add(y * width + mx)
        if (this == VERTICAL || this == BOTH) out.add(my * width + x)
        if (this == BOTH) out.add(my * width + mx)
        return out.toList()
    }
}
