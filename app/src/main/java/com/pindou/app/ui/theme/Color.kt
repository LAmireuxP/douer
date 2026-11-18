package com.pindou.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 当前主题的可变色板。
 *
 * 以前这里是 `object ClayColors` 的一堆 `val`（写死天蓝）。现在值由
 * MainActivity 按用户选的 [AppTheme] 提供，`ClayColors.Xxx` 保留成
 * `@Composable get()` 门面，17 个文件、186 处调用点不用改一行。
 *
 * 唯一代价：这些属性只能在 Composable 里读。Canvas/DrawScope 里的
 * 绘制函数改成显式收颜色参数（见 CropScreen.drawCropUi 等）。
 */
val LocalPalette = staticCompositionLocalOf { AppThemes.Douer.light }

val LocalShapeStyle = staticCompositionLocalOf { AppThemes.Douer.style }

object ClayColors {
    private val p: Palette
        @Composable @ReadOnlyComposable get() = LocalPalette.current

    val Primary: Color @Composable @ReadOnlyComposable get() = p.primary
    val PrimaryActive: Color @Composable @ReadOnlyComposable get() = p.primaryActive
    val PrimaryInk: Color @Composable @ReadOnlyComposable get() = p.primaryInk
    val PrimaryDisabled: Color @Composable @ReadOnlyComposable get() = p.primaryDisabled
    val Ink: Color @Composable @ReadOnlyComposable get() = p.ink
    val Body: Color @Composable @ReadOnlyComposable get() = p.body
    val BodyStrong: Color @Composable @ReadOnlyComposable get() = p.bodyStrong
    val Muted: Color @Composable @ReadOnlyComposable get() = p.muted
    val MutedSoft: Color @Composable @ReadOnlyComposable get() = p.mutedSoft
    val Hairline: Color @Composable @ReadOnlyComposable get() = p.hairline
    val HairlineSoft: Color @Composable @ReadOnlyComposable get() = p.hairlineSoft
    val Canvas: Color @Composable @ReadOnlyComposable get() = p.canvas
    val Stage: Color @Composable @ReadOnlyComposable get() = p.stage
    val SurfaceSoft: Color @Composable @ReadOnlyComposable get() = p.surfaceSoft
    val SurfaceCard: Color @Composable @ReadOnlyComposable get() = p.surfaceCard
    val SurfaceStrong: Color @Composable @ReadOnlyComposable get() = p.surfaceStrong
    val SurfaceDark: Color @Composable @ReadOnlyComposable get() = p.surfaceDark
    val SurfaceDarkElevated: Color @Composable @ReadOnlyComposable get() = p.surfaceDarkElevated
    val OnPrimary: Color @Composable @ReadOnlyComposable get() = p.onPrimary
    val OnDark: Color @Composable @ReadOnlyComposable get() = p.onDark
    val OnDarkSoft: Color @Composable @ReadOnlyComposable get() = p.onDarkSoft
    val BrandBlue: Color @Composable @ReadOnlyComposable get() = p.brandBlue
    val BrandPink: Color @Composable @ReadOnlyComposable get() = p.brandPink
    val BrandTeal: Color @Composable @ReadOnlyComposable get() = p.brandTeal
    val BrandLavender: Color @Composable @ReadOnlyComposable get() = p.brandLavender
    val BrandPeach: Color @Composable @ReadOnlyComposable get() = p.brandPeach
    val BrandOchre: Color @Composable @ReadOnlyComposable get() = p.brandOchre
    val BrandMint: Color @Composable @ReadOnlyComposable get() = p.brandMint
    val BrandCoral: Color @Composable @ReadOnlyComposable get() = p.brandCoral
    val Success: Color @Composable @ReadOnlyComposable get() = p.success
    val Warning: Color @Composable @ReadOnlyComposable get() = p.warning
    val Error: Color @Composable @ReadOnlyComposable get() = p.error
    val TintGreen: Color @Composable @ReadOnlyComposable get() = p.tintGreen
    val TintMint: Color @Composable @ReadOnlyComposable get() = p.tintMint
    val TintButter: Color @Composable @ReadOnlyComposable get() = p.tintButter
    val TintSky: Color @Composable @ReadOnlyComposable get() = p.tintSky
    val TintRose: Color @Composable @ReadOnlyComposable get() = p.tintRose
}
