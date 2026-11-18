package com.pindou.app.ui.pixel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pindou.app.core.pattern.GeneratorOptions
import com.pindou.app.core.pattern.PixelCalibration
import com.pindou.app.core.pattern.PixelGridInspector
import com.pindou.app.core.pattern.PixelGridOps
import com.pindou.app.core.pattern.PixelImportPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 像素画网格校准子域控制器。
 *
 * 与色号图控制器同一条边界：状态、Job、token 全在这里；源图像素由
 * [Host] 提供（裁剪/导入会改它），落图后的导航与落库仍由宿主处理。
 */
class PixelCalibrationController(
    private val scope: CoroutineScope,
    private val host: Host,
) {

    interface Host {
        /** 当前源图像素；null = 没有进入校准流程。 */
        val imagePixels: IntArray?
        val imageWidth: Int
        val imageHeight: Int

        /** 当前选中色卡；null = 未加载。 */
        val currentPalette: com.pindou.app.core.palette.BeadPalette?

        /** 色卡未加载时先补一次；返回当前选中色卡。 */
        suspend fun ensurePalette(): com.pindou.app.core.palette.BeadPalette?

        /** 生成选项由 VM 汇总（网格、过滤、背景等），controller 不再读全局设置。 */
        fun generatorOptions(): GeneratorOptions

        /** 初始网格或重识别结果落到页面时同步画像素网格尺寸。 */
        fun onPixelGridPrepared(cols: Int, rows: Int)

        /** 生成进度写回宿主的 genState。 */
        fun onPixelProgress(stage: com.pindou.app.core.pattern.GenerationStage, progress: Float)

        /** 确认校准后的完整收尾（换源图、写 genState、落库、进编辑器）。 */
        suspend fun confirmPixelImport(
            grid: PixelCalibration.Grid,
            croppedPixels: IntArray,
            croppedWidth: Int,
            croppedHeight: Int,
            result: com.pindou.app.core.pattern.PatternResult,
            aligned: Boolean,
            smart: Boolean,
        )

        fun onPixelImportFailed(message: String)
    }

    var grid by mutableStateOf<PixelCalibration.Grid?>(null)
        private set

    /** 智能对齐开关：确认时先按格线梯度吸附，再裁出采样区域 */
    var smart by mutableStateOf(true)
        private set

    /** 智能对齐阈值：低于该格线强度就不动用户的网格 */
    var threshold by mutableFloatStateOf(PixelCalibration.DEFAULT_THRESHOLD)
        private set

    var score by mutableFloatStateOf(0f)
        private set

    var applied by mutableStateOf(false)
        private set

    var detected by mutableStateOf(false)
        private set

    /** 当前网格来自原生 1:1、周期检测，还是用户手动兜底 */
    var method by mutableStateOf(PixelGridInspector.Method.MANUAL)
        private set

    /** 当前网格的逐格采样结果：预览小图与占用/空白/颜色统计 */
    var analysis by mutableStateOf<PixelGridInspector.Analysis?>(null)
        private set

    /** 当前网格的最终图纸（已过色卡、背景移除与清理）。 */
    var preview by mutableStateOf<PixelImportPipeline.Conversion?>(null)
        private set

    var analysisRunning by mutableStateOf(false)
        private set

    var refineRunning by mutableStateOf(false)
        private set

    var busy by mutableStateOf(false)
        private set

    /** 用户确认过校准网格：后续几何变换不再让自动检测覆盖它 */
    var confirmed = false
        private set

    private var calibrationJob: Job? = null
    private var refineJob: Job? = null
    private var analysisJob: Job? = null
    private var analysisToken = 0

    /** 每次吸附自增；协程回来时 token 不同就丢弃结果，避免旧网格覆盖新网格 */
    private var refineToken = 0

    /** 新导入初始化：默认阈值 + 智能吸附 + 立即算预览。 */
    suspend fun initialize(pixels: IntArray, w: Int, h: Int) {
        smart = true
        // 每次新导入都从默认阈值开始：上一次把阈值拉高后，新图会
        // 看起来「智能对齐开着但没反应」。需要更严格时用户再调高。
        threshold = PixelCalibration.DEFAULT_THRESHOLD
        // 冷启动直接导入时色卡可能还没加载完；没有色卡就没有最终预览，
        // 用户点「看图纸」会看到空页。这里先补一次，代价可忽略。
        if (host.currentPalette == null) host.ensurePalette()
        val prepared = PixelImportPipeline.prepare(pixels, w, h, smart, threshold)
        withContext(Dispatchers.Main) { applyPrepared(prepared) }
        analyzeNow(pixels, w, h, prepared.grid, prepared.method)
    }

    private fun applyPrepared(prepared: PixelImportPipeline.Prepared) {
        grid = prepared.grid
        method = prepared.method
        detected = prepared.detected
        score = prepared.score
        applied = prepared.applied
        host.onPixelGridPrepared(prepared.grid.cols, prepared.grid.rows)
    }

    /**
     * 逐格采样 + 最终图纸预览。
     *
     * 两件事必须一起算：统计来自原始采样，预览来自色卡生成。拖动与滚轮
     * 连续触发，90ms 防抖；token 防止旧网格的慢结果覆盖新结果。
     */
    fun scheduleAnalysis(delayMs: Long = 90L) {
        val pixels = host.imagePixels ?: return
        val w = host.imageWidth
        val h = host.imageHeight
        val currentGrid = grid ?: return
        val currentMethod = method
        analysisJob?.cancel()
        val token = ++analysisToken
        analysisRunning = true
        analysisJob = scope.launch(Dispatchers.Default) {
            if (delayMs > 0L) delay(delayMs)
            val nextAnalysis = PixelGridInspector.analyze(pixels, w, h, currentGrid, currentMethod)
            val conversion = host.currentPalette?.let { palette ->
                runCatching {
                    PixelImportPipeline.convert(
                        pixels, w, h, currentGrid, palette, host.generatorOptions(),
                    )
                }.getOrNull()
            }
            withContext(Dispatchers.Main) {
                if (token == analysisToken) {
                    analysis = nextAnalysis
                    preview = conversion
                    analysisRunning = false
                }
            }
        }
    }

    /** 进页面/重新识别时同步算完，避免先显示旧网格再跳一下 */
    private suspend fun analyzeNow(
        pixels: IntArray,
        w: Int,
        h: Int,
        currentGrid: PixelCalibration.Grid,
        currentMethod: PixelGridInspector.Method,
    ) {
        analysisJob?.cancel()
        val token = ++analysisToken
        analysisRunning = true
        val nextAnalysis = PixelGridInspector.analyze(pixels, w, h, currentGrid, currentMethod)
        val conversion = host.currentPalette?.let { palette ->
            runCatching {
                PixelImportPipeline.convert(
                    pixels, w, h, currentGrid, palette, host.generatorOptions(),
                )
            }.getOrNull()
        }
        withContext(Dispatchers.Main) {
            if (token == analysisToken) {
                analysis = nextAnalysis
                preview = conversion
                analysisRunning = false
            }
        }
    }

    /**
     * 网格位置微调：一次移动 1 个源图像素。
     *
     * 允许网格滑出图片边缘（至少保留一格重叠），这样拖动才能把网格
     * 移到色块边界上。生成时 PixelImportPipeline 会按网格范围铺一张
     * 等大的画布，边缘外的格子保持透明，和屏幕上看到的网格一致。
     */
    fun nudge(dx: Double, dy: Double) {
        val currentGrid = grid ?: return
        grid = PixelGridOps.nudge(currentGrid, host.imageWidth, host.imageHeight, dx, dy)
        applied = false
        method = PixelGridInspector.Method.MANUAL
        scheduleAnalysis(delayMs = 40L)
    }

    /** 调整列数：格子大小、行数和位置都不动，网格整体变宽/变窄。 */
    fun setCols(cols: Int) {
        val old = grid ?: return
        grid = PixelGridOps.setCols(old, cols)
        applied = false
        method = PixelGridInspector.Method.MANUAL
        scheduleAnalysis()
    }

    /** 调整行数：格子大小、列数和位置都不动，网格整体变高/变矮。 */
    fun setRows(rows: Int) {
        val old = grid ?: return
        grid = PixelGridOps.setRows(old, rows)
        applied = false
        method = PixelGridInspector.Method.MANUAL
        scheduleAnalysis()
    }

    /** 调整一格大小：列数/行数不动，网格整体等比放大/缩小。 */
    fun setCellSize(px: Float) {
        val old = grid ?: return
        grid = PixelGridOps.setCellSize(old, px)
        applied = false
        method = PixelGridInspector.Method.MANUAL
        scheduleAnalysis()
    }

    /** 恢复自动检测的初值；检测失败则给覆盖整图的 32×32 兜底 */
    fun reset() {
        val pixels = host.imagePixels ?: return
        if (busy) return
        val w = host.imageWidth
        val h = host.imageHeight
        refineJob?.cancel()
        calibrationJob?.cancel()
        val token = ++refineToken
        refineRunning = true
        calibrationJob = scope.launch(Dispatchers.Default) {
            try {
                // 重新识别也要走完整流程：检测 + 默认智能吸附，不能只换网格
                // 让开关显示"开启"、实际没有吸附。
                val prepared = PixelImportPipeline.prepare(pixels, w, h, smart, threshold)
                withContext(Dispatchers.Main) { applyPrepared(prepared) }
                analyzeNow(pixels, w, h, prepared.grid, prepared.method)
            } finally {
                withContext(Dispatchers.Main) {
                    if (token == refineToken) refineRunning = false
                }
            }
        }
    }

    /** 用户在校准页点「重新识别」 */
    fun redetect() {
        if (busy) return
        reset()
    }

    fun updateSmart(enabled: Boolean) {
        smart = enabled
        refineJob?.cancel()
        if (enabled) {
            refine(applyToGrid = true, requireSmart = true)
        } else {
            // 关掉开关后，还在跑的吸附不许再写回网格；否则用户看到开关
            // 是关的，网格却突然跳一下。
            calibrationJob?.cancel()
            refineToken++
            refineRunning = false
            applied = false
            method = PixelGridInspector.Method.MANUAL
            // 立即按当前手动网格重算预览，让「关掉」有可见结果，
            // 也让后续「完成」严格使用屏幕上的线。
            scheduleAnalysis(delayMs = 0L)
        }
    }

    /** 阈值变化后自动重吸附；滑条会连续回调，220ms 防抖 */
    fun updateThreshold(value: Float) {
        threshold = value.coerceIn(0.05f, 0.90f)
        if (!smart) return
        refineJob?.cancel()
        refineJob = scope.launch {
            delay(220)
            refine(applyToGrid = true, requireSmart = true)
        }
    }

    /**
     * 智能对齐：在用户当前网格附近吸附。
     *
     * [applyToGrid] 为 true 时把吸附结果直接落到页面；开关打开、改阈值、
     * 点「自动对齐」都走这里，保证开关状态和实际网格一致。
     *
     * [requireSmart] 用于开关/阈值触发的吸附：协程返回时开关可能已经被
     * 关掉，此时丢弃结果，不覆盖用户手动摆好的网格。
     */
    private fun refine(applyToGrid: Boolean, requireSmart: Boolean = false) {
        val pixels = host.imagePixels ?: return
        val currentGrid = grid ?: return
        calibrationJob?.cancel()
        val token = ++refineToken
        refineRunning = true
        calibrationJob = scope.launch(Dispatchers.Default) {
            try {
                val alignment = PixelCalibration.refine(
                    pixels, host.imageWidth, host.imageHeight, currentGrid, threshold,
                )
                withContext(Dispatchers.Main) {
                    if (token != refineToken) return@withContext
                    if (requireSmart && !smart) return@withContext
                    score = alignment.score
                    applied = alignment.applied
                    if (applyToGrid && alignment.applied) {
                        grid = alignment.grid.withCompleteBlocks()
                        method = PixelGridInspector.Method.MANUAL
                        scheduleAnalysis(delayMs = 0L)
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    if (token == refineToken) refineRunning = false
                }
            }
        }
    }

    /** 用户在页面上主动点「自动对齐」 */
    fun autoAlign() {
        refine(applyToGrid = true)
    }

    /**
     * 确认校准：按校准网格裁出像素画本体，行列即图纸网格。
     *
     * 不再在确认时二次 refine：智能对齐在进页面、开开关、改阈值时就已经
     * 落到了网格上，用户看到哪张网格就按哪张生成。
     */
    fun confirm() {
        val pixels = host.imagePixels ?: return
        val initialGrid = grid ?: return
        val palette = host.currentPalette ?: return
        if (busy) return
        busy = true
        val w = host.imageWidth
        val h = host.imageHeight
        val options = host.generatorOptions()
        val wasAligned = applied
        scope.launch(Dispatchers.Default) {
            try {
                val conversion = PixelImportPipeline.convert(
                    pixels, w, h, initialGrid, palette, options,
                ) { stage, progress -> host.onPixelProgress(stage, progress) }
                host.confirmPixelImport(
                    grid = conversion.finalGrid,
                    croppedPixels = conversion.finalCropped.pixels,
                    croppedWidth = conversion.finalCropped.width,
                    croppedHeight = conversion.finalCropped.height,
                    result = conversion.finalResult,
                    aligned = wasAligned,
                    smart = smart,
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    host.onPixelImportFailed(e.message ?: e.javaClass.simpleName)
                }
            } finally {
                withContext(Dispatchers.Main) { busy = false }
            }
        }
    }

    /** 导入/清场时停掉全部任务，防止旧图的慢结果写进新状态。 */
    fun resetForNewImage() {
        calibrationJob?.cancel()
        refineJob?.cancel()
        analysisJob?.cancel()
        analysisToken++
        refineToken++
        grid = null
        detected = false
        applied = false
        score = 0f
        method = PixelGridInspector.Method.MANUAL
        analysis = null
        preview = null
        analysisRunning = false
        refineRunning = false
        busy = false
        confirmed = false
    }

    fun markConfirmed() {
        confirmed = true
    }

    fun clearConfirmed() {
        confirmed = false
    }

    fun clearAnalysis() {
        analysisJob?.cancel()
        analysisToken++
        analysis = null
        preview = null
        analysisRunning = false
    }

    fun clearAfterConfirm(grid: PixelCalibration.Grid) {
        this.grid = grid.copy(offsetX = 0.0, offsetY = 0.0)
        clearAnalysis()
        confirmed = true
    }
}
