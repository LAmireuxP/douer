package com.pindou.app.core.studio

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.pindou.app.core.color.ColorConversions
import com.pindou.app.core.palette.BeadPalette
import kotlin.math.max
import kotlin.math.min

/**
 * 把相册图片缩放、居中放进画布，再按当前色卡量化成图层格。
 *
 * 量化按唯一 RGB 缓存：照片缩到 100×100 后通常只有几千种颜色，
 * 避免对每个像素重复跑 221 次 CIEDE2000。复杂度 O(唯一色数 × 色卡数)。
 */
object StudioImageImport {

    private const val MAX_DECODE_SIDE = 2048
    private const val ALPHA_CUTOFF = 16

    fun decodeCells(
        resolver: ContentResolver,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
        palette: BeadPalette,
    ): IntArray {
        require(targetWidth > 0 && targetHeight > 0) { "画布尺寸无效" }
        require(palette.colors.isNotEmpty()) { "当前色卡没有颜色" }

        val bitmap = decodeBitmap(resolver, uri)
        return try {
            val fitted = fitCenter(bitmap, targetWidth, targetHeight)
            try {
                val pixels = IntArray(targetWidth * targetHeight)
                fitted.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
                quantize(pixels, palette)
            } finally {
                if (fitted !== bitmap) fitted.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** 纯函数，便于单测；[argb] 已经是目标画布尺寸。 */
    internal fun quantize(argb: IntArray, palette: BeadPalette): IntArray {
        if (palette.colors.isEmpty()) return IntArray(argb.size) { -1 }
        val cache = HashMap<Int, Int>(minOf(argb.size, 4096))
        return IntArray(argb.size) { i ->
            val color = argb[i]
            val alpha = color ushr 24
            if (alpha < ALPHA_CUTOFF) {
                -1
            } else {
                // 半透明边缘先压到白底，避免描边处落成一圈深色豆。
                val rgb = if (alpha == 255) {
                    color and 0xFFFFFF
                } else {
                    val inv = 255 - alpha
                    val r = (((color shr 16) and 0xFF) * alpha + 255 * inv) / 255
                    val g = (((color shr 8) and 0xFF) * alpha + 255 * inv) / 255
                    val b = ((color and 0xFF) * alpha + 255 * inv) / 255
                    (r shl 16) or (g shl 8) or b
                }
                cache.getOrPut(rgb) {
                    val opaque = (0xFF shl 24) or rgb
                    palette.nearestIndex(ColorConversions.argbToLab(opaque))
                }
            }
        }
    }

    private fun decodeBitmap(resolver: ContentResolver, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // inJustDecodeBounds 时 decodeStream 必然返回 null，不能拿返回值判断流。
        val boundsInput = openInput(resolver, uri) ?: error("无法读取图片")
        boundsInput.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片解码失败" }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DECODE_SIDE) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = openInput(resolver, uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("图片解码失败")
        return applyOrientation(decoded, readOrientation(resolver, uri))
    }

    private fun openInput(resolver: ContentResolver, uri: Uri): java.io.InputStream? {
        runCatching { resolver.openInputStream(uri) }
            .getOrNull()
            ?.let { return it }
        return runCatching { resolver.openAssetFileDescriptor(uri, "r")?.createInputStream() }
            .getOrNull()
    }

    private fun fitCenter(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
        if (source.width == targetW && source.height == targetH) return source
        val scale = min(targetW.toFloat() / source.width, targetH.toFloat() / source.height)
        val drawW = max(1, (source.width * scale).toInt())
        val drawH = max(1, (source.height * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(source, drawW, drawH, true)
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            scaled,
            (targetW - drawW) / 2f,
            (targetH - drawH) / 2f,
            null,
        )
        if (scaled !== source) scaled.recycle()
        return out
    }

    private fun readOrientation(resolver: ContentResolver, uri: Uri): Int =
        openInput(resolver, uri)?.use {
            runCatching {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
