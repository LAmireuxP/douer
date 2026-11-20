package com.pindou.app.ui

import android.graphics.Bitmap
import com.pindou.app.core.image.CropRect
import com.pindou.app.core.pattern.AutoTuner
import com.pindou.app.core.pattern.GenerationStage
import com.pindou.app.core.pattern.PatternResult

/** SAF URI 解码结果：位图 + ARGB 像素 + 尺寸 */
internal class DecodedImage(
    val bitmap: Bitmap,
    val pixels: IntArray,
    val w: Int,
    val h: Int,
)

/** 页面导航（线性流程，M5 引入项目库后再考虑导航库） */
enum class Screen {
    HOME, CROP, PARAMS, PREVIEW, EDITOR, STUDIO, CONSTRUCTION, CHART_CALIBRATE, PIXEL_CALIBRATE,
}

/**
 * 首页底部导航页签。
 *
 * 创建页是唯一的新建入口；项目库只负责浏览、搜索、整理已保存的图纸。
 * 两边不再各放一套「导入照片 / 空白画布」，避免同一个流程出现两个入口。
 */
enum class HomeTab { LIBRARY, CREATE, SETTINGS }

/** 编辑工具 */
enum class EditorTool { NONE, PAINT, ERASE, PICKER, FILL, PAN, SHAPE }

/** 裁剪选区模式：自由（网格跟随选区）或 1:1 正方形 */
enum class CropAspectMode { FREE, SQUARE }

/** 生成过程中的 UI 状态 */
sealed interface GenState {
    data object Idle : GenState
    data class Running(val stage: GenerationStage, val progress: Float) : GenState
    /**
     * [alternatives] 只在自动优选后非空：预览页用它做档位轮播，
     * 切换只改显示，点「应用这版」才写回参数与项目。
     */
    data class Done(
        val result: PatternResult,
        val alternatives: List<AutoTuner.Candidate> = emptyList(),
    ) : GenState
    data class Error(val message: String) : GenState
}

/**
 * 裁剪页实时网格预览：一格就是最终图纸里的一粒豆。
 *
 * [image] 是 gridW×gridH 的缩略位图，画布按选区尺寸一次 drawImage 铺开。
 * 不在 Canvas 里逐格 drawRect——56×50 是 2800 次绘制，每帧都画必然掉帧，
 * 而且位图可以复用现成的 FilterQuality.None 最近邻放大，正好是"看色块"。
 * [rect] 用来判断预览是否还对应当前选区：拖动过程中旧预览不画，
 * 松手防抖算完再出图，避免色块跟着手指歪着跑。
 */
class CropGridPreview(
    val rect: CropRect,
    val gridW: Int,
    val gridH: Int,
    val image: Bitmap,
    val colorCount: Int,
    val elapsedMs: Long,
)
