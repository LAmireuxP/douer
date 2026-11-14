package com.pindou.app.ui.studio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pindou.app.core.editor.SymmetryMode
import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.studio.StudioEngine

/** 画像素工作区的工具。文本按钮直接显示中文，避免再引入一套图标依赖。 */
enum class StudioTool(val label: String, val hint: String) {
    BRUSH("画笔", "拖动连续上色，支持笔刷大小和对称"),
    ERASER("橡皮", "擦成透明格"),
    FILL("油漆桶", "填充连通同色区域"),
    PICKER("吸管", "从画布取色"),
    LINE("直线", "点起点再点终点"),
    RECT("矩形", "点两个角，可描边/填充"),
    ELLIPSE("椭圆", "点两个角，可描边/填充"),
    POLYGON("多边形", "连续点顶点，点「闭合」落形"),
    CURVE("曲线", "点三个控制点画贝塞尔曲线"),
    GRADIENT("渐变", "拖出方向；线性/径向，可选抖动"),
    DITHER("抖动", "两种颜色按 Bayer 矩阵铺满选区/图层"),
    TEXT("文字", "输入文字后点画布落字"),
    SELECT_RECT("矩形选区", "拖出矩形选区"),
    SELECT_LASSO("套索", "手指圈出任意选区"),
    MAGIC_WAND("魔棒", "点一下选中连通同色区域"),
    MOVE("移动选区", "拖动选区内容，不破坏原图"),
    PAN("移动", "单指拖动画布，双指缩放"),
}

/**
 * Compose 侧的工作区状态。
 *
 * StudioEngine 是可变对象，Compose 不会自动观察它；所有改像素/图层/帧的入口
 * 都过 [act]，由 [revision] 触发重绘。画布 draw lambda 只读 revision，
 * 笔画过程中不会让整页重组。
 */
class StudioSession(
    val engine: StudioEngine,
    var palette: BeadPalette,
    var projectId: Long?,
    var name: String,
    initialToolX: Float = -1f,
    initialToolY: Float = -1f,
    initialColorX: Float = -1f,
    initialColorY: Float = -1f,
    initialToolCollapsed: Boolean = false,
    initialColorCollapsed: Boolean = false,
) {
    var revision by mutableIntStateOf(0)
        private set

    /** 默认进入画布是「移动」，避免手指一碰画布就落笔。 */
    var tool by mutableStateOf(StudioTool.PAN)
    var color by mutableIntStateOf(0)
    var secondaryColor by mutableIntStateOf(1)
    val recentColors = androidx.compose.runtime.mutableStateListOf<Int>()

    init {
        val counts = HashMap<Int, Int>()
        for (c in engine.composite(engine.frameIndex)) {
            if (c in palette.colors.indices) counts[c] = (counts[c] ?: 0) + 1
        }
        val used = counts.entries.sortedByDescending { it.value }.map { it.key }.take(6)
        if (used.isNotEmpty()) {
            recentColors.addAll(used)
            color = used[0]
            secondaryColor = used.getOrElse(1) { used[0] }
        } else if (palette.colors.isNotEmpty()) {
            recentColors.add(0)
        }
    }

    var brushSize by mutableIntStateOf(1)
    var circleBrush by mutableStateOf(false)
    var symmetry by mutableStateOf(SymmetryMode.NONE)
    var shapeFilled by mutableStateOf(false)
    var showGrid by mutableStateOf(true)
    var showCenterLines by mutableStateOf(false)
    var showCodes by mutableStateOf(true)
    var pixelPerfect by mutableStateOf(true)
    var onionSkin by mutableStateOf(false)
    var transparentBackground by mutableStateOf(true)
    var fitToScreenTick by mutableIntStateOf(0)

    /**
     * 竖屏两块浮窗的位置：0..1 的比例，-1 = 未自定义。
     * 用比例而不是像素，换分辨率/横竖屏后不会跑到屏幕外。
     */
    var toolX by mutableFloatStateOf(initialToolX)
    var toolY by mutableFloatStateOf(initialToolY)
    var colorX by mutableFloatStateOf(initialColorX)
    var colorY by mutableFloatStateOf(initialColorY)
    var toolCollapsed by mutableStateOf(initialToolCollapsed)
    var colorCollapsed by mutableStateOf(initialColorCollapsed)

    fun resetRailPositions() {
        toolX = -1f
        toolY = -1f
        colorX = -1f
        colorY = -1f
        toolCollapsed = false
        colorCollapsed = false
    }

    var playing by mutableStateOf(false)
    var fps by mutableIntStateOf(10)
    var gradientRadial by mutableStateOf(false)
    var gradientDithered by mutableStateOf(false)
    var ditherDensity by mutableIntStateOf(50)

    /** 多边形/曲线的临时顶点，坐标单位是格。 */
    var pendingPoints by mutableStateOf<List<Int>>(emptyList())

    fun touch() {
        revision++
    }

    fun act(block: (StudioEngine) -> Unit) {
        block(engine)
        revision++
    }

    fun selectColor(index: Int, secondary: Boolean = false) {
        val safe = index.coerceIn(0, palette.colors.size - 1)
        if (secondary) {
            secondaryColor = safe
        } else {
            color = safe
            recentColors.remove(safe)
            recentColors.add(0, safe)
            while (recentColors.size > 6) recentColors.removeAt(recentColors.lastIndex)
        }
        if (!secondary) tool = StudioTool.BRUSH
    }

    fun swapColors() {
        val t = color
        color = secondaryColor
        secondaryColor = t
    }

    fun undo() {
        if (engine.undo()) revision++
    }

    fun redo() {
        if (engine.redo()) revision++
    }

    fun clearPending() {
        if (pendingPoints.isNotEmpty()) {
            pendingPoints = emptyList()
            revision++
        }
    }
}
