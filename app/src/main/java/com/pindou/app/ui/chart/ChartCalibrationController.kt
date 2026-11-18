package com.pindou.app.ui.chart

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pindou.app.core.chart.ChartRecognizer
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.pattern.PatternResult
import com.pindou.app.ui.decodeScaled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 色号图校准子域控制器。
 *
 * 从 AppViewModel 原样抽出：字段全带 chart 前缀、Job/token 自包含，
 * 与生成/裁剪状态只有两处交接（导入清场、落图收尾），都通过 [host] 回调。
 * Compose state 留在 controller 内，ChartCalibrateScreen 仍按只读状态订阅。
 */
class ChartCalibrationController(
    private val scope: CoroutineScope,
    private val host: Host,
) {

    /** AppViewModel 提供的宿主能力，收窄到图表校准真正需要的几件事。 */
    interface Host {
        /** 导入前清掉上一张图的项目状态，并声明这次创建来源是色号图。 */
        fun startChartProject()

        /** 采样完成、准备进入校准页；[detectedGrid] = 自动识别是否成功。 */
        fun onChartPatternReadyForCalibration(detectedGrid: Boolean)

        /** 打开指定输入流；IOException 由 controller 统一转为错误文案。 */
        fun openChartInput(uri: Uri): java.io.InputStream?

        /** 当前选中的色卡；未选中时落图按钮不生效，与原逻辑一致。 */
        val selectedPalette: BeadPalette?

        /** 采样完成后落库；controller 不碰 repository。 */
        suspend fun saveChartResult(result: PatternResult)

        /** 落库后的导航与轻提示都交回宿主，避免这里持有 screen/toast。 */
        fun onChartPatternSaved()

        /** 重新识别成功后的提示文案由宿主统一管理。 */
        fun onChartRedetected(cols: Int, rows: Int)

        /** 重新识别失败：保留当前网格，让用户继续手调。 */
        fun onChartRedetectFailed()

        /** 采样没拿到颜色：不落图，提示用户调整网格。 */
        fun onChartEmptySample()

        /** 落图开始：宿主把 genState 切到 Running。 */
        fun onChartGenerationStarted()

        /** 图纸生成成功：宿主先写 genState.Done，再保存。 */
        fun onChartPatternGenerated(result: PatternResult)

        /** 落图失败：宿主写 genState.Error 与 toast。 */
        fun onChartPatternFailed(message: String)

        /** 校准页返回/导入入口复用宿主返回栈。 */
        fun onChartImportFailed(message: String)
    }

    /** 原图（校准页画布 + 网格线叠加用） */
    var bitmap by mutableStateOf<Bitmap?>(null)
        private set
    var pixels: IntArray? = null
        private set
    var width: Int = 0
        private set
    var height: Int = 0
        private set
    var grid by mutableStateOf<ChartRecognizer.ChartGrid?>(null)
        private set
    var sample by mutableStateOf<ChartRecognizer.ChartSample?>(null)
        private set

    /** 空白阈值：格内与纸白接近的像素占比达到该值即判空 */
    var threshold by mutableFloatStateOf(0.55f)
        private set
    var autoAligned by mutableStateOf(false)
        private set

    /** 用户是否手动改过网格（列/行/格距/位置），用于底部状态标签 */
    var manual by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var refineRunning by mutableStateOf(false)
        private set
    private var refineJob: Job? = null

    /** 拖动时只保留最后一次采样；和像素校准一样先取消旧任务再防抖 */
    private var sampleJob: Job? = null
    private var refineToken = 0

    /** 本次识别是否由自动检测得到的网格（false = 用了默认网格兜底） */
    var detected by mutableStateOf(false)
        private set
    private var sampleToken = 0

    /**
     * 导入别人做好的拼豆图纸（网格 + 每格颜色 + 色号文字）。
     *
     * 解码上限 2600px：色号图的价值全在格子里那几个字，压到 1600 以下 OCR 和
     * 取色都会糊。检测失败不拦用户——直接给一张居中的默认网格进校准页，
     * 手动调列数/格子大小也能落图。
     */
    fun onPicked(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val bmp = decodeScaled(
                    openInput = { runCatching { host.openChartInput(uri) }.getOrNull() },
                    maxSide = 2600,
                )
                val px = IntArray(bmp.width * bmp.height)
                bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                Triple(bmp, px, intArrayOf(bmp.width, bmp.height))
            }.onSuccess { (bmp, px, size) ->
                val w = size[0]
                val h = size[1]
                val found = ChartRecognizer.detect(px, w, h)
                val detectedGrid = found ?: ChartRecognizer.ChartGrid(
                    cols = 29,
                    rows = 29,
                    cellW = minOf(w, h) / 29.0,
                    cellH = minOf(w, h) / 29.0,
                    offsetX = (w - minOf(w, h)) / 2.0,
                    offsetY = (h - minOf(w, h)) / 2.0,
                )
                host.startChartProject()
                bitmap = bmp
                pixels = px
                width = w
                height = h
                grid = detectedGrid
                detected = found != null
                manual = false
                autoAligned = false
                threshold = 0.55f
                sample = null
                resample(debounce = false) {
                    host.onChartPatternReadyForCalibration(
                        detectedGrid = found != null,
                    )
                }
            }.onFailure { e ->
                host.onChartImportFailed(e.message ?: "图纸导入失败")
            }
        }
    }

    fun setCols(n: Int) {
        val g = grid ?: return
        cancelRefine()
        grid = g.withCols(n)
        manual = true
        autoAligned = false
        resample()
    }

    fun setRows(n: Int) {
        val g = grid ?: return
        cancelRefine()
        grid = g.withRows(n)
        manual = true
        autoAligned = false
        resample()
    }

    fun setCellSize(px: Float) {
        val g = grid ?: return
        cancelRefine()
        grid = g.withCellSize(px.toDouble())
        manual = true
        autoAligned = false
        resample()
    }

    /** 网格整体挪一格（不是一像素）：手指拖不准时用按钮对齐 */
    fun nudge(dx: Int, dy: Int) {
        val g = grid ?: return
        cancelRefine()
        grid = g.copy(
            offsetX = g.offsetX + dx * g.cellW,
            offsetY = g.offsetY + dy * g.cellH,
        )
        manual = true
        autoAligned = false
        resample()
    }

    /** 手指拖拽：按屏幕像素直接挪网格（换算在 UI 层做） */
    fun moveBy(dx: Float, dy: Float) {
        val g = grid ?: return
        cancelRefine()
        grid = g.copy(offsetX = g.offsetX + dx, offsetY = g.offsetY + dy)
        manual = true
        autoAligned = false
        resample()
    }

    fun updateThreshold(v: Float) {
        threshold = v.coerceIn(0.2f, 0.95f)
        resample()
    }

    /** 色号图智能对齐开关：关闭必须立即生效，不能等下一次拖动。 */
    fun setSmart(enabled: Boolean) {
        autoAligned = enabled
        if (enabled) {
            refine()
        } else {
            cancelRefine()
        }
    }

    /** 重新跑一次自动识别；失败时保留当前网格，让用户继续手调。 */
    fun redetect() {
        val px = pixels ?: return
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        cancelRefine()
        autoAligned = false
        busy = true
        scope.launch(Dispatchers.Default) {
            val found = ChartRecognizer.detect(px, w, h)
            withContext(Dispatchers.Main) {
                busy = false
                if (found != null) {
                    grid = found
                    detected = true
                    manual = false
                    host.onChartRedetected(found.cols, found.rows)
                    resample(debounce = false)
                } else {
                    detected = false
                    host.onChartRedetectFailed()
                }
            }
        }
    }

    /** 智能对齐：在梯度曲线上把网格线贴到真实网格线上 */
    fun refine() {
        val px = pixels ?: return
        val w = width
        val h = height
        val g = grid ?: return
        refineJob?.cancel()
        val token = ++refineToken
        refineRunning = true
        refineJob = scope.launch(Dispatchers.Default) {
            try {
                val refined = ChartRecognizer.refine(px, w, h, g)
                withContext(Dispatchers.Main) {
                    if (token != refineToken) return@withContext
                    grid = refined
                    manual = false
                    autoAligned = true
                }
                resample()
            } finally {
                withContext(Dispatchers.Main) {
                    if (token == refineToken) refineRunning = false
                }
            }
        }
    }

    /**
     * 重新采样 + 生成预览缩略图。网格每动一次都跑，属于热路径：
     * 采样 O(格数 × 格内像素/步长)，400×400 格在 2000px 图上是 16 万次取色，
     * 必须丢到 Default 线程。token 保证只有最后一次结果能落到 state。
     */
    private fun resample(debounce: Boolean = true, onDone: (() -> Unit)? = null) {
        val px = pixels ?: return
        val g = grid ?: return
        val w = width
        val h = height
        val th = threshold
        sampleJob?.cancel()
        sampleToken++
        val token = sampleToken
        // 拖动中不立刻切 busy：否则顶栏进度条每一帧开关一次，布局高度
        // 跟着变，pointerInput 会被重启，手感就是「咔咔」的。
        if (!debounce) busy = true
        sampleJob = scope.launch(Dispatchers.Default) {
            if (debounce) delay(40)
            if (token != sampleToken) return@launch
            withContext(Dispatchers.Main) {
                if (token == sampleToken) busy = true
            }
            val sampled = ChartRecognizer.sample(px, w, h, g, th)
            withContext(Dispatchers.Main) {
                if (token != sampleToken) return@withContext
                sample = sampled
                busy = false
                onDone?.invoke()
            }
        }
    }

    /** 校准完落图纸：采样色 → 色卡最近色 → 预览页 */
    fun toPattern() {
        val sampled = sample ?: return
        val palette = host.selectedPalette ?: return
        if (sampled.filledCells == 0) {
            host.onChartEmptySample()
            return
        }
        host.onChartGenerationStarted()
        scope.launch(Dispatchers.Default) {
            try {
                val result = ChartRecognizer.toPattern(sampled, palette)
                host.onChartPatternGenerated(result)
                host.saveChartResult(result)
                withContext(Dispatchers.Main) {
                    host.onChartPatternSaved()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    host.onChartPatternFailed(e.message ?: "图纸生成失败")
                }
            }
        }
    }

    /** startNewProject 清场时停掉校准任务，防止旧图采样结果写进新项目状态。 */
    fun reset() {
        refineJob?.cancel()
        sampleJob?.cancel()
        refineToken++
        sampleToken++
        refineRunning = false
        busy = false
    }

    private fun cancelRefine() {
        refineJob?.cancel()
        refineToken++
        refineRunning = false
    }
}
