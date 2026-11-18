package com.pindou.app.ui.library

import com.pindou.app.core.data.ProjectSummary

/**
 * 项目库列表的纯筛选逻辑。
 *
 * 从 [ProjectLibraryController] 拆出来单独放，是为了能在 JVM 测试里
 * 用假 [ProjectSummary] 直接验证搜索词/收藏/专辑的组合语义，
 * 不必构造 Room 和 Android Context。
 */
internal object ProjectLibraryFilter {

    /**
     * 按搜索词、收藏、专辑过滤。
     *
     * - 搜索词只匹配名称，忽略大小写与首尾空白；
     * - 收藏与专辑是「与」关系，专辑为 null 表示不过滤；
     * - 空搜索词不做名称过滤，保证空库与全量列表行为一致。
     */
    fun filter(
        all: List<ProjectSummary>,
        query: String,
        favOnly: Boolean,
        album: String?,
    ): List<ProjectSummary> {
        val q = query.trim()
        return all.filter { p ->
            (q.isEmpty() || p.name.contains(q, ignoreCase = true)) &&
                (!favOnly || p.fav) &&
                (album == null || p.album == album)
        }
    }

    /** 已用到的专辑名（供筛选 chips）：去空、去重，保持首次出现顺序。 */
    fun albumNames(all: List<ProjectSummary>): List<String> =
        all.map { it.album }.filter { it.isNotBlank() }.distinct()
}
