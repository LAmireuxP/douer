package com.pindou.app.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.theme.Spacing

private data class TutorialPage(
    val icon: ImageVector,
    val title: String,
    val summary: String,
    val points: List<String>,
)

private val tutorialPages = listOf(
    TutorialPage(
        icon = MorphIcons.Hand,
        title = "先认识画布",
        summary = "画像素是逐格点色的工作区，先把看图和移动弄清楚。",
        points = listOf(
            "顶栏的 78×78 尺寸可以点击，直接打开画布设置。",
            "单指拖动画布，双指缩放；双指开始时不会落笔。",
            "画布右下角的「适应」随时回到完整画面。",
        ),
    ),
    TutorialPage(
        icon = MorphIcons.Brush,
        title = "选颜色，再落笔",
        summary = "左侧是颜色，右侧是工具。先选颜色再点格，最不容易画错。",
        points = listOf(
            "点左侧色块会自动切回画笔。",
            "右侧有画笔、橡皮、油漆桶、吸管和移动。",
            "吸管取色后会显示色号；移动工具只平移画布，不改像素。",
        ),
    ),
    TutorialPage(
        icon = StudioIcons.Layers,
        title = "原图参考 + 自己画",
        summary = "导入的图片只铺在底层作为原画参考，不会自动变成像素。",
        points = listOf(
            "底部「图层」→「导入底层图片」。",
            "软件会保留原图颜色和细节，不会自动转成像素。",
            "自动新建的「绘制层」才是你要逐格点色的图层。",
            "底层原图不参与 PNG/PDF/GIF 导出，可在图层里隐藏或清除。",
        ),
    ),
    TutorialPage(
        icon = MorphIcons.Grid,
        title = "画得像、画得快",
        summary = "常用工具都在右侧浮窗，底部动作栏负责图层、帧、选区和导出。",
        points = listOf(
            "画笔和橡皮支持笔刷大小、方形/圆形与对称。",
            "直线、矩形、椭圆、多边形、曲线、渐变、文字都可用。",
            "画错了点顶栏撤销；双指缩放/移动不会污染画面。",
        ),
    ),
    TutorialPage(
        icon = MorphIcons.Download,
        title = "保存和导出",
        summary = "先完成保存，再从导出面板生成成品或继续施工。",
        points = listOf(
            "右上角「完成」保存当前画像素工程。",
            "导出面板支持像素 PNG、GIF、色号图、PDF 和调色板。",
            "新手建议先用 40×40 左右的小画布熟悉流程。",
            "以后可从右上角「更多 → 新手教程」再次打开。",
        ),
    ),
)

@Composable
fun StudioTutorialDialog(
    step: Int,
    onStepChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val safeStep = step.coerceIn(tutorialPages.indices)
    val page = tutorialPages[safeStep]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.padding(Spacing.lg)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "新手教程",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${safeStep + 1} / ${tutorialPages.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onDismiss) { Text("跳过") }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(
                        modifier = Modifier.padding(top = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                page.icon,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                page.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                page.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(Spacing.md))
                    page.points.forEachIndexed { index, point ->
                        Row(
                            modifier = Modifier.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                point,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(tutorialPages.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == safeStep) 18.dp else 7.dp, 7.dp)
                                .background(
                                    if (index == safeStep) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    RoundedCornerShape(999.dp),
                                ),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { onStepChange(safeStep - 1) },
                        enabled = safeStep > 0,
                    ) { Text("上一步") }
                    Button(
                        onClick = {
                            if (safeStep < tutorialPages.lastIndex) onStepChange(safeStep + 1)
                            else onDismiss()
                        },
                    ) {
                        Text(if (safeStep < tutorialPages.lastIndex) "下一步" else "开始画")
                    }
                }
            }
        }
    }
}
