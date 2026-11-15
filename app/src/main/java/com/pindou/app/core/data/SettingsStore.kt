package com.pindou.app.core.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** 用户设置（默认参数、外观） */
class SettingsStore(private val context: Context) {

    companion object {
        val GRID_W = intPreferencesKey("default_grid_w")
        val GRID_H = intPreferencesKey("default_grid_h")
        val PALETTE_BRAND = stringPreferencesKey("default_palette_brand")
        val THEME_MODE = stringPreferencesKey("theme_mode")       // system | light | dark
        val REFRESH_MODE = stringPreferencesKey("refresh_mode")   // system | 60 | 90 | 120
        val AI_SUBJECT = booleanPreferencesKey("ai_subject_segmentation")
        // 浮窗位置用 0..1 的比例保存，换屏幕/横竖屏后仍落在可见范围内。
        // -1 表示尚未自定义，使用默认左/右居中。
        val STUDIO_TOOL_X = floatPreferencesKey("studio_tool_x")
        val STUDIO_TOOL_Y = floatPreferencesKey("studio_tool_y")
        val STUDIO_COLOR_X = floatPreferencesKey("studio_color_x")
        val STUDIO_COLOR_Y = floatPreferencesKey("studio_color_y")
        val STUDIO_TOOL_COLLAPSED = booleanPreferencesKey("studio_tool_collapsed")
        val STUDIO_COLOR_COLLAPSED = booleanPreferencesKey("studio_color_collapsed")
        val STUDIO_TUTORIAL_SEEN = booleanPreferencesKey("studio_tutorial_seen")

        // 统计只在“新建项目落库”时累加，重新生成/改参数不会重复计数。
        val STAT_TOTAL = intPreferencesKey("stat_total")
        val STAT_PHOTO = intPreferencesKey("stat_photo")
        val STAT_PIXEL = intPreferencesKey("stat_pixel")
        val STAT_CHART = intPreferencesKey("stat_chart")
        val STAT_TEXT = intPreferencesKey("stat_text")
        val STAT_BLANK = intPreferencesKey("stat_blank")
        val STAT_BPAT = intPreferencesKey("stat_bpat")
    }

    /** 创建方式：与创建页入口一一对应，便于统计口径稳定。 */
    enum class CreationKind { PHOTO, PIXEL, CHART, TEXT, BLANK, BPAT }

    /**
     * 累计生成统计。
     *
     * total 用独立 key 持久化，不在这里从分项求和：旧版本升级时各分项
     * 可能不存在，而 total 已经写入后，求和会让页面数字随分项补齐而跳变。
     */
    data class Stats(
        val total: Int = 0,
        val photo: Int = 0,
        val pixel: Int = 0,
        val chart: Int = 0,
        val text: Int = 0,
        val blank: Int = 0,
        val bpat: Int = 0,
    ) {
        fun count(kind: CreationKind): Int = when (kind) {
            CreationKind.PHOTO -> photo
            CreationKind.PIXEL -> pixel
            CreationKind.CHART -> chart
            CreationKind.TEXT -> text
            CreationKind.BLANK -> blank
            CreationKind.BPAT -> bpat
        }

        /** 纯函数递增，UI 与测试都不依赖 DataStore。 */
        fun bump(kind: CreationKind): Stats = when (kind) {
            CreationKind.PHOTO -> copy(total = total + 1, photo = photo + 1)
            CreationKind.PIXEL -> copy(total = total + 1, pixel = pixel + 1)
            CreationKind.CHART -> copy(total = total + 1, chart = chart + 1)
            CreationKind.TEXT -> copy(total = total + 1, text = text + 1)
            CreationKind.BLANK -> copy(total = total + 1, blank = blank + 1)
            CreationKind.BPAT -> copy(total = total + 1, bpat = bpat + 1)
        }
    }

    data class Snapshot(
        val defaultGridW: Int = 48,
        val defaultGridH: Int = 48,
        val defaultPaletteBrand: String = "MARD",
        val themeMode: String = "system",
        val refreshMode: String = "system",
        val aiSubjectEnabled: Boolean = false,
        val studioToolX: Float = -1f,
        val studioToolY: Float = -1f,
        val studioColorX: Float = -1f,
        val studioColorY: Float = -1f,
        val studioToolCollapsed: Boolean = false,
        val studioColorCollapsed: Boolean = false,
        /** null = 从未记录，首次进入画像素时展示教程。 */
        val studioTutorialSeen: Boolean? = null,
    )

