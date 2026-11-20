@file:OptIn(ExperimentalLayoutApi::class)

package com.pindou.app.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pindou.app.core.data.ProjectSummary
import com.pindou.app.core.data.ProjectEntity
import com.pindou.app.core.data.ProjectStats
import com.pindou.app.core.build.BuildTimer
import com.pindou.app.ui.motion.shimmer
import com.pindou.app.ui.icon.MorphIcons
import com.pindou.app.ui.motion.AnimatedNumber
import com.pindou.app.ui.motion.LocalMotion
import com.pindou.app.ui.motion.Motion
import com.pindou.app.ui.motion.pressScale
import com.pindou.app.ui.shadcn.Button
import com.pindou.app.ui.shadcn.OutlinedButton
import com.pindou.app.ui.shadcn.ShCard
import com.pindou.app.ui.shadcn.ShChip
import com.pindou.app.ui.shadcn.ShSearchField
import com.pindou.app.ui.shadcn.TextButton
import com.pindou.app.ui.shadcn.ShVariant
import com.pindou.app.ui.theme.ClayColors
import com.pindou.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 项目库首页：作品管理。
 *
 * 性能上有三条硬约束：
 * 1. 列表订阅 ProjectSummary，不读 cells / locked / doneCells 三个大 BLOB；
 * 2. 用 LazyVerticalGrid 只组合可见卡片，不再 chunked(2) 一次性铺完；
 * 3. 缩略图在 IO 线程按卡片尺寸降采样解码，并放进 LruCache，主线程不碰文件。
 *
 * 交互分两层：
 * - 常驻态：点卡片打开，长按或右上角「⋯」弹底部动作面板；
 * - 多选态：点卡片加选/取消，顶栏全选或完成，底部浮条批量收藏/专辑/删除。
 *
 * 多选态会通过 vm.librarySelecting 让首页隐藏玻璃底栏，避免「创建」被误触
 * 把整批选择丢掉；返回键先退出多选，再走首页默认返回。
 */
