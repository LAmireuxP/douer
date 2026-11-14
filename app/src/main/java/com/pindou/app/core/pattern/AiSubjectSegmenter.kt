package com.pindou.app.core.pattern

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 端侧 AI 主体分割（ML Kit Subject Segmentation）。
 *
 * 只做一件事：给「自动取景」和「去背景」提供主体剪影，解决传统边界色模型
 * 分不清"主体内部和背景同色"的问题。模型由 Play Services 动态下发，
 * 首次调用可能较慢；任何失败都返回 null，调用方回退现有算法，
 * 不阻塞生成流程。
 */
object AiSubjectSegmenter {

    /** 推理长边上限 1024，兼顾小图和内存；小于上限的图不放大。 */
    private const val MAX_LONG_SIDE = 1024
    private const val FOREGROUND_THRESHOLD = 0.5f
    private const val MIN_FOREGROUND_SHARE = 0.02

    suspend fun segment(bitmap: Bitmap): SubjectMask? = withContext(Dispatchers.Default) {
        if (bitmap.isRecycled) return@withContext null
        val input = try {
            scaleForInference(bitmap)
        } catch (_: Exception) {
            return@withContext null
        }
        try {
            try {
                val options = SubjectSegmenterOptions.Builder()
                    .enableForegroundConfidenceMask()
                    .build()
                val segmenter = SubjectSegmentation.getClient(options)
                try {
                    val result = suspendCancellableCoroutine<SubjectSegmentationResult?> { cont ->
                        segmenter.process(InputImage.fromBitmap(input, 0))
                            .addOnSuccessListener { r ->
                                if (cont.isActive) cont.resume(r)
                            }
                            .addOnFailureListener {
                                if (cont.isActive) cont.resume(null)
                            }
                    } ?: return@withContext null

                    val buffer = result.foregroundConfidenceMask ?: return@withContext null
                    buffer.rewind()
                    val maskW = input.width
                    val maskH = input.height
                    val count = maskW.toLong() * maskH
                    // ML Kit 的掩码按输入 Bitmap 尺寸逐像素返回。尺寸不足时
                    // 不能补 0 继续读，否则会把缺的那部分全判成背景。
                    if (maskW <= 0 || maskH <= 0 || count > Int.MAX_VALUE) {
                        return@withContext null
                    }
                    if (buffer.remaining().toLong() < count) return@withContext null
                    val raw = ByteArray(count.toInt())
                    var fg = 0
                    for (i in raw.indices) {
                        val v = buffer.get()
                        if (v >= FOREGROUND_THRESHOLD) {
                            raw[i] = 1
                            fg++
                        }
                    }
                    if (fg * 50L < raw.size.toLong()) return@withContext null
                    if (input === bitmap) {
                        SubjectMask(maskW, maskH, raw)
                    } else {
                        resample(raw, maskW, maskH, bitmap.width, bitmap.height)
                    }
                } finally {
                    segmenter.close()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } finally {
            if (input !== bitmap) input.recycle()
        }
    }

    /** 长边收进 1024 以内。小图不放大：放大不会增加信息，反而多一次拷贝。 */
    private fun scaleForInference(bitmap: Bitmap): Bitmap {
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide <= MAX_LONG_SIDE) return bitmap
        val k = MAX_LONG_SIDE.toDouble() / longSide
        val w = (bitmap.width * k).roundToInt().coerceAtLeast(8)
        val h = (bitmap.height * k).roundToInt().coerceAtLeast(8)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /** 最近邻回原图尺寸：掩码只有 0/1，双线性插值会造出灰色过渡带。 */
    private fun resample(
        src: ByteArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
    ): SubjectMask {
        val out = ByteArray(dstW * dstH)
        for (y in 0 until dstH) {
            val sy = (y.toLong() * srcH / dstH).toInt().coerceIn(0, srcH - 1)
            val srcRow = sy * srcW
            val dstRow = y * dstW
            for (x in 0 until dstW) {
                val sx = (x.toLong() * srcW / dstW).toInt().coerceIn(0, srcW - 1)
                out[dstRow + x] = src[srcRow + sx]
            }
        }
        return SubjectMask(dstW, dstH, out)
    }
}
