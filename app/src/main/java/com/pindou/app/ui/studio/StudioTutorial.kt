package com.pindou.app.ui.studio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.motion.LocalMotion
import com.pindou.app.ui.motion.Motion
import com.pindou.app.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * 教程锚点：全部存屏幕窗口坐标。
 *
 * 不用根节点局部坐标的原因：StudioScreen 外面还套着 Scaffold 的系统栏
 * innerPadding，root 的 0,0 不在窗口左上角；存窗口坐标后，覆盖层只要减去
 * 自己的 boundsInWindow 就能换算，横竖屏切换也不会错位。
 */
@Stable
class StudioTourAnchors {
    var root: Rect = Rect.Zero
    var canvasSize: Rect? by mutableStateOf(null)
    var canvas: Rect? by mutableStateOf(null)
    var toolRail: Rect? by mutableStateOf(null)
    var colorRail: Rect? by mutableStateOf(null)
    var zoom: Rect? by mutableStateOf(null)
    var bottomBar: Rect? by mutableStateOf(null)
    var layersAction: Rect? by mutableStateOf(null)
}

private data class StudioTourStep(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val placement: TourPlacement = TourPlacement.Auto,
    val primaryAction: TourPrimaryAction = TourPrimaryAction.Next,
    val target: (StudioTourAnchors) -> Rect?,
)

private enum class TourPlacement { Auto, RightOfTarget, LeftOfTarget }
private enum class TourPrimaryAction { Next, OpenLayerImport }

private fun tourSteps(canvasLabel: String) = listOf(
    StudioTourStep(
        icon = MorphIcons.Grid,
        title = "先定画布尺寸",
        body = "顶栏这个 $canvasLabel 可以点，随时改尺寸和参考线。" +
            "新手先用 40×40 左右的小画布，格子大、好下笔。",
        target = { it.canvasSize },
    ),
    StudioTourStep(
        icon = MorphIcons.Hand,
        title = "单指画，双指看",
        body = "单指拖动是上色；双指捏合缩放、双指拖动平移。" +
            "双指刚落下时不会误画一格，放心把画布放大。",
        // 整块画布几乎占满屏幕，挖满会看不到遮罩；只框上半段，
        // 卡片放下面，既能指出画布位置又保留「聚光灯」层次。
        target = { anchors ->
            anchors.canvas?.let { canvas ->
                Rect(
                    left = canvas.left,
                    top = canvas.top,
                    right = canvas.right,
                    bottom = canvas.top + canvas.height * 0.42f,
                )
            }
        },
    ),
    StudioTourStep(
        icon = MorphIcons.ImagePlus,
        title = "把原图铺到底层",
        body = "点底部「图层」→「导入底层图片」，选一张照片。" +
            "照片只铺在绘制层下面做参考，不会自动转成像素，也不参与 PNG/PDF/GIF 导出。",
        primaryAction = TourPrimaryAction.OpenLayerImport,
        target = { it.layersAction },
    ),
    StudioTourStep(
        icon = MorphIcons.Brush,
        title = "左侧是工具",
        body = "画笔、橡皮、油漆桶、吸管、形状都在左侧浮窗。" +
            "按住浮窗顶部把手可以拖走，别挡住要画的位置。",
        placement = TourPlacement.RightOfTarget,
        target = { it.toolRail },
    ),
    StudioTourStep(
        icon = MorphIcons.Palette,
        title = "右侧是颜色",
        body = "色号栏按品牌色卡分组，点色块就选中颜色。" +
            "点顶部色号名可以换整套色卡，画出来的色号才能对上实物。",
        placement = TourPlacement.LeftOfTarget,
        target = { it.colorRail },
    ),
    StudioTourStep(
        icon = MorphIcons.Expand,
        title = "放大画细节",
        body = "右下角 − / + 调倍率，「适应」一键回到完整画面。" +
            "画眼睛、描边这类小结构时先放大，找整体位置时再缩回去。",
        target = { it.zoom },
    ),
)

/**
 * Tour 式新手引导：遮罩上按锚点挖洞，提示卡自动躲在洞的上方或下方，
 * 找不到锚点时退化成居中的说明卡（比如横屏没有左右浮窗）。
 */