@Composable
fun ProjectLibraryScreen(
    vm: AppViewModel,
    onGoCreate: () -> Unit,
    onPickBpat: () -> Unit = {},
) {
    // 必须订阅（collectAsState）而非读 .value：StateFlow 配 WhileSubscribed 时读取不会启动上游查询
    val projects by vm.projects.collectAsState()
    var renameTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var albumTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var actionTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var batchDeleteOpen by remember { mutableStateOf(false) }
    var batchAlbumOpen by remember { mutableStateOf(false) }
    var compareIds by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var comparePair by remember {
        mutableStateOf<Pair<ProjectEntity, ProjectEntity>?>(null)
    }

    val selecting = vm.librarySelecting
    var selectedIds by rememberSaveable(stateSaver = SelectionSaver) {
        mutableStateOf(emptySet<Long>())
    }

    fun exitSelection() {
        vm.librarySelecting = false
        selectedIds = emptySet()
        batchDeleteOpen = false
        batchAlbumOpen = false
        compareIds = null
        comparePair = null
    }

    // 对比页要完整实体（cells / doneCells）：选中两张后再读库，不在列表阶段预取
    LaunchedEffect(compareIds) {
        val ids = compareIds
        if (ids == null) {
            comparePair = null
            return@LaunchedEffect
        }
        val left = vm.projectEntityForCompare(ids.first)
        val right = vm.projectEntityForCompare(ids.second)
        comparePair = if (left != null && right != null) left to right else null
    }

    fun toggleSelection(id: Long) {
        val next = LibrarySelection.toggle(selectedIds, id)
        selectedIds = next
        if (next.isEmpty()) vm.librarySelecting = false
    }

    fun enterSelection(ids: Set<Long>) {
        selectedIds = ids
        batchDeleteOpen = false
        batchAlbumOpen = false
        vm.librarySelecting = true
    }

    BackHandler(enabled = selecting) { exitSelection() }

    // 搜索/筛选只在这几个输入变化时重算，输入文字时不做整表遍历
    val visibleProjects = remember(projects, vm.libraryQuery, vm.libraryFavOnly, vm.libraryAlbum) {
        vm.filteredProjects(projects)
    }
    val albums = remember(projects) { vm.albumNames(projects) }
    val paletteLabels = remember(vm.palettes) {
        vm.palettes.associate { it.brand to it.brandLabel }
    }
    val selectedProjects = remember(projects, selectedIds) {
        projects.filter { it.id in selectedIds }
    }
    val allVisibleSelected = remember(selectedIds, visibleProjects) {
        LibrarySelection.allSelected(selectedIds, visibleProjects.map { it.id })
    }
    val allSelectedFav = selectedProjects.isNotEmpty() && selectedProjects.all { it.fav }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selecting) {
                SelectionTopBar(
                    count = selectedProjects.size,
                    allVisibleSelected = allVisibleSelected,
                    canSelectAll = visibleProjects.isNotEmpty(),
                    onToggleAll = {
                        selectedIds =
                            LibrarySelection.toggleAll(selectedIds, visibleProjects.map { it.id })
                    },
                    onDone = ::exitSelection,
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Spacing.md,
                    end = Spacing.md,
                    top = if (selecting) Spacing.sm else Spacing.lg,
                    // 底栏浮在列表上，尾部多留滚动余量，最后一行不会被操作条压住
                    bottom = if (selecting) 108.dp else 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (!selecting) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "项目库",
                                    style = MaterialTheme.typography.displayLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                // 标题下只保留图纸总数；多选入口走长按卡片/右上角「⋯」，
                                // 顶栏不再单独摆一个「多选」链接，避免和标题抢视线。
                                Row(
                                    modifier = Modifier.padding(top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AnimatedNumber(
                                        value = "${projects.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        " 张图纸",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = onPickBpat,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.padding(bottom = 4.dp),
                            ) {
                                Icon(
                                    MorphIcons.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("导入 .bpat", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    ShSearchField(
                        value = vm.libraryQuery,
                        onValueChange = { vm.libraryQuery = it },
                        placeholder = "搜索图纸名称或文件夹",
                    )
                }

                // 收藏 / 专辑筛选：横向滚动的 pill chip
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        ShChip(
                            text = "全部",
                            selected = !vm.libraryFavOnly && vm.libraryAlbum == null,
                            onClick = {
                                vm.libraryFavOnly = false
                                vm.libraryAlbum = null
                            },
                        )
                        ShChip(
                            text = "收藏",
                            selected = vm.libraryFavOnly,
                            onClick = {
                                vm.libraryFavOnly = !vm.libraryFavOnly
                                vm.libraryAlbum = null
                            },
                            leading = MorphIcons.Star,
                        )
                        albums.forEach { album ->
                            ShChip(
                                text = album,
                                selected = vm.libraryAlbum == album,
                                onClick = {
                                    vm.libraryAlbum = if (vm.libraryAlbum == album) null else album
                                },
                            )
                        }
                    }
                }

                vm.lastError?.let { err ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(Spacing.sm),
                        ) {
                            Text(
                                err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                when {
                    projects.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        ShCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(ClayColors.TintSky),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        MorphIcons.LayoutGrid,
                                        contentDescription = null,
                                        tint = ClayColors.PrimaryInk,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                                Text(
                                    "还没有图纸",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = Spacing.md),
                                )
                                Text(
                                    "去创建页导入照片、像素画或色号图，生成的图纸会自动保存在这里。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = Spacing.xxs),
                                )
                                Button(
                                    onClick = onGoCreate,
                                    modifier = Modifier.padding(top = Spacing.md),
                                ) {
                                    Text("去创建")
                                }
                            }
                        }
                    }

                    visibleProjects.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        ShCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.lg),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("没有匹配的图纸", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "换个关键词，或清除当前筛选",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.xxs),
                                )
                                TextButton(
                                    onClick = {
                                        vm.libraryQuery = ""
                                        vm.libraryFavOnly = false
                                        vm.libraryAlbum = null
                                    },
                                    modifier = Modifier.padding(top = Spacing.xs),
                                ) {
                                    Text("清除筛选")
                                }
                            }
                        }
                    }

                    else -> items(
                        items = visibleProjects,
                        key = { it.id },
                        contentType = { "project-card" },
                    ) { project ->
                        ProjectCard(
                            project = project,
                            paletteLabel = paletteLabels[project.paletteBrand]
                                ?: project.paletteBrand,
                            selecting = selecting,
                            selected = project.id in selectedIds,
                            onClick = {
                                if (selecting) toggleSelection(project.id)
                                else vm.openProject(project)
                            },
                            onLongClick = {
                                if (selecting) toggleSelection(project.id)
                                else actionTarget = project
                            },
                            onMore = { actionTarget = project },
                        )
                    }
                }
            }
        }

        if (selecting) {
            SelectionActionBar(
                count = selectedProjects.size,
                favorite = allSelectedFav,
                onFav = {
                    vm.setFavProjects(selectedProjects.map { it.id }, !allSelectedFav)
                },
                onAlbum = { batchAlbumOpen = true },
                onCompare = {
                    val ids = selectedProjects.map { it.id }
                    if (ids.size == 2) compareIds = ids[0] to ids[1]
                },
                onDelete = { batchDeleteOpen = true },
                compareEnabled = selectedProjects.size == 2,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }

    actionTarget?.let { project ->
        ProjectActionSheet(
            project = project,
            paletteLabel = paletteLabels[project.paletteBrand] ?: project.paletteBrand,
            onDismiss = { actionTarget = null },
            onFav = { vm.setFavProject(project, !project.fav) },
            onDuplicate = { vm.duplicateProject(project) },
            onAlbum = { albumTarget = project },
            onRename = { renameTarget = project },
            onSelect = { enterSelection(setOf(project.id)) },
            onDelete = { deleteTarget = project },
        )
    }

    renameTarget?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("项目名") },
                    shape = RoundedCornerShape(8.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) vm.renameProject(target, name.trim())
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }

    deleteTarget?.let { target ->
        DeleteProjectDialog(
            title = "删除这份图纸？",
            message = "「${target.name}」会从项目库移除，缩略图和画像素图层工程一并清理。",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                vm.deleteProject(target)
                deleteTarget = null
            },
        )
    }

    if (batchDeleteOpen && selectedProjects.isNotEmpty()) {
        val count = selectedProjects.size
        DeleteProjectDialog(
            title = "删除 $count 份图纸？",
            message = "选中的图纸、缩略图和图层工程会一起移除。删除后 10 秒内还能从底部提示里撤销。",
            onDismiss = { batchDeleteOpen = false },
            onConfirm = {
                val ids = selectedProjects.map { it.id }
                batchDeleteOpen = false
                vm.deleteProjects(ids)
                exitSelection()
            },
        )
    }

    albumTarget?.let { target ->
        AlbumDialog(
            title = "创建文件夹",
            subtitle = "「${target.name}」",
            initial = target.album,
            albums = albums,
            onDismiss = { albumTarget = null },
            onConfirm = { album ->
                vm.setAlbumProject(target, album)
                albumTarget = null
            },
        )
    }

    if (batchAlbumOpen && selectedProjects.isNotEmpty()) {
        val commonAlbum = selectedProjects
            .map { it.album.trim() }
            .distinct()
            .singleOrNull()
            .orEmpty()
        AlbumDialog(
            title = "创建文件夹",
            subtitle = "已选 ${selectedProjects.size} 份图纸",
            initial = commonAlbum,
            albums = albums,
            onDismiss = { batchAlbumOpen = false },
            onConfirm = { album ->
                vm.setAlbumProjects(selectedProjects.map { it.id }, album)
                batchAlbumOpen = false
            },
        )
    }

    comparePair?.let { (left, right) ->
        ProjectCompareDialog(
            left = left,
            right = right,
            paletteLabel = { brand -> paletteLabels[brand] ?: brand },
            onDismiss = { compareIds = null },
            onDelete = { target ->
                vm.deleteProjects(listOf(target.id))
                exitSelection()
            },
        )
    }
}

