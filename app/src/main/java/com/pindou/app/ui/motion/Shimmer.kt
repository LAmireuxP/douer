package com.pindou.app.ui.motion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 流光扫过：只用于「等加载 / 等完成」，不用在静态装饰上。
 *
 * 相位在 draw 阶段读，扫光只触发重绘不触发重组；「移除动画」时直接返回原 Modifier，
 * 一帧都不多画。高光默认取 10% 主色，深浅主题都吃 token。
 */
@Composable
fun Modifier.shimmer(
    enabled: Boolean = true,
    periodMs: Int = 1200,
    highlight: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
): Modifier {
    if (!enabled || !LocalMotion.current.enabled) return this
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerPhase",
    )
    return drawWithContent {
        drawContent()
        val width = size.width
        if (width <= 0f) return@drawWithContent
        val band = width * 0.45f
        val x = -band + (width + band * 2f) * phase
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent,
                0.5f to highlight,
                1f to Color.Transparent,
                start = Offset(x, 0f),
                end = Offset(x + band, size.height * 0.6f),
            ),
        )
    }
}
