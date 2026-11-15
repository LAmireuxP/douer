package com.pindou.app.core.studio

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 把相册图片解码成底层原图参考。
 *
 * 关键点：这里不做色卡量化，也不经过 PixelCalibration。保留图片真实 RGB，
 * 只按画布比例居中缩放；上层像素层由用户自己点。
 */
object StudioImageImport {

    private const val MAX_DECODE_SIDE = 2048
    private const val MAX_REFERENCE_SIDE = 1280

    fun decodeReference(
        resolver: ContentResolver,
        uri: Uri,
        canvasWidth: Int,
        canvasHeight: Int,
    ): StudioReference {
        require(canvasWidth > 0 && canvasHeight > 0) { "画布尺寸无效" }
        val canvasAspect = canvasWidth.toFloat() / canvasHeight
        val refW: Int
        val refH: Int
        if (canvasAspect >= 1f) {
            refW = MAX_REFERENCE_SIDE
            refH = (MAX_REFERENCE_SIDE / canvasAspect).roundToInt().coerceAtLeast(1)
        } else {
            refH = MAX_REFERENCE_SIDE
            refW = (MAX_REFERENCE_SIDE * canvasAspect).roundToInt().coerceAtLeast(1)
        }

        val bitmap = decodeBitmap(resolver, uri)
        return try {
            val fitted = fitCenter(bitmap, refW, refH)
            try {
                val pixels = IntArray(refW * refH)
                fitted.getPixels(pixels, 0, refW, 0, 0, refW, refH)
                StudioReference(refW, refH, pixels)
            } finally {
                if (fitted !== bitmap) fitted.recycle()
            }
        } finally {
            bitmap.recycle()
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
        val drawW = max(1, (source.width * scale).roundToInt())
        val drawH = max(1, (source.height * scale).roundToInt())
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