/**
 * 删除确认框。
 *
 * 用图标 + 标题 + 说明 + 「取消 / 删除」两键：删除键是 error 实心样式，
 * 和普通确认框区分开；正文明确写出「10 秒内可撤销」，
 * 用户不会以为点了就没了。
 */
@Composable
private fun DeleteProjectDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    MorphIcons.Trash,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.padding(top = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        MorphIcons.RefreshCcw,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "删除后可在底部提示条里撤销",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                variant = ShVariant.Destructive,
            ) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/** 多选 Set 的保存器：旋转屏幕后选择不丢。 */
private val SelectionSaver = listSaver<Set<Long>, Long>(
    save = { it.toList() },
    restore = { it.toSet() },
)

/**
 * 多选顶栏。固定在列表上方不随滚动走，返回逻辑只保留「完成」一个出口，
 * 避免用户在长列表里滚到一半找不到退出按钮。
 */
@Composable
private fun SelectionTopBar(
    count: Int,
    allVisibleSelected: Boolean,
    canSelectAll: Boolean,
    onToggleAll: () -> Unit,
    onDone: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(ClayColors.Canvas)
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(ClayColors.Primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    MorphIcons.Check,
                    contentDescription = null,
                    tint = ClayColors.PrimaryInk,
                    modifier = Modifier.size(17.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            ) {
                Text(
                    "已选 $count 项",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ClayColors.Ink,
                )
                Text(
                    "点按图纸继续加选",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = ClayColors.Muted,
                )
            }
            TextButton(onClick = onToggleAll, enabled = canSelectAll) {
                Text(
                    if (allVisibleSelected) "取消全选" else "全选",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canSelectAll) ClayColors.BodyStrong else ClayColors.MutedSoft,
                )
            }
            TextButton(onClick = onDone) {
                Text(
                    "完成",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ClayColors.PrimaryInk,
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = ClayColors.HairlineSoft)
    }
}

