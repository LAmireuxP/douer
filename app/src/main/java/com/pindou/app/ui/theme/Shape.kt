package com.pindou.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// shadcn/ui 圆角：控件 10 / 卡片 16 / 弹层 22。
// 现在按主题的 ShapeStyle 生成：紧凑型（Linear/Stripe）比默认小一档，
// 胶囊型（Spotify/Airbnb/Notion）按钮直接 999dp。
fun ClayShapesOf(style: ShapeStyle): Shapes = Shapes(
    extraSmall = RoundedCornerShape(style.button),
    small = RoundedCornerShape(style.button),
    medium = RoundedCornerShape(style.card),
    large = RoundedCornerShape(style.card),
    extraLarge = RoundedCornerShape(22.dp),
)

/** 兼容旧调用点：默认主题的圆角。 */
val ClayShapes: Shapes = ClayShapesOf(AppThemes.Douer.style)

val PillShape = RoundedCornerShape(9999.dp)
