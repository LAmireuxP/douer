package com.pindou.app.core.data

/**
 * 项目对比用的纯统计：全部从已落库的 cells / doneCells 现算，不加 schema。
 *
 * 60×60 一页也就 3600 格，解一遍是毫秒级；对比页只对两张项目算，
 * 不值得为它把色数和进度冗余进数据库。
 */
object ProjectStats {

    /** 用到的色卡索引数（跳过空格 -1；多页合并去重） */
    fun usedColors(entity: ProjectEntity): Int {
        if (entity.cells.isEmpty()) return 0
        val cells = GridCodec.decodeCells(entity.cells)
        val seen = HashSet<Int>(64)
        for (c in cells) if (c >= 0) seen.add(c)
        return seen.size
    }

    fun totalCells(entity: ProjectEntity): Int =
        entity.width * entity.height * entity.pages.coerceAtLeast(1)

    fun doneCells(entity: ProjectEntity): Int =
        entity.doneCells?.count { it.toInt() == 1 } ?: 0

    /** 0..100；没有进度数据返回 0 */
    fun donePercent(entity: ProjectEntity): Int {
        val total = totalCells(entity)
        if (total <= 0) return 0
        return (doneCells(entity) * 100 / total).coerceIn(0, 100)
    }

    /** 底板数：与施工引导同口径，29 格一块板 */
    fun boards(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 0
        return ((width + 28) / 29) * ((height + 28) / 29)
    }

    /** 对比表一行：值和「是否不同」一起算好，UI 只负责画 */
    data class Row(
        val id: String,
        val label: String,
        val left: String,
        val right: String,
        val differs: Boolean,
    )

    /**
     * @param paletteLabel 品牌 id → 展示名（UI 层字典）
     */
    fun rows(
        a: ProjectEntity,
        b: ProjectEntity,
        paletteLabel: (String) -> String,
    ): List<Row> {
        fun row(id: String, label: String, left: String, right: String) =
            Row(id, label, left, right, left != right)

        return listOf(
            row("size", "尺寸", "${a.width}×${a.height}", "${b.width}×${b.height}"),
            row("pages", "页数", "${a.pages} 页", "${b.pages} 页"),
            row(
                "palette", "色卡",
                paletteLabel(a.paletteBrand), paletteLabel(b.paletteBrand),
            ),
            row("colors", "用色数", "${usedColors(a)} 色", "${usedColors(b)} 色"),
            row("cells", "颗粒数", "${totalCells(a)} 粒", "${totalCells(b)} 粒"),
            row(
                "boards", "底板数",
                "${boards(a.width, a.height)} 块", "${boards(b.width, b.height)} 块",
            ),
            row(
                "done", "施工进度",
                "${donePercent(a)}%", "${donePercent(b)}%",
            ),
            row(
                "build", "已拼时长",
                com.pindou.app.core.build.BuildTimer.format(a.buildSeconds),
                com.pindou.app.core.build.BuildTimer.format(b.buildSeconds),
            ),
            row(
                "updated", "更新时间",
                formatDate(a.updatedAt), formatDate(b.updatedAt),
            ),
        )
    }

    private fun formatDate(epochMs: Long): String {
        if (epochMs <= 0) return "—"
        val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        return fmt.format(java.util.Date(epochMs))
    }
}
