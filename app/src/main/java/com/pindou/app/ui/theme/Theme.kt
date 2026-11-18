package com.pindou.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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

@Composable
fun PindouTheme(
    appTheme: AppTheme = AppThemes.Douer,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = appTheme.palette(darkTheme)
    val scheme = if (darkTheme) palette.toDarkScheme() else palette.toLightScheme()
    val shapes = ClayShapesOf(appTheme.style)

    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalShapeStyle provides appTheme.style,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ClayTypography,
            shapes = shapes,
            content = content,
        )
    }
}
