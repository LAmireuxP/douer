package com.pindou.app.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 全局动效开关。
 *
 * 系统「移除动画」开启后，ValueAnimator.areAnimatorsEnabled() 返回 false，
 * MainActivity 把它注入到这里。降级不是把动画时长设为 0：位移动画直接归位，
 * 颜色/透明度这类 Tier 3 过渡保留极短时长，界面不会像瞬间换了一张图。
 */
@Immutable
data class MotionPreferences(
    val enabled: Boolean = true,
) {
    fun duration(normal: Int, reduced: Int = 0): Int = if (enabled) normal else reduced
}

val LocalMotion = staticCompositionLocalOf { MotionPreferences() }

@Composable
fun ProvidePindouMotion(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMotion provides MotionPreferences(enabled),
        content = content,
    )
}

/**
 * 全部时长/弹簧集中在这里，调用点不再散落魔法数字。
 * 弹簧参数按 transitions-dev 的 100–250ms 交互节奏折算：
 * dampingRatio 越高越稳，stiffness 越高越快。
 */
object Motion {
    val Press: SpringSpec<Float> = spring(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMedium,
    )
    val Pill: SpringSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow,
    )
    val Reveal: SpringSpec<Float> = spring(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessMediumLow,
    )

    const val FAST = 140
    const val BASE = 200
    const val EMPHASIS = 260
    const val SHAKE = 320

    fun <T> spec(normal: FiniteAnimationSpec<T>, reduced: FiniteAnimationSpec<T>, motion: MotionPreferences): FiniteAnimationSpec<T> =
        if (motion.enabled) normal else reduced
}

/**
 * 点按缩放：卡片、chip、tab 都用这一层，不改变点击/长按语义。
 * graphicsLayer 只改合成层，不触发布局与重绘。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
): Modifier {
    val motion = LocalMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) pressedScale else 1f,
        animationSpec = Motion.spec(Motion.Press, snap(), motion),
        label = "motionPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 数字/文字换值：旧值向上淡出，新值从下往上淡入并轻微放大。
 * 对应 transitions-dev 的 number pop-in / text states swap；blur 不做，
 * 因为 Compose 的 RenderEffect 在 API 31 以下没有等价物。
 */
@Composable
fun AnimatedNumber(
    value: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    val motion = LocalMotion.current
    Box(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                if (!motion.enabled) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    (
                        fadeIn(tween(Motion.FAST)) +
                            slideInVertically(tween(Motion.BASE)) { it / 2 } +
                            scaleIn(tween(Motion.BASE), initialScale = 0.88f)
                        ) togetherWith (
                        fadeOut(tween(Motion.FAST / 2)) +
                            slideOutVertically(tween(Motion.FAST)) { -it / 2 } +
                            scaleOut(tween(Motion.FAST), targetScale = 0.94f)
                        )
                }
            },
            label = "motionAnimatedNumber",
        ) { text ->
            Text(
                text = text,
                style = style,
                color = color,
                fontWeight = fontWeight,
            )
        }
    }
}

/** 底栏图标切换：同一个槽位里 cross-fade + scale，位置不跳。 */
@Composable
fun AnimatedIconSwap(
    selected: Boolean,
    outlined: ImageVector,
    filled: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 21.dp,
) {
    val motion = LocalMotion.current
    AnimatedContent(
        targetState = selected,
        modifier = modifier.size(size),
        transitionSpec = {
            if (!motion.enabled) {
                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            } else {
                (
                    fadeIn(tween(Motion.FAST)) +
                        scaleIn(tween(Motion.BASE), initialScale = 0.72f)
                    ) togetherWith (
                    fadeOut(tween(Motion.FAST / 2)) +
                        scaleOut(tween(Motion.FAST), targetScale = 0.78f)
                    )
            }
        },
        label = "motionIconSwap",
    ) { active ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (active) filled else outlined,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 面板/弹窗内容进入：用透明度 + 小位移，不用整屏放大。
 * 用于 BottomSheet 内容，外层 sheet 自己仍负责上滑手势与退出。
 */
@Composable
fun PanelReveal(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = LocalMotion.current
    var shown by remember { mutableStateOf(!motion.enabled) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = if (motion.enabled) {
            fadeIn(tween(Motion.BASE, delayMillis = 40)) +
                slideInVertically(tween(Motion.EMPHASIS)) { it / 14 }
        } else {
            EnterTransition.None
        },
        exit = ExitTransition.None,
    ) {
        content()
    }
}

/** 列表首帧错峰进入；只在卡片第一次组合时播放。 */
@Composable
fun StaggeredAppear(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = LocalMotion.current
    var shown by remember { mutableStateOf(!motion.enabled) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = if (motion.enabled) {
            fadeIn(tween(Motion.BASE, delayMillis = index * 24)) +
                slideInVertically(tween(Motion.BASE, delayMillis = index * 24)) { it / 8 }
        } else {
            EnterTransition.None
        },
        exit = ExitTransition.None,
    ) {
        content()
    }
}

/**
 * 错误抖动：按段折返，最后回到 0。只改 offset，不触发布局。
 * trigger 每次递增即可重播；0 表示尚未触发。
 */
@Composable
fun Modifier.shake(
    trigger: Int,
    enabled: Boolean = true,
    distance: Dp = 5.dp,
): Modifier {
    val motion = LocalMotion.current
    if (!enabled || !motion.enabled) return this
    val density = LocalDensity.current
    val px = with(density) { distance.toPx() }
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        offset.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = Motion.SHAKE
                -px at 0
                px at 70
                -px * 0.62f at 135
                px * 0.38f at 195
                -px * 0.18f at 250
                0f at Motion.SHAKE
            },
        )
    }
    return offset { IntOffset(offset.value.roundToInt(), 0) }
}
