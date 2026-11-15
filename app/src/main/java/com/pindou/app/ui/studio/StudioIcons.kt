package com.pindou.app.ui.studio

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.pindou.app.ui.icon.MorphIcons

/** Studio 工具图标：未在 MorphIcons 里的形状/选区图标补在这里。 */
object StudioIcons {
    val Line: ImageVector by lazy { strokeIcon("Line", "M5 19 19 5") }

    val Rect: ImageVector by lazy {
        strokeIcon("Rect", "M4 4h16v16H4z")
    }

    val Ellipse: ImageVector by lazy {
        strokeIcon("Ellipse", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18")
    }

    val Polygon: ImageVector by lazy {
        strokeIcon("Polygon", "M12 3l8.5 6.2-3.2 10.1H6.7L3.5 9.2z")
    }

    val Curve: ImageVector by lazy {
        strokeIcon("Curve", "M4 18c0-8 4-14 8-14s8 6 8 14")
    }

    val Gradient: ImageVector by lazy {
        strokeIcon(
            "Gradient",
            "M4 4h16v16H4z",
            "M4 9h16",
            "M4 14h16",
            "M4 19h16",
        )
    }

    val Dither: ImageVector by lazy {
        strokeIcon(
            "Dither",
            "M4 4h4v4H4z",
            "M12 4h4v4h-4z",
            "M8 8h4v4H8z",
            "M16 8h4v4h-4z",
            "M4 12h4v4H4z",
            "M12 12h4v4h-4z",
            "M8 16h4v4H8z",
            "M16 16h4v4h-4z",
        )
    }

    val SelectRect: ImageVector by lazy {
        strokeIcon(
            "SelectRect",
            "M4 8V6a2 2 0 0 1 2-2h2",
            "M16 4h2a2 2 0 0 1 2 2v2",
            "M20 16v2a2 2 0 0 1-2 2h-2",
            "M8 20H6a2 2 0 0 1-2-2v-2",
        )
    }

    val Lasso: ImageVector by lazy {
        strokeIcon(
            "Lasso",
            // 椭圆套索收在 4..20 的安全区，右下留出绳尾，避免图标在 20dp 时被裁切。
            "M4 10c0-3.3 3.6-6 8-6s8 2.7 8 6-3.6 6-8 6c-1.7 0-3.2-.3-4.5-1",
            "M7.5 15.2c-1.6.8-2.5 2-2.5 3.3 0 1 .6 1.8 1.6 2.2",
        )
    }

    val Wand: ImageVector by lazy {
        strokeIcon(
            "Wand",
            "m15 4 5 5",
            "M13 6 3 16l5 5L18 11",
            "M14 2l1 3",
            "M20 8l3 1",
        )
    }

    val Move: ImageVector by lazy {
        strokeIcon(
            "Move",
            "M12 2v20",
            "M2 12h20",
            "m5 9-3 3 3 3",
            "m19 9 3 3-3 3",
            "m9 5 3-3 3 3",
            "m9 19 3 3 3-3",
        )
    }

    val Layers: ImageVector by lazy {
        strokeIcon(
            "Layers",
            "M12 2 2 7l10 5 10-5z",
            "m2 17 10 5 10-5",
            "m2 12 10 5 10-5",
        )
    }

    val Frames: ImageVector by lazy {
        strokeIcon(
            "Frames",
            "M4 4h16v16H4z",
            "M4 9h16",
            "M4 15h16",
        )
    }

    val Palette: ImageVector by lazy {
        strokeIcon(
            "Palette",
            "M12 3a9 9 0 1 0 0 18c1.7 0 2-1 2-2 0-1.5 1-2 2.5-2H18a3 3 0 0 0 3-3c0-6-4-11-9-11z",
            "M7.5 10.5h.01",
            "M10.5 7.5h.01",
            "M14.5 7.5h.01",
        )
    }
}

fun StudioTool.icon(): ImageVector = when (this) {
    StudioTool.BRUSH -> MorphIcons.Brush
    StudioTool.ERASER -> MorphIcons.Eraser
    StudioTool.FILL -> MorphIcons.PaintBucket
    StudioTool.PICKER -> MorphIcons.Pipette
    StudioTool.LINE -> StudioIcons.Line
    StudioTool.RECT -> StudioIcons.Rect
    StudioTool.ELLIPSE -> StudioIcons.Ellipse
    StudioTool.POLYGON -> StudioIcons.Polygon
    StudioTool.CURVE -> StudioIcons.Curve
    StudioTool.GRADIENT -> StudioIcons.Gradient
    StudioTool.DITHER -> StudioIcons.Dither
    StudioTool.TEXT -> MorphIcons.Type
    StudioTool.SELECT_RECT -> StudioIcons.SelectRect
    StudioTool.SELECT_LASSO -> StudioIcons.Lasso
    StudioTool.MAGIC_WAND -> StudioIcons.Wand
    StudioTool.MOVE -> StudioIcons.Move
    StudioTool.PAN -> MorphIcons.Hand
}

private fun strokeIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
