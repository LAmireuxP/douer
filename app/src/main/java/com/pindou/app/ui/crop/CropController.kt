package com.pindou.app.ui.crop

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.createBitmap
import com.pindou.app.core.image.CropRect
import com.pindou.app.core.image.ImageTransforms
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.pattern.GeneratorOptions
import com.pindou.app.core.pattern.PatternGenerator
import com.pindou.app.core.pattern.PixelArtDetector
import com.pindou.app.ui.CropAspectMode
import com.pindou.app.ui.CropGridPreview
import com.pindou.app.ui.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 裁剪页子域：选框、网格尺寸、实时色号预览、旋转/镜像/重置和确认裁剪。
 *
 * 图像缓冲、色卡、生成参数、导航和像素校准确认态仍由 AppViewModel 持有；
 * controller 只通过 [Host] 读写这些共享状态，自身持有裁剪页私有的 Compose
 * state / Job / token。这样 CropScreen 的 `vm.crop*` API 不用改。
 */
class CropController(
    private val scope: CoroutineScope,
    private val host: Host,
) {

    interface Host {
        val screen: Screen
        val imagePixels: IntArray?
        val imageWidth: Int
        val imageHeight: Int
        val originalPixels: IntArray?
        val originalWidth: Int
        val originalHeight: Int
        val gridWidth: Int
        val gridHeight: Int
        val pixelArtGrid: Pair<Int, Int>?
        val pixelArtMode: Boolean
        val pixelCalibrationConfirmed: Boolean
        val selectedPalette: BeadPalette?

        suspend fun ensurePalette(): BeadPalette?
        fun generatorOptions(): GeneratorOptions

        fun setWorkingImage(pixels: IntArray, width: Int, height: Int)
        fun setPreviewBitmap(bitmap: Bitmap?)
        fun setGridSize(width: Int, height: Int)
        fun setPixelArtGrid(grid: Pair<Int, Int>?)
        fun setFilterLevelNone()
        fun resetAdaptive()

        /** 裁剪会换掉源图，让像素校准的旧确认网格失效。 */
        fun clearPixelCalibration()

        /** 完整裁剪/确认后的导航：写 paramsFromCrop 并切到参数页。 */
        fun navigateToParams()
    }

    /** 裁剪框（工作图像素坐标系），null = 不在裁剪流程中 */
    var cropRect by mutableStateOf<CropRect?>(null)
        private set
    var cropOriginalPreset by mutableStateOf(false)
        private set
    var cropPreviewEnabled by mutableStateOf(true)
        private set
    var cropPreview by mutableStateOf<CropGridPreview?>(null)
        private set
    var cropPreviewRunning by mutableStateOf(false)
        private set
    var cropAspectMode by mutableStateOf(CropAspectMode.FREE)
        private set

    private var transformRunning = false
    private var cropPreviewJob: Job? = null
    private var cropPreviewToken = 0
    private var cropGridUserSet = false

    /** 新项目：清掉裁剪框和所有预览状态。 */
    fun resetForNewProject() {
        cropRect = null
        cropOriginalPreset = false
        cropAspectMode = CropAspectMode.FREE
        cropGridUserSet = false
        cancelPreview()
        host.resetAdaptive()
    }

    /**
     * 导入完成后的初始选框。调用前 VM 已写入源图/尺寸。
     * [artGrid] 非空时同时套用检测到的画像素网格。
     */
    fun prepareForImport(rect: CropRect, artGrid: Pair<Int, Int>?) {
        cropAspectMode = CropAspectMode.FREE
        cropOriginalPreset = false
        cropRect = rect
        if (artGrid != null) host.setGridSize(artGrid.first, artGrid.second)
        cropGridUserSet = false
        cancelPreview()
        host.resetAdaptive()
        syncCropGridToRect(rect)
    }

    /** 文字栅格化不是普通照片导入：只把选框重置为整图，不重算参数页网格。 */
    fun resetCropToFull(width: Int, height: Int) {
        cropRect = CropRect.full(width, height)
        cropOriginalPreset = false
        cropGridUserSet = false
        cancelPreview()
    }

    /** 参数页「重新裁剪」：从整张图开始，导航由 VM 先切到 CROP。 */
    fun prepareForOpen() {
        cropAspectMode = CropAspectMode.FREE
        cropOriginalPreset = false
        cropRect = CropRect.full(host.imageWidth, host.imageHeight)
        cropGridUserSet = false
        syncCropGridToRect(cropRect!!)
        schedulePreview()
    }

    /** 像素校准确认后，裁剪页的旧选框/预览/手动网格都不再有效。 */
    fun onPixelConfirmed(width: Int, height: Int) {
        cropRect = CropRect.full(width, height)
        cropGridUserSet = true
        cancelPreview()
    }

    fun updateCropRect(rect: CropRect) {
        cropRect = rect
        cropOriginalPreset = false
        if (cropAspectMode == CropAspectMode.FREE) syncCropGridToRect(rect)
        schedulePreview()
    }

    fun selectCropOriginal() {
        if (host.imageWidth <= 0 || host.imageHeight <= 0) return
        cropAspectMode = CropAspectMode.FREE
        cropRect = CropRect.full(host.imageWidth, host.imageHeight)
        cropOriginalPreset = true
        syncCropGridToRect(cropRect!!)
        schedulePreview()
    }

    fun changeCropAspectMode(mode: CropAspectMode) {
        if (cropAspectMode == mode) return
        cropOriginalPreset = false
        cropAspectMode = mode
        val w = host.imageWidth
        val h = host.imageHeight
        if (w <= 0 || h <= 0) return
        when (mode) {
            CropAspectMode.SQUARE -> {
                cropRect = cropRect
                    ?.let { ImageTransforms.squareAround(it, w.toFloat(), h.toFloat()) }
                    ?: ImageTransforms.centerSquare(w, h)
                val side = max(host.gridWidth, host.gridHeight)
                host.setGridSize(side, side)
            }
            CropAspectMode.FREE -> cropRect?.let { syncCropGridToRect(it) }
        }
        schedulePreview()
    }

    fun toggleCropPreview() {
        cropPreviewEnabled = !cropPreviewEnabled
        if (cropPreviewEnabled) schedulePreview() else cancelPreview()
    }

    fun setCropGridWidth(w: Int) {
        cropGridUserSet = true
        val cw = w.coerceIn(5, 200)
        if (cropAspectMode == CropAspectMode.SQUARE) {
            host.setGridSize(cw, cw)
        } else {
            val rect = cropRect
            val aspect = if (rect != null && rect.height > 0f) rect.width / rect.height else 1f
            val gridH = (cw / aspect).roundToInt().coerceIn(5, 200)
            // 短边被上下限钳制时反推宽，保持比例精确，宁可差一格也不拉伸。
            val gridW = (gridH * aspect).roundToInt().coerceIn(5, 200)
            host.setGridSize(gridW, gridH)
        }
        host.resetAdaptive()
        schedulePreview()
    }

    fun setCropGridHeight(h: Int) {
        cropGridUserSet = true
        val ch = h.coerceIn(5, 200)
        if (cropAspectMode == CropAspectMode.SQUARE) {
            host.setGridSize(ch, ch)
        } else {
            val rect = cropRect
            val aspect = if (rect != null && rect.height > 0f) rect.width / rect.height else 1f
            val gridW = (ch * aspect).roundToInt().coerceIn(5, 200)
            val gridH = (gridW / aspect).roundToInt().coerceIn(5, 200)
            host.setGridSize(gridW, gridH)
        }
        host.resetAdaptive()
        schedulePreview()
    }

    fun setCropGridPreset(w: Int, h: Int) {
        cropGridUserSet = true
        host.setGridSize(w, h)
        host.resetAdaptive()
        schedulePreview()
    }

    fun applyArtGridAtCrop() {
        val art = host.pixelArtGrid ?: return
        cropGridUserSet = true
        host.setGridSize(art.first, art.second)
        host.resetAdaptive()
        cropRect?.let { syncCropGridToRect(it) }
        schedulePreview()
    }

    private fun syncCropGridToRect(rect: CropRect) {
        if (cropAspectMode != CropAspectMode.FREE) return
        if (rect.width <= 0f || rect.height <= 0f) return
        val anchor = max(host.gridWidth, host.gridHeight)
        val (w, h) = ImageTransforms.gridSizeForAspect(rect.width / rect.height, anchor)
        host.setGridSize(w, h)
    }

    /**
     * 拖动时 updateCropRect 每帧都会进来：160ms 防抖 + 递增 token，
     * 只把最后一次结果写回，中间算完就丢。
     */
    fun schedulePreview() {
        if (host.screen != Screen.CROP || !cropPreviewEnabled) return
        val pixels = host.imagePixels ?: return
        val rect = cropRect ?: return
        val token = ++cropPreviewToken
        cropPreviewJob?.cancel()
        cropPreviewJob = scope.launch(Dispatchers.Default) {
            try {
                delay(160)
                if (token != cropPreviewToken) return@launch
                val loaded = host.ensurePalette()
                val palette = host.selectedPalette ?: loaded ?: return@launch
                cropPreviewRunning = true
                val sub = ImageTransforms.crop(pixels, host.imageWidth, host.imageHeight, rect)
                val res = PatternGenerator.generate(
                    sub.pixels, sub.width, sub.height, host.generatorOptions(), palette,
                )
                if (token != cropPreviewToken) return@launch
                val argb = IntArray(res.cells.size)
                val distinct = HashSet<Int>()
                for (i in argb.indices) {
                    val idx = res.cells[i]
                    if (idx >= 0 && idx < res.palette.colors.size) {
                        argb[i] = res.palette.colors[idx].argb
                        distinct.add(idx)
                    } else {
                        argb[i] = 0
                    }
                }
                val bmp = createBitmap(res.width, res.height)
                bmp.setPixels(argb, 0, res.width, 0, 0, res.width, res.height)
                cropPreview = CropGridPreview(
                    rect = rect,
                    gridW = res.width,
                    gridH = res.height,
                    image = bmp,
                    colorCount = distinct.size,
                    elapsedMs = res.elapsedMs,
                )
            } catch (e: Exception) {
                android.util.Log.w("PindouCrop", "裁剪预览生成失败", e)
            } finally {
                if (token == cropPreviewToken) cropPreviewRunning = false
            }
        }
    }

    fun rotateImage(clockwise: Boolean) {
        val pixels = host.imagePixels ?: return
        val w = host.imageWidth
        val h = host.imageHeight
        val rect = cropRect
        launchTransform {
            val t = ImageTransforms.rotate90(pixels, w, h, clockwise)
            host.setWorkingImage(t.pixels, t.width, t.height)
            if (rect != null) cropRect = ImageTransforms.rotateRect(rect, w, h, clockwise)
            rebuildPreview()
            refreshPixelArt(t.pixels, t.width, t.height)
            schedulePreview()
        }
    }

    fun mirrorImage(horizontal: Boolean) {
        val pixels = host.imagePixels ?: return
        val w = host.imageWidth
        val h = host.imageHeight
        val rect = cropRect
        launchTransform {
            val t = ImageTransforms.mirror(pixels, w, h, horizontal)
            host.setWorkingImage(t.pixels, t.width, t.height)
            if (rect != null) cropRect = ImageTransforms.mirrorRect(rect, w, h, horizontal)
            rebuildPreview()
            refreshPixelArt(t.pixels, t.width, t.height)
            schedulePreview()
        }
    }

    fun resetImageTransforms() {
        val pixels = host.originalPixels ?: return
        val w = host.originalWidth
        val h = host.originalHeight
        launchTransform {
            host.setWorkingImage(pixels, w, h)
            cropRect = CropRect.full(w, h)
            rebuildPreview()
            refreshPixelArt(pixels, w, h)
            schedulePreview()
        }
    }

    fun confirmCrop() {
        val pixels = host.imagePixels ?: return
        val rect = cropRect ?: return
        val w = host.imageWidth
        val h = host.imageHeight
        syncCropGridToRect(rect)
        val isFull = rect.left <= 0f && rect.top <= 0f &&
            rect.right >= w.toFloat() && rect.bottom >= h.toFloat()
        if (isFull) {
            host.navigateToParams()
            return
        }
        launchTransform {
            if (host.pixelCalibrationConfirmed) host.clearPixelCalibration()
            val t = ImageTransforms.crop(pixels, w, h, rect)
            host.setWorkingImage(t.pixels, t.width, t.height)
            cropRect = CropRect.full(t.width, t.height)
            rebuildPreview()
            refreshPixelArt(
                t.pixels, t.width, t.height,
                forceGrid = !cropGridUserSet && !host.pixelCalibrationConfirmed,
            )
            cropGridUserSet = false
            cancelPreview()
            withContext(Dispatchers.Main) { host.navigateToParams() }
        }
    }

    private fun launchTransform(block: suspend () -> Unit) {
        if (transformRunning) return
        transformRunning = true
        scope.launch(Dispatchers.Default) {
            try {
                block()
            } finally {
                transformRunning = false
            }
        }
    }

    private fun rebuildPreview() {
        val p = host.imagePixels ?: return
        val w = host.imageWidth
        val h = host.imageHeight
        if (w <= 0 || h <= 0) return
        host.setPreviewBitmap(Bitmap.createBitmap(p, w, h, Bitmap.Config.ARGB_8888))
    }

    private fun refreshPixelArt(
        pixels: IntArray,
        w: Int,
        h: Int,
        forceGrid: Boolean = true,
    ) {
        if (host.pixelCalibrationConfirmed) return
        val art = if (host.pixelArtMode) {
            PixelArtDetector.detectForced(pixels, w, h)
        } else {
            PixelArtDetector.detect(pixels, w, h)
        }
        host.setPixelArtGrid(art?.let { it.artW to it.artH })
        if (art != null) {
            host.setFilterLevelNone()
            if (forceGrid) {
                host.setGridSize(
                    art.artW.coerceIn(5, 200),
                    art.artH.coerceIn(5, 200),
                )
            }
            host.resetAdaptive()
        } else if (host.pixelArtMode) {
            host.setFilterLevelNone()
        }
    }

    private fun cancelPreview() {
        ++cropPreviewToken
        cropPreviewJob?.cancel()
        cropPreviewJob = null
        cropPreview = null
        cropPreviewRunning = false
    }
}
