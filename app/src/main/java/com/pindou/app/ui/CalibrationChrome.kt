package com.pindou.app.ui

import com.pindou.app.ui.icon.MorphIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pindou.app.ui.shadcn.TextButton
import com.pindou.app.ui.theme.ClayColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 两个识别模块共用的校准页骨架。
 *
 * 像素图识别和色号图识别只在「采样什么」上不同，操作顺序完全一致：
 * 顶栏返回/完成 → 看图对齐 → 底部面板调网格。这里把顶栏、说明、底部
 * 面板、步进器和智能对齐开关收在一处，避免两个页面各写一套后越改越偏。
 */

@Composable
internal fun CalibrationTopBar(
    title: String,
    busy: Boolean,
    actionLabel: String,
    actionEnabled: Boolean,
    onBack: () -> Unit,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(enabled = !busy, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                MorphIcons.ArrowLeft,
                contentDescription = "返回",
                tint = ClayColors.Ink,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onAction,
            enabled = actionEnabled && !busy,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) {
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (!actionEnabled || busy) ClayColors.Muted else ClayColors.PrimaryInk,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * 两个识别模块共用的整页骨架：顶栏 → 固定 2dp 进度槽 → 说明 → 画布区。
 *
 * 进度条不能按需插入布局：显隐会改画布高度，onSizeChanged 会让
 * pointerInput 重启，拖动就会「咔咔」的。这里固定占 2dp。
 */
@Composable
internal fun CalibrationPage(
    title: String,
    busy: Boolean,
    actionLabel: String,
    actionEnabled: Boolean,
    onBack: () -> Unit,
    onAction: () -> Unit,
    guidePrimary: AnnotatedString,
    guideSecondary: String,
    stage: @Composable BoxScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(ClayColors.Canvas),
    ) {
        CalibrationTopBar(
            title = title,
            busy = busy,
            actionLabel = actionLabel,
            actionEnabled = actionEnabled,
            onBack = onBack,
            onAction = onAction,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp),
        ) {
            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = ClayColors.Primary,
                    trackColor = ClayColors.HairlineSoft,
                )
            }
        }
        CalibrationGuide(
            primary = guidePrimary,
            secondary = guideSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            stage()
        }
    }
}

@Composable
internal fun CalibrationEmptyState(
    message: String,
    onHome: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = ClayColors.Muted,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onHome) { Text("返回首页") }
    }
}

@Composable
internal fun CalibrationGuide(
    primary: AnnotatedString,
    secondary: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            primary,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 18.sp,
            ),
            color = ClayColors.Body,
        )
        Text(
            secondary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = ClayColors.Muted,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/**
 * 说明文字里给关键操作加主色，和像素校准页保持同一阅读节奏。
 *
 * 必须 @Composable：主色现在来自 CompositionLocal（可切换主题），
 * 不再是编译期常量。
 */
@Composable
internal fun calibrationGuideText(prefix: String, accent: String, suffix: String): AnnotatedString {
    val accentColor = ClayColors.PrimaryInk
    return buildAnnotatedString {
        append(prefix)
        withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.SemiBold)) {
            append(accent)
        }
        append(suffix)
    }
}


/** 底部浮层：圆角、阴影、拖动条、左右 20dp 内边距，两个模块共用。 */
@Composable
internal fun CalibrationSheetFrame(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Column(
        modifier = modifier
            .shadow(
                elevation = 18.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.14f),
            )
            .clip(shape)
            .background(ClayColors.SurfaceCard)
            .border(1.dp, ClayColors.Hairline, shape)
            .padding(horizontal = 20.dp)
            // 底部给系统手势条留空间，否则最后一行滑块会被盖住一半。
            .padding(top = 7.dp, bottom = 18.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(34.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(ClayColors.SurfaceStrong),
        )
        content()
    }
}

@Composable
internal fun CalibrationHeaderRow(
    sizeLabel: String,
    badge: String,
    summary: String,
    redetectEnabled: Boolean,
    onRedetect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            sizeLabel,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
            fontWeight = FontWeight.SemiBold,
            color = ClayColors.Ink,
        )
        Box(
            Modifier
                .padding(start = 7.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(ClayColors.Primary.copy(alpha = 0.10f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = ClayColors.PrimaryInk,
                maxLines = 1,
            )
        }
        Text(
            summary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = ClayColors.Muted,
            modifier = Modifier.padding(start = 7.dp),
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onRedetect,
            enabled = redetectEnabled,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        ) {
            Text(
                "重新识别",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = ClayColors.PrimaryInk,
            )
        }
    }
}

@Composable
internal fun CalibrationNudgeRow(
    onNudge: (dx: Int, dy: Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalibrationStepBadge(1)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "贴住格子",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                color = ClayColors.BodyStrong,
            )
            Text(
                "拖动网格，或点箭头微调",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = ClayColors.Muted,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CalibrationNudgeButton(MorphIcons.ChevronLeft, "左移") { onNudge(-1, 0) }
            CalibrationNudgeButton(MorphIcons.ArrowUp, "上移") { onNudge(0, -1) }
            CalibrationNudgeButton(MorphIcons.ArrowDown, "下移") { onNudge(0, 1) }
            CalibrationNudgeButton(MorphIcons.ChevronRight, "右移") { onNudge(1, 0) }
        }
    }
}