/**
 * 多选底部操作条：三栏等宽，图标与文字都居中，删除固定在最右并用错误色。
 * 浮在列表上、离系统手势条 10dp，不占 Scaffold 的 bottomBar 槽位。
 */
@Composable
private fun SelectionActionBar(
    count: Int,
    favorite: Boolean,
    onFav: () -> Unit,
    onAlbum: () -> Unit,
    onCompare: () -> Unit,
    onDelete: () -> Unit,
    compareEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (count == 0) return
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .padding(horizontal = Spacing.md, vertical = 10.dp)
            .shadow(
                elevation = 14.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.14f),
            )
            .clip(shape)
            .background(ClayColors.SurfaceCard)
            .border(1.dp, ClayColors.Hairline, shape)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SelectionAction(
            icon = MorphIcons.Star,
            label = if (favorite) "取消收藏" else "收藏",
            onClick = onFav,
            modifier = Modifier.weight(1f),
        )
        SelectionAction(
            icon = MorphIcons.FolderOpen,
            label = "创建文件夹",
            onClick = onAlbum,
            modifier = Modifier.weight(1f),
        )
        // 恰选两张才能对比：少于两张置灰，避免点了没反应还以为是 bug
        SelectionAction(
            icon = MorphIcons.SwapHorizontal,
            label = "对比",
            onClick = onCompare,
            enabled = compareEnabled,
            modifier = Modifier.weight(1f),
        )
        SelectionAction(
            icon = MorphIcons.Trash,
            label = "删除",
            onClick = onDelete,
            danger = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SelectionAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val tint = when {
        !enabled -> ClayColors.MutedSoft
        danger -> MaterialTheme.colorScheme.error
        else -> ClayColors.BodyStrong
    }
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .pressScale(interaction, pressedScale = 0.94f)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = tint,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(
    project: ProjectSummary,
    paletteLabel: String,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val thumb by rememberProjectThumb(project)
    val edge by animateColorAsState(
        targetValue = if (selected) ClayColors.Primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(Motion.FAST),
        label = "projectCardEdge",
    )
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, edge, shape)
            .pressScale(interaction, pressedScale = 0.97f)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = thumb
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = project.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                BeadDot(ClayColors.BrandMint)
            }

            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(ClayColors.Primary.copy(alpha = 0.07f)),
                )
            }

            if (selecting) {
                SelectionBadge(
                    selected = selected,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            } else {
                if (project.fav) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            MorphIcons.Star,
                            contentDescription = "已收藏",
                            tint = ClayColors.BrandOchre,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.90f))
                        .border(1.dp, ClayColors.Hairline, CircleShape)
                        .clickable(onClick = onMore),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        MorphIcons.MoreVertical,
                        contentDescription = "更多操作",
                        tint = ClayColors.BodyStrong,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = Spacing.sm, vertical = 10.dp)) {
            Text(
                project.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append("${project.width}×${project.height} · $paletteLabel")
                    if (project.album.isNotBlank()) append(" · ${project.album}")
                    if (project.buildSeconds > 0) append(" · 已拼 ${BuildTimer.format(project.buildSeconds)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                relativeTime(project.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectionBadge(selected: Boolean, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val quick = if (motion.enabled) Motion.FAST else 0
    val base = if (motion.enabled) Motion.BASE else 0
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) ClayColors.Primary else Color.White.copy(alpha = 0.92f))
            .border(
                1.5.dp,
                if (selected) ClayColors.Primary else ClayColors.MutedSoft,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(quick)) + scaleIn(tween(base), initialScale = 0.55f),
            exit = fadeOut(tween(quick)) + scaleOut(tween(quick), targetScale = 0.55f),
        ) {
            Icon(
                MorphIcons.Check,
                contentDescription = "已选中",
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * 长按动作面板。
 *
 * 用底部 sheet 而不是 AlertDialog：选项行统一 20dp 左内边距、34dp 图标容器、
 * 12dp 图文间距，标题和数值各占一列，删除单独分隔并置底。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectActionSheet(
    project: ProjectSummary,
    paletteLabel: String,
    onDismiss: () -> Unit,
    onFav: () -> Unit,
    onDuplicate: () -> Unit,
    onAlbum: () -> Unit,
    onRename: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // 先播完收起动画再执行动作；否则弹窗会在 sheet 还盖着屏幕时弹出来
    fun closeThen(action: () -> Unit) {
        scope.launch {
            runCatching { sheetState.hide() }
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ClayColors.SurfaceCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(ClayColors.SurfaceStrong),
                )
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProjectThumb(project = project, size = 54)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            project.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (project.fav) {
                            Icon(
                                MorphIcons.Star,
                                contentDescription = "已收藏",
                                tint = ClayColors.BrandOchre,
                                modifier = Modifier
                                    .padding(start = 5.dp)
                                    .size(14.dp),
                            )
                        }
                    }
                    Text(
                        buildString {
                            append("${project.width}×${project.height} · $paletteLabel")
                            append(" · ${project.pages} 页")
                            if (project.buildSeconds > 0) append(" · 已拼 ${BuildTimer.format(project.buildSeconds)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ClayColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SheetActionRow(
                icon = MorphIcons.Star,
                label = if (project.fav) "取消收藏" else "收藏",
                iconTint = if (project.fav) ClayColors.BrandOchre else ClayColors.BodyStrong,
                iconBackground = if (project.fav) {
                    ClayColors.BrandOchre.copy(alpha = 0.12f)
                } else {
                    ClayColors.SurfaceSoft
                },
                onClick = { closeThen(onFav) },
            )
            SheetActionRow(
                icon = MorphIcons.FolderOpen,
                label = "创建文件夹",
                trailing = project.album.ifBlank { "未分组" },
                onClick = { closeThen(onAlbum) },
            )
            SheetActionRow(
                icon = MorphIcons.Copy,
                label = "复制",
                onClick = { closeThen(onDuplicate) },
            )
            SheetActionRow(
                icon = MorphIcons.Type,
                label = "重命名",
                onClick = { closeThen(onRename) },
            )
            SheetActionRow(
                icon = MorphIcons.CircleCheck,
                label = "多选",
                onClick = { closeThen(onSelect) },
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                thickness = 1.dp,
                color = ClayColors.HairlineSoft,
            )
            SheetActionRow(
                icon = MorphIcons.Trash,
                label = "删除",
                danger = true,
                onClick = { closeThen(onDelete) },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ClayColors.SurfaceSoft)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "取消",
                    style = MaterialTheme.typography.labelLarge,
                    color = ClayColors.BodyStrong,
                )
            }
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    trailing: String? = null,
    danger: Boolean = false,
    iconTint: Color = if (danger) MaterialTheme.colorScheme.error else ClayColors.BodyStrong,
    iconBackground: Color = if (danger) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.09f)
    } else {
        ClayColors.SurfaceSoft
    },
) {
    val labelColor = if (danger) MaterialTheme.colorScheme.error else ClayColors.BodyStrong
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .pressScale(interaction, pressedScale = 0.98f)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = ClayColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .widthIn(max = 140.dp),
            )
        }
    }
}

