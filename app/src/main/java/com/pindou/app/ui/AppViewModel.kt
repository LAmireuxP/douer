package com.pindou.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.palette.PaletteAssets
import com.pindou.app.core.build.ConstructionState
import com.pindou.app.core.data.GridCodec
import com.pindou.app.core.data.ProjectGridRepair
import com.pindou.app.core.data.ProjectSummary
import com.pindou.app.core.data.ProjectRepository
import com.pindou.app.core.data.SettingsStore
import com.pindou.app.core.editor.EditorEngine
import com.pindou.app.core.editor.ColorReplaceOps
import com.pindou.app.core.editor.PaletteOps
import com.pindou.app.core.editor.ShapeTool
import com.pindou.app.core.editor.SymmetryMode
import com.pindou.app.core.image.CropRect
import com.pindou.app.core.image.ImageTransforms
import com.pindou.app.core.chart.ChartRecognizer
import com.pindou.app.core.pattern.GenerationStage
import com.pindou.app.core.pattern.AiSubjectSegmenter
import com.pindou.app.core.pattern.BpatCodec
import com.pindou.app.core.pattern.GeneratorOptions
import com.pindou.app.core.pattern.AutoTuner
import com.pindou.app.core.pattern.FilterLevel
import com.pindou.app.core.pattern.PatternGenerator
import com.pindou.app.core.pattern.PatternResult
import com.pindou.app.core.pattern.PatternTransform
import com.pindou.app.core.pattern.PixelCalibration
import com.pindou.app.core.pattern.PixelArtDetector
import com.pindou.app.core.pattern.PixelGridInspector
import com.pindou.app.core.pattern.PixelImportPipeline
import com.pindou.app.core.pattern.SubjectMask
import com.pindou.app.core.studio.StudioCodec
import com.pindou.app.core.studio.StudioEngine
import com.pindou.app.core.studio.StudioFrame
import com.pindou.app.core.studio.StudioLayer
import com.pindou.app.core.settings.GlassAppearance
import com.pindou.app.ui.theme.AppThemes
import com.pindou.app.ui.pixel.PixelCalibrationController
import com.pindou.app.ui.library.ProjectLibraryController
import com.pindou.app.ui.studio.StudioPersistence
import com.pindou.app.ui.studio.StudioSession
import com.pindou.app.ui.chart.ChartCalibrationController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import java.io.File

class AppViewModel(application: Application) : AndroidViewModel(application) {

    var screen by mutableStateOf(Screen.HOME)
        private set

    var homeTab by mutableStateOf(HomeTab.LIBRARY)
        private set

    // 导入的图（像素供管线使用，缩略图供参数页显示）
    var imagePixels: IntArray? = null
        private set
    var imageW: Int = 0
        private set
    var imageH: Int = 0
        private set
    var imagePreview by mutableStateOf<Bitmap?>(null)
        private set
    var importError by mutableStateOf<String?>(null)
        private set

    // 原始导入图（裁剪页「重置」恢复用；变换不原地修改数组，别名共享是安全的）
    var originalPixels: IntArray? = null
        private set
    var originalW: Int = 0
        private set
    var originalH: Int = 0
        private set

    // ---------- 色号图识别（导入别人做好的网格图纸） ----------

    /**
     * 色号图子域控制器。转发属性保留 `vm.chart*` 旧 API，
     * ChartCalibrateScreen 零改动即可继续编译。
     */
    val chartController = ChartCalibrationController(viewModelScope, ChartHost())

    val chartBitmap: Bitmap? get() = chartController.bitmap
    val chartPixels: IntArray? get() = chartController.pixels
    val chartW: Int get() = chartController.width
    val chartH: Int get() = chartController.height
    val chartGrid: ChartRecognizer.ChartGrid? get() = chartController.grid
    val chartSample: ChartRecognizer.ChartSample? get() = chartController.sample
    val chartThreshold: Float get() = chartController.threshold
    val chartAutoAligned: Boolean get() = chartController.autoAligned
    val chartManual: Boolean get() = chartController.manual
    val chartBusy: Boolean get() = chartController.busy
    val chartRefineRunning: Boolean get() = chartController.refineRunning
    val chartDetected: Boolean get() = chartController.detected

    /** 裁剪框（工作图像素坐标系），null = 不在裁剪流程中 */
    var cropRect by mutableStateOf<CropRect?>(null)
        private set
    private var cropReturn = Screen.HOME

    /**
     * 用户是否显式点过「原图」预设。
     *
     * 不能再用「选区是否等于整图」来反推选中态：导入时默认就是整图，
     * 那样「原图」会一直高亮，而用户实际上想要的是自由裁剪。
     */
    var cropOriginalPreset by mutableStateOf(false)
        private set

    /** 几何变换执行中标志（防止连点导致的状态竞争） */
    private var transformRunning = false

    // ---------- 裁剪页实时网格预览 ----------

    /** 裁剪页是否把每格画成最终色号（关掉只剩框线与辅助线） */
    var cropPreviewEnabled by mutableStateOf(true)
        private set

    var cropPreview by mutableStateOf<CropGridPreview?>(null)
        private set

    /** 正在算预览（防抖结束 + 管线一次） */
    var cropPreviewRunning by mutableStateOf(false)
        private set

    private var cropPreviewJob: Job? = null
    private var cropPreviewToken = 0
    private var generationJob: Job? = null
    private var autoTuneJob: Job? = null

    /**
     * 用户在裁剪页显式定过网格：裁剪完成后不再被「像素画原画网格」覆盖。
     * 否则 56×50 的图改成 78×70 再裁，confirmCrop 里检测一次又跳回原画，
     * 用户会觉得定的尺寸没生效。
     */
    private var cropGridUserSet = false

    /** 参数页返回目标：true = 从裁剪页确认过来，返回时回裁剪页 */
    private var paramsFromCrop = false

    /**
     * 预览页的返回目标。
     *
     * 生成流程回参数页；项目库编辑、文字生成回首页。之前写死 PARAMS，
     * 项目库图纸编辑完再返回就会掉进一个没有源图、什么都不能做的参数页。
     */
    var previewReturn by mutableStateOf(Screen.HOME)
        private set
    /** 预览返回 HOME 时落在哪个首页 tab；null 按项目库处理。 */
    var previewReturnTab by mutableStateOf<HomeTab?>(null)
        private set

    /** 预览页是否还允许回参数页调整（只有从参数页来的生成结果才有源图） */
    val canAdjustParams: Boolean
        get() = previewReturn == Screen.PARAMS && imagePixels != null && textSource == null

    /** 当前裁剪选区模式；默认自由 */
    var cropAspectMode by mutableStateOf(CropAspectMode.FREE)
        private set

    /** 本次编辑是否由项目库打开（决定编辑完成后预览返回哪里） */
    private var editorFromLibrary = false

    // ---------- 像素画网格校准 ----------

    /** 像素画校准子域控制器；只读转发属性保留 `vm.pixel*` 旧 API。 */
    private val pixelController = PixelCalibrationController(viewModelScope, PixelHost())

    val pixelCalibration: PixelCalibration.Grid? get() = pixelController.grid
    val pixelCalibrationSmart: Boolean get() = pixelController.smart
    val pixelCalibrationThreshold: Float get() = pixelController.threshold
    val pixelCalibrationScore: Float get() = pixelController.score
    val pixelCalibrationApplied: Boolean get() = pixelController.applied
    val pixelCalibrationDetected: Boolean get() = pixelController.detected
    val pixelCalibrationMethod: PixelGridInspector.Method get() = pixelController.method
    val pixelGridAnalysis: PixelGridInspector.Analysis? get() = pixelController.analysis

    /**
     * 当前网格的最终图纸（已过色卡、背景移除与清理）。
     *
     * 「看图纸」直接铺它的色号，顶部「完成」也复用它，保证预览和成品
     * 是同一张图，而不是预览一套采样色、生成另一套色卡色。
     */
    val pixelCalibrationPreview: PixelImportPipeline.Conversion? get() = pixelController.preview
    val pixelAnalysisRunning: Boolean get() = pixelController.analysisRunning
    val pixelRefineRunning: Boolean get() = pixelController.refineRunning
    val pixelCalibrationBusy: Boolean get() = pixelController.busy

    // 项目库与设置
    private val repository = ProjectRepository(application)
    private val libraryController = ProjectLibraryController(
        viewModelScope,
        repository,
        LibraryHost(),
    )
    val settingsStore = SettingsStore(application)

    /** 当前正在编辑的项目 id（null = 尚未保存的新图纸） */
    var currentProjectId by mutableStateOf<Long?>(null)
        private set

    var projectName by mutableStateOf("未命名图纸")

    // 列表只订阅轻量投影；整行（含 cells BLOB）在 openProject / duplicate 时按 id 读取
    val projects = repository.observeSummaries().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    var settings by mutableStateOf(SettingsStore.Snapshot())
        private set

    /** 累计生成统计（从 1.2.3 起记录） */
    var stats by mutableStateOf(SettingsStore.Stats())
        private set

    /**
     * 当前这次新建的来源。
     *
     * 只在真正落库时消费，所以用户中途取消裁剪、返回首页都不会污染统计。
     * 每次 startNewProject 重置为 PHOTO，各入口在重置之后再覆盖。
     */
    private var pendingCreationKind = SettingsStore.CreationKind.PHOTO

    var toast by mutableStateOf<String?>(null)

    /**
     * 删除后的撤销窗口。
     *
     * 回调放在内存里而不是序列化：撤销只跨一次 Snackbar 展示（10 秒），
     * 进程被杀时本来也不该再恢复一份已经删掉的图纸。
     */
    class DeleteUndo(
        val message: String,
        val onUndo: suspend () -> Unit,
        val onExpired: suspend () -> Unit,
    )

    var deleteUndo by mutableStateOf<DeleteUndo?>(null)
        private set

    fun undoDelete() {
        val pending = deleteUndo ?: return
        deleteUndo = null
        viewModelScope.launch {
            runCatching { pending.onUndo() }
                .onSuccess { toast = "已恢复" }
                .onFailure { lastError = "恢复失败：${it.message ?: it.javaClass.simpleName}" }
        }
    }

    /** Snackbar 超时/被顶掉：确认删除，清理 sidecar 文件。 */
    fun commitDelete() {
        val pending = deleteUndo ?: return
        deleteUndo = null
        viewModelScope.launch { runCatching { pending.onExpired() } }
    }

    /** 最近一次操作失败原因（常驻显示，便于排查与反馈） */
    var lastError by mutableStateOf<String?>(null)

    // 色卡
    var palettes by mutableStateOf<List<BeadPalette>>(emptyList())
        private set
    var selectedPalette by mutableStateOf<BeadPalette?>(null)
        private set

    // 参数
    var gridW by mutableIntStateOf(29)
    var gridH by mutableIntStateOf(29)

    /**
     * 像素画原图的画像素网格（null = 当前图不是像素画 / 或尚未检测）。
     *
     * 有这个值的时候，网格尺寸不应该由用户随手输入——56×50 的原画配
     * 56×50 之外的网格，ArtQuantizer 会把每个画像素拉伸成不等宽块，
     * 花瓣的圆弧就会变成台阶。UI 优先展示「原画网格」一键套用。
     */
    var pixelArtGrid by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    /**
     * 用户从「像素画生成」入口导入：强制像素支路。
     *
     * 与自动检测分开的原因：自动检测宁漏勿错（照片被认成像素画，整张图
     * 会碎成色块），所以门槛高；用户显式声明"这是像素画"时，宁可放宽到
     * 兜底算法也要走像素支路，否则入口点了跟「图片生成」没有任何区别。
     */
    var pixelArtMode by mutableStateOf(false)
        private set

    /** 自定义宽高时是否联动另一轴（默认开，防止宽高独立输入把图拉扁） */
    var lockGridAspect by mutableStateOf(true)
        private set
    var limitColors by mutableStateOf(false)
    var maxColors by mutableIntStateOf(24)
    var dithering by mutableStateOf(false)
    var removeBackground by mutableStateOf(false)
    /** 自动贴着主体取景：空白边占的格子等于白扔的豆子，默认开 */
    var autoFrame by mutableStateOf(true)
    var backgroundTolerance by mutableFloatStateOf(12f)
    // 过滤等级：四档，默认中过滤
    var filterLevel by mutableStateOf(com.pindou.app.core.pattern.FilterLevel.MEDIUM)

