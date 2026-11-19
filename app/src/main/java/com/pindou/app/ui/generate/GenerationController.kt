package com.pindou.app.ui.generate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.pattern.AutoTuner
import com.pindou.app.core.pattern.FilterLevel
import com.pindou.app.core.pattern.GenerationStage
import com.pindou.app.core.pattern.GeneratorOptions
import com.pindou.app.core.pattern.PatternGenerator
import com.pindou.app.core.pattern.PatternResult
import com.pindou.app.core.pattern.SubjectMask
import com.pindou.app.ui.GenState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * 照片/参数页生成子域：手动生成、自动优选、两份 Job 和它们的结果状态。
 *
 * 源图、参数、色卡和落库仍归 AppViewModel；controller 通过 [Host] 取参数快照
 * 与存放结果。`GenState` 仍由 VM 的私有 setter 暴露给 chart/pixel/studio 等
 * 其它流程使用，避免二十多处直接改写失去取消语义。
 */
class GenerationController(
    private val scope: CoroutineScope,
    private val host: Host,
) {

    interface Host {
        val imagePixels: IntArray?
        val imageWidth: Int
        val imageHeight: Int
        val selectedPalette: BeadPalette?
        val pixelArtGrid: Pair<Int, Int>?

        fun options(): GeneratorOptions
        suspend fun prepareSubjectMask(): SubjectMask?
        fun clearProjectPages()
        suspend fun saveAfterGenerate(result: PatternResult)
        fun applyTunedParams(filterLevel: FilterLevel, maxColors: Int?)
        fun navigateToPreview()
        fun reportNotReady(message: String)
    }

    /** 只写一次由 controller 管理；其它流程通过 AppViewModel 的私有 setter 间接写。 */
    var state by mutableStateOf<GenState>(GenState.Idle)
    var autoTuneInfo by mutableStateOf<String?>(null)
        private set
    var autoTuneScores by mutableStateOf<List<Pair<String, Int>>>(emptyList())
        private set
    var autoTuneRunning by mutableStateOf(false)
        private set

    private var generationJob: Job? = null
    private var autoTuneJob: Job? = null

    fun cancel() {
        generationJob?.cancel()
        generationJob = null
        autoTuneJob?.cancel()
        autoTuneJob = null
        autoTuneRunning = false
        if (state is GenState.Running) state = GenState.Idle
    }

    fun generate() {
        if (state is GenState.Running || autoTuneRunning) return
        val pixels = host.imagePixels ?: return
        val palette = host.selectedPalette ?: return
        val w = host.imageWidth
        val h = host.imageHeight
        if (w <= 0 || h <= 0 || pixels.size != w * h) {
            host.reportNotReady("源图尺寸未就绪，请重新导入")
            return
        }
        generationJob?.cancel()
        host.clearProjectPages()
        autoTuneInfo = null
        autoTuneScores = emptyList()
        state = GenState.Running(GenerationStage.DOWNSAMPLE, 0f)
        generationJob = scope.launch(Dispatchers.Default) {
            try {
                val options = host.options().copy(subjectMask = host.prepareSubjectMask())
                val result = PatternGenerator.generate(
                    pixels, w, h,
                    options,
                    palette,
                ) { stage, progress ->
                    state = GenState.Running(stage, progress)
                }
                state = GenState.Done(result)
                host.saveAfterGenerate(result)
                withContext(Dispatchers.Main) { host.navigateToPreview() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = GenState.Error(e.message ?: "生成失败")
            }
        }
    }

    /**
     * 自动优选：跑 4 个过滤档（未限色时再加一个 20 色档），按还原度评分取最高。
     * 选择写回 UI 参数，让用户能看见它替自己做了什么选择。
     */
    fun autoTune() {
        val pixels = host.imagePixels ?: return
        val palette = host.selectedPalette ?: return
        if (autoTuneRunning || state is GenState.Running) return
        val w = host.imageWidth
        val h = host.imageHeight
        if (w <= 0 || h <= 0 || pixels.size != w * h) {
            host.reportNotReady("源图尺寸未就绪，请重新导入")
            return
        }
        autoTuneJob?.cancel()
        val base = host.options()
        host.clearProjectPages()
        autoTuneRunning = true
        autoTuneInfo = null
        autoTuneScores = emptyList()
        state = GenState.Running(GenerationStage.DOWNSAMPLE, 0f)
        autoTuneJob = scope.launch(Dispatchers.Default) {
            try {
                val tunedBase = base.copy(subjectMask = host.prepareSubjectMask())
                val outcome = AutoTuner.sweep(
                    pixels, w, h, tunedBase, palette,
                    levels = if (host.pixelArtGrid != null) {
                        listOf(FilterLevel.NONE)
                    } else {
                        FilterLevel.entries.toList()
                    },
                    maxColorsOptions = if (host.pixelArtGrid != null) {
                        listOf(base.maxColors)
                    } else {
                        AutoTuner.defaultColorOptions(base)
                    },
                ) { done, total, _ ->
                    state = GenState.Running(GenerationStage.MAP, done.toFloat() / total)
                } ?: throw IllegalStateException("没有可用参数组合")

                val best = outcome.best
                host.applyTunedParams(best.filterLevel, best.maxColors)
                val baseline = outcome.all.firstOrNull {
                    it.filterLevel == base.filterLevel && it.maxColors == base.maxColors
                }
                val gain = if (baseline != null) best.score - baseline.score else 0
                autoTuneScores = FilterLevel.entries.mapNotNull { lvl ->
                    outcome.all.filter { it.filterLevel == lvl }
                        .maxByOrNull { it.score }
                        ?.let { lvl.label to it.score }
                }
                val qualityText = if (best.result.pixelArt) {
                    "逐像素还原 · 平均色差 %.1f · %d 色".format(
                        best.result.quality.meanDeltaE, best.result.quality.colorCount,
                    )
                } else {
                    best.result.quality.summary()
                }
                autoTuneInfo = "自动优选：${best.filterLevel.label} · " +
                    (best.maxColors?.let { "$it 色" } ?: "不限色") + " · " + qualityText +
                    if (gain > 0) " · 比原参数高 $gain 分" else ""
                state = GenState.Done(best.result)
                host.saveAfterGenerate(best.result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = GenState.Error(e.message ?: "自动优选失败")
            } finally {
                if (autoTuneJob === coroutineContext[Job]) {
                    autoTuneRunning = false
                    autoTuneJob = null
                }
            }
        }
    }
}
