package com.pindou.app.core.editor

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 绘制对称轴。索引以「格」为单位，镜像后返回去重后的中心点。
 *
 * 画像素里的对称模式是全局开关，这里沿用同一思路：画笔/橡皮每落一笔，
 * 同时在镜像位置补一笔；方形笔刷本身对称，所以镜像中心点即可。
 *
 * 旋转对称（曼陀罗）把落点绕画布中心按 360/n 等分旋转；中心点 n 次重合，
 * LinkedHashSet 自动去重。奇数画布用 (w-1)/2 的几何中心，四舍五入落格。
 */
enum class SymmetryMode(val label: String, val rotateCount: Int = 0) {
    NONE("无"),
    HORIZONTAL("水平"),
    VERTICAL("垂直"),
    BOTH("中心"),
    ROTATE_4("旋转4", 4),
    ROTATE_6("旋转6", 6),
    ROTATE_8("旋转8", 8);

    fun centers(index: Int, width: Int, height: Int): List<Int> {
        if (this == NONE || width <= 0 || height <= 0 || index !in 0 until width * height) {
            return listOf(index)
        }
        val x = index % width
        val y = index / width
        if (rotateCount > 0) {
            val cx = (width - 1) / 2.0
            val cy = (height - 1) / 2.0
            val dx = x - cx
            val dy = y - cy
            val out = LinkedHashSet<Int>()
            for (k in 0 until rotateCount) {
                val angle = 2.0 * PI * k / rotateCount
                val nx = cx + dx * cos(angle) - dy * sin(angle)
                val ny = cy + dx * sin(angle) + dy * cos(angle)
                val ix = nx.roundToInt()
                val iy = ny.roundToInt()
                if (ix in 0 until width && iy in 0 until height) out.add(iy * width + ix)
            }
            return out.toList()
        }
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