@Composable
private fun ProjectThumb(project: ProjectSummary, size: Int) {
    val thumb by rememberProjectThumb(project)
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = thumb
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            // 缩略图解码期间用流光占位：比白底更像「正在浮出」
            Box(
                Modifier
                    .fillMaxSize()
                    .shimmer(),
            )
        }
    }
}

@Composable
private fun rememberProjectThumb(project: ProjectSummary) = produceState<ImageBitmap?>(
    initialValue = null,
    project.thumbnailPath,
    project.updatedAt,
) {
    value = ThumbnailCache.load(project.thumbnailPath, project.updatedAt)
}

/** 专辑编辑框：单条和批量共用，已有专辑做成可点 chip，避免手打错字分出新组。 */
@Composable
private fun AlbumDialog(
    title: String,
    subtitle: String,
    initial: String,
    albums: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var album by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = ClayColors.Muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    singleLine = true,
                    label = { Text("文件夹名（留空 = 未分组）") },
                    shape = RoundedCornerShape(8.dp),
                )
                if (albums.isNotEmpty()) {
                    Text(
                        "已有文件夹",
                        style = MaterialTheme.typography.labelSmall,
                        color = ClayColors.Muted,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        albums.forEach { name ->
                            ShChip(
                                text = name,
                                selected = album.trim() == name,
                                onClick = { album = name },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(album) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 缩略图缓存：键带 updatedAt，图纸重新生成后不会拿到旧图。
 * 16MB 上限约等于 16 张 512×512 ARGB 图，够两屏卡片滚动复用。
 */
private object ThumbnailCache {
    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    suspend fun load(path: String?, updatedAt: Long): ImageBitmap? {
        if (path.isNullOrBlank()) return null
        val key = "$path#$updatedAt"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            cache.get(key) ?: decode(path)?.also { cache.put(key, it) }
        }
    }

    private fun decode(path: String): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    }
}

private fun relativeTime(time: Long): String {
    val diff = System.currentTimeMillis() - time
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "刚刚"
        diff < hour -> "${diff / minute} 分钟前"
        diff < day -> "${diff / hour} 小时前"
        diff < 7 * day -> "${diff / day} 天前"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(time))
    }
}

