package com.pindou.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.pindou.app.ui.motion.LocalMotion
import com.pindou.app.ui.motion.Motion

/**
 * 把一套 [Palette] 映射到 Material3 的 colorScheme。
 *
 * 映射规则写死在这里，主题只需要给 Palette：新增主题时不会出现
 * 「MaterialTheme.primary 和 ClayColors.Primary 不是同一个颜色」的错位。
 */
private fun Palette.toLightScheme() = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = tintGreen,
    onPrimaryContainer = lerp(ink, primary, 0.45f),
    secondary = surfaceSoft,
    onSecondary = ink,
    secondaryContainer = tintGreen,
    onSecondaryContainer = lerp(ink, primary, 0.45f),
    tertiary = brandTeal,
    onTertiary = Color.White,
    tertiaryContainer = tintMint,
    onTertiaryContainer = lerp(ink, brandTeal, 0.40f),
    background = canvas,
    onBackground = ink,
    surface = surfaceCard,
    onSurface = ink,
    surfaceVariant = surfaceSoft,
    onSurfaceVariant = muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = canvas,
    surfaceContainer = surfaceCard,
    surfaceContainerHigh = surfaceSoft,
    surfaceContainerHighest = surfaceStrong,
    inverseSurface = surfaceDark,
    inverseOnSurface = canvas,
    inversePrimary = primaryInk,
    outline = hairline,
    outlineVariant = hairlineSoft,
    error = error,
    onError = Color.White,
    errorContainer = mix(primaryDisabled, error, 0.10f),
    onErrorContainer = lerp(error, Color.Black, 0.35f),
)

private fun Palette.toDarkScheme() = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = tintSky,
    onPrimaryContainer = primaryInk,
    secondary = surfaceSoft,
    onSecondary = onDark,
    secondaryContainer = surfaceStrong,
    onSecondaryContainer = onDark,
    tertiary = brandTeal,
    onTertiary = Color(0xFF0A2620),
    tertiaryContainer = tintMint,
    onTertiaryContainer = onDark,
    background = canvas,
    onBackground = onDark,
    surface = surfaceCard,
    onSurface = onDark,
    surfaceVariant = surfaceSoft,
    onSurfaceVariant = onDarkSoft,
    surfaceContainerLowest = lerp(canvas, Color.Black, 0.35f),
    surfaceContainerLow = canvas,
    surfaceContainer = surfaceCard,
    surfaceContainerHigh = surfaceStrong,
    surfaceContainerHighest = lerp(surfaceStrong, onDark, 0.06f),
    inverseSurface = canvas,
    inverseOnSurface = ink,
    inversePrimary = primaryActive,
    outline = hairline,
    outlineVariant = hairlineSoft,
    error = error,
    onError = Color(0xFF3A1209),
    errorContainer = lerp(canvas, error, 0.30f),
    onErrorContainer = onDark,
)

/**
 * 主题切换用颜色过渡而不是整页缩放。
 *
 * 大块背景、卡面、正文、主色、描边一起做 260ms 颜色插值，属于
 * prefers-reduced-motion 里的 Tier 3（不造成前庭不适）；系统移除动画时
 * 直接落到目标色。只动画这一组高频 token，其余 30 多个 token 保持原样，
 * 避免一次主题切换把整棵树的重绘面铺得过大。
 */
 /** 渐变主题的双色（已按明暗适配）；null = 纯色主题，背景走 colorScheme.background */
 val LocalThemeGradient = staticCompositionLocalOf<List<Color>?> { null }

@Composable
private fun ColorScheme.withMotion(): ColorScheme {
    val motion = LocalMotion.current
    if (!motion.enabled) return this
    val spec = tween<Color>(Motion.EMPHASIS)
    val animatedPrimary by animateColorAsState(primary, spec, label = "themePrimary")
    val animatedOnPrimary by animateColorAsState(onPrimary, spec, label = "themeOnPrimary")
    val animatedBackground by animateColorAsState(background, spec, label = "themeBackground")
    val animatedOnBackground by animateColorAsState(onBackground, spec, label = "themeOnBackground")
    val animatedSurface by animateColorAsState(surface, spec, label = "themeSurface")
    val animatedOnSurface by animateColorAsState(onSurface, spec, label = "themeOnSurface")
    val animatedSurfaceVariant by animateColorAsState(surfaceVariant, spec, label = "themeSurfaceVariant")
    val animatedOnSurfaceVariant by animateColorAsState(onSurfaceVariant, spec, label = "themeOnSurfaceVariant")
    val animatedOutline by animateColorAsState(outline, spec, label = "themeOutline")
    return copy(
        primary = animatedPrimary,
        onPrimary = animatedOnPrimary,
        background = animatedBackground,
        onBackground = animatedOnBackground,
        surface = animatedSurface,
        onSurface = animatedOnSurface,
        surfaceVariant = animatedSurfaceVariant,
        onSurfaceVariant = animatedOnSurfaceVariant,
        outline = animatedOutline,
    )
}

@Composable
fun PindouTheme(
    appTheme: AppTheme = AppThemes.Douer,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = appTheme.palette(darkTheme)
    val baseScheme = if (darkTheme) palette.toDarkScheme() else palette.toLightScheme()
    val scheme = baseScheme.withMotion()
    val shapes = ClayShapesOf(appTheme.style)
    // 渐变色彩在深色主题下与画布混合，避免整屏高亮刺眼；浅色直接用原双色
    val gradientColors = appTheme.gradient?.let { (first, second) ->
        if (darkTheme) {
            listOf(lerp(first, palette.canvas, 0.72f), lerp(second, palette.canvas, 0.72f))
        } else {
            listOf(first, second)
        }
    }

    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalShapeStyle provides appTheme.style,
        LocalThemeGradient provides gradientColors,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ClayTypography,
            shapes = shapes,
            content = content,
        )
    }
}
