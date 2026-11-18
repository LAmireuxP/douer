package com.pindou.app.core.palette

import android.content.Context

/** 从 APK assets 加载内置色卡。文件名即资产目录 app/src/main/assets/palettes/ 下的 json。 */
object PaletteAssets {

    const val DIR = "palettes"

    /** 内置色卡文件清单（新增色卡时同步维护） */
    val FILES = listOf(
        // 全色系套装（2.6mm 融合豆实物色卡，24→272 逐级扩色）
        "all_solid_24.json", "all_solid_48.json", "all_solid_72.json",
        "all_solid_96.json", "all_solid_120.json", "all_solid_144.json",
        "all_solid_216.json", "all_solid_221.json", "all_solid_272.json",
        // 8mm 大颗粒：色号体系沿用 MARD，尺寸由 beadSize 区分
        "mard_8mm.json",
        "mard.json", "coco.json", "manman.json", "panpan.json", "mixiaowo.json",
        "perler.json", "hama.json", "artkal_s.json", "artkal_mini.json",
    )

    fun loadAll(context: Context): List<BeadPalette> =
        FILES.mapNotNull { file ->
            runCatching {
                context.assets.open("$DIR/$file").bufferedReader().use { PaletteJson.parse(it.readText()) }
            }.getOrNull()
        }

    /**
     * 同品牌可能有多套色卡（Artkal S 5mm / Artkal Mini 2.6mm）。
     * 只按 brand 取第一套会把小豆项目装进大豆色卡，格子索引越界直接崩预览；
     * 优先 brand + beadSize，找不到再退回 brand。
     */
    fun pick(
        palettes: List<BeadPalette>,
        brand: String,
        beadSizeTag: String,
    ): BeadPalette? {
        val size = BeadSize.fromTag(beadSizeTag)
        return palettes.firstOrNull { it.brand == brand && it.beadSize == size }
            ?: palettes.firstOrNull { it.brand == brand }
    }
}
