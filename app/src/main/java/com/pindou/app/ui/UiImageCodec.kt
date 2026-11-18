package com.pindou.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import com.pindou.app.core.palette.BeadPalette

/** 取色卡 ARGB 数组：Studio 引擎/帧归一化用 */
internal fun paletteArgbs(palette: BeadPalette): IntArray =
    IntArray(palette.colors.size) { palette.colors[it].argb }

/** Studio sidecar 工程文件路径 */
internal fun studioFile(application: Application, id: Long): File =
    File(application.filesDir, "studio/project_$id.studio")

/**
 * SAF URI → Bitmap + ARGB。先解 bounds 决定采样率，最大边压到 2048。
 *
 * 注意：inJustDecodeBounds 时 decodeStream 必然返回 null，不能拿它的返回值
 * 判断流是否打开；这里单独判断 InputStream。
 */
internal fun decodeImage(application: Application, uri: Uri): DecodedImage {
    val bmp = decodeScaled(
        openInput = { openImageInput(application, uri) },
        maxSide = 2048,
    )
    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    return DecodedImage(bmp, pixels, bmp.width, bmp.height)
}

/** MIUI 文件管理器对 openInputStream/openAssetFileDescriptor 支持不一致，两种都试。 */
internal fun openImageInput(application: Application, uri: Uri): java.io.InputStream? {
    val resolver = application.contentResolver
    runCatching { resolver.openInputStream(uri) }
        .onSuccess { if (it != null) return it }
        .onFailure {
            android.util.Log.e("PindouImage", "openInputStream failed", it)
        }
    runCatching { resolver.openAssetFileDescriptor(uri, "r")?.createInputStream() }
        .onSuccess { if (it != null) return it }
        .onFailure {
            android.util.Log.e("PindouImage", "openAssetFileDescriptor failed", it)
        }
    return null
}

/** 共用的降采样解码：先读 bounds，再按 2 的幂压到 maxSide 以内。 */
fun decodeScaled(
    openInput: () -> java.io.InputStream?,
    maxSide: Int,
): Bitmap {
    require(maxSide > 0) { "maxSide must be positive" }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsInput = openInput() ?: error("无法读取图片")
    boundsInput.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片解码失败" }

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmpInput = openInput() ?: error("无法读取图片")
    return bmpInput.use { BitmapFactory.decodeStream(it, null, opts) }
        ?: error("图片解码失败")
}