@Composable
internal fun CalibrationDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ClayColors.HairlineSoft),
    )
}

/**
 * 大号上下箭头步进器。
 *
 * 数值区本身也接收竖向拖动：向上加、向下减，每约 12dp 走一步；箭头
 * 则给只想点一下的用户。两种操作都绕开数字键盘。
 */
@Composable
internal fun CalibrationBigStepper(
    step: Int,
    label: String,
    unit: String?,
    value: String,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val threshold = with(LocalDensity.current) { 12.dp.toPx() }
    val currentOnStep by rememberUpdatedState(onStep)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            CalibrationStepBadge(step)
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Medium,
                color = ClayColors.Muted,
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .pointerInput(Unit) {
                        var accumulated = 0f
                        detectVerticalDragGestures(
                            onDragStart = { accumulated = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                accumulated += dragAmount
                                while (accumulated <= -threshold) {
                                    accumulated += threshold
                                    currentOnStep(1)
                                }
                                while (accumulated >= threshold) {
                                    accumulated -= threshold
                                    currentOnStep(-1)
                                }
                            },
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    buildAnnotatedString {
                        append(value)
                        if (unit != null) {
                            withStyle(
                                SpanStyle(
                                    fontSize = 10.sp,
                                    color = ClayColors.Muted,
                                    fontWeight = FontWeight.Medium,
                                ),
                            ) {
                                append(" ")
                                append(unit)
                            }
                        }
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = ClayColors.Ink,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    "上下拖动",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = ClayColors.MutedSoft,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CalibrationStepArrow(up = true) { currentOnStep(1) }
                CalibrationStepArrow(up = false) { currentOnStep(-1) }
            }
        }
    }
}

@Composable
internal fun CalibrationStepBadge(number: Int) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(ClayColors.Primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = ClayColors.PrimaryInk,
        )
    }
}

@Composable
internal fun CalibrationNudgeButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(ClayColors.SurfaceSoft)
            .border(1.dp, ClayColors.Hairline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = ClayColors.BodyStrong,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
internal fun CalibrationStepArrow(
    up: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp, 27.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(ClayColors.SurfaceSoft)
            .border(1.dp, ClayColors.Hairline, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (up) MorphIcons.ArrowUp else MorphIcons.ArrowDown,
            contentDescription = if (up) "增加" else "减少",
            tint = ClayColors.BodyStrong,
            modifier = Modifier.size(19.dp),
        )
    }
}

/**
 * 智能对齐开关行。
 *
 * 整行可点，Switch 自己不接收点击：开关本身很小，用户经常点不中，
 * 误以为「关不掉」。关闭动作必须立刻生效，不能被「对齐中」禁用。
 */
@Composable
internal fun CalibrationSmartRow(
    checked: Boolean,
    status: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "列/行按 5×5 大格增删；格子保持正方形",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = ClayColors.Muted,
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggle)
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "智能对齐",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                color = ClayColors.BodyStrong,
            )
            Text(
                status,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = if (checked) ClayColors.Muted else ClayColors.MutedSoft,
                modifier = Modifier.padding(start = 6.dp),
            )
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(start = 6.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ClayColors.Primary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = ClayColors.SurfaceStrong,
                    uncheckedBorderColor = ClayColors.Hairline,
                ),
            )
        }
    }
}

@Composable
internal fun CalibrationThresholdRow(
    label: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (enabled) ClayColors.Muted else ClayColors.MutedSoft,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = range,
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .padding(horizontal = 5.dp),
            colors = SliderDefaults.colors(
                thumbColor = ClayColors.Primary,
                activeTrackColor = ClayColors.Primary,
                inactiveTrackColor = ClayColors.SurfaceStrong,
            ),
        )
        Text(
            "${(value * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (enabled) ClayColors.PrimaryInk else ClayColors.MutedSoft,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(32.dp),
        )
    }
}

/** 图片区右上角的「看图纸 / 看原图」开关。 */
@Composable
internal fun CalibrationPreviewToggle(
    showingPreview: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ClayColors.SurfaceCard.copy(alpha = 0.94f))
            .border(1.dp, ClayColors.Hairline, RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (showingPreview) MorphIcons.EyeOff else MorphIcons.Eye,
            contentDescription = null,
            tint = ClayColors.PrimaryInk,
            modifier = Modifier.size(15.dp),
        )
        Text(
            if (showingPreview) "看原图" else "看图纸",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = ClayColors.PrimaryInk,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** 一格大小步长：大格走 0.25，小格走 0.01。 */
internal fun calibrationCellStep(cellW: Double): Double = when {
    cellW >= 20.0 -> 0.25
    cellW >= 8.0 -> 0.10
    cellW >= 2.0 -> 0.02
    else -> 0.01
}

/** 1.0 显示 1，6.85 显示 6.85，不把整数硬写成两位小数。 */
internal fun formatCalibrationCell(value: Double): String {
    if (abs(value - value.roundToInt()) < 0.0001) return value.roundToInt().toString()
    return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}
