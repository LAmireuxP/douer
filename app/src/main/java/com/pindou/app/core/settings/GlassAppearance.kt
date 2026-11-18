package com.pindou.app.core.settings

/**
 * 底栏玻璃的可调参数。
 *
 * 所有值统一在这里做范围钳制：DataStore 里的旧值、滑块的浮点误差、
 * 将来迁移进来的脏数据都走同一个入口，UI 不需要再各自 coerce。
 * 单位说明：alpha 是表面白膜的不透明度，blur/refraction/corner 是 dp。
 */
data class GlassAppearance(
    /** 玻璃表面白膜不透明度：0 = 几乎全透，0.62 = 磨砂 */
    val alpha: Float = 0.30f,
    /** 背景模糊半径 */
    val blurDp: Float = 12f,
    /** 边缘折射高度，越大越像厚玻璃 */
    val refractionDp: Float = 26f,
    /** 边缘高光强度 */
    val highlight: Float = 0.72f,
    /** 胶囊圆角 */
    val cornerDp: Float = 32f,
    /** 折射色散：只在 Android 13+ 有 RenderEffect 时生效 */
    val chromatic: Boolean = true,
) {

    fun sanitized(): GlassAppearance = copy(
        alpha = alpha.coerceIn(MIN_ALPHA, MAX_ALPHA),
        blurDp = blurDp.coerceIn(MIN_BLUR, MAX_BLUR),
        refractionDp = refractionDp.coerceIn(MIN_REFRACTION, MAX_REFRACTION),
        highlight = highlight.coerceIn(MIN_HIGHLIGHT, MAX_HIGHLIGHT),
        cornerDp = cornerDp.coerceIn(MIN_CORNER, MAX_CORNER),
    )

    /** 表面渐变的三段 alpha：顶部亮、中部薄、底部留一点反光。 */
    fun surfaceAlphas(dark: Boolean): SurfaceAlphas {
        val scale = if (dark) 0.42f else 1f
        return SurfaceAlphas(
            top = (alpha * 1.25f * scale).coerceIn(0f, 0.72f),
            middle = (alpha * 0.46f * scale).coerceIn(0f, 0.34f),
            bottom = (alpha * 0.22f * scale).coerceIn(0f, 0.20f),
        )
    }

    data class SurfaceAlphas(val top: Float, val middle: Float, val bottom: Float)

    companion object {
        const val MIN_ALPHA = 0.06f
        const val MAX_ALPHA = 0.62f
        const val MIN_BLUR = 0f
        const val MAX_BLUR = 30f
        const val MIN_REFRACTION = 0f
        const val MAX_REFRACTION = 40f
        const val MIN_HIGHLIGHT = 0f
        const val MAX_HIGHLIGHT = 1f
        const val MIN_CORNER = 18f
        const val MAX_CORNER = 40f

        val Default = GlassAppearance()
        val Clear = GlassAppearance(
            alpha = 0.14f,
            blurDp = 8f,
            refractionDp = 18f,
            highlight = 0.55f,
            cornerDp = 32f,
            chromatic = true,
        )
        val Frosted = GlassAppearance(
            alpha = 0.50f,
            blurDp = 24f,
            refractionDp = 8f,
            highlight = 0.95f,
            cornerDp = 28f,
            chromatic = false,
        )

        val Presets: List<Pair<String, GlassAppearance>> = listOf(
            "清透" to Clear,
            "标准" to Default,
            "磨砂" to Frosted,
        )
    }
}
