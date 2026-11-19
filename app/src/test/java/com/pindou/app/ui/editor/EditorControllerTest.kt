package com.pindou.app.ui.editor

import com.pindou.app.core.editor.EditorEngine
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.palette.BeadSize
import com.pindou.app.core.pattern.PatternResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorControllerTest {

    private class FakeHost : EditorController.Host {
        var engineRef: EditorEngine? = null
        override val engine: EditorEngine? get() = engineRef
        override fun installEngine(engine: EditorEngine) {
            engineRef = engine
        }

        var pagesRef: List<IntArray> = emptyList()
        override val pages: List<IntArray> get() = pagesRef
        override fun setPages(pages: List<IntArray>) {
            pagesRef = pages
        }

        var doneRef: PatternResult? = null
        override val done: PatternResult? get() = doneRef
        override fun setDone(result: PatternResult) {
            doneRef = result
        }

        var paletteRef: BeadPalette? = null
        override val palette: BeadPalette? get() = paletteRef
        override fun setPalette(palette: BeadPalette) {
            paletteRef = palette
        }

        override var colorIndex: Int = 0
        var clearedRecent = 0
        override fun clearRecentColors() {
            clearedRecent++
        }

        override val projectId: Long? = 42L
        var persisted = 0
        var persistedPages = -1
        override fun persist(
            width: Int,
            height: Int,
            pages: List<IntArray>,
            locks: List<BooleanArray>,
            palette: BeadPalette,
        ) {
            persisted++
            persistedPages = pages.size
        }

        var notified = 0
        override fun notifyChanged() {
            notified++
        }

        val toasts = mutableListOf<String>()
        override fun toast(message: String) {
            toasts += message
        }
    }

    private fun palette(brand: String, vararg lab: FloatArray): BeadPalette = BeadPalette(
        brand = brand,
        brandLabel = brand,
        beadSize = BeadSize.MM5,
        source = null,
        colors = lab.mapIndexed { index, l ->
            BeadPalette.Entry("C$index", null, 0xFF000000.toInt() + index, l)
        },
    )

    private fun host(
        width: Int = 10,
        height: Int = 10,
        cells: IntArray = IntArray(width * height),
    ): FakeHost = FakeHost().apply {
        engineRef = EditorEngine(width, height, listOf(cells))
        pagesRef = listOf(cells.copyOf())
    }

    @Test
    fun `改尺寸夹到 5 到 500`() {
        val h = host()
        EditorController(h).resize(2, 9_000)
        assertEquals(5, h.engineRef!!.width)
        assertEquals(500, h.engineRef!!.height)
        assertEquals(1, h.notified)
    }

    @Test
    fun `越界裁剪不动画布也不刷新`() {
        val h = host()
        val controller = EditorController(h)
        controller.crop(9, 9, 5, 5)
        controller.crop(-1, 0, 5, 5)
        assertEquals(10, h.engineRef!!.width)
        assertEquals(10, h.engineRef!!.height)
        assertEquals(0, h.notified)
    }

    @Test
    fun `合法裁剪改变画布并刷新`() {
        val h = host()
        EditorController(h).crop(2, 3, 5, 5)
        assertEquals(5, h.engineRef!!.width)
        // 高度同样被夹到最小 5 格：4 会变成 5，与 VM 原来的 coerceIn 一致
        assertEquals(5, h.engineRef!!.height)
        assertEquals(1, h.notified)
    }

    @Test
    fun `水平翻转会重排格子`() {
        val h = host(2, 1, intArrayOf(1, 2))
        EditorController(h).flipHorizontal()
        assertEquals(listOf(2, 1), h.engineRef!!.cells.toList())
        assertEquals(1, h.notified)
    }

    @Test
    fun `旋转九十度交换宽高`() {
        val h = host(2, 1, intArrayOf(1, 2))
        EditorController(h).rotate90(clockwise = true)
        assertEquals(1, h.engineRef!!.width)
        assertEquals(2, h.engineRef!!.height)
    }

    @Test
    fun `换色卡重建引擎并把旧索引夹回范围`() {
        val old = palette("OLD", floatArrayOf(0f, 0f, 0f), floatArrayOf(100f, 0f, 0f))
        val new = palette(
            "NEW",
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(50f, 0f, 0f),
            floatArrayOf(100f, 0f, 0f),
        )
        val h = host(2, 1, intArrayOf(0, 1))
        h.paletteRef = old
        h.doneRef = PatternResult(2, 1, intArrayOf(0, 1), old, 12L)
        h.colorIndex = 99
        val before = h.engineRef

        EditorController(h).applyPaletteRemap(new)

        assertNotSame(before, h.engineRef)
        assertEquals(1, h.pagesRef.size)
        assertEquals(new, h.paletteRef)
        assertEquals(new, h.doneRef!!.palette)
        assertEquals(2, h.colorIndex)
        assertEquals(1, h.clearedRecent)
        assertEquals(1, h.persisted)
        assertEquals(1, h.notified)
        assertTrue(h.toasts.last().contains("NEW"))
    }

    @Test
    fun `合并相近色返回明细并落库`() {
        val p = palette(
            "TEST",
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(90f, 0f, 0f),
        )
        val h = host(2, 2, intArrayOf(0, 1, 2, 2))
        h.paletteRef = p
        h.doneRef = PatternResult(2, 2, intArrayOf(0, 1, 2, 2), p, 1L)

        val moves = EditorController(h).mergeSimilar(5.0)

        assertTrue(moves.isNotEmpty())
        assertEquals(1, h.persisted)
        assertEquals(1, h.persistedPages)
        // 两种相近色并成一色，画布上只剩两个不同值（含 -1 之外）
        assertEquals(2, h.engineRef!!.cells.toSet().size)
        assertTrue(h.toasts.last().startsWith("已合并"))
    }

    @Test
    fun `合并阈值内没有可并色时只提示`() {
        val p = palette(
            "TEST",
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(90f, 0f, 0f),
        )
        val h = host(1, 2, intArrayOf(0, 1))
        h.paletteRef = p
        h.doneRef = PatternResult(1, 2, intArrayOf(0, 1), p, 1L)

        val moves = EditorController(h).mergeSimilar(1.0)

        assertTrue(moves.isEmpty())
        assertEquals(0, h.persisted)
        assertTrue(h.toasts.last().contains("没有可合并"))
    }
}