    val flow: Flow<Snapshot> = context.dataStore.data.map { p -> p.toSnapshot() }

    val statsFlow: Flow<Stats> = context.dataStore.data.map { p -> p.toStats() }

    private fun Preferences.toSnapshot() = Snapshot(
        defaultGridW = this[GRID_W] ?: 48,
        defaultGridH = this[GRID_H] ?: 48,
        defaultPaletteBrand = this[PALETTE_BRAND] ?: "MARD",
        themeMode = this[THEME_MODE] ?: "system",
        refreshMode = this[REFRESH_MODE] ?: "system",
        aiSubjectEnabled = this[AI_SUBJECT] ?: false,
        studioToolX = this[STUDIO_TOOL_X] ?: -1f,
        studioToolY = this[STUDIO_TOOL_Y] ?: -1f,
        studioColorX = this[STUDIO_COLOR_X] ?: -1f,
        studioColorY = this[STUDIO_COLOR_Y] ?: -1f,
        studioToolCollapsed = this[STUDIO_TOOL_COLLAPSED] ?: false,
        studioColorCollapsed = this[STUDIO_COLOR_COLLAPSED] ?: false,
        studioTutorialSeen = this[STUDIO_TUTORIAL_SEEN],
    )

    private fun Preferences.toStats() = Stats(
        total = this[STAT_TOTAL] ?: 0,
        photo = this[STAT_PHOTO] ?: 0,
        pixel = this[STAT_PIXEL] ?: 0,
        chart = this[STAT_CHART] ?: 0,
        text = this[STAT_TEXT] ?: 0,
        blank = this[STAT_BLANK] ?: 0,
        bpat = this[STAT_BPAT] ?: 0,
    )

    suspend fun setDefaultGrid(w: Int, h: Int) = context.dataStore.edit {
        it[GRID_W] = w
        it[GRID_H] = h
    }

    suspend fun setDefaultPalette(brand: String) = context.dataStore.edit { it[PALETTE_BRAND] = brand }

    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[THEME_MODE] = mode }

    suspend fun setRefreshMode(mode: String) = context.dataStore.edit { it[REFRESH_MODE] = mode }

    suspend fun setAiSubjectEnabled(enabled: Boolean) =
        context.dataStore.edit { it[AI_SUBJECT] = enabled }

    suspend fun setStudioRailPositions(toolX: Float, toolY: Float, colorX: Float, colorY: Float) =
        context.dataStore.edit {
            // -1 是“未自定义”哨兵，保留；有效位置才夹到 0..1
            it[STUDIO_TOOL_X] = toolX.coerceIn(-1f, 1f)
            it[STUDIO_TOOL_Y] = toolY.coerceIn(-1f, 1f)
            it[STUDIO_COLOR_X] = colorX.coerceIn(-1f, 1f)
            it[STUDIO_COLOR_Y] = colorY.coerceIn(-1f, 1f)
        }

    suspend fun setStudioRailCollapsed(toolCollapsed: Boolean, colorCollapsed: Boolean) =
        context.dataStore.edit {
            it[STUDIO_TOOL_COLLAPSED] = toolCollapsed
            it[STUDIO_COLOR_COLLAPSED] = colorCollapsed
        }

    suspend fun setStudioTutorialSeen(seen: Boolean) =
        context.dataStore.edit { it[STUDIO_TUTORIAL_SEEN] = seen }

    /**
     * 记录一次“新图纸落库”。
     *
     * 分项与总数在同一次 edit 里写完：DataStore 的 edit 是串行的，进程被
     * 杀时不会出现总数加了、分项没加的中间态。
     */
    suspend fun recordCreation(kind: CreationKind) = context.dataStore.edit { p ->
        val key = when (kind) {
            CreationKind.PHOTO -> STAT_PHOTO
            CreationKind.PIXEL -> STAT_PIXEL
            CreationKind.CHART -> STAT_CHART
            CreationKind.TEXT -> STAT_TEXT
            CreationKind.BLANK -> STAT_BLANK
            CreationKind.BPAT -> STAT_BPAT
        }
        val category = p[key] ?: 0
        val oldTotal = p[STAT_TOTAL] ?: (
            (p[STAT_PHOTO] ?: 0) + (p[STAT_PIXEL] ?: 0) + (p[STAT_CHART] ?: 0) +
                (p[STAT_TEXT] ?: 0) + (p[STAT_BLANK] ?: 0) + (p[STAT_BPAT] ?: 0)
            )
        p[STAT_TOTAL] = oldTotal + 1
        p[key] = category + 1
    }

}
