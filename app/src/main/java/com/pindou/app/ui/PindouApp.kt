package com.pindou.app.ui

import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.theme.ClayColors
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Tab(
    val target: HomeTab,
    val label: String,
    val outlined: ImageVector,
    val filled: ImageVector,
)

private val Tabs = listOf(
    Tab(HomeTab.LIBRARY, "项目库", MorphIcons.House, MorphIcons.House),
    Tab(HomeTab.CREATE, "创建", MorphIcons.CirclePlus, MorphIcons.CirclePlus),
    Tab(HomeTab.SETTINGS, "设置", MorphIcons.Settings, MorphIcons.Settings),
)

@Composable
fun PindouApp(vm: AppViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        while (true) {
            // toast 显示后清空，避免重复弹出
            val msg = vm.toast
            if (msg != null) {
                // 1.8 秒自动收：Material 的 Short 是 4 秒，压在画布/工具条上
                // 挡点击的时间太长。自己起一个计时协程提前 dismiss。
                val autoHide = launch {
                    delay(1800)
                    snackbarHostState.currentSnackbarData?.dismiss()
                }
                snackbarHostState.showSnackbar(
                    message = msg,
                    duration = SnackbarDuration.Short,
                    withDismissAction = false,
                )
                autoHide.cancel()
                vm.toast = null
            }
            delay(300)
        }
    }

    // 图片选择器：走标准 SAF（OpenDocument）。
    // 已知问题：HyperOS 上 PickVisualMedia（小米「安全访问」通道）返回后，
    // 会残留一个透明层吞掉应用内所有点击，故暂时全程使用 SAF 选择器。
    // 当前这次选择器回调走哪条支路：true = 显式「像素画生成」
    var pendingPixelMode by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            // 只在真的选到文件后重置。取消选择器不能把用户当前打开的项目清空。
            vm.startNewProject()
            vm.onImagePicked(it, pendingPixelMode)
        }
    }

    // .bpat 工程文件导入：同样走 SAF，任意 mime（导出方自定义扩展名，不做类型白名单）
    val bpatPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importBpat) }

    // 色号图（现成图纸）走同一套 SAF 选择器，回调进校准页
    val chartPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::onChartPicked) }

    fun pickImage() {
        pendingPixelMode = false
        imagePicker.launch(arrayOf("image/*"))
    }

    fun pickPixelImage() {
        pendingPixelMode = true
        imagePicker.launch(arrayOf("image/*"))
    }

    fun pickBpat() {
        bpatPicker.launch(arrayOf("*/*"))
    }

    fun pickChart() {
        chartPicker.launch(arrayOf("image/*"))
    }

    BackHandler(enabled = vm.screen != Screen.HOME) {
        vm.handleBack()
    }

    // 首页的创建/设置页按返回先回项目库，避免一按返回直接退出应用。
    BackHandler(enabled = vm.screen == Screen.HOME && vm.homeTab != HomeTab.LIBRARY) {
        vm.selectHomeTab(HomeTab.LIBRARY)
    }

    // 底栏玻璃的取样源：页面内容先画进 graphics layer，底栏按全局坐标
    // 回采这块内容，才能真正出现「透过玻璃看到列表」的折射。
    val backdropColor = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(backdropColor)
        drawContent()
    }

    Scaffold(
        // 结果提示做成小药丸：默认的整条 Snackbar 又宽又高，会盖住底部工具条
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(bottom = if (vm.screen == Screen.HOME) 96.dp else 0.dp),
            ) { data ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ClayColors.Ink,
                    contentColor = ClayColors.OnDark,
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    Text(
                        data.visuals.message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
            ) {
                Box(Modifier.padding(innerPadding)) {
                    when (vm.screen) {
                        Screen.HOME -> HomeScreen(
                            vm,
                            onPickImage = ::pickImage,
                            onPickPixel = ::pickPixelImage,
                            onPickBpat = ::pickBpat,
                            onPickChart = ::pickChart,
                        )
                        Screen.CROP -> CropScreen(vm)
                        Screen.PARAMS -> ParamsScreen(vm)
                        Screen.PREVIEW -> PreviewScreen(vm)
                        Screen.EDITOR -> com.pindou.app.ui.editor.EditorScreen(vm)
                        Screen.STUDIO -> com.pindou.app.ui.studio.StudioScreen(vm)
                        Screen.CONSTRUCTION -> com.pindou.app.ui.build.ConstructionScreen(vm)
                        Screen.CHART_CALIBRATE -> ChartCalibrateScreen(vm)
                        Screen.PIXEL_CALIBRATE -> PixelCalibrateScreen(vm)
                    }
                }
            }

            // 玻璃底栏叠在内容之上，列表从半透明背景后面滚过。
            // 项目库多选时让位给批量操作条，避免误触「创建」丢掉整批选择。
            val selectionBarActive = vm.homeTab == HomeTab.LIBRARY && vm.librarySelecting
            if (vm.screen == Screen.HOME && !selectionBarActive) {
                GlassBottomBar(
                    backdrop = backdrop,
                    current = vm.homeTab,
                    onSelect = vm::selectHomeTab,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    vm: AppViewModel,
    onPickImage: () -> Unit,
    onPickPixel: () -> Unit,
    onPickBpat: () -> Unit,
    onPickChart: () -> Unit,
) {
    when (vm.homeTab) {
        HomeTab.LIBRARY -> ProjectLibraryScreen(
            vm,
            onGoCreate = { vm.selectHomeTab(HomeTab.CREATE) },
            onPickBpat = onPickBpat,
        )
        HomeTab.CREATE -> CreateScreen(
            vm,
            onPickPhoto = onPickImage,
            onPickPixel = onPickPixel,
            onPickChart = onPickChart,
        )
        HomeTab.SETTINGS -> SettingsScreen(vm)
    }
}

/**
 * 底部液体玻璃导航。
 *
 * 三层结构，缺一层都不像玻璃：
 * 1. 页面内容先录进 backdrop 的 graphics layer；
 * 2. 底栏用 RenderEffect 链做 vibrancy → blur → lens，边缘按圆角 SDF
 *    折射，选中项再叠一块折射更强的小透镜；
 * 3. 玻璃表面只留一层很薄的白色渐变，高光/内阴影/投影由 backdrop 的
 *    Highlight/InnerShadow/Shadow 画，避免以前整条发白、字看不清。
 *
 * API < 31 没有 RenderEffect，退回 80% 不透明底色；布局和交互不变。
 * 底栏不占 Scaffold 的 bottomBar 槽位，三个首页列表末尾都留了滚动余量。
 */
@Composable
private fun GlassBottomBar(
    backdrop: Backdrop,
    current: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(32.dp)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.35f
    val accent = MaterialTheme.colorScheme.primary
    val selectedInk = if (dark) Color.White else ClayColors.PrimaryInk
    val idleInk = if (dark) Color.White.copy(alpha = 0.60f) else ClayColors.Body.copy(alpha = 0.56f)
    val glassTint = if (dark) Color(0xFF0A1117) else Color.White
    val selectedIndex = Tabs.indexOfFirst { it.target == current }.coerceAtLeast(0)

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .widthIn(max = 372.dp)
            .fillMaxWidth()
            .height(64.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    // 12dp 是 64dp 高胶囊的甜点：再高会把图标边缘糊掉，
                    // 再低则列表文字会从玻璃里透出来抢注意力。
                    blur(12.dp.toPx())
                    lens(
                        refractionHeight = 14.dp.toPx(),
                        refractionAmount = 26.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                },
                highlight = {
                    Highlight(
                        width = 1.dp,
                        blurRadius = 0.5.dp,
                        alpha = if (dark) 0.95f else 0.90f,
                        style = HighlightStyle.Default(
                            intensity = if (dark) 0.55f else 0.72f,
                            angle = 45f,
                            falloff = 1.1f,
                        ),
                    )
                },
                shadow = {
                    Shadow(
                        radius = 22.dp,
                        offset = DpOffset(0.dp, 10.dp),
                        color = Color.Black.copy(alpha = if (dark) 0.42f else 0.16f),
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 8.dp,
                        offset = DpOffset(0.dp, 1.dp),
                        color = Color.White.copy(alpha = if (dark) 0.18f else 0.55f),
                        alpha = 0.9f,
                    )
                },
                onDrawSurface = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        drawRect(glassTint.copy(alpha = if (dark) 0.86f else 0.80f))
                    }
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = if (dark) 0.10f else 0.38f),
                            0.45f to Color.White.copy(alpha = if (dark) 0.02f else 0.14f),
                            0.75f to Color.Transparent,
                            1f to if (dark) Color.White.copy(alpha = 0.05f)
                            else accent.copy(alpha = 0.05f),
                        ),
                    )
                },
            ),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            val slot = maxWidth / Tabs.size
            // 一块玻璃片在三个槽位间移动，比每个 tab 各闪一下更像液体
            val pillX by animateDpAsState(
                targetValue = slot * selectedIndex,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "glassTabPill",
            )
            Box(
                Modifier
                    .offset { IntOffset(pillX.roundToPx(), 0) }
                    .width(slot)
                    .fillMaxHeight()
                    .padding(horizontal = 3.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(26.dp) },
                        effects = {
                            vibrancy()
                            blur(4.dp.toPx())
                            lens(
                                refractionHeight = 8.dp.toPx(),
                                refractionAmount = 16.dp.toPx(),
                                depthEffect = true,
                            )
                        },
                        highlight = {
                            Highlight(
                                width = 0.8.dp,
                                alpha = if (dark) 0.55f else 0.75f,
                                style = HighlightStyle.Default(
                                    intensity = if (dark) 0.40f else 0.55f,
                                    angle = 45f,
                                    falloff = 1.2f,
                                ),
                            )
                        },
                        shadow = null,
                        innerShadow = {
                            InnerShadow(
                                radius = 5.dp,
                                offset = DpOffset(0.dp, 1.dp),
                                color = Color.White.copy(alpha = if (dark) 0.12f else 0.40f),
                            )
                        },
                        onDrawSurface = {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                drawRect(accent.copy(alpha = 0.24f))
                            }
                            drawRect(
                                Brush.verticalGradient(
                                    0f to accent.copy(alpha = if (dark) 0.26f else 0.20f),
                                    1f to accent.copy(alpha = if (dark) 0.10f else 0.08f),
                                ),
                            )
                        },
                    ),
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tabs.forEach { tab ->
                    val selected = current == tab.target
                    val tint = if (selected) selectedInk else idleInk
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(
                        targetValue = if (pressed) 0.92f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "tabPress",
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            }
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                            ) { onSelect(tab.target) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (selected) tab.filled else tab.outlined,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(21.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            color = tint,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
