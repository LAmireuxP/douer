package com.pindou.app.ui.crop

import android.graphics.Bitmap
import com.pindou.app.core.image.CropRect
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.pattern.GeneratorOptions
import com.pindou.app.ui.CropAspectMode
import com.pindou.app.ui.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CropControllerTest {

    private class FakeHost(
        override var imageWidth: Int = 400,
        override var imageHeight: Int = 300,
    ) : CropController.Host {
        override var screen: Screen = Screen.HOME
        override var imagePixels: IntArray? = IntArray(4)
        override var originalPixels: IntArray? = IntArray(4)
        override var originalWidth: Int = imageWidth
        override var originalHeight: Int = imageHeight
        override var gridWidth: Int = 40
        override var gridHeight: Int = 30
        private var pixelArt: Pair<Int, Int>? = null
        override val pixelArtGrid: Pair<Int, Int>? get() = pixelArt
        override var pixelArtMode: Boolean = false
        override var pixelCalibrationConfirmed: Boolean = false
        override var selectedPalette: BeadPalette? = null

        var adaptiveResets = 0
        var filteredToNone = false
        var clearedPixelCalibration = false
        var navigatedToParams = false

        override suspend fun ensurePalette(): BeadPalette? = null
        override fun generatorOptions() = GeneratorOptions(gridW = 10, gridH = 10)

        override fun setWorkingImage(pixels: IntArray, width: Int, height: Int) {
            imagePixels = pixels
            imageWidth = width
            imageHeight = height
        }

        override fun setPreviewBitmap(bitmap: Bitmap?) = Unit

        override fun setGridSize(width: Int, height: Int) {
            gridWidth = width.coerceIn(5, 200)
            gridHeight = height.coerceIn(5, 200)
        }

        override fun setPixelArtGrid(grid: Pair<Int, Int>?) {
            pixelArt = grid
        }

        override fun setFilterLevelNone() {
            filteredToNone = true
        }

        override fun resetAdaptive() {
            adaptiveResets++
        }

        override fun clearPixelCalibration() {
            clearedPixelCalibration = true
            pixelCalibrationConfirmed = false
        }

        override fun navigateToParams() {
            navigatedToParams = true
        }
    }

    private fun controller(host: FakeHost) = CropController(
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        host,
    )

    @Test
    fun `自由裁剪按选区比例联动网格`() {
        val host = FakeHost()
        val crop = controller(host)

        crop.prepareForImport(CropRect.full(400, 300), null)

        assertEquals(40, host.gridWidth)
        assertEquals(30, host.gridHeight)
        assertEquals(CropAspectMode.FREE, crop.cropAspectMode)
    }

    @Test
    fun `手输宽高在自由模式下保持比例并钳制`() {
        val host = FakeHost()
        val crop = controller(host)
        crop.prepareForImport(CropRect.full(400, 300), null)

        crop.setCropGridWidth(60)
        assertEquals(60, host.gridWidth)
        assertEquals(45, host.gridHeight)

        crop.setCropGridHeight(30)
        assertEquals(40, host.gridWidth)
        assertEquals(30, host.gridHeight)

        crop.setCropGridWidth(999)
        assertTrue(host.gridWidth in 5..200)
        assertTrue(host.gridHeight in 5..200)
    }

    @Test
    fun `正方形模式锁死双轴且导入保留原画网格`() {
        val host = FakeHost()
        val crop = controller(host)

        crop.changeCropAspectMode(CropAspectMode.SQUARE)
        crop.setCropGridWidth(50)
        assertEquals(50, host.gridWidth)
        assertEquals(50, host.gridHeight)

        crop.prepareForImport(CropRect(0f, 0f, 200f, 100f), 20 to 10)
        assertEquals(CropAspectMode.FREE, crop.cropAspectMode)
        assertEquals(20, host.gridWidth)
        assertEquals(10, host.gridHeight)
        assertFalse(crop.cropOriginalPreset)
    }

    @Test
    fun `整图确认只导航而新项目清空选框`() {
        val host = FakeHost()
        val crop = controller(host)
        crop.prepareForImport(CropRect.full(400, 300), null)

        crop.confirmCrop()
        assertTrue(host.navigatedToParams)
        assertFalse(host.clearedPixelCalibration)

        crop.resetForNewProject()
        assertNull(crop.cropRect)
        assertTrue(host.adaptiveResets >= 2)
    }
}
