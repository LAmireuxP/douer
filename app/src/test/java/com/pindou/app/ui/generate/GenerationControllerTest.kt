package com.pindou.app.ui.generate

import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.palette.BeadSize
import com.pindou.app.core.pattern.FilterLevel
import com.pindou.app.core.pattern.GenerationStage
import com.pindou.app.core.pattern.GeneratorOptions
import com.pindou.app.core.pattern.PatternResult
import com.pindou.app.core.pattern.SubjectMask
import com.pindou.app.ui.GenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        override fun reportNotReady(message: String) {
            notReady = message
        }
    }

    private fun controller(host: FakeHost) = GenerationController(
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        host,
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
}
