package com.pindou.app.ui

/**
 * 页面层级：转场方向靠它判定，不靠调用点。
 *
 * 同层（如 CROP→PARAMS、PREVIEW→EDITOR）按「前进」处理——用户感觉都是往里走。
 * 这样返回键、深链、任意跳转都不用手写方向，target 与 initial 一比就有答案。
 */
object ScreenOrder {

    const val HOME = 0
    const val FLOW = 1      // 裁剪 / 参数
    const val RESULT = 2    // 预览
    const val DETAIL = 3    // 编辑器 / 画像素 / 施工 / 校准

    fun rank(screen: Screen): Int = when (screen) {
        Screen.HOME -> HOME
        Screen.CROP, Screen.PARAMS -> FLOW
        Screen.PREVIEW -> RESULT
        Screen.EDITOR, Screen.STUDIO, Screen.CONSTRUCTION,
        Screen.CHART_CALIBRATE, Screen.PIXEL_CALIBRATE,
        -> DETAIL
    }

    /** 同层或更深 = 前进（右滑入），更浅 = 后退 */
    fun isForward(from: Screen, to: Screen): Boolean = rank(to) >= rank(from)
}
