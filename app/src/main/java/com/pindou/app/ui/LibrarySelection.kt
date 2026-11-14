package com.pindou.app.ui

/**
 * 项目库多选的纯集合逻辑。
 *
 * 页面状态只存一个 Set<Long>：选中、取消、全选都在这里算，
 * Compose 层不写分支，单测能直接覆盖「筛选后再全选」这类边界。
 */
object LibrarySelection {

    /** 点一次加选，再点一次取消。 */
    fun toggle(selected: Set<Long>, id: Long): Set<Long> =
        if (id in selected) selected - id else selected + id

    /**
     * 全选只作用于当前筛选结果：已选但被搜索/专辑过滤掉的图纸保持选中，
     * 用户切回「全部」时不会丢选择。
     */
    fun toggleAll(selected: Set<Long>, visible: Collection<Long>): Set<Long> {
        if (visible.isEmpty()) return selected
        val visibleSet = visible.toSet()
        return if (selected.containsAll(visibleSet)) selected - visibleSet else selected + visibleSet
    }

    /** 当前筛选结果是否已全部选中；空列表不算全选，按钮应保持「全选」。 */
    fun allSelected(selected: Set<Long>, visible: Collection<Long>): Boolean =
        visible.isNotEmpty() && selected.containsAll(visible)
}
