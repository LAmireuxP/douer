package com.pindou.app.ui.generate

import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.palette.BeadSize
import com.pindou.app.core.pattern.FilterLevel
import com.pindou.app.core.pattern.GenerationStage
import com.pindou.app.core.pattern.GeneratorOptions
import com.pindou.app.core.pattern.PatternResult
import com.pindou.app.core.pattern.SubjectMask
import com.pindou.app.ui.GenState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationControllerTest {

    private class FakeHost : GenerationController.Host {
        override var imagePixels: IntArray? = IntArray(1)
        override var imageWidth: Int = 0
        override var imageHeight: Int = 0
        override var selectedPalette: BeadPalette? =
            BeadPalette("TEST", "测试色卡", BeadSize.MM5, null, emptyList())
        override var pixelArtGrid: Pair<Int, Int>? = null

        var notReady = ""
        var clearedPages = 0
        var saved = 0
        var tuned: Pair<FilterLevel, Int?>? = null
        var navigated = false

        override fun options() = GeneratorOptions(gridW = 10, gridH = 10)
        override suspend fun prepareSubjectMask(): SubjectMask? = null

        override fun clearProjectPages() {
            clearedPages++
        }

        override suspend fun saveAfterGenerate(result: PatternResult) {
            saved++
        }

        override fun applyTunedParams(filterLevel: FilterLevel, maxColors: Int?) {
            tuned = filterLevel to maxColors
        }

        override fun navigateToPreview() {
            navigated = true
        }

        var beginText: Triple<String, Int, Int>? = null
        var preparedText: Triple<String, Int, Int>? = null
        var prepareResult = true
        var prepareGate: CompletableDeferred<Unit>? = null
        var textNavigated = false

        override fun beginTextGeneration(text: String, width: Int, height: Int) {
            beginText = Triple(text, width, height)
        }

        override suspend fun prepareTextSource(text: String, width: Int, height: Int): Boolean {
            preparedText = Triple(text, width, height)
            prepareGate?.await()
            return prepareResult
        }

        override fun navigateToTextPreview() {
            textNavigated = true
        }

        override fun reportNotReady(message: String) {
            notReady = message
        }
    }

    private fun controller(host: FakeHost) = GenerationController(
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        host,
        Dispatchers.Unconfined,
        Dispatchers.Unconfined,
    )

    @Test
    fun `缺少源图时生成报未就绪且不改状态`() {
        val host = FakeHost()
        val gen = controller(host)

        gen.generate()

        assertEquals("源图尺寸未就绪，请重新导入", host.notReady)
        assertEquals(GenState.Idle, gen.state)
        assertEquals(0, host.clearedPages)
    }

    @Test
    fun `缺少源图时自动优选报未就绪`() {
        val host = FakeHost()
        val gen = controller(host)

        gen.autoTune()

        assertEquals("源图尺寸未就绪，请重新导入", host.notReady)
        assertFalse(gen.autoTuneRunning)
        assertTrue(gen.autoTuneScores.isEmpty())
    }

    @Test
    fun `取消运行态回到 Idle`() {
        val host = FakeHost()
        val gen = controller(host)
        gen.state = GenState.Running(GenerationStage.DOWNSAMPLE, 0.4f)

        gen.cancel()

        assertEquals(GenState.Idle, gen.state)
        assertFalse(gen.autoTuneRunning)
    }

    // ------------------------------------------------------------ 文字拼豆

    private fun textPalette() = BeadPalette(
        "T", "测试", BeadSize.MM5, null,
        listOf(
            BeadPalette.Entry("K", null, 0xFF000000.toInt(), floatArrayOf(0f, 0f, 0f)),
            BeadPalette.Entry("W", null, 0xFFFFFFFF.toInt(), floatArrayOf(100f, 0f, 0f)),
        ),
    )

    private fun textHost(): FakeHost = FakeHost().apply {
        selectedPalette = textPalette()
        val w = 20
        val h = 20
        imagePixels = IntArray(w * h) { i ->
            if (i % w < 10) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        imageWidth = w
        imageHeight = h
    }

    @Test
    fun `空文字不启动也不改状态`() {
        val host = textHost()
        val gen = controller(host)

        gen.generateFromText("   ", 10, 10)

        assertEquals(GenState.Idle, gen.state)
        assertNull(host.beginText)
        assertNull(host.preparedText)
        assertEquals(0, host.saved)
    }

    @Test
    fun `没有色卡时文字生成不启动`() {
        val host = textHost().apply { selectedPalette = null }
        val gen = controller(host)

        gen.generateFromText("AB", 10, 10)

        assertEquals(GenState.Idle, gen.state)
        assertNull(host.beginText)
    }

    @Test
    fun `文字生成走文字参数并落库导航`() {
        val host = textHost()
        val gen = controller(host)

        gen.generateFromText("AB", 10, 12)

        assertEquals(Triple("AB", 10, 12), host.beginText)
        assertEquals(Triple("AB", 10, 12), host.preparedText)
        assertEquals(1, host.saved)
        assertTrue(host.textNavigated)
        val done = gen.state as GenState.Done
        assertEquals(10, done.result.width)
        assertEquals(12, done.result.height)
    }

    @Test
    fun `文字参数不抖动不去背景不自动取景`() {
        val gen = controller(textHost())
        // 直接测纯函数：把 UI 上开着的选项全部打开，文字仍必须覆盖它们
        val out = gen.textOptions(
            GeneratorOptions(
                gridW = 1,
                gridH = 1,
                ditheringEnabled = true,
                removeBackground = true,
                autoFrame = true,
                exactArt = true,
                pixelGrid = 5 to 5,
            ),
            10,
            12,
        )
        assertEquals(10, out.gridW)
        assertEquals(12, out.gridH)
        assertFalse(out.ditheringEnabled)
        assertFalse(out.removeBackground)
        assertFalse(out.autoFrame)
        assertFalse(out.exactArt)
        assertNull(out.pixelGrid)
        assertEquals(12.0, out.backgroundTolerance, 1e-9)
    }

    @Test
    fun `栅格化失败进入 Error 且不落库`() {
        val host = textHost().apply { prepareResult = false }
        val gen = controller(host)

        gen.generateFromText("AB", 10, 10)

        assertTrue(gen.state is GenState.Error)
        assertEquals(0, host.saved)
        assertFalse(host.textNavigated)
    }

    @Test
    fun `宽度高度夹到 5 到 200`() {
        val host = textHost()
        val gen = controller(host)

        gen.generateFromText("AB", 1, 999)

        assertEquals(Triple("AB", 5, 200), host.beginText)
        assertEquals(Triple("AB", 5, 200), host.preparedText)
    }

    @Test
    fun `取消文字生成回到 Idle`() {
        val gate = CompletableDeferred<Unit>()
        val host = textHost().apply { prepareGate = gate }
        val gen = controller(host)

        gen.generateFromText("AB", 10, 10)
        assertTrue(gen.state is GenState.Running)

        gen.cancel()

        assertEquals(GenState.Idle, gen.state)
    }
}