    var genState by mutableStateOf<GenState>(GenState.Idle)
        private set

    /** 自动优选结果说明（"自动优选：高过滤 · 还原度 79 …"），null = 未跑过 */
    var autoTuneInfo by mutableStateOf<String?>(null)
        private set

    /**
     * 自动优选的逐档得分（过滤档名 → 该档最高分，按档位顺序）。
     * 展示四档分数，便于判断档位之间的差距。
     */
    var autoTuneScores by mutableStateOf<List<Pair<String, Int>>>(emptyList())
        private set

    /** 自动优选进行中（要多跑几组参数，UI 单独禁用按钮） */
    var autoTuneRunning by mutableStateOf(false)
        private set

    // 施工模式
    var construction by mutableStateOf<ConstructionState?>(null)

    // 多页图纸：编辑器内产生页；空列表 = 只有 genState 里的单页结果
    var projectPages: List<IntArray> = emptyList()
        private set

    // 参考图对比：原图叠加显示在格子上，透明度可调
    var refBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var showRefOverlay by mutableStateOf(false)
    var refOpacity by mutableFloatStateOf(0.5f)

    // 项目库筛选
    var libraryQuery by mutableStateOf("")
    var libraryFavOnly by mutableStateOf(false)
    /** null = 全部；否则按专辑名过滤 */
    var libraryAlbum by mutableStateOf<String?>(null)
    /** 项目库多选模式：为 true 时首页隐藏玻璃底栏，由项目库接管底部操作条 */
    var librarySelecting by mutableStateOf(false)

    // 编辑器
    var editor by mutableStateOf<EditorEngine?>(null)
        private set
    var editorTool by mutableStateOf(EditorTool.NONE)
    /** 从创建页「画像素」进入：编辑器顶栏显示模块名，不改变底层图纸模型 */
    var pixelModuleActive by mutableStateOf(false)
    /** 橡皮的「区域擦除」模式：点按整块 4 连通同色区域；拖动仍是方形橡皮 */
    var editorEraseRegion by mutableStateOf(false)
    /** 画笔/橡皮的镜像轴：关 / 水平 / 垂直 / 中心 */
    var editorSymmetry by mutableStateOf(SymmetryMode.NONE)
    /** 形状工具类型、是否填充、第一点（null = 等待起点） */
    var editorShape by mutableStateOf(ShapeTool.LINE)
    var editorShapeFilled by mutableStateOf(false)
    var editorShapeStart by mutableStateOf<Int?>(null)
    var editorColor by mutableIntStateOf(0) // palette 索引
    /** 方形笔刷边长：1 / 3 / 5 格 */
    var editorBrushSize by mutableIntStateOf(1)
    /** 最近使用色（调色板索引），只保留 8 个，最新的在最前 */
    val editorRecentColors = mutableStateListOf<Int>()
    var editorShowCodes by mutableStateOf(true)
    var editorShowGrid by mutableStateOf(true)
    var editorRevision by mutableIntStateOf(0)
        private set

    // ---------- 画像素工作区（帧/图层/选区/动画） ----------

    var studio by mutableStateOf<StudioSession?>(null)
        private set
    var studioProjectId by mutableStateOf<Long?>(null)
        private set
    private var studioSaving = false
    /** 画像素返回键：true = 回预览，false = 回项目库。 */
    private var studioExitToPreview = false
    private var studioBackTab = HomeTab.LIBRARY

    // AI 主体分割结果缓存：以源像素数组身份 + 尺寸为键。
    // 生成一次后重复调参不再重新跑模型，避免每次调色都等几秒。
    private var aiMaskKey: IntArray? = null
    private var aiMaskW = 0
    private var aiMaskH = 0
    private var aiMask: SubjectMask? = null
    var aiMaskStatus by mutableStateOf<String?>(null)
        private set

    fun notifyEditorChanged() {
        editorRevision++
    }

    /**
     * 取当前图的主体掩码，供生成管线裁边与去背景共用。
     *
     * 只在用户打开「AI 实验」开关时跑；模型缺失、设备不支持、GMS 不可用
     * 都返回 null，管线自动落回原来的边界色算法，不阻塞生成。
     */
    private suspend fun prepareAiMask(): SubjectMask? {
        if (!settings.aiSubjectEnabled) {
            aiMaskStatus = null
            return null
        }
        val pixels = imagePixels ?: return null
        if (aiMaskKey === pixels && aiMaskW == imageW && aiMaskH == imageH) {
            return aiMask
        }
        aiMaskStatus = "AI 正在识别主体…"
        val bitmap = try {
            Bitmap.createBitmap(pixels, imageW, imageH, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            aiMaskStatus = "AI 不可用，已回退传统算法"
            return null
        }
        val mask = try {
            AiSubjectSegmenter.segment(bitmap)
        } finally {
            bitmap.recycle()
        }
        if (mask == null) {
            // 首次模型下载可能临时失败；不写缓存，下一次生成仍会重试。
            aiMaskStatus = "AI 不可用，已回退传统算法"
            return null
        }
        aiMaskKey = pixels
        aiMaskW = imageW
        aiMaskH = imageH
        aiMask = mask
        aiMaskStatus = "AI 主体识别完成"
        return mask
    }

    /** 统一入口：选色后自动切回画笔，并记录最近使用 */
    fun selectEditorColor(index: Int) {
        val size = selectedPalette?.colors?.size ?: return
        if (size == 0) return
        val safe = index.coerceIn(0, size - 1)
        editorColor = safe
        editorRecentColors.remove(safe)
        editorRecentColors.add(0, safe)
        while (editorRecentColors.size > 8) editorRecentColors.removeAt(editorRecentColors.lastIndex)
        editorTool = EditorTool.PAINT
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = PaletteAssets.loadAll(application)
            palettes = loaded
            selectedPalette = loaded.firstOrNull { it.brand == "MARD" } ?: loaded.firstOrNull()
        }
        viewModelScope.launch {
            settingsStore.flow.collect { snapshot ->
                settings = snapshot
                // 首次加载时应用默认参数（未生成过图纸）
                if (genState is GenState.Idle && imagePixels == null) {
                    gridW = snapshot.defaultGridW
                    gridH = snapshot.defaultGridH
                    palettes.firstOrNull { it.brand == snapshot.defaultPaletteBrand }?.let {
                        selectedPalette = it
                    }
                }
            }
        }
        viewModelScope.launch {
            settingsStore.statsFlow.collect { stats = it }
        }
    }

    fun palettesFor(beadSizeTag: String): List<BeadPalette> =
        palettes.filter { it.beadSize.name == beadSizeTag }