@Composable
fun StudioTourOverlay(
    step: Int,
    anchors: StudioTourAnchors,
    canvasLabel: String,
    onStepChange: (Int) -> Unit,
    onOpenLayerImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val steps = remember(canvasLabel) { tourSteps(canvasLabel) }
    val safeStep = step.coerceIn(steps.indices)
    val page = steps[safeStep]
    val density = LocalDensity.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.35f
    val motion = LocalMotion.current

    BackHandler(onBack = onDismiss)

    val targetRect = page.target(anchors)
    val root = anchors.root
    val pad = with(density) { 8.dp.toPx() }
    val corner = with(density) { 18.dp.toPx() }

    val target = if (targetRect == null || root.width <= 0f) null else {
        Rect(
            left = targetRect.left - root.left - pad,
            top = targetRect.top - root.top - pad,
            right = targetRect.right - root.left + pad,
            bottom = targetRect.bottom - root.top + pad,
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val fullW = constraints.maxWidth.toFloat()
        val fullH = constraints.maxHeight.toFloat()
        val margin = with(density) { 16.dp.toPx() }
        val gap = with(density) { 14.dp.toPx() }
        val defaultCardWidthPx =
            minOf(fullW - margin * 2f, with(density) { 380.dp.toPx() })
        val minSideCardWidthPx = with(density) { 220.dp.toPx() }
        // 先给一个 230dp 的估值，真正布局后用 onSizeChanged 校正，
        // 避免第一帧卡片从屏幕中心跳到洞口旁边。
        var cardHeightPx by remember {
            mutableStateOf(with(density) { 230.dp.toPx() })
        }

        // 洞的四条边分别做弹簧，换步骤时像镜头在移动，而不是整块瞬移
        val holeLeft by animateFloatAsState(
            targetValue = target?.left ?: (fullW / 2f),
            animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
            label = "tourHoleLeft",
        )
        val holeTop by animateFloatAsState(
            targetValue = target?.top ?: (fullH / 2f),
            animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
            label = "tourHoleTop",
        )
        val holeRight by animateFloatAsState(
            targetValue = target?.right ?: (fullW / 2f),
            animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
            label = "tourHoleRight",
        )
        val holeBottom by animateFloatAsState(
            targetValue = target?.bottom ?: (fullH / 2f),
            animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
            label = "tourHoleBottom",
        )

        val hole = Rect(
            left = holeLeft.coerceIn(0f, fullW),
            top = holeTop.coerceIn(0f, fullH),
            right = holeRight.coerceIn(0f, fullW),
            bottom = holeBottom.coerceIn(0f, fullH),
        )
        val hasHole = target != null && hole.width > 1f && hole.height > 1f

        val sideRightWidth = fullW - hole.right - gap - margin
        val sideLeftWidth = hole.left - gap - margin
        val sideRight = hasHole && page.placement == TourPlacement.RightOfTarget &&
            sideRightWidth >= minSideCardWidthPx
        val sideLeft = hasHole && page.placement == TourPlacement.LeftOfTarget &&
            sideLeftWidth >= minSideCardWidthPx
        val cardWidthPx = when {
            sideRight -> minOf(defaultCardWidthPx, sideRightWidth)
            sideLeft -> minOf(defaultCardWidthPx, sideLeftWidth)
            else -> defaultCardWidthPx
        }

        val below = fullH - hole.bottom - gap - margin
        val above = hole.top - gap - margin
        val sideTop = (hole.center.y - cardHeightPx / 2f)
            .coerceIn(margin, (fullH - cardHeightPx - margin).coerceAtLeast(margin))
        val cardTop: Float
        val cardLeft: Float
        when {
            sideRight -> {
                cardLeft = hole.right + gap
                cardTop = sideTop
            }
            sideLeft -> {
                cardLeft = hole.left - gap - cardWidthPx
                cardTop = sideTop
            }
            else -> {
                cardLeft = if (hasHole) {
                    (hole.center.x - cardWidthPx / 2f)
                        .coerceIn(margin, fullW - margin - cardWidthPx)
                } else {
                    (fullW - cardWidthPx) / 2f
                }
                cardTop = when {
                    !hasHole -> (fullH - cardHeightPx) / 2f
                    below >= cardHeightPx -> hole.bottom + gap
                    above >= cardHeightPx -> hole.top - gap - cardHeightPx
                    else -> (fullH - cardHeightPx) / 2f
                }.coerceIn(margin, (fullH - cardHeightPx - margin).coerceAtLeast(margin))
            }
        }

        val scrim = Color.Black.copy(alpha = if (isDark) 0.66f else 0.50f)
        val accent = MaterialTheme.colorScheme.primary
        // 步骤切换只让内容淡入 + 轻抬，Surface 的布局高度从第一帧就是满的，
        // 避免 onSizeChanged 先收到 0 再跳回真实高度导致卡片位置抖一下。
        var cardShown by remember(safeStep) { mutableStateOf(!motion.enabled) }
        LaunchedEffect(safeStep) { cardShown = true }
        val cardAlpha by animateFloatAsState(
            targetValue = if (cardShown) 1f else 0f,
            animationSpec = tween(if (motion.enabled) Motion.BASE else 0),
            label = "tourCardAlpha",
        )
        val cardShift by animateFloatAsState(
            targetValue = if (cardShown) 0f else 12f,
            animationSpec = tween(if (motion.enabled) Motion.BASE else 0),
            label = "tourCardShift",
        )

        // 全屏吃点击，防止引导期间误触画布；Canvas 负责挖洞和描边
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(scrim)
                if (hasHole) {
                    val holeSize = Size(hole.width, hole.height)
                    val topLeft = Offset(hole.left, hole.top)
                    // Clear 只在离屏层里生效，不会把下面的页面一起擦掉
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = topLeft,
                        size = holeSize,
                        cornerRadius = CornerRadius(corner, corner),
                        blendMode = BlendMode.Clear,
                    )
                    drawRoundRect(
                        color = accent.copy(alpha = 0.16f),
                        topLeft = topLeft - Offset(3.dp.toPx(), 3.dp.toPx()),
                        size = Size(
                            holeSize.width + 6.dp.toPx(),
                            holeSize.height + 6.dp.toPx(),
                        ),
                        cornerRadius = CornerRadius(corner + 3.dp.toPx(), corner + 3.dp.toPx()),
                        style = Stroke(width = 6.dp.toPx()),
                    )
                    drawRoundRect(
                        color = accent.copy(alpha = 0.92f),
                        topLeft = topLeft,
                        size = holeSize,
                        cornerRadius = CornerRadius(corner, corner),
                        style = Stroke(width = 1.6.dp.toPx()),
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .offset { IntOffset(cardLeft.roundToInt(), cardTop.roundToInt()) }
                .width(with(density) { cardWidthPx.toDp() })
                .onSizeChanged { cardHeightPx = it.height.toFloat() },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 20.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = cardAlpha
                        translationY = cardShift
                    }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "新手引导",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${safeStep + 1} / ${steps.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text("跳过", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            page.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        page.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Text(
                    page.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = Spacing.sm),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    repeat(steps.size) { index ->
                        val indicatorWidth by animateDpAsState(
                            targetValue = if (index == safeStep) 16.dp else 6.dp,
                            animationSpec = if (motion.enabled) {
                                spring(
                                    dampingRatio = 0.82f,
                                    stiffness = Spring.StiffnessMediumLow,
                                )
                            } else {
                                snap()
                            },
                            label = "tourStepIndicator",
                        )
                        Box(
                            modifier = Modifier
                                .size(indicatorWidth, 6.dp)
                                .background(
                                    if (index == safeStep) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                                    RoundedCornerShape(999.dp),
                                ),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (safeStep > 0) {
                        TextButton(
                            onClick = { onStepChange(safeStep - 1) },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text("上一步", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Button(
                        onClick = {
                            when {
                                page.primaryAction == TourPrimaryAction.OpenLayerImport ->
                                    onOpenLayerImport()
                                safeStep < steps.lastIndex -> onStepChange(safeStep + 1)
                                else -> onDismiss()
                            }
                        },
                        modifier = Modifier.height(38.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        Text(
                            when {
                                page.primaryAction == TourPrimaryAction.OpenLayerImport -> "去导入"
                                safeStep < steps.lastIndex -> "下一步"
                                else -> "开始画"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
