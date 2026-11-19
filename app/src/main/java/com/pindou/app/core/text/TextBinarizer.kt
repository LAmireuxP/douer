package com.pindou.app.core.text

/**
 * 文字位图二值化：把「白底黑字」的抗锯齿过渡灰压成纯黑/纯白。
 *
 * 为什么必须做：文字栅格化后笔画边缘有一圈灰，降采样到格子时灰格会被
 * 映射成色卡里的灰色号，字形边缘就碎了。阈值取 128 是按人眼亮度
 * （BT.601：0.299R + 0.587G + 0.114B）而不是简单平均——纯蓝的亮度只有
 * 29，按平均会被误判成中灰。
 *
 * 纯 IntArray 进、纯 IntArray 出，方便 JVM 单测；alpha 统一写 0xFF。
 */
object TextBinarizer {

    const val THRESHOLD = 128

    fun binarize(pixels: IntArray) {
        for (i in pixels.indices) {
            val p = pixels[i]
            val luminance = (
                ((p shr 16) and 0xFF) * 299 +
                    ((p shr 8) and 0xFF) * 587 +
                    (p and 0xFF) * 114
                ) / 1000
            pixels[i] = if (luminance >= THRESHOLD) {
                0xFFFFFFFF.toInt()
            } else {
                0xFF000000.toInt()
            }
        }
    }
}
