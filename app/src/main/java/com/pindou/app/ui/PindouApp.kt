package com.pindou.app.ui

import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.core.settings.GlassAppearance
import com.pindou.app.ui.theme.ClayColors
import com.pindou.app.ui.theme.LocalThemeGradient
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
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
import com.pindou.app.ui.motion.AnimatedIconSwap
import com.pindou.app.ui.motion.LocalMotion
import com.pindou.app.ui.motion.Motion
import com.pindou.app.ui.motion.pressScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val pendingUndo = vm.deleteUndo
    val appMotion = LocalMotion.current
    // 拖动底栏时冻结 pager 自身的手势，避免「底栏驱动 + pager 惯性」双重滚动导致多跳一页
    var barDragging by remember { mutableStateOf(false) }
    val homePager = rememberPagerState(initialPage = vm.homeTab.ordinal) {
        HomeTab.entries.size
    }
    val homeScope = rememberCoroutineScope()

    // 页面滑动和 ViewModel 的 homeTab 双向同步：
    // - 用户拖页只改 pager，翻过半页后再把 homeTab 写回；
    // - 项目库「去创建」/返回键等外部调用仍写 homeTab，再由这里把 pager 滚过去。
    LaunchedEffect(homePager) {
        snapshotFlow { homePager.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                val tab = HomeTab.entries[page]
                if (vm.homeTab != tab) vm.selectHomeTab(tab)
            }
    }
    LaunchedEffect(vm.homeTab, vm.screen) {
        if (vm.screen != Screen.HOME) return@LaunchedEffect
        val target = vm.homeTab.ordinal
        if (homePager.currentPage != target) {
            if (appMotion.enabled) homePager.animateScrollToPage(target)
            else homePager.scrollToPage(target)
        }
    }

    fun goToHomePage(page: Int) {
        val target = page.coerceIn(0, HomeTab.entries.lastIndex)
        homeScope.launch {
            if (appMotion.enabled) homePager.animateScrollToPage(target)
            else homePager.scrollToPage(target)
        }
    }

    // 删除撤销：Snackbar 带 action，用户点「撤销」就回插；10 秒到时或
    // 被下一条 Snackbar 顶掉，就确认删除并清 sidecar。
    // key 用实例而不是文案：连续删两个同名图纸时文案相同，
    // 用文案做 key 会让第二次的 Snackbar 根本不出。
    LaunchedEffect(pendingUndo) {
        if (pendingUndo != null) {
            val result = snackbarHostState.showSnackbar(
                message = pendingUndo.message,
                actionLabel = "撤销",
                duration = SnackbarDuration.Long,
                withDismissAction = false,
            )
            if (result == SnackbarResult.ActionPerformed) {
                vm.undoDelete()
            } else if (vm.deleteUndo === pendingUndo) {
                vm.commitDelete()
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            // toast 显示后清空，避免重复弹出
            val msg = vm.toast
            if (msg != null) {
                // 1.8 秒自动收：Material 的 Short 是 4 秒，压在画布/工具条上
                // 挡点击的时间太长。自己起一个计时协程提前 dismiss。
                // 只收没有 action 的普通提示；带「撤销」的 Snackbar 要留给
                // 用户点，提前 dismiss 会被当成 Dismissed 直接确认删除。
                val autoHide = launch {
                    delay(1800)
                    val current = snackbarHostState.currentSnackbarData
                    if (current?.visuals?.actionLabel == null) current?.dismiss()
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

    // 渐变主题：整页背景用主题双色铺底，卡片仍是实色，避免全屏花
    val themeGradient = LocalThemeGradient.current
    val baseCanvas = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                themeGradient?.let { Brush.verticalGradient(it) }
                    ?: Brush.verticalGradient(listOf(baseCanvas, baseCanvas)),
            ),
    ) {
    Scaffold(
        // 结果提示做成小药丸：默认的整条 Snackbar 又宽又高，会盖住底部工具条
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(bottom = if (vm.screen == Screen.HOME) 96.dp else 0.dp),
            ) { data ->
                // 有 action 时走 Material 的 Snackbar 本体，才会出现右侧按钮
                if (data.visuals.actionLabel != null) {
                    Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(12.dp),
                        containerColor = ClayColors.Ink,
                        contentColor = ClayColors.OnDark,
                        actionColor = ClayColors.Primary,
                        actionOnNewLine = false,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ClayColors.Ink,
                        contentColor = ClayColors.OnDark,
                        shadowElevation = 8.dp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    ) {
                        AnimatedContent(
                            targetState = data.visuals.message,
                            transitionSpec = {
                                if (!appMotion.enabled) {
                                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                } else {
                                    (
                                        fadeIn(tween(Motion.FAST)) +
                                            slideInVertically(tween(Motion.BASE)) { it / 2 }
                                        ) togetherWith (
                                        fadeOut(tween(Motion.FAST / 2)) +
                                            slideOutVertically(tween(Motion.FAST)) { -it / 2 }
                                        )
                                }
                            },
                            label = "snackbarText",
                        ) { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
            ) {
                Box(Modifier.padding(innerPadding)) {
                    // 转场方向由 ScreenOrder 的层级差决定，调用点不用手写方向；
                    // 「移除动画」时退化成硬切（snap），不动任何布局
                    AnimatedContent(
                        targetState = vm.screen,
                        transitionSpec = {
                            if (!appMotion.enabled) {
                                (fadeIn(tween(0)) togetherWith fadeOut(tween(0)))
                                    .using(SizeTransform(clip = false))
                            } else if (ScreenOrder.isForward(initialState, targetState)) {
                                (
                                    slideInHorizontally(tween(Motion.EMPHASIS)) { it } +
                                        fadeIn(tween(Motion.BASE))
                                    ) togetherWith (
                                    slideOutHorizontally(tween(Motion.EMPHASIS)) { -it / 3 } +
                                        scaleOut(tween(Motion.EMPHASIS), targetScale = 0.86f) +
                                        fadeOut(tween(Motion.BASE))
                                    ) using SizeTransform(clip = false)
                            } else {
                                (
                                    slideInHorizontally(tween(Motion.EMPHASIS)) { -it / 3 } +
                                        fadeIn(tween(Motion.BASE))
                                    ) togetherWith (
                                    slideOutHorizontally(tween(Motion.EMPHASIS)) { it } +
                                        scaleOut(tween(Motion.EMPHASIS), targetScale = 0.86f) +
                                        fadeOut(tween(Motion.BASE))
                                    ) using SizeTransform(clip = false)
                            }
                        },
                        label = "screenTransition",
                    ) { screen ->
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                vm,
                                pagerState = homePager,
                                userScrollEnabled = !barDragging,
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
            }

            // 玻璃底栏叠在内容之上，列表从半透明背景后面滚过。
            // 项目库多选时让位给批量操作条，避免误触「创建」丢掉整批选择。
            val selectionBarActive = vm.homeTab == HomeTab.LIBRARY && vm.librarySelecting
            val bottomBarVisible = vm.screen == Screen.HOME && !selectionBarActive
            val motion = LocalMotion.current
            AnimatedVisibility(
                visible = bottomBarVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = if (motion.enabled) {
                    slideInVertically(tween(Motion.EMPHASIS)) { it } +
                        fadeIn(tween(Motion.BASE))
                } else {
                    androidx.compose.animation.EnterTransition.None
                },
                exit = if (motion.enabled) {
                    slideOutVertically(tween(Motion.BASE)) { it } +
                        fadeOut(tween(Motion.FAST))
                } else {
                    androidx.compose.animation.ExitTransition.None
                },
            ) {
                GlassBottomBar(
                    backdrop = backdrop,
                    glass = vm.settings.glass,
                    current = HomeTab.entries[
                        homePager.currentPage.coerceIn(0, HomeTab.entries.lastIndex)
                    ],
                    pagerState = homePager,
                    onSelect = { tab -> goToHomePage(tab.ordinal) },
                    onDraggingChange = { barDragging = it },
                    modifier = Modifier,
                )
            }
        }
    }
    }
}

@Composable
private fun HomeScreen(
    vm: AppViewModel,
    pagerState: PagerState,
    userScrollEnabled: Boolean = true,
    onPickImage: () -> Unit,
    onPickPixel: () -> Unit,
    onPickBpat: () -> Unit,
    onPickChart: () -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = userScrollEnabled && !vm.librarySelecting,
        key = { page -> HomeTab.entries[page] },
    ) { page ->
        when (HomeTab.entries[page]) {
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
    glass: GlassAppearance,
    current: HomeTab,
    pagerState: PagerState,
    onSelect: (HomeTab) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var barPressed by remember { mutableStateOf(false) }
    // 手指按住底栏任意位置（含轻点 tab，不经过拖动阈值）时整条玻璃一起下压
    var barTouched by remember { mutableStateOf(false) }
    val pressedVisual = barTouched || barPressed
    // 底栏拖拽的"手指位移"（单位：页）。offsetFraction 在拖拽期间读不到时，
    // 靠它保证胶囊仍然跟手；松手吸附时归零。
    var dragVisual by remember { mutableFloatStateOf(0f) }
    // 拖拽起点页：pager 的 currentPage 会在半页处跳变，结算必须从起点算，
    // 否则会被算成「1 + 拖动量」而多跳一页。
    var dragStartPage by remember { mutableIntStateOf(0) }
    val pillScale by animateFloatAsState(
        targetValue = if (pressedVisual) 0.86f else 1f,
        animationSpec = tween(Motion.FAST),
        label = "pillPress",
    )
    // 拖动经过 tab 时，子项的 clickable 也可能收到一次 up；
    // 用 250ms 时间闸门吞掉这次误点，正常轻点不受影响。
    val clickGate = remember { LongArray(1) }
    val shape = RoundedCornerShape(glass.cornerDp.dp)
    val motion = LocalMotion.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.35f
    val accent = MaterialTheme.colorScheme.primary
    val selectedInk = if (dark) Color.White else ClayColors.PrimaryInk
    val idleInk = if (dark) Color.White.copy(alpha = 0.60f) else ClayColors.Body.copy(alpha = 0.56f)
    val glassTint = if (dark) Color(0xFF0A1117) else Color.White
    val alphas = glass.surfaceAlphas(dark)

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .widthIn(max = 372.dp)
            .fillMaxWidth()
            .height(64.dp)
            // 只读触摸态：不消费事件，子项的 clickable 照常工作
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    barTouched = true
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        barTouched = false
                    }
                }
            }
            // 拖动底栏 = 拖动胶囊：pager 偏移跟手走，标题/页面同步移动；
            // 松手吸附到最近一页。和子项的 clickable 共存：没过 touch slop 的轻点仍是点 tab。
            .pointerInput(Unit) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        drag = 0f
                        dragVisual = 0f
                        dragStartPage = pagerState.currentPage
                        barPressed = true
                        onDraggingChange(true)
                    },
                    onDragCancel = {
                        drag = 0f
                        dragVisual = 0f
                        barPressed = false
                        onDraggingChange(false)
                    },
                    onHorizontalDrag = { change, amount ->
                        drag += amount
                        change.consume()
                        // 底栏一个槽宽 = 一页：手指走一格，页面正好走完一页，
                        // 胶囊（1:1 跟手）和页面才会同时到位；直接按 px 推会只走 1/3 页。
                        val slotPx = size.width.toFloat() / Tabs.size
                        val viewport = pagerState.layoutInfo.viewportSize.width
                        if (slotPx > 0f && viewport > 0) {
                            val pageDelta = amount * viewport / slotPx
                            pagerState.dispatchRawDelta(pageDelta)
                            // 胶囊按手指位移走，并夹在当前页的边界内：到边时不脱手
                            val min = -dragStartPage.toFloat()
                            val max = (Tabs.size - 1 - dragStartPage).toFloat()
                            dragVisual = (dragVisual + amount / slotPx).coerceIn(min, max)
                        }
                    },
                    onDragEnd = {
                        barPressed = false
                        onDraggingChange(false)
                        if (abs(drag) > 8f) {
                            // 只在真的拖动时落闸；轻点不能吞掉下一拍点击
                            clickGate[0] = SystemClock.uptimeMillis() + 250L
                        }
                        val target = (dragStartPage + dragVisual)
                            .roundToInt()
                            .coerceIn(0, Tabs.size - 1)
                        scope.launch { pagerState.animateScrollToPage(target) }
                        dragVisual = 0f
                        drag = 0f
                    },
                )
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(glass.blurDp.dp.toPx())
                    lens(
                        refractionHeight = (glass.refractionDp * 0.54f).dp.toPx(),
                        refractionAmount = glass.refractionDp.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = glass.chromatic,
                    )
                },
                highlight = {
                    Highlight(
                        width = 1.dp,
                        blurRadius = 0.5.dp,
                        alpha = if (dark) 0.95f else 0.90f,
                        style = HighlightStyle.Default(
                            intensity = if (dark) {
                                (glass.highlight * 0.78f).coerceIn(0f, 1f)
                            } else {
                                glass.highlight
                            },
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
                            0f to Color.White.copy(alpha = alphas.top),
                            0.45f to Color.White.copy(alpha = alphas.middle),
                            0.75f to Color.Transparent,
                            1f to if (dark) Color.White.copy(alpha = alphas.bottom)
                            else accent.copy(alpha = alphas.bottom),
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
            // 玻璃片直接跟 Pager 的 currentPage + offsetFraction 走。
            // 在组合阶段读：layout lambda 里读 offsetFraction 在拖拽时不触发重排，
            // 实测胶囊会停在旧页；这里宁可每帧重组这一个小条，也要跟手。
            // 拖底栏时用 dragVisual（与 pager 实际滚动量一致），其余时候跟 pager 自己的偏移
            val pagePosition = (
                (if (barPressed) dragStartPage + dragVisual else pagerState.currentPage.toFloat()) +
                    (if (barPressed) 0f else pagerState.currentPageOffsetFraction)
                )
                .coerceIn(0f, (Tabs.size - 1).toFloat())
            // 局部按压：光斑落在胶囊当前槽位，跟着拖动走；不缩整条，避免「整块玻璃被按住」
            val pressCenterPx = with(LocalDensity.current) { (slot * pagePosition + slot / 2).toPx() }
            val pressRadiusPx = with(LocalDensity.current) { slot.toPx() * 0.95f }
            Canvas(Modifier.matchParentSize()) {
                if (!pressedVisual) return@Canvas
                val center = Offset(pressCenterPx, size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = if (dark) 0.20f else 0.14f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = pressRadiusPx,
                    ),
                    radius = pressRadiusPx,
                    center = center,
                )
            }
            Box(
                Modifier
                    .offset(x = slot * pagePosition)
                    .width(slot)
                    .fillMaxHeight()
                    .padding(horizontal = 3.dp)
                    // 触摸/拖动时胶囊轻微下压，松手回弹
                    .graphicsLayer {
                        scaleX = pillScale
                        scaleY = pillScale
                    }
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
            ) {
                // V4 呼吸感：选中胶囊每 6 秒掠过一次光泽，180ms、alpha ≤ 8%。
                // 只在静止时跑；拖 pager / 关闭动画时不启动，避免廉价感。
                val breath = remember { Animatable(0f) }
                LaunchedEffect(motion.enabled, pagerState.isScrollInProgress) {
                    if (!motion.enabled) {
                        breath.snapTo(0f)
                        return@LaunchedEffect
                    }
                    while (true) {
                        delay(6000)
                        if (!pagerState.isScrollInProgress) {
                            breath.animateTo(1f, tween(180, easing = LinearEasing))
                            breath.snapTo(0f)
                        }
                    }
                }
                Canvas(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(26.dp)),
                ) {
                    val progress = breath.value
                    if (progress <= 0f) return@Canvas
                    val bandW = size.width * 0.40f
                    val x = -bandW + (size.width + bandW * 2f) * progress
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to accent.copy(alpha = if (dark) 0.10f else 0.08f),
                            1f to Color.Transparent,
                            start = Offset(x, 0f),
                            end = Offset(x + bandW, size.height * 0.7f),
                        ),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tabs.forEach { tab ->
                    val selected = current == tab.target
                    val tint by animateColorAsState(
                        targetValue = if (selected) selectedInk else idleInk,
                        animationSpec = tween(Motion.FAST),
                        label = "tabTint",
                    )
                    val interaction = remember { MutableInteractionSource() }

                    Column(
                        modifier = Modifier
                            .width(slot)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .pressScale(interaction, pressedScale = 0.88f)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                            ) {
                                if (SystemClock.uptimeMillis() >= clickGate[0]) {
                                    onSelect(tab.target)
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        AnimatedIconSwap(
                            selected = selected,
                            outlined = tab.outlined,
                            filled = tab.filled,
                            contentDescription = tab.label,
                            tint = tint,
                            size = 21.dp,
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