@Composable
private fun BeadDot(color: Color) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(color, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(3.dp)),
        )
    }
}

/**
 * 两张图纸并排对比：缩略图 + 9 行指标，值不同的行高亮。
 * 对比完直接删掉不想要的那张——这才是用户选两份进来的真实目的。
 */
@Composable
private fun ProjectCompareDialog(
    left: ProjectEntity,
    right: ProjectEntity,
    paletteLabel: (String) -> String,
    onDismiss: () -> Unit,
    onDelete: (ProjectEntity) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<ProjectEntity?>(null) }
    val rows = remember(left, right) { ProjectStats.rows(left, right, paletteLabel) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = ClayColors.SurfaceCard,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "对比图纸",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(MorphIcons.X, contentDescription = "关闭对比")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompareThumbColumn(left, Modifier.weight(1f))
                    CompareThumbColumn(right, Modifier.weight(1f))
                }
                HorizontalDivider(thickness = 1.dp, color = ClayColors.HairlineSoft)
                rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (row.differs) ClayColors.Primary.copy(alpha = 0.07f)
                                else Color.Transparent,
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = ClayColors.Muted,
                            modifier = Modifier.weight(0.85f),
                        )
                        Text(
                            row.left,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (row.differs) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            row.right,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (row.differs) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    TextButton(
                        onClick = { pendingDelete = left },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("删左侧", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = { pendingDelete = right },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("删右侧", color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("关闭")
                    }
                }
            }
        }
    }
    pendingDelete?.let { target ->
        DeleteProjectDialog(
            title = "删除「${target.name}」？",
            message = "会从项目库移除，缩略图和图层工程一并清理；10 秒内可从底部提示撤销。",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                onDelete(target)
            },
        )
    }
}

@Composable
private fun CompareThumbColumn(entity: ProjectEntity, modifier: Modifier) {
    val thumb by produceState<ImageBitmap?>(null, entity.thumbnailPath, entity.updatedAt) {
        value = ThumbnailCache.load(entity.thumbnailPath, entity.updatedAt)
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            thumb?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Text(
            entity.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