    /** 换图/离开当前生成流程时，先把旧的后台生成停掉，避免旧结果落库。 */
    private fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        autoTuneJob?.cancel()
        autoTuneJob = null
        autoTuneRunning = false
        if (genState is GenState.Running) genState = GenState.Idle
    }

    /** Photo Picker / GetContent 回调：解码并限制最大边 2048 */
    fun onImagePicked(uri: Uri, pixelMode: Boolean = false) {
        cancelGeneration()
        pendingCreationKind = if (pixelMode) {
            SettingsStore.CreationKind.PIXEL
        } else {
            SettingsStore.CreationKind.PHOTO
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                decodeImage(getApplication(), uri)
            }.onSuccess { decoded ->
                val bmp = decoded.bitmap
                val pixels = decoded.pixels
                val size = intArrayOf(decoded.w, decoded.h)
                // 像素画检测放在导入线程里做（O(源像素)，200 条抽样线），
                // 检测结果决定默认裁剪和默认网格，必须在进入裁剪页之前拿到。
                val art = if (pixelMode) {
                    PixelArtDetector.detectForced(pixels, size[0], size[1])
                } else {
                    PixelArtDetector.detect(pixels, size[0], size[1])
                }
                pixelArtMode = pixelMode
                pixelArtGrid = art?.let { it.artW to it.artH }
                pixelController.resetForNewImage()
                // 像素画原图默认「无过滤」：不聚类、不限色，逐画像素直接映射
                // 色卡，源图每一层淡粉/淡黄都能落成独立色号。照片维持中过滤。
                filterLevel = if (pixelMode || art != null) FilterLevel.NONE else FilterLevel.MEDIUM
                originalPixels = pixels
                originalW = size[0]
                originalH = size[1]
                imagePreview = bmp
                imagePixels = pixels
                imageW = size[0]
                imageH = size[1]
                cropAspectMode = CropAspectMode.FREE
                cropOriginalPreset = false
                // 导入时默认保留整张图。自由模式下网格会跟随选区比例，
                // 不再用中心方框去猜主体，也不会出现「方框配长格」的拉伸。
                cropRect = if (art != null) {
                    val l = art.ox.toFloat().coerceIn(0f, size[0] - 1f)
                    val t = art.oy.toFloat().coerceIn(0f, size[1] - 1f)
                    val r = (art.ox + art.artW * art.blockW).toFloat().coerceIn(l + 1f, size[0].toFloat())
                    val b = (art.oy + art.artH * art.blockH).toFloat().coerceIn(t + 1f, size[1].toFloat())
                    CropRect(l, t, r, b)
                } else {
                    CropRect.full(size[0], size[1])
                }
                if (art != null) {
                    gridW = art.artW.coerceIn(5, 200)
                    gridH = art.artH.coerceIn(5, 200)
                }
                cropGridUserSet = false
                cropPreview = null
                paramsFromCrop = false
                adaptiveAnchor = 0
                adaptiveScale = 1f
                importError = null
                genState = GenState.Idle
                // 无论照片还是像素画，都先让用户确认画布范围。
                // 像素画只是不显示网格着色预览：它的网格在下一步专门校准，
                // 裁剪页再叠一层格线只会让用户分不清该调哪一层。
                syncCropGridToRect(cropRect!!)
                if (pixelMode) {
                    // 像素画流程不再经过「原图/自由/正方形」预选页：
                    // 导入后直接进校准，整张图就是初始网格范围。
                    cropPreviewJob?.cancel()
                    cropPreview = null
                    cropPreviewRunning = false
                    pixelController.initialize(pixels, size[0], size[1])
                    withContext(Dispatchers.Main) {
                        screen = Screen.PIXEL_CALIBRATE
                    }
                } else {
                    cropReturn = Screen.HOME
                    screen = Screen.CROP
                    scheduleCropPreview()
                }
            }.onFailure { e ->
                importError = e.message ?: "导入失败"
            }
        }
    }

    /**
     * 内置示例图：assets 里的样图落到缓存文件后走同一条导入链路。
     *
     * 不复制导入逻辑——示例图和用户照片必须走同一个解码/检测/裁剪入口，
     * 否则示例能跑通、真机导入出问题的情况会漏测。
     */
    // ---------- 裁剪与几何变换 ----------

    private fun launchTransform(block: suspend () -> Unit) {
        if (transformRunning) return
        transformRunning = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                block()
            } finally {
                transformRunning = false
            }
        }
    }

    private fun rebuildPreview() {
        val p = imagePixels ?: return
        if (imageW <= 0 || imageH <= 0) return
        imagePreview = Bitmap.createBitmap(p, imageW, imageH, Bitmap.Config.ARGB_8888)
    }

    /**
     * 几何变换后重新识别像素画网格。
     *
     * 裁剪/旋转会换掉像素数组（检测器的缓存按数组实例失效），画像素网格
     * 也可能变（裁掉一圈后 56×50 会变成 54×48）。识别到就把网格同步成
     * 原画尺寸，用户不必自己数格子。
     */
    private fun refreshPixelArt(pixels: IntArray, w: Int, h: Int, forceGrid: Boolean = true) {
        // 用户在像素校准页确认过的网格优先：再次自动检测会把
        // 他们手动修过的列/行/偏移覆盖掉，这是之前"校准等于没校准"的根因。
        if (pixelController.confirmed) return
        val art = if (pixelArtMode) {
            PixelArtDetector.detectForced(pixels, w, h)
        } else {
            PixelArtDetector.detect(pixels, w, h)
        }
        pixelArtGrid = art?.let { it.artW to it.artH }
        if (art != null) {
            filterLevel = FilterLevel.NONE
            // forceGrid=false：用户在裁剪页已经定过网格，尊重他们的选择
            if (forceGrid) {
                gridW = art.artW.coerceIn(5, 200)
                gridH = art.artH.coerceIn(5, 200)
            }
            adaptiveAnchor = 0
            adaptiveScale = 1f
        } else if (pixelArtMode) {
            filterLevel = FilterLevel.NONE
        }
    }

    /** 进入裁剪页（参数页「重新裁剪」入口；导入后自动进入，不经此函数） */
    fun openCrop() {
        if (imagePixels == null || imageW <= 0 || imageH <= 0) return
        cropAspectMode = CropAspectMode.FREE
        cropOriginalPreset = false
        // 重新裁剪从整张图开始，用户再按主体框选；默认不再替用户切方图
        cropRect = CropRect.full(imageW, imageH)
        cropGridUserSet = false
        syncCropGridToRect(cropRect!!)
        cropReturn = Screen.PARAMS
        screen = Screen.CROP
        scheduleCropPreview()
    }

    fun updateCropRect(rect: CropRect) {
        cropRect = rect
        // 手动拖过选框就不再算「原图」预设
        cropOriginalPreset = false
        if (cropAspectMode == CropAspectMode.FREE) syncCropGridToRect(rect)
        scheduleCropPreview()
    }

    /** 显式选择「原图」：整图入框，并记住这个高亮状态 */
    fun selectCropOriginal() {
        if (imageW <= 0 || imageH <= 0) return
        cropAspectMode = CropAspectMode.FREE
        cropRect = CropRect.full(imageW, imageH)
        cropOriginalPreset = true
        syncCropGridToRect(cropRect!!)
        scheduleCropPreview()
    }

    /** 切换自由 / 正方形；正方形立刻把选区和网格都收成 1:1 */
    fun changeCropAspectMode(mode: CropAspectMode) {
        if (cropAspectMode == mode) return
        cropOriginalPreset = false
        cropAspectMode = mode
        if (imageW <= 0 || imageH <= 0) return
        when (mode) {
            CropAspectMode.SQUARE -> {
                cropRect = cropRect
                    ?.let { ImageTransforms.squareAround(it, imageW.toFloat(), imageH.toFloat()) }
                    ?: ImageTransforms.centerSquare(imageW, imageH)
                val side = max(gridW, gridH)
                gridW = side
                gridH = side
            }
            CropAspectMode.FREE -> {
                cropRect?.let { syncCropGridToRect(it) }
            }
        }
        scheduleCropPreview()
    }

    /**
     * 自由裁剪的根修复：网格宽高比永远跟着选区走。
     *
     * 降采样按宽、高独立映射，选区 4:3、网格 1:1 就会把主体压扁。
     * 这里锚定长边重算另一边，选区怎么拖都不会拉伸；正方形模式
     * 由 [setCropAspectMode] 独立锁死 1:1。
     */
    private fun syncCropGridToRect(rect: CropRect) {
        if (cropAspectMode != CropAspectMode.FREE) return
        if (rect.width <= 0f || rect.height <= 0f) return
        val anchor = max(gridW, gridH)
        val (w, h) = ImageTransforms.gridSizeForAspect(rect.width / rect.height, anchor)
        gridW = w
        gridH = h
    }

    // ---------- 裁剪页：先定网格，再看每格颜色 ----------

    fun toggleCropPreview() {
        cropPreviewEnabled = !cropPreviewEnabled
        if (cropPreviewEnabled) {
            scheduleCropPreview()
        } else {
            cropPreviewJob?.cancel()
            cropPreview = null
            cropPreviewRunning = false
        }
    }

    fun setCropGridWidth(w: Int) {
        cropGridUserSet = true
        val cw = w.coerceIn(5, 200)
        if (cropAspectMode == CropAspectMode.SQUARE) {
            gridW = cw
            gridH = cw
        } else {
            val rect = cropRect
            val aspect = if (rect != null && rect.height > 0f) rect.width / rect.height else 1f
            gridW = cw
            gridH = (cw / aspect).roundToInt().coerceIn(5, 200)
            // 短边被下限/上限钳制时反推宽，保持比例精确，宁可格数差一格也不拉伸
            gridW = (gridH * aspect).roundToInt().coerceIn(5, 200)
        }
        adaptiveAnchor = 0
        adaptiveScale = 1f
        scheduleCropPreview()
    }

    fun setCropGridHeight(h: Int) {
        cropGridUserSet = true
        val ch = h.coerceIn(5, 200)
        if (cropAspectMode == CropAspectMode.SQUARE) {
            gridW = ch
            gridH = ch
        } else {
            val rect = cropRect
            val aspect = if (rect != null && rect.height > 0f) rect.width / rect.height else 1f
            gridH = ch
            gridW = (ch * aspect).roundToInt().coerceIn(5, 200)
            gridH = (gridW / aspect).roundToInt().coerceIn(5, 200)
        }
        adaptiveAnchor = 0
        adaptiveScale = 1f
        scheduleCropPreview()
    }

    fun setCropGridPreset(w: Int, h: Int) {
        cropGridUserSet = true
        setGridSize(w, h)
        scheduleCropPreview()
    }

    fun applyArtGridAtCrop() {
        cropGridUserSet = true
        applyArtGrid()
        cropRect?.let { syncCropGridToRect(it) }
        scheduleCropPreview()
    }

    /**
     * 请求一次裁剪网格预览。
     *
     * 拖动时 updateCropRect 每帧都会进来，这里用 160ms 防抖 + 递增 token：
     * 只有最后一次请求的结果会被写回状态，中间那些算完就丢。管线本身是纯
     * Kotlin（无 Bitmap 锁），可以放心在 Default 线程跑。
     */
    private fun scheduleCropPreview() {
        if (screen != Screen.CROP || !cropPreviewEnabled) return
        val pixels = imagePixels ?: return
        val rect = cropRect ?: return
        val token = ++cropPreviewToken
        cropPreviewJob?.cancel()
        cropPreviewJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                delay(160)
                if (token != cropPreviewToken) return@launch
                // 色卡是异步加载的：导入后立刻排预览时 selectedPalette 可能还是 null，
                // 这里等一下加载完成再算，否则预览永远不出现（裁剪页只剩空网格）。
                val loaded = ensurePalettes()
                val palette = selectedPalette
                    ?: loaded.firstOrNull { it.brand == "MARD" }
                    ?: loaded.firstOrNull()
                    ?: return@launch
                cropPreviewRunning = true
                val sub = ImageTransforms.crop(pixels, imageW, imageH, rect)
                // 与最终生成完全同一套参数：预览色号 = 成品色号，
                // 不会出现"裁的时候好看、生成完变样"。
                val res = PatternGenerator.generate(
                    sub.pixels, sub.width, sub.height, currentOptions(), palette,
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
                        // 背景移除掉的格子保持透明，底图透出来才知道哪块被判成背景
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
                // 预览失败不打断裁剪；正式生成会重新报错。留日志便于定位静默失败。
                android.util.Log.w("PindouCrop", "裁剪预览生成失败", e)
            } finally {
                if (token == cropPreviewToken) cropPreviewRunning = false
            }
        }
    }

    /** 旋转 90°：即时作用于工作图，裁剪框随图像同步变换 */
    fun rotateImage(clockwise: Boolean) {
        val pixels = imagePixels ?: return
        val w = imageW
        val h = imageH
        val rect = cropRect
        launchTransform {
            val t = ImageTransforms.rotate90(pixels, w, h, clockwise)
            imagePixels = t.pixels
            imageW = t.width
            imageH = t.height
            if (rect != null) cropRect = ImageTransforms.rotateRect(rect, w, h, clockwise)
            rebuildPreview()
            refreshPixelArt(t.pixels, t.width, t.height)
            scheduleCropPreview()
        }
    }

    /** 镜像：即时作用于工作图，裁剪框随图像同步变换 */
    fun mirrorImage(horizontal: Boolean) {
        val pixels = imagePixels ?: return
        val w = imageW
        val h = imageH
        val rect = cropRect
        launchTransform {
            val t = ImageTransforms.mirror(pixels, w, h, horizontal)
            imagePixels = t.pixels
            imageW = t.width
            imageH = t.height
            if (rect != null) cropRect = ImageTransforms.mirrorRect(rect, w, h, horizontal)
            rebuildPreview()
            refreshPixelArt(t.pixels, t.width, t.height)
            scheduleCropPreview()
        }
    }

    /** 重置全部几何变换，恢复刚导入时的原图 */
    fun resetImageTransforms() {
        val pixels = originalPixels ?: return
        val w = originalW
        val h = originalH
        launchTransform {
            imagePixels = pixels
            imageW = w
            imageH = h
            cropRect = CropRect.full(w, h)
            rebuildPreview()
            refreshPixelArt(pixels, w, h)
            scheduleCropPreview()
        }
    }

    /**
     * 像素模式：用当前裁剪框（或整图）初始化校准网格。
     *
     * 自动检测必须在裁剪后的图上重跑：整图里的背景留白会把单格尺寸
     * 算大，进校准页时线全都压在色块中间。检测失败就用覆盖整图的
     * 32×32 兜底，至少让用户有东西可拖。
     *
     * 智能对齐默认开着，所以这里直接在进页面之前吸附一次，并把预览
     * 算完再切屏——用户第一眼看到的就是最终网格和最终图纸。
     */
    /** 确认裁剪：像素画进校准页，照片进参数页 */
    fun confirmCrop() {
        val pixels = imagePixels ?: return
        val rect = cropRect ?: return
        val w = imageW
        val h = imageH
        // 最后一帧再同步一次：拖动中和确认之间不留下比例差
        syncCropGridToRect(rect)
        val isFull = rect.left <= 0f && rect.top <= 0f &&
            rect.right >= w.toFloat() && rect.bottom >= h.toFloat()
        if (isFull) {
            paramsFromCrop = true
            screen = Screen.PARAMS
            return
        }
        launchTransform {
            // 裁切会改变画像素网格的起点和列数，旧校准直接失效；
            // 留着它会让下一次生成按错误块长切块，整张图错位。
            if (pixelController.confirmed) {
                pixelController.clearConfirmed()
                pixelController.resetForNewImage()
            }
            val t = ImageTransforms.crop(pixels, w, h, rect)
            imagePixels = t.pixels
            imageW = t.width
            imageH = t.height
            cropRect = CropRect.full(t.width, t.height)
            rebuildPreview()
            // 裁剪页定过网格就保留，没定过才跟随新的画像素尺寸
            refreshPixelArt(
                t.pixels, t.width, t.height,
                forceGrid = !cropGridUserSet && !pixelController.confirmed,
            )
            cropGridUserSet = false
            cropPreviewJob?.cancel()
            cropPreview = null
            cropPreviewRunning = false
            withContext(Dispatchers.Main) {
                paramsFromCrop = true
                screen = Screen.PARAMS
            }
        }
    }

    // ---------- 像素画校准页 ----------

    /**
     * 网格位置微调：一次移动 1 个源图像素。
     *
     * 允许网格滑出图片边缘（至少保留一格重叠），这样拖动才能把网格
     * 移到色块边界上。生成时 PixelImportPipeline 会按网格范围铺一张
     * 等大的画布，边缘外的格子保持透明，和屏幕上看到的网格一致。
     */
    fun pixelCalibrationNudge(dx: Double, dy: Double) {
        pixelController.nudge(dx, dy)
    }

    /** 调整列数：格子大小、行数和位置都不动，网格整体变宽/变窄。 */
    fun pixelCalibrationSetCols(cols: Int) {
        pixelController.setCols(cols)
    }

    /** 调整行数：格子大小、列数和位置都不动，网格整体变高/变矮。 */
    fun pixelCalibrationSetRows(rows: Int) {
        pixelController.setRows(rows)
    }

    /** 调整一格大小：列数/行数不动，网格整体等比放大/缩小。 */
    fun pixelCalibrationSetCellSize(px: Float) {
        pixelController.setCellSize(px)
    }

    /** 恢复自动检测的初值；检测失败则给覆盖整图的 32×32 兜底 */
    fun pixelCalibrationReset() {
        pixelController.reset()
    }

    /** 用户在校准页点「重新识别」 */
    fun pixelCalibrationRedetect() {
        pixelController.redetect()
    }

    fun pixelCalibrationSetSmart(enabled: Boolean) {
        pixelController.updateSmart(enabled)
    }

    /** 阈值变化后自动重吸附；滑条会连续回调，220ms 防抖 */
    fun pixelCalibrationSetThreshold(value: Float) {
        pixelController.updateThreshold(value)
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
    /** 用户在页面上主动点「自动对齐」 */
    fun pixelCalibrationAutoAlign() {
        pixelController.autoAlign()
    }

    /**
     * 确认校准：按校准网格裁出像素画本体，行列即图纸网格。
     *
     * 不再在确认时二次 refine：智能对齐在进页面、开开关、改阈值时就已经
     * 落到了网格上，用户看到哪张网格就按哪张生成。生成走
     * [PixelImportPipeline.convert]，和「看图纸」完全同一条管线。
     */
    fun confirmPixelCalibration() {
        pixelController.confirm()
    }

    fun selectPalette(palette: BeadPalette) {
        selectedPalette = palette
        scheduleCropPreview()
    }

    /** 自适应网格：长边沿用当前设定的长边，短边按图片比例计算，避免生成拉伸 */
    var adaptiveScale by mutableFloatStateOf(1f)
        private set

    /** 自适应的锚定格数（点自适应时的网格长边）；0 = 未处于自适应状态 */
    private var adaptiveAnchor = 0

    fun applyAdaptiveSize() {
        if (imageW <= 0 || imageH <= 0) return
        if (adaptiveAnchor <= 0) adaptiveAnchor = maxOf(gridW, gridH)
        recomputeAdaptive()
    }

    /** 调节自适应结果的放大倍数（0.5~4），仅在自适应状态下有效 */
    fun updateAdaptiveScale(scale: Float) {
        if (adaptiveAnchor <= 0) return
        adaptiveScale = scale
        recomputeAdaptive()
    }

    /** 显式设定网格（固定档位/手动输入），退出自适应状态 */
    fun setGridSize(w: Int, h: Int) {
        gridW = w.coerceIn(5, 200)
        gridH = h.coerceIn(5, 200)
        adaptiveAnchor = 0
        adaptiveScale = 1f
    }

    /** 当前应使用的宽高比：像素画用原画网格，照片用图片本身 */
    private fun effectiveAspect(): Double {
        val art = pixelArtGrid
        if (art != null && art.second > 0) return art.first.toDouble() / art.second
        return if (imageW > 0 && imageH > 0) imageW.toDouble() / imageH else 1.0
    }

    fun toggleGridAspectLock() {
        lockGridAspect = !lockGridAspect
    }

    /**
     * 自定义宽输入：锁定比例时按当前有效宽高比推导高。
     *
     * 两个输入框各自独立时，29×46 这类组合会让降采样按宽、高独立映射，
     * 圆脸被拉成椭圆。锁定比例后按当前宽高比推导高。
     */
    fun setGridWidthInput(w: Int) {
        val cw = w.coerceIn(5, 200)
        gridW = cw
        if (lockGridAspect) {
            gridH = (cw / effectiveAspect()).roundToInt().coerceIn(5, 200)
        }
        adaptiveAnchor = 0
        adaptiveScale = 1f
    }

    /** 自定义高输入：锁定比例时按当前有效宽高比推导宽 */
    fun setGridHeightInput(h: Int) {
        val ch = h.coerceIn(5, 200)
        gridH = ch
        if (lockGridAspect) {
            gridW = (ch * effectiveAspect()).roundToInt().coerceIn(5, 200)
        }
        adaptiveAnchor = 0
        adaptiveScale = 1f
    }

    /** 一键套用像素画原画网格（56×50 这种，一比一还原画像素） */
    fun applyArtGrid() {
        val art = pixelArtGrid ?: return
        setGridSize(art.first, art.second)
    }

    /**
     * 「原图尺寸」档位：宽度固定，高度按图片比例推导。
     * 只选一个数，长边不拉伸，避免方形网格把照片压扁。
     */
    fun setGridWidthPreset(width: Int) {
        val w = width.coerceIn(5, 200)
        val h = (w / effectiveAspect()).roundToInt().coerceIn(5, 200)
        setGridSize(w, h)
    }

    private fun recomputeAdaptive() {
        // 像素画的自适应就是原画网格（×放大倍数）：56×50 的图给 78×70
        // 会把画像素切成两半，颜色被平均成源图不存在的过渡色。
        val art = pixelArtGrid
        if (art != null) {
            val (w, h) = ImageTransforms.scaledGridSize(
                art.first, art.second, adaptiveScale.toDouble(),
            )
            gridW = w
            gridH = h
            return
        }
        val (baseW, baseH) = ImageTransforms.adaptiveGridSize(imageW, imageH, adaptiveAnchor)
        val (w, h) = ImageTransforms.scaledGridSize(baseW, baseH, adaptiveScale.toDouble())
        gridW = w
        gridH = h
    }

    fun generate() {
        if (genState is GenState.Running || autoTuneRunning) return
        val pixels = imagePixels ?: return
        val palette = selectedPalette ?: return
        val w = imageW
        val h = imageH
        if (w <= 0 || h <= 0 || pixels.size != w * h) {
            lastError = "源图尺寸未就绪，请重新导入"
            return
        }
        generationJob?.cancel()
        projectPages = emptyList()
        // 手动生成后清空自动优选结论
        autoTuneInfo = null
        autoTuneScores = emptyList()
        genState = GenState.Running(GenerationStage.DOWNSAMPLE, 0f)
        generationJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val options = currentOptions().copy(subjectMask = prepareAiMask())
                val result = PatternGenerator.generate(
                    pixels, w, h,
                    options,
                    palette,
                ) { stage, progress ->
                    genState = GenState.Running(stage, progress)
                }
                genState = GenState.Done(result)
                saveAfterGenerate(result)
                withContext(Dispatchers.Main) {
                    previewReturn = Screen.PARAMS
                    previewReturnTab = null
                    screen = Screen.PREVIEW
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                genState = GenState.Error(e.message ?: "生成失败")
            }
        }
    }

    /** 当前 UI 参数 → 管线参数；generate 与 autoTune 共用，避免两处漂移 */
    private fun currentOptions(): GeneratorOptions = GeneratorOptions(
        gridW = gridW.coerceIn(5, 200),
        gridH = gridH.coerceIn(5, 200),
        maxColors = if (limitColors) maxColors.coerceIn(1, 64) else null,
        cleanupEnabled = true,
        ditheringEnabled = dithering,
        removeBackground = removeBackground,
        backgroundTolerance = backgroundTolerance.toDouble(),
        filterLevel = filterLevel,
        exactArt = pixelArtMode,
        // 只有用户在校准页确认过的网格才当硬约束；自动检测的偏移/块长
        // 由采样器自己算，硬塞一个 ox=0 的网格反而会切歪。
        pixelGrid = pixelArtGrid.takeIf { pixelArtMode && pixelController.confirmed },
        autoFrame = autoFrame,
    )

    /**
     * 自动优选：跑 4 个过滤档（未限色时再加一个 20 色档），按还原度评分取最高。
     *
     * 选中后把结果写回 UI 参数（过滤档/限色），用户能看见"它替我做了什么选择"，
     * 下次手动生成会沿用同一组参数——不是一次性黑箱。
     */
    fun autoTune() {
        val pixels = imagePixels ?: return
        val palette = selectedPalette ?: return
        if (autoTuneRunning || genState is GenState.Running) return
        val w = imageW
        val h = imageH
        if (w <= 0 || h <= 0 || pixels.size != w * h) {
            lastError = "源图尺寸未就绪，请重新导入"
            return
        }
        autoTuneJob?.cancel()
        val base = currentOptions()
        projectPages = emptyList()
        autoTuneRunning = true
        autoTuneInfo = null
        autoTuneScores = emptyList()
        genState = GenState.Running(GenerationStage.DOWNSAMPLE, 0f)
        autoTuneJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val tunedBase = base.copy(subjectMask = prepareAiMask())
                val outcome = AutoTuner.sweep(
                    pixels, w, h, tunedBase, palette,
                    // 像素画只比「无过滤」一档：聚类/限色都会把原画的离散色块
                    // 重新揉成过渡色，和"一比一还原画像素"目标相反。
                    levels = if (pixelArtGrid != null) {
                        listOf(FilterLevel.NONE)
                    } else {
                        FilterLevel.entries.toList()
                    },
                    maxColorsOptions = if (pixelArtGrid != null) {
                        listOf(base.maxColors)
                    } else {
                        AutoTuner.defaultColorOptions(base)
                    },
                ) { done, total, _ ->
                    // 进度按组数推进，视觉上要动；最后一组留给评分/落库
                    genState = GenState.Running(GenerationStage.MAP, done.toFloat() / total)
                } ?: throw IllegalStateException("没有可用参数组合")

                val best = outcome.best
                filterLevel = best.filterLevel
                if (best.maxColors != null) {
                    limitColors = true
                    maxColors = best.maxColors
                } else {
                    limitColors = false
                }
                // 与"用户原来那组参数"对比，告诉他人话上的收益
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
                genState = GenState.Done(best.result)
                saveAfterGenerate(best.result)
                // 停在参数页：逐档得分和结论要给人看见，自动跳预览等于把
                // 刚算出来的对比信息扔掉。看完由用户点"查看图纸"进预览。
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                genState = GenState.Error(e.message ?: "自动优选失败")
            } finally {
                if (autoTuneJob === coroutineContext[Job]) {
                    autoTuneRunning = false
                    autoTuneJob = null
                }
            }
        }
    }

    /** 自动优选跑完但还没进预览时，用这个入口看结果（不重算、不清得分） */
    fun openPreview() {
        if (genState is GenState.Done) {
            previewReturn = Screen.PARAMS
            previewReturnTab = null
            screen = Screen.PREVIEW
        }
    }

    fun backToHome() {
        cancelGeneration()
        homeTab = HomeTab.CREATE
        screen = Screen.HOME
        editor = null
        editorFromLibrary = false
    }

    /** 参数页返回：从裁剪来就回裁剪，否则回创建页。 */
    fun backFromParams() {
        if (paramsFromCrop) {
            paramsFromCrop = false
            screen = Screen.CROP
        } else {
            backToHome()
        }
    }

    /** 裁剪页返回：从参数页来就回参数页，初始导入则回创建页。 */
    fun exitCrop() {
        if (cropReturn == Screen.PARAMS) {
            paramsFromCrop = true
            screen = Screen.PARAMS
        } else {
            backToHome()
        }
    }

    fun selectHomeTab(tab: HomeTab) {
        homeTab = tab
        librarySelecting = false
    }

    /** 预览返回上一步：从参数页来就回参数页，从项目库来就回项目库 */
    fun backFromPreview() {
        screen = previewReturn
        // 图纸保存后从预览退回首页时落在项目库，用户能立刻看到刚生成的作品；
        // 从参数页/校准页退回则保留在创建流程里，方便继续调参。
        if (previewReturn == Screen.HOME) {
            homeTab = previewReturnTab ?: HomeTab.LIBRARY
        }
    }

    /** 系统返回键：按流程层级上退 */
    fun handleBack(): Boolean = when (screen) {
        Screen.STUDIO -> {
            exitStudio()
            true
        }
        Screen.CONSTRUCTION -> {
            exitConstruction()
            true
        }
        Screen.EDITOR -> {
            if (pixelModuleActive && currentProjectId == null) {
                // 画像素模块还没保存：返回创建页，不往项目库里塞空白图纸
                pixelModuleActive = false
                editor = null
                projectPages = emptyList()
                genState = GenState.Idle
                screen = Screen.HOME
                homeTab = HomeTab.CREATE
            } else {
                exitEditor()
            }
            true
        }
        Screen.PREVIEW -> {
            backFromPreview()
            true
        }
        Screen.PARAMS -> {
            backFromParams()
            true
        }
        Screen.PIXEL_CALIBRATE -> {
            if (pixelCalibrationBusy) {
                true
            } else {
                homeTab = HomeTab.CREATE
                screen = Screen.HOME
                true
            }
        }
        Screen.CROP -> {
            exitCrop()
            true
        }
        Screen.CHART_CALIBRATE -> {
            backToHome()
            true
        }
        Screen.HOME -> false
    }

    fun backToParams() {
        if (canAdjustParams) screen = Screen.PARAMS
    }

    /** 存档用名称：参数页可自定义，留空回退默认名 */
    private fun displayName(): String = projectName.trim().ifBlank { "未命名图纸" }

    /** 各页包装成 PatternResult（预览/导出用；调色板与尺寸全页一致） */
    fun currentPages(): List<PatternResult> {
        val result = (genState as? GenState.Done)?.result ?: return emptyList()
        val pages = projectPages.ifEmpty { listOf(result.cells) }
        return pages.map { PatternResult(result.width, result.height, it, result.palette, result.elapsedMs) }
    }

    /** 生成完成后自动建档/更新保存 */
    private suspend fun saveAfterGenerate(result: PatternResult) {
        val id = currentProjectId
        if (id != null) {
            repository.updateContent(
                id, result.width, result.height,
                result.cells, BooleanArray(result.cells.size), result.palette,
            )
            // 重新生成 = 图纸整体变化：照片来源则更新参考图，无照片（空白/文字）则清除
            if (imagePreview != null && textSource == null) {
                saveRefImage(id)
            } else {
                repository.clearRefPath(id)
                refBitmap = null
            }
            toast = "修改已保存"
        } else {
            currentProjectId = repository.create(displayName(), result)
            projectName = displayName()
            settingsStore.recordCreation(pendingCreationKind)
            if (imagePreview != null && textSource == null) {
                saveRefImage(currentProjectId!!)
            }
            toast = "已保存到项目库"
        }
    }

    /** 参考原图落盘：长边压到 ≤1024px 的 JPEG，供编辑器对比叠加 */
    private suspend fun saveRefImage(id: Long) {
        val bmp = imagePreview ?: return
        StudioPersistence.saveRefImage(getApplication(), repository, id, bmp)
            .onSuccess { refBitmap = bmp }
            .onFailure { lastError = "参考图保存失败：${it.message ?: it.javaClass.simpleName}" }
    }

    /**
     * 创建页「画像素」模块：进入独立工作区（帧 / 图层 / 选区 / 动画 / 导出）。
     * 完成后把每帧拍平成一页存进项目库，同时写一份 sidecar 保留分层结构。
     */
    fun startPixelModule(width: Int, height: Int) {
        val palette = selectedPalette ?: return
        val w = width.coerceIn(5, 500)
        val h = height.coerceIn(5, 500)
        val engine = StudioCodec.blank(w, h, paletteArgbs(palette))
        studio = StudioSession(
            engine, palette, null, "画像素 ${w}×${h}",
            initialToolX = settings.studioToolX,
            initialToolY = settings.studioToolY,
            initialColorX = settings.studioColorX,
            initialColorY = settings.studioColorY,
            initialToolCollapsed = settings.studioToolCollapsed,
            initialColorCollapsed = settings.studioColorCollapsed,
        )
        studioProjectId = null
        studioExitToPreview = false
        studioBackTab = HomeTab.LIBRARY
        currentProjectId = null
        projectName = "画像素 ${w}×${h}"
        genState = GenState.Idle
        editor = null
        projectPages = emptyList()
        refBitmap = null
        showRefOverlay = false
        imagePixels = null
        imagePreview = null
        textSource = null
        pixelModuleActive = false
        pendingCreationKind = SettingsStore.CreationKind.BLANK
        pixelArtMode = true
        screen = Screen.STUDIO
    }

    /**
     * 把工作区落盘：每帧拍平后作为项目的一页，sidecar 保留完整图层树。
     * 返回项目 id；失败返回 null 并把原因写进 lastError。
     */
    private suspend fun persistStudio(session: StudioSession): Long? = withContext(Dispatchers.IO) {
        StudioPersistence.persistStudio(getApplication(), repository, session) { id ->
            studioFile(getApplication(), id)
        }.getOrElse { e ->
            lastError = "分层工程保存失败：${e.message ?: e.javaClass.simpleName}"
            null
        }
    }

    /**
     * 完成/返回画像素。默认存盘后去预览；[exitTo] = HOME 时回项目库。
     * 新建且一笔没画的工作区直接丢弃，不往项目库塞空白图纸。
     */
    fun finishStudio(exitTo: Screen = Screen.PREVIEW) {
        val session = studio ?: return
        if (studioSaving) return
        if (session.projectId == null && session.engine.revision == 0 && exitTo == Screen.HOME) {
            studio = null
            studioProjectId = null
            homeTab = HomeTab.CREATE
            screen = Screen.HOME
            return
        }
        val isNew = session.projectId == null
        studioSaving = true
        viewModelScope.launch {
            try {
                val id = persistStudio(session) ?: return@launch
                val pages = session.engine.frames.indices.map { session.engine.composite(it) }
                val palette = session.palette
                projectPages = pages
                currentProjectId = id
                studioProjectId = id
                projectName = session.name
                selectedPalette = palette
                genState = GenState.Done(
                    PatternResult(
                        session.engine.width,
                        session.engine.height,
                        pages.first(),
                        palette,
                        0,
                    ),
                )
                if (isNew) settingsStore.recordCreation(pendingCreationKind)
                studio = null
                studioProjectId = null
                toast = "已保存到项目库"
                if (exitTo == Screen.HOME) {
                    homeTab = studioBackTab
                    screen = Screen.HOME
                } else {
                    previewReturn = Screen.HOME
                    previewReturnTab = HomeTab.LIBRARY
                    screen = Screen.PREVIEW
                }
            } catch (e: Exception) {
                lastError = "保存失败：${e.message ?: e.javaClass.simpleName}"
            } finally {
                studioSaving = false
            }
        }
    }

    /** 顶栏返回 / 系统返回：按进入来源保存并退出，未落笔的新画布直接丢弃。 */
    fun exitStudio() {
        val session = studio ?: return
        if (session.projectId == null && session.engine.revision == 0) {
            studio = null
            studioProjectId = null
            homeTab = HomeTab.CREATE
            screen = Screen.HOME
            return
        }
        finishStudio(if (studioExitToPreview) Screen.PREVIEW else Screen.HOME)
    }

    /** 放弃当前画像素工作区（仅用于「不保存退出」）。 */
    fun discardStudio() {
        studio = null
        studioProjectId = null
        screen = Screen.HOME
        homeTab = HomeTab.CREATE
    }

    fun setStudioPalette(palette: BeadPalette) {
        val session = studio ?: return
        if (session.palette.brand == palette.brand && session.palette.beadSize == palette.beadSize) return
        session.palette = palette
        session.engine.setPalette(paletteArgbs(palette))
        session.color = session.color.coerceIn(0, palette.colors.size - 1)
        session.secondaryColor = session.secondaryColor.coerceIn(0, palette.colors.size - 1)
        session.touch()
    }

    /** 记录两块浮窗的自由位置（0..1 比例），拖完立即持久化。 */
    fun setStudioRailPositions(toolX: Float, toolY: Float, colorX: Float, colorY: Float) {
        val session = studio ?: return
        session.toolX = toolX
        session.toolY = toolY
        session.colorX = colorX
        session.colorY = colorY
        settings = settings.copy(
            studioToolX = toolX,
            studioToolY = toolY,
            studioColorX = colorX,
            studioColorY = colorY,
        )
        viewModelScope.launch {
            settingsStore.setStudioRailPositions(toolX, toolY, colorX, colorY)
        }
    }

    fun setStudioRailCollapsed(toolCollapsed: Boolean, colorCollapsed: Boolean) {
        val session = studio ?: return
        session.toolCollapsed = toolCollapsed
        session.colorCollapsed = colorCollapsed
        settings = settings.copy(
            studioToolCollapsed = toolCollapsed,
            studioColorCollapsed = colorCollapsed,
        )
        viewModelScope.launch {
            settingsStore.setStudioRailCollapsed(toolCollapsed, colorCollapsed)
        }
    }

    fun markStudioTutorialSeen() {
        if (settings.studioTutorialSeen == true) return
        settings = settings.copy(studioTutorialSeen = true)
        viewModelScope.launch { settingsStore.setStudioTutorialSeen(true) }
    }

    /**
     * 底栏玻璃参数。
     *
     * 先写内存再落盘：拖动滑块时底栏要当帧跟手，等 DataStore 往返会明显滞后。
     * DataStore 的 flow 稍后回灌同值，UI 不会二次跳变。
     */
    fun setGlassAppearance(glass: GlassAppearance) {
        val safe = glass.sanitized()
        if (settings.glass == safe) return
        settings = settings.copy(glass = safe)
        viewModelScope.launch { settingsStore.setGlassAppearance(safe) }
    }

    /** 界面主题包：写内存立即重绘，再落盘；未知 id 回落到默认配色。 */
    fun setThemeSkin(id: String) {
        val theme = AppThemes.byId(id)
        if (settings.themeSkin == theme.id) return
        settings = settings.copy(themeSkin = theme.id)
        viewModelScope.launch { settingsStore.setThemeSkin(theme.id) }
    }

    fun resetStudioRailPositions() {
        val session = studio ?: return
        session.resetRailPositions()
        settings = settings.copy(
            studioToolX = -1f,
            studioToolY = -1f,
            studioColorX = -1f,
            studioColorY = -1f,
            studioToolCollapsed = false,
            studioColorCollapsed = false,
        )
        viewModelScope.launch {
            settingsStore.setStudioRailPositions(-1f, -1f, -1f, -1f)
            settingsStore.setStudioRailCollapsed(false, false)
        }
    }

    /**
     * 普通图纸 → 画像素工程。
     *
     * 照片生成、像素画导入、文字图纸、旧项目都走这里：每页变成一帧、一层，
     * 色卡/尺寸/格子原样保留。已经是 Studio 工程时优先读 sidecar，不重复拍平。
     */
    fun editInStudio() {
        val palette = selectedPalette ?: return
        val result = (genState as? GenState.Done)?.result
        val editorPages = editor?.pages?.map { it.copyOf() }
        val pages = editorPages ?: projectPages.ifEmpty {
            result?.let { listOf(it.cells.copyOf()) } ?: return
        }
        if (pages.isEmpty()) return
        val width = editor?.width ?: result?.width ?: return
        val height = editor?.height ?: result?.height ?: return
        if (pages.any { it.size != width * height }) return
        val locks = editor?.locks?.map { it.copyOf() }
        val id = currentProjectId
        val name = projectName
        val fromLibrary = editorFromLibrary
        viewModelScope.launch {
            // 从旧编辑器过来：先把当前未保存的笔画落库，再转 Studio。
            if (editor != null && id != null) {
                val flat = pages.reduce { acc, cur -> acc + cur }
                val flatLocks = locks?.reduce { acc, cur -> acc + cur }
                    ?: BooleanArray(flat.size)
                runCatching {
                    repository.updateCells(
                        id = id,
                        width = width,
                        height = height,
                        cells = flat,
                        locked = flatLocks,
                        palette = palette,
                        pages = pages.size,
                    )
                }
            }

            var loaded: StudioEngine? = null
            if (editor == null && id != null) {
                val entity = repository.get(id)
                val path = entity?.studioPath
                if (path != null) {
                    loaded = withContext(Dispatchers.IO) {
                        StudioCodec.load(File(path), paletteArgbs(palette))
                    }
                }
            }
            val target = loaded ?: StudioEngine(
                width,
                height,
                pages.mapIndexed { index, cells ->
                    StudioFrame(
                        mutableListOf(
                            StudioLayer(
                                if (pages.size == 1) "图层 1" else "第 ${index + 1} 页",
                                cells.copyOf(),
                            ),
                        ),
                    )
                },
                paletteArgb = paletteArgbs(palette),
            )

            if (id != null) {
                withContext(Dispatchers.IO) {
                    val file = studioFile(getApplication(), id)
                    if (StudioCodec.save(target, file)) {
                        repository.setStudioPath(id, file.absolutePath)
                    }
                }
            }
            projectPages = pages
            currentProjectId = id
            projectName = name
            selectedPalette = palette
            genState = GenState.Done(PatternResult(width, height, pages.first(), palette, 0))
            editor = null
            editorFromLibrary = false
            studioProjectId = id
            studio = StudioSession(
                target, palette, id, name,
                initialToolX = settings.studioToolX,
                initialToolY = settings.studioToolY,
                initialColorX = settings.studioColorX,
                initialColorY = settings.studioColorY,
                initialToolCollapsed = settings.studioToolCollapsed,
                initialColorCollapsed = settings.studioColorCollapsed,
            )
            studioExitToPreview = !fromLibrary
            studioBackTab = HomeTab.LIBRARY
            screen = Screen.STUDIO
        }
    }

    /** 画像素工程 → 普通图纸：拍平每帧为页，移除 sidecar，进入单层编辑器。 */
    fun flattenStudioToEditor() {
        val session = studio ?: return
        viewModelScope.launch {
            val id = persistStudio(session) ?: return@launch
            val pages = session.engine.frames.indices.map { session.engine.composite(it) }
            repository.setStudioPath(id, null)
            withContext(Dispatchers.IO) {
                runCatching { studioFile(getApplication(), id).delete() }
            }
            projectPages = pages
            currentProjectId = id
            projectName = session.name
            selectedPalette = session.palette
            genState = GenState.Done(
                PatternResult(
                    session.engine.width,
                    session.engine.height,
                    pages.first(),
                    session.palette,
                    0,
                ),
            )
            editor = EditorEngine(session.engine.width, session.engine.height, pages)
            editorRevision++
            editorTool = EditorTool.NONE
            editorEraseRegion = false
            editorFromLibrary = true
            studio = null
            studioProjectId = null
            toast = "已转为普通图纸（图层已拍平）"
            screen = Screen.EDITOR
        }
    }

    /** 文字拼豆：文字栅格化后走常规管线 */
    fun generateFromText(text: String, width: Int? = null, height: Int? = null) {
        val palette = selectedPalette ?: return
        if (text.isBlank()) return
        if (genState is GenState.Running || autoTuneRunning) return
        generationJob?.cancel()
        val w = (width ?: gridW).coerceIn(5, 200)
        val h = (height ?: gridH).coerceIn(5, 200)
        gridW = w
        gridH = h
        pendingCreationKind = SettingsStore.CreationKind.TEXT
        textSource = text
        projectPages = emptyList()
        genState = GenState.Running(GenerationStage.DOWNSAMPLE, 0f)
        generationJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val bitmap = TextRasterizer.rasterize(text, w, h)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                // 二值化：文字是"白底黑字"，抗锯齿在笔画边缘留了一圈过渡灰，
                // 降采样后灰格会被映射成杂色号导致字形破碎、识别率低。
                // 按亮度阈值把过渡灰切开归为纯黑/纯白，字迹锐利后映射色号干净利落。
                for (i in pixels.indices) {
                    val p = pixels[i]
                    val lum = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                    pixels[i] = if (lum >= 128) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
                }
                bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                // 让文字栅格化的像素成为"当前源"，这样参数页有预览、调整参数后 generate() 重新生成仍是文字图
                imagePixels = pixels
                imageW = bitmap.width
                imageH = bitmap.height
                // 文字位图不是照片也不是像素画，清掉画像素网格，避免沿用上一张图的 56×50
                pixelArtGrid = null
                pixelArtMode = false
                originalPixels = pixels
                originalW = bitmap.width
                originalH = bitmap.height
                cropRect = com.pindou.app.core.image.CropRect.full(bitmap.width, bitmap.height)
                imagePreview = bitmap // 保留位图供预览，不 recycle
                val result = PatternGenerator.generate(
                    pixels, bitmap.width, bitmap.height,
                    GeneratorOptions(
                        gridW = w,
                        gridH = h,
                        maxColors = if (limitColors) maxColors.coerceIn(1, 64) else null,
                        cleanupEnabled = true,
                        ditheringEnabled = false,
                        filterLevel = filterLevel,
                        // 文字画布尺寸就是用户选的成品尺寸，不能再被自动取景裁掉白边
                        autoFrame = false,
                    ),
                    palette,
                ) { stage, progress ->
                    genState = GenState.Running(stage, progress)
                }
                genState = GenState.Done(result)
                saveAfterGenerate(result)
                withContext(Dispatchers.Main) {
                    previewReturn = Screen.HOME
                    previewReturnTab = HomeTab.CREATE
                    screen = Screen.PREVIEW
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                genState = GenState.Error(e.message ?: "文字生成失败")
            }
        }
    }

    /** 打开已有项目（进入编辑器继续修改） */
    fun openProject(summary: ProjectSummary) {
        cancelGeneration()
        viewModelScope.launch {
            try {
                librarySelecting = false
                pixelModuleActive = false
                val entity = repository.get(summary.id)
                if (entity == null) {
                    lastError = "打开失败：项目已不存在"
                    return@launch
                }
                // 色卡可能尚未加载完成：这里按需等待一次，避免点了没反应
                var palette = paletteFor(entity.paletteBrand, entity.beadSize)
                if (palette == null) {
                    // 已经有缓存就直接用；只有真正为空才回退到 assets 解析。
                    ensurePalettes()
                    palette = paletteFor(entity.paletteBrand, entity.beadSize)
                }
                if (palette == null) {
                    lastError = "打开失败：未找到色卡「${entity.paletteBrand}」，当前已载入 ${palettes.size} 套"
                    return@launch
                }
                val cells = GridCodec.decodeCells(entity.cells)
                val pageCount = entity.pages.coerceAtLeast(1)
                var gridWidth = entity.width
                var gridHeight = entity.height
                var pageLen = gridWidth * gridHeight
                if (cells.size != pageLen * pageCount) {
                    val repaired = ProjectGridRepair.infer(
                        entity.width, entity.height, cells.size, pageCount,
                    )
                    if (repaired == null) {
                        lastError =
                            "打开失败：数据尺寸不符（${cells.size} vs ${pageLen * pageCount}）"
                        return@launch
                    }
                    gridWidth = repaired.width
                    gridHeight = repaired.height
                    pageLen = gridWidth * gridHeight
                    withContext(Dispatchers.IO) {
                        repository.repairSize(entity.id, gridWidth, gridHeight)
                    }
                    toast = "已修复旧项目尺寸为 ${gridWidth}×${gridHeight}"
                }
                val pages = chunkInts(cells, pageLen)
                // 项目库统一进画像素编辑器（新编辑页面）：
                // 有 sidecar 就还原图层/帧，没有就按页建一层。
                // 旧单层编辑器只保留在创建流程内部使用。
                val studioPath = entity.studioPath
                val loaded = if (studioPath != null) {
                    withContext(Dispatchers.IO) {
                        StudioCodec.load(File(studioPath), paletteArgbs(palette))
                    }
                } else {
                    null
                }
                val studioEngine = loaded?.takeIf {
                    it.width == gridWidth && it.height == gridHeight
                } ?: StudioEngine(
                    gridWidth,
                    gridHeight,
                    pages.mapIndexed { index, page ->
                        StudioFrame(
                            mutableListOf(
                                StudioLayer(
                                    if (pages.size == 1) "图层 1" else "第 ${index + 1} 页",
                                    page.copyOf(),
                                ),
                            ),
                        )
                    },
                    paletteArgb = paletteArgbs(palette),
                )
                studio = StudioSession(
                    studioEngine, palette, entity.id, entity.name,
                    initialToolX = settings.studioToolX,
                    initialToolY = settings.studioToolY,
                    initialColorX = settings.studioColorX,
                    initialColorY = settings.studioColorY,
                    initialToolCollapsed = settings.studioToolCollapsed,
                    initialColorCollapsed = settings.studioColorCollapsed,
                )
                studioProjectId = entity.id
                currentProjectId = entity.id
                projectName = entity.name
                projectPages = pages
                genState = GenState.Done(
                    PatternResult(gridWidth, gridHeight, pages.first(), palette, 0),
                )
                editor = null
                selectedPalette = palette
                editorFromLibrary = false
                lastError = null
                studioExitToPreview = false
                studioBackTab = HomeTab.LIBRARY
                screen = Screen.STUDIO
            } catch (e: Exception) {
                lastError = "打开失败：${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun deleteProject(summary: ProjectSummary) {
        libraryController.delete(summary)
    }

    /** 批量删除：Repository 负责逐条清理缩略图 / 图层 sidecar */
    fun deleteProjects(ids: Collection<Long>) {
        libraryController.deleteAll(ids)
    }

    fun setFavProjects(ids: Collection<Long>, fav: Boolean) {
        libraryController.setFav(ids, fav)
    }

    fun setAlbumProjects(ids: Collection<Long>, album: String) {
        libraryController.setAlbum(ids, album)
    }

    fun renameProject(summary: ProjectSummary, newName: String) {
        libraryController.rename(summary, newName)
    }

    fun startNewProject() {
        cancelGeneration()
        pixelModuleActive = false
        pendingCreationKind = SettingsStore.CreationKind.PHOTO
        currentProjectId = null
        projectName = "未命名图纸"
        genState = GenState.Idle
        editor = null
        projectPages = emptyList()
        refBitmap = null
        showRefOverlay = false
        imagePixels = null
        imagePreview = null
        imageW = 0
        imageH = 0
        originalPixels = null
        originalW = 0
        originalH = 0
        cropRect = null
        cropOriginalPreset = false
        paramsFromCrop = false
        adaptiveAnchor = 0
        adaptiveScale = 1f
        textSource = null
        pixelArtMode = false
        pixelArtGrid = null
        previewReturn = Screen.HOME
        previewReturnTab = null
        cropAspectMode = CropAspectMode.FREE
        editorTool = EditorTool.NONE
        editorEraseRegion = false
        editorFromLibrary = false
        pixelController.resetForNewImage()
        aiMaskKey = null
        aiMask = null
        aiMaskStatus = null
        chartController.reset()
    }

    /** 当前文字拼豆的源文字（非 null = 文字模式）；改文字时更新 */
    var textSource by mutableStateOf<String?>(null)
        private set

    /** 文字拼豆改文字内容后重新生成 */
    fun regenerateFromText(text: String, width: Int? = null, height: Int? = null) {
        textSource = text
        generateFromText(text, width, height)
    }

    /**
     * 从预览进入编辑器：统一走画像素工作区。
     *
     * 已生成 sidecar 的项目直接载入分层/帧结构；没有 sidecar 时把当前
     * 预览结果转成 Studio 工程。原来的「在画像素中编辑」与「编辑图纸」
     * 是同一个出口，现在只保留后者。
     */
    fun enterEditor() = editInStudio()

    /** 顶栏「完成」：保存并进入图纸预览。 */
    fun finishEditor() = finishEditorInternal(Screen.PREVIEW)

    /**
     * 返回键 / 顶栏返回：保存并回到进入编辑器的位置。
     * 项目库打开的图纸回项目库；创建流程来的回预览。
     */
    fun exitEditor() {
        val destination = if (editorFromLibrary) Screen.HOME else Screen.PREVIEW
        finishEditorInternal(destination)
    }

    /** 写回当前结果并持久化到项目库。 */
    private fun finishEditorInternal(destination: Screen) {
        val engine = editor ?: return
        val current = (genState as? GenState.Done)?.result ?: return
        val fromLibrary = editorFromLibrary
        val pages = engine.pages.map { it.copyOf() }
        // 编辑器里可能改过尺寸：以引擎当前尺寸为准重建结果
        val updated = if (engine.width != current.width || engine.height != current.height) {
            PatternResult(engine.width, engine.height, pages[0], current.palette, current.elapsedMs)
        } else {
            current.copy(cells = pages[0])
        }
        projectPages = pages
        genState = GenState.Done(updated)
        editor = null
        if (fromLibrary) {
            previewReturn = Screen.HOME
            previewReturnTab = HomeTab.LIBRARY
        }
        editorFromLibrary = false
        editorTool = EditorTool.NONE
        editorEraseRegion = false
        val id = currentProjectId
        if (id != null) {
            viewModelScope.launch {
                // 编辑是局部修改：保留施工进度，只写回格数据与锁（多页平铺）
                repository.updateCells(
                    id = id,
                    width = updated.width,
                    height = updated.height,
                    cells = pages.reduce { acc, cur -> acc + cur },
                    locked = engine.locks.reduce { acc, cur -> acc + cur },
                    palette = updated.palette,
                    pages = pages.size,
                )
                toast = "修改已保存"
            }
        } else {
            viewModelScope.launch {
                currentProjectId = repository.importProject(
                    displayName(),
                    updated.width,
                    updated.height,
                    updated.palette,
                    pages,
                    engine.locks,
                )
                settingsStore.recordCreation(pendingCreationKind)
            }
        }
        if (destination == Screen.HOME) {
            homeTab = HomeTab.LIBRARY
            screen = Screen.HOME
        } else {
            screen = destination
        }
    }

    /** 进入施工模式（载入已保存的进度） */
    fun enterConstruction() {
        val result = (genState as? GenState.Done)?.result ?: return
        viewModelScope.launch {
            val pages = currentPages().map { it.cells }.ifEmpty { listOf(result.cells) }
            val pageCount = pages.size.coerceAtLeast(1)
            val pageSize = result.width * result.height
            val expectedSize = pageSize * pageCount
            val entity = currentProjectId?.let { repository.get(it) }
            // 编辑器改过尺寸后进度数组长度会失配：失配即作废旧进度、游标归零
            val stored = entity?.doneCells
            val validStored = stored != null && stored.size == expectedSize
            val done = when {
                validStored ->
                    BooleanArray(expectedSize) { stored[it] != 0.toByte() }
                // 旧多页项目可能只存了前 N 页：保留已有页，其余页从空白开始
                stored != null && pageSize > 0 &&
                    stored.size % pageSize == 0 && stored.size <= expectedSize ->
                    BooleanArray(expectedSize).also { out ->
                        for (i in stored.indices) out[i] = stored[i] != 0.toByte()
                    }
                else -> BooleanArray(expectedSize)
            }
            construction = ConstructionState(
                width = result.width,
                height = result.height,
                done = done,
                pages = pageCount,
                // 0=按行 1=按列 2=按色号 3=按底板；cursor 语义随模式变化
                orientation = when (entity?.buildOrientation ?: 0) {
                    1 -> ConstructionState.Orientation.COLUMN
                    2 -> ConstructionState.Orientation.COLOR
                    3 -> ConstructionState.Orientation.BOARD
                    else -> ConstructionState.Orientation.ROW
                },
                cursor = if (validStored) {
                    val upper = when (entity?.buildOrientation ?: 0) {
                        2 -> (result.palette.colors.size - 1).coerceAtLeast(0)
                        3 -> ((result.width + 28) / 29 * ((result.height + 28) / 29) - 1)
                            .coerceAtLeast(0)
                        1 -> (result.width - 1).coerceAtLeast(0)
                        else -> (result.height - 1).coerceAtLeast(0)
                    }
                    (entity?.buildCursor ?: 0).coerceIn(0, upper)
                } else {
                    0
                },
            )
            screen = Screen.CONSTRUCTION
        }
    }

    fun exitConstruction() {
        saveConstructionProgress()
        construction = null
        screen = Screen.PREVIEW
    }

    /** 进度落库（节流由调用方保证：每格操作后调用一次即可） */
    fun saveConstructionProgress() {
        val id = currentProjectId ?: return
        val state = construction ?: return
        viewModelScope.launch {
            repository.saveBuildProgress(id, state.done, state.orientation.ordinal, state.cursor)
        }
    }

    fun undoEditor() {
        editor?.undo()
        notifyEditorChanged()
    }

    fun redoEditor() {
        editor?.redo()
        notifyEditorChanged()
    }

    // ---------- 编辑器：页操作 ----------

    /** 切换页 */
    fun switchEditorPage(index: Int) {
        editor?.setPage(index)
        notifyEditorChanged()
    }

    /** 新增一页（可复制当前页） */
    fun addEditorPage(copyCurrent: Boolean) {
        editor?.addPage(copyCurrent)
        notifyEditorChanged()
    }

    /** 删除一页（至少保留一页） */
    fun removeEditorPage(index: Int) {
        editor?.removePage(index)
        notifyEditorChanged()
    }

    /** 改尺寸：所有页内容居中保留 */
    fun resizeEditor(newWidth: Int, newHeight: Int) {
        val engine = editor ?: return
        engine.resize(newWidth.coerceIn(5, 500), newHeight.coerceIn(5, 500))
        notifyEditorChanged()
    }

    /** 编辑时裁剪：只保留 (left, top) 起的 w×h，所有页同步，可撤销 */
    fun cropEditor(left: Int, top: Int, newWidth: Int, newHeight: Int) {
        val engine = editor ?: return
        val w = newWidth.coerceIn(5, 500)
        val h = newHeight.coerceIn(5, 500)
        if (left < 0 || top < 0) return
        if (left + w > engine.width || top + h > engine.height) return
        if (engine.crop(left, top, w, h)) notifyEditorChanged()
    }

    fun flipEditorHorizontal() {
        if (editor?.flipHorizontal() == true) notifyEditorChanged()
    }

    fun flipEditorVertical() {
        if (editor?.flipVertical() == true) notifyEditorChanged()
    }

    /**
     * 图纸预览页的镜像：直接重排当前所有页并立即落库。
     *
     * 预览页没有撤销栈，用「再点一次还原」的往返操作兜底；翻转会改变每格
     * 语义，旧的施工进度必须作废，否则勾选记录会停在镜像前的位置。
     */
    fun flipPreview(horizontal: Boolean) {
        val current = (genState as? GenState.Done)?.result ?: return
        if (horizontal && current.width < 2) return
        if (!horizontal && current.height < 2) return
        val pages = projectPages.ifEmpty { listOf(current.cells) }
        val mirrored = pages.map { page ->
            if (horizontal) {
                PatternTransform.mirrorHorizontal(page, current.width, current.height)
            } else {
                PatternTransform.mirrorVertical(page, current.width, current.height)
            }
        }
        projectPages = mirrored
        genState = GenState.Done(current.copy(cells = mirrored.first()))
        toast = if (horizontal) "已水平镜像，再点一次还原" else "已垂直镜像，再点一次还原"

        val id = currentProjectId ?: return
        val pageSize = current.width * current.height
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = repository.get(id)
                val locks = entity?.locked
                    ?.let { GridCodec.decodeLocks(it) }
                    ?.takeIf { it.size == pageSize * pages.size }
                    ?: BooleanArray(pageSize * pages.size)
                val flatLocks = mirrored.indices
                    .map { index ->
                        val slice = locks.copyOfRange(index * pageSize, (index + 1) * pageSize)
                        if (horizontal) {
                            PatternTransform.mirrorHorizontal(slice, current.width, current.height)
                        } else {
                            PatternTransform.mirrorVertical(slice, current.width, current.height)
                        }
                    }
                    .reduce { acc, next -> acc + next }
                val flat = mirrored.reduce { acc, next -> acc + next }
                repository.updateCells(
                    id, current.width, current.height, flat, flatLocks,
                    selectedPalette ?: current.palette, mirrored.size,
                    resetProgress = true,
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    lastError = "镜像保存失败：${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    fun rotateEditor(clockwise: Boolean) {
        if (editor?.rotate90(clockwise) == true) notifyEditorChanged()
    }

    /** 参考图叠加开关 */
    fun toggleRefOverlay() {
        if (refBitmap != null) showRefOverlay = !showRefOverlay
    }

    fun updateRefOpacity(value: Float) {
        refOpacity = value.coerceIn(0.05f, 1f)
    }

    // ---------- 编辑器：色板操作 ----------

    /** 换品牌：按 ΔE2000 最近色整图重映射并立即持久化 */
    fun applyPaletteRemap(newPalette: BeadPalette) {
        val engine = editor ?: return
        val current = (genState as? GenState.Done)?.result ?: return
        val remapped = PaletteOps.remapToPalette(engine.pages, current.palette, newPalette)
        editor = EditorEngine(engine.width, engine.height, remapped, engine.locks, engine.pageIndex)
        genState = GenState.Done(
            PatternResult(engine.width, engine.height, remapped[0], newPalette, current.elapsedMs),
        )
        projectPages = remapped
        selectedPalette = newPalette
        // 新品牌色号数量可能不同，旧索引越界会让编辑页顶部色块直接崩
        editorColor = editorColor.coerceIn(0, newPalette.colors.lastIndex)
        editorRecentColors.clear()
        val id = currentProjectId
        if (id != null) {
            viewModelScope.launch {
                repository.updateCells(
                    id = id,
                    width = engine.width,
                    height = engine.height,
                    cells = remapped.reduce { acc, cur -> acc + cur },
                    locked = engine.locks.reduce { acc, cur -> acc + cur },
                    palette = newPalette,
                    pages = remapped.size,
                )
            }
        }
        notifyEditorChanged()
        toast = "已换用「${newPalette.brandLabel}」色卡"
    }

    /**
     * 全图合并相近色（ΔE 阈值），沿用编辑引擎的撤销栈：
     * 合并后按一次撤销即可回到合并前，不丢编辑历史。
     * 返回逐条「旧色 → 新色 · 格数」明细，UI 用于展示合并了什么。
     */
    fun mergeSimilarEditorColorsWithReport(maxDeltaE: Double): List<ColorReplaceOps.Move> {
        val engine = editor ?: return emptyList()
        val palette = selectedPalette ?: return emptyList()
        val outcome = ColorReplaceOps.mergeSimilar(engine.pages, engine.locks, palette, maxDeltaE)
        if (!outcome.changed) {
            toast = "ΔE ≤ %.1f 没有可合并的相近色".format(maxDeltaE)
            return emptyList()
        }
        if (!engine.applyPages(outcome.pages)) return emptyList()
        syncEditorAfterColorOp()
        persistEditorPages()
        val usedBefore = engine.pages.flatMap { p -> p.filter { it >= 0 } }.distinct().size
        toast = "已合并 ${outcome.moves.size} 组 · ${outcome.movedCells} 格（剩 $usedBefore 色）"
        return outcome.moves
    }

    /**
     * 替换相似色：[from] 及与它 ΔE ≤ [maxDeltaE] 的已用色统一替换为 [to]。
     * maxDeltaE = 0 即只替换这一个色号。返回明细供 UI 展示。
     */
    fun replaceSimilarEditorColors(
        from: Int,
        to: Int,
        maxDeltaE: Double,
    ): List<ColorReplaceOps.Move> {
        val engine = editor ?: return emptyList()
        val palette = selectedPalette ?: return emptyList()
        if (from == to) {
            toast = "源色与目标色相同"
            return emptyList()
        }
        val outcome = ColorReplaceOps.replaceSimilar(
            engine.pages, engine.locks, palette, from, to, maxDeltaE,
        )
        if (!outcome.changed) {
            toast = if (outcome.lockedSkipped > 0) {
                "命中 ${outcome.lockedSkipped} 格，但都是锁定格"
            } else {
                "没有匹配的格子"
            }
            return emptyList()
        }
        if (!engine.applyPages(outcome.pages)) return emptyList()
        syncEditorAfterColorOp()
        persistEditorPages()
        val target = palette.colors.getOrNull(to)?.code ?: "?"
        toast = "已把 ${outcome.moves.size} 色 · ${outcome.movedCells} 格换成 $target"
        return outcome.moves
    }

    /** 全局换色后同步 genState / projectPages，并刷新画布 */
    private fun syncEditorAfterColorOp() {
        val engine = editor ?: return
        val current = (genState as? GenState.Done)?.result ?: return
        val pages = engine.pages.map { it.copyOf() }
        projectPages = pages
        genState = GenState.Done(
            current.copy(
                width = engine.width,
                height = engine.height,
                cells = pages.first(),
                palette = selectedPalette ?: current.palette,
            ),
        )
        editorRevision++
    }

    /** 全局换色后立即落库（拷贝数组，避免协程执行时被继续编辑） */
    private fun persistEditorPages() {
        val engine = editor ?: return
        val id = currentProjectId ?: return
        val palette = selectedPalette ?: return
        val pages = engine.pages.map { it.copyOf() }
        val locks = engine.locks.map { it.copyOf() }
        viewModelScope.launch {
            repository.updateCells(
                id = id,
                width = engine.width,
                height = engine.height,
                cells = pages.reduce { acc, cur -> acc + cur },
                locked = locks.reduce { acc, cur -> acc + cur },
                palette = palette,
                pages = pages.size,
            )
        }
    }

    // ---------- .bpat 导入导出 ----------

    /** 导出当前图纸为 .bpat（含全部页） */
    fun exportBpatFile() {
        val result = (genState as? GenState.Done)?.result ?: return
        val pages = editor?.pages ?: projectPages.ifEmpty { listOf(result.cells) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = currentProjectId?.let { repository.get(it) }
                val json = BpatCodec.encode(
                    title = projectName,
                    album = entity?.album ?: "",
                    brand = result.palette.brand,
                    beadSizeTag = result.palette.beadSize.name,
                    width = result.width,
                    height = result.height,
                    pages = pages,
                    paletteColors = result.palette.colors.map {
                        BpatCodec.BpatColorSource(
                            it.code,
                            String.format("#%06X", it.argb and 0xFFFFFF),
                            it.name,
                        )
                    },
                )
                val out = com.pindou.app.core.export.ExportService.exportBpat(
                    getApplication(), json, result,
                )
                withContext(Dispatchers.Main) {
                    when (out) {
                        is com.pindou.app.core.export.ExportService.Exported.FileReady ->
                            toast = "已导出 ${out.file.name}"
                        is com.pindou.app.core.export.ExportService.Exported.Failed ->
                            lastError = "导出失败：${out.message}"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { lastError = "导出失败：${e.message}" }
            }
        }
    }

    /** 导入 .bpat：落到项目库（不自动打开） */
    fun importBpat(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: error("无法读取文件")
                val data = BpatCodec.decode(text)
                val loaded = ensurePalettes()
                if (loaded.isEmpty()) error("没有可用色卡")
                val size = com.pindou.app.core.palette.BeadSize.fromTag(data.beadSizeTag)
                val target = loaded.firstOrNull { it.brand == data.brand && it.beadSize == size }
                    ?: loaded.firstOrNull { it.brand == data.brand }
                    ?: loaded.firstOrNull { it.beadSize == size }
                    ?: loaded.first()
                // 缺失色号用全色卡 Lab 最近匹配兜底
                val colorIndex = IntArray(data.colors.size) { i ->
                    val c = data.colors[i]
                    val local = target.indexOfCode(c.code)
                    if (local >= 0) {
                        local
                    } else {
                        com.pindou.app.core.color.ColorConversions.argbToLab(c.hex)
                            .let { target.nearestIndex(it) }
                            .takeIf { it >= 0 } ?: 0
                    }
                }
                val pages = data.pages.map { page ->
                    IntArray(page.size) { i -> if (page[i] >= 0) colorIndex[page[i]] else -1 }
                }
                repository.importProject(
                    data.title.ifBlank { "导入图纸" },
                    data.width,
                    data.height,
                    target,
                    pages,
                    pages.map { BooleanArray(it.size) },
                    data.album,
                )
                settingsStore.recordCreation(SettingsStore.CreationKind.BPAT)
                withContext(Dispatchers.Main) { toast = "已导入「${data.title.ifBlank { "图纸" }}」" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { lastError = "导入失败：${e.message ?: e.javaClass.simpleName}" }
            }
        }
    }

    // ---------- 项目库：筛选 / 收藏 / 专辑 / 复刻 ----------

    /** 按搜索词/收藏/专辑过滤（库页组合使用） */
    fun filteredProjects(all: List<ProjectSummary>): List<ProjectSummary> =
        libraryController.filtered(all, libraryQuery, libraryFavOnly, libraryAlbum)

    /** 已用到的专辑名（供筛选 chips） */
    fun albumNames(all: List<ProjectSummary>): List<String> =
        libraryController.albumNames(all)

    fun setFavProject(summary: ProjectSummary, fav: Boolean) {
        libraryController.setFav(summary, fav)
    }

    fun setAlbumProject(summary: ProjectSummary, album: String) {
        libraryController.setAlbum(summary, album)
    }

    /** 复刻项目：全量拷贝为新项目，进度清零，重新开始拼 */
    fun duplicateProject(summary: ProjectSummary) {
        libraryController.duplicate(summary)
    }

    // ---------- 色号图识别 ----------

    fun onChartPicked(uri: Uri) = chartController.onPicked(uri)
    fun chartSetCols(n: Int) = chartController.setCols(n)
    fun chartSetRows(n: Int) = chartController.setRows(n)
    fun chartSetCellSize(px: Float) = chartController.setCellSize(px)
    fun chartNudge(dx: Int, dy: Int) = chartController.nudge(dx, dy)
    fun chartMoveBy(dx: Float, dy: Float) = chartController.moveBy(dx, dy)
    fun chartSetThreshold(v: Float) = chartController.updateThreshold(v)
    fun chartSetSmart(enabled: Boolean) = chartController.setSmart(enabled)
    fun chartRedetect() = chartController.redetect()
    fun chartRefine() = chartController.refine()
    fun chartToPattern() = chartController.toPattern()

    // ---------- 内部工具 ----------

    /** 色卡按需加载（首次打开项目/复刻/导入时可能尚未就绪） */
    private suspend fun ensurePalettes(): List<BeadPalette> {
        if (palettes.isNotEmpty()) return palettes
        val loaded = withContext(Dispatchers.IO) { PaletteAssets.loadAll(getApplication()) }
        if (loaded.isNotEmpty()) {
            palettes = loaded
            if (selectedPalette == null) {
                selectedPalette = loaded.firstOrNull { it.brand == "MARD" } ?: loaded.first()
            }
        }
        return loaded
    }

    private fun paletteFor(brand: String, beadSizeTag: String): BeadPalette? =
        PaletteAssets.pick(palettes, brand, beadSizeTag)

    /** 项目库 controller 回调：当前项目 id 与提示仍归 VM。 */
    private inner class LibraryHost : ProjectLibraryController.Host {
        override val currentProjectId: Long?
            get() = this@AppViewModel.currentProjectId

        override fun onCurrentProjectDeleted(id: Long) {
            this@AppViewModel.currentProjectId = null
        }

        override fun showLibraryToast(message: String) {
            toast = message
        }

        override fun showLibraryError(message: String) {
            lastError = message
        }

        override fun showDeleteUndo(
            names: List<String>,
            onUndo: suspend () -> Unit,
            onExpired: suspend () -> Unit,
        ) {
            // 新删除直接顶掉旧的：旧的一批先落盘，避免文件泄在磁盘上
            val previous = deleteUndo
            if (previous != null) {
                deleteUndo = null
                viewModelScope.launch { runCatching { previous.onExpired() } }
            }
            val message = when (names.size) {
                0 -> "已删除"
                1 -> "已删除「${names.first()}」"
                else -> "已删除 ${names.size} 份图纸"
            }
            deleteUndo = DeleteUndo(message, onUndo, onExpired)
        }

        override suspend fun ensurePalettes(): List<BeadPalette> = this@AppViewModel.ensurePalettes()

        override fun paletteFor(brand: String, beadSize: String): BeadPalette? =
            this@AppViewModel.paletteFor(brand, beadSize)
    }

    /** 像素校准控制器回调：源图、生成参数、落库和导航仍留在 VM 一侧。 */
    private inner class PixelHost : PixelCalibrationController.Host {
        override val imagePixels: IntArray?
            get() = this@AppViewModel.imagePixels
        override val imageWidth: Int
            get() = this@AppViewModel.imageW
        override val imageHeight: Int
            get() = this@AppViewModel.imageH
        override val currentPalette: BeadPalette?
            get() = this@AppViewModel.selectedPalette

        override fun generatorOptions(): GeneratorOptions = currentOptions()

        override suspend fun ensurePalette(): BeadPalette? {
            ensurePalettes()
            return selectedPalette
        }

        override fun onPixelGridPrepared(cols: Int, rows: Int) {
            pixelArtGrid = cols to rows
        }

        override fun onPixelProgress(stage: GenerationStage, progress: Float) {
            genState = GenState.Running(stage, progress)
        }

        override suspend fun confirmPixelImport(
            grid: PixelCalibration.Grid,
            croppedPixels: IntArray,
            croppedWidth: Int,
            croppedHeight: Int,
            result: PatternResult,
            aligned: Boolean,
            smart: Boolean,
        ) {
            val preview = Bitmap.createBitmap(
                croppedPixels, croppedWidth, croppedHeight, Bitmap.Config.ARGB_8888,
            )
            withContext(Dispatchers.Main) {
                this@AppViewModel.imagePixels = croppedPixels
                this@AppViewModel.imageW = croppedWidth
                this@AppViewModel.imageH = croppedHeight
                this@AppViewModel.imagePreview = preview
                this@AppViewModel.cropRect = CropRect.full(croppedWidth, croppedHeight)
                // 裁完图后网格原点就是 (0,0)，保留旧 offset 会让下一次
                // 复查/重算把整张网格推出画面。
                pixelController.clearAfterConfirm(grid)
                this@AppViewModel.pixelArtMode = true
                this@AppViewModel.pixelArtGrid = grid.cols to grid.rows
                this@AppViewModel.gridW = grid.cols
                this@AppViewModel.gridH = grid.rows
                this@AppViewModel.filterLevel = FilterLevel.NONE
                this@AppViewModel.cropGridUserSet = true
                this@AppViewModel.cropPreview = null
                this@AppViewModel.paramsFromCrop = false
                this@AppViewModel.adaptiveAnchor = 0
                this@AppViewModel.adaptiveScale = 1f
                this@AppViewModel.genState = GenState.Running(GenerationStage.DOWNSAMPLE, 0f)
            }
            genState = GenState.Done(result)
            saveAfterGenerate(result)
            withContext(Dispatchers.Main) {
                projectPages = listOf(result.cells)
                editor = EditorEngine(result.width, result.height, result.cells)
                editorRevision++
                editorTool = EditorTool.NONE
                editorEraseRegion = false
                editorFromLibrary = false
                previewReturn = Screen.HOME
                previewReturnTab = HomeTab.CREATE
                toast = when {
                    aligned -> "已对齐 ${grid.cols}×${grid.rows}，进入编辑"
                    smart -> "格线信号偏低，按手动网格生成 ${grid.cols}×${grid.rows}"
                    else -> "已按手动网格生成 ${grid.cols}×${grid.rows}"
                }
                screen = Screen.EDITOR
            }
        }

        override fun onPixelImportFailed(message: String) {
            toast = "校准失败：$message"
        }
    }

    /** 色号图控制器回调：把 screen/toast/repository 这些宿主状态挡在 VM 一侧。 */
    private inner class ChartHost : ChartCalibrationController.Host {
        override fun startChartProject() {
            startNewProject()
            pendingCreationKind = SettingsStore.CreationKind.CHART
            importError = null
        }

        override fun openChartInput(uri: Uri): java.io.InputStream? =
            runCatching { getApplication<Application>().contentResolver.openInputStream(uri) }.getOrNull()

        override val selectedPalette: BeadPalette?
            get() = this@AppViewModel.selectedPalette

        override suspend fun saveChartResult(result: PatternResult) {
            saveAfterGenerate(result)
        }

        override fun onChartPatternReadyForCalibration(detectedGrid: Boolean) {
            screen = Screen.CHART_CALIBRATE
            if (!detectedGrid) {
                toast = "没自动识别到网格，请手动调列数和格子大小"
            }
        }

        override fun onChartRedetected(cols: Int, rows: Int) {
            toast = "已重新识别 ${cols}×${rows}"
        }

        override fun onChartRedetectFailed() {
            toast = "没识别到网格，请手动调列数和格子大小"
        }

        override fun onChartEmptySample() {
            toast = "这组网格没采到颜色，调一下列数或格子大小"
        }

        override fun onChartGenerationStarted() {
            genState = GenState.Running(GenerationStage.MAP, 0.4f)
        }

        override fun onChartPatternGenerated(result: PatternResult) {
            genState = GenState.Done(result)
        }

        override fun onChartPatternSaved() {
            previewReturn = Screen.CHART_CALIBRATE
            previewReturnTab = null
            screen = Screen.PREVIEW
        }

        override fun onChartPatternFailed(message: String) {
            genState = GenState.Error(message)
            toast = "图纸生成失败：$message"
        }

        override fun onChartImportFailed(message: String) {
            importError = message
            toast = "图纸导入失败：$message"
        }
    }

}
