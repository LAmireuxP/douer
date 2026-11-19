package com.pindou.app.ui.params

import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.palette.BeadSize
import kotlin.math.roundToInt
import java.util.Locale

/**
 * 参数页的纯计算层。
 *
 * 这里只放「能用手算验证」的东西：豆子数、成品厘米数、比例偏差、档位归属、
 * 色卡分组。放在 Compose 外面是为了能在 JVM 单测里把边界值钉死——
 * 这些数字直接决定用户买多少豆子，算错比排版错更难发现。
 */
object ParamsMetrics {

    /** 固定档位：语义是「宽」，高按图片比例推。顺序 = 从粗到细。 */
    val GridWidths = listOf(36, 52, 78, 104, 156, 200)

    /** 图片宽高比与网格宽高比相差超过这个倍数就提示会拉伸（约 15%） */
    const val SKEW_WARN = 1.15

    fun beadCount(gridW: Int, gridH: Int): Int =
        gridW.coerceAtLeast(0) * gridH.coerceAtLeast(0)

    /**
     * 千分位。不用 String.format("%,d")：它跟默认 Locale 绑定，
     * 阿拉伯语/印地语环境会得到不一样的分组符，界面会突然变样。
     */
    fun formatCount(value: Int): String {
        val digits = value.coerceAtLeast(0).toString()
        if (digits.length <= 3) return digits
        val out = StringBuilder(digits.length + digits.length / 3)
        digits.forEachIndexed { index, ch ->
            if (index > 0 && (digits.length - index) % 3 == 0) out.append(',')
            out.append(ch)
        }
        return out.toString()
    }

    /** 当前应使用的宽高比：像素画用原画网格，照片用图片本身 */
    fun effectiveAspect(pixelArt: Pair<Int, Int>?, imageW: Int, imageH: Int): Double {
        if (pixelArt != null && pixelArt.second > 0) {
            return pixelArt.first.toDouble() / pixelArt.second
        }
        return if (imageW > 0 && imageH > 0) imageW.toDouble() / imageH else 1.0
    }

    /**
     * 只给宽度时的推导高度。
     * 必须与 AppViewModel.setGridWidthPreset 用同一条公式，否则 chip 会点不亮。
     */
    fun derivedHeight(width: Int, aspect: Double): Int {
        if (width <= 0 || aspect <= 0.0) return width.coerceAtLeast(5)
        return (width / aspect).roundToInt().coerceIn(5, 200)
    }

    /** 当前网格命中了哪个固定档位；没命中返回 null */
    fun matchGridWidth(
        gridW: Int,
        gridH: Int,
        pixelArt: Pair<Int, Int>?,
        imageW: Int,
        imageH: Int,
    ): Int? {
        val aspect = effectiveAspect(pixelArt, imageW, imageH)
        return GridWidths.firstOrNull { it == gridW && derivedHeight(it, aspect) == gridH }
    }

    /**
     * 成品物理尺寸（厘米）。珠径是圆心距，所以网格边长 × 珠径就是成品边长；
     * 8mm 的 36 格是 28.8cm，不是「36 × 8mm」以外的什么数。
     */
    fun finishedSizeCm(gridW: Int, gridH: Int, pitchMm: Double): Pair<Double, Double> {
        if (pitchMm <= 0.0) return 0.0 to 0.0
        val perCellCm = pitchMm / 10.0
        return (gridW.coerceAtLeast(0) * perCellCm) to (gridH.coerceAtLeast(0) * perCellCm)
    }

    /** 一位小数，固定用 '.'，与 Locale 无关 */
    fun formatCm(cm: Double): String =
        String.format(Locale.ROOT, "%.1f", cm.coerceAtLeast(0.0))

    /** 宽高比偏差倍数，>= 1；任一维为 0 时按 1 处理（没有可比比例） */
    fun aspectSkew(imageW: Int, imageH: Int, gridW: Int, gridH: Int): Double {
        if (imageW <= 0 || imageH <= 0 || gridW <= 0 || gridH <= 0) return 1.0
        val image = imageW.toDouble() / imageH
        val grid = gridW.toDouble() / gridH
        return if (image > grid) image / grid else grid / image
    }
}

/** 色卡筛选：页面卡片和选择弹窗共用同一份状态 */
enum class PaletteFilter(val label: String) {
    ALL("全部"),
    FULL("全色系"),
    MM5("5mm"),
    MM8("8mm"),
    MM26("2.6mm"),
    ;

    companion object {
        fun fromName(name: String): PaletteFilter =
            entries.firstOrNull { it.name == name } ?: ALL
    }
}

/**
 * 色卡分组。分组顺序写死在 enum 里：全色系→常用 5mm→8mm→2.6mm，
 * 与「先选规格再选品牌」的决策顺序一致，不按数据库返回顺序漂移。
 */
enum class PaletteGroup(val title: String, val note: String) {
    FULL("全色系套装", "2.6mm 融合豆，一套覆盖全部色相"),
    MM5("5mm 普通豆", "最常见的规格，新手先从这个开始"),
    MM8("8mm 大颗粒", "大板、儿童手作，一格就是 8mm"),
    MM26("2.6mm 小豆", "细节多、成品小，适合挂件"),
}

/** 色卡 → 分组。全色系优先，避免 2.6mm 融合豆被算进品牌小豆组 */
fun BeadPalette.group(): PaletteGroup = when {
    brand.startsWith("ALL") -> PaletteGroup.FULL
    beadSize == BeadSize.MM8 -> PaletteGroup.MM8
    beadSize == BeadSize.MM2_6 -> PaletteGroup.MM26
    else -> PaletteGroup.MM5
}

/**
 * 色卡的唯一键。
 *
 * 不能只用 brand：MARD 同时有 5mm 和 8mm 两套，brand 完全一样，
 * 只比 brand 会让弹窗里两张卡同时打勾、列表 key 也会撞。
 */
fun BeadPalette.paletteKey(): String =
    "$brand|${beadSize.name}|${colors.size}|$brandLabel"

fun filterPalettes(list: List<BeadPalette>, filter: PaletteFilter): List<BeadPalette> = when (filter) {
    PaletteFilter.ALL -> list
    PaletteFilter.FULL -> list.filter { it.group() == PaletteGroup.FULL }
    PaletteFilter.MM5 -> list.filter { it.group() == PaletteGroup.MM5 }
    PaletteFilter.MM8 -> list.filter { it.group() == PaletteGroup.MM8 }
    PaletteFilter.MM26 -> list.filter { it.group() == PaletteGroup.MM26 }
}

/** 按 [PaletteGroup] 顺序分组，空组不返回 */
fun groupPalettes(list: List<BeadPalette>): List<Pair<PaletteGroup, List<BeadPalette>>> =
    PaletteGroup.entries.mapNotNull { group ->
        val items = list.filter { it.group() == group }
        if (items.isEmpty()) null else group to items
    }
