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
import kotlinx.coroutines.CoroutineDispatcher
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
    /** 生成线程；单测注入 Unconfined，避免异步等待 */
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** 导航回主线程；单测注入 Unconfined，避免依赖 Android Main */
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
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

        /** 文字模式开场：textSource / 创建类型 / 网格尺寸，并清空多页 */
        fun beginTextGeneration(text: String, width: Int, height: Int)

        /**
         * 把文字栅格化为当前源图（像素、预览、原图缓冲、裁剪框）。
         * Bitmap 相关代码留在宿主；controller 只关心「源图是否就绪」。
         */
        suspend fun prepareTextSource(text: String, width: Int, height: Int): Boolean

        /** 文字生成完成：回创建页对应的预览 */
        fun navigateToTextPreview()
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
    private var textJob: Job? = null

    fun cancel() {
        generationJob?.cancel()
        generationJob = null
        autoTuneJob?.cancel()
        autoTuneJob = null
        textJob?.cancel()
        textJob = null
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
        generationJob = scope.launch(workDispatcher) {
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
                withContext(mainDispatcher) { host.navigateToPreview() }
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
        autoTuneJob = scope.launch(workDispatcher) {
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

    /**
     * 文字拼豆：栅格化后走与照片相同的生成管线。
     *
     * 以前这段生命周期在 VM 里另起一份 Job 和 Running/Done/Error 写入，
     * 取消时要三处手动 cancel。现在 generationJob / autoTuneJob / textJob
     * 都由 controller 持有，[cancel] 一次收干净。
     *
     * 文字画布尺寸就是成品尺寸：不自动取景、不抖动、不去背景，
     * 否则白边被裁掉或边缘被抖动成杂色，字会缺笔。
     */
    fun generateFromText(text: String, width: Int, height: Int) {
        if (text.isBlank()) return
        val palette = host.selectedPalette ?: return
        if (state is GenState.Running || autoTuneRunning) return
        val w = width.coerceIn(5, 200)
        val h = height.coerceIn(5, 200)
        textJob?.cancel()
        textJob = null
        generationJob?.cancel()
        generationJob = null
        autoTuneJob?.cancel()
        autoTuneJob = null

        host.beginTextGeneration(text, w, h)
        state = GenState.Running(GenerationStage.DOWNSAMPLE, 0f)
        textJob = scope.launch(workDispatcher) {
            try {
                if (!host.prepareTextSource(text, w, h)) {
                    state = GenState.Error("文字栅格化失败")
                    return@launch
                }
                val options = textOptions(host.options(), w, h)
                val pixels = host.imagePixels ?: error("文字源图未就绪")
                val result = PatternGenerator.generate(
                    pixels, host.imageWidth, host.imageHeight,
                    options,
                    palette,
                ) { stage, progress ->
                    state = GenState.Running(stage, progress)
                }
                state = GenState.Done(result)
                host.saveAfterGenerate(result)
                withContext(mainDispatcher) { host.navigateToTextPreview() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = GenState.Error(e.message ?: "文字生成失败")
            } finally {
                if (textJob === coroutineContext[Job]) {
                    textJob = null
                }
            }
        }
    }

    /**
     * 文字管线的参数：从当前 UI 参数复制，只覆盖文字必须固定的几项。
     *
     * 单列成函数是为了让「文字不抖动、不去背景、不自动取景」这条规则
     * 有 JVM 断言，而不是埋在协程体里只靠联调。
     */
    internal fun textOptions(base: GeneratorOptions, width: Int, height: Int): GeneratorOptions =
        base.copy(
            gridW = width,
            gridH = height,
            ditheringEnabled = false,
            removeBackground = false,
            backgroundTolerance = 12.0,
            autoFrame = false,
            exactArt = false,
            pixelGrid = null,
            subjectMask = null,
        )
}
