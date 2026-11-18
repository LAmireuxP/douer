package com.pindou.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 一套界面的完整色板。
 *
 * 保留 37 个命名 token（而不是让界面直接读 Material 的 primary/surface）：
 * 现有 17 个文件、186 处调用点都在用 `ClayColors.Xxx`，把 ClayColors 改成
 * 读当前色板的门面，切换主题时这些调用点一行都不用改。
 *
 * 注意：本类型刻意不含任何 @Composable，纯数据 + 纯函数，
 * 这样 JVM 单测可以直接断言配色关系（对比度、深色是否真的更暗）。
 */
@Immutable
data class Palette(
    val primary: Color,
    val primaryActive: Color,
    val primaryInk: Color,
    val primaryDisabled: Color,
    val ink: Color,
    val body: Color,
    val bodyStrong: Color,
    val muted: Color,
    val mutedSoft: Color,
    val hairline: Color,
    val hairlineSoft: Color,
    val canvas: Color,
    val stage: Color,
    val surfaceSoft: Color,
    val surfaceCard: Color,
    val surfaceStrong: Color,
    val surfaceDark: Color,
    val surfaceDarkElevated: Color,
    val onPrimary: Color,
    val onDark: Color,
    val onDarkSoft: Color,
    val brandBlue: Color,
    val brandPink: Color,
    val brandTeal: Color,
    val brandLavender: Color,
    val brandPeach: Color,
    val brandOchre: Color,
    val brandMint: Color,
    val brandCoral: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val tintGreen: Color,
    val tintMint: Color,
    val tintButter: Color,
    val tintSky: Color,
    val tintRose: Color,
)

/**
 * 圆角风格：不同设计语言的「手感」差异主要在这里。
 *
 * - Tight：Stripe / Linear / Apple 一类的紧凑圆角；
 * - Soft：默认豆儿、Notion 一类的卡片感；
 * - Pill：Spotify / Airbnb 一类的胶囊按钮。
 */
@Immutable
data class ShapeStyle(
    val button: Dp,
    val card: Dp,
    val pillButtons: Boolean = false,
)

/**
 * 一套可切换的主题。
 *
 * 参考 awesome-design-md 的 DESIGN.md（VoltAgent/awesome-design-md，MIT）里
 * 对各家设计语言的取色与几何描述，转成 App 只需要的那部分：
 * 主色 / 画布 / 卡面 / 文字层级 / 描边 / 深色底 / 圆角。
 * 只借「设计语言」，不复制任何品牌素材，也不冒用品牌名做主题名。
 */
@Immutable
data class AppTheme(
    val id: String,
    val name: String,
    val tagline: String,
    val reference: String,
    val style: ShapeStyle,
    val swatch: List<Color>,
    val light: Palette,
    val dark: Palette,
    /** 设置页渐变条的两端颜色；渐变主题才有值，不参与色板推导。 */
    val gradient: Pair<Color, Color>? = null,
) {
    fun palette(dark: Boolean): Palette = if (dark) this.dark else light
}

/** 一套主题的「种子」：只写差异最大的十几个值，其余按明暗关系统一推导。 */
private class Seeds(
    val accent: Color,
    val onAccent: Color,
    val accentInk: Color,
    val canvas: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val hairline: Color,
    val darkCanvas: Color,
    val darkCard: Color,
    val darkInk: Color,
    val darkAccent: Color,
    val accents: List<Color>,
    val semantic: Triple<Color, Color, Color> = Triple(
        Color(0xFF3E9B72),
        Color(0xFFC9912A),
        Color(0xFFCF5346),
    ),
)

internal fun mix(base: Color, accent: Color, amount: Float) =
    androidx.compose.ui.graphics.lerp(base, accent, amount)

private fun lightOf(s: Seeds): Palette {
    val (success, warning, error) = s.semantic
    val a = s.accents
    return Palette(
        primary = s.accent,
        primaryActive = mix(s.accent, Color.Black, 0.18f),
        primaryInk = s.accentInk,
        primaryDisabled = mix(s.canvas, s.accent, 0.18f),
        ink = s.ink,
        body = mix(s.ink, s.canvas, 0.22f),
        bodyStrong = mix(s.ink, s.canvas, 0.08f),
        muted = s.muted,
        mutedSoft = mix(s.muted, s.canvas, 0.42f),
        hairline = s.hairline,
        hairlineSoft = mix(s.hairline, s.card, 0.55f),
        canvas = s.canvas,
        stage = mix(s.canvas, s.ink, 0.045f),
        surfaceSoft = mix(s.canvas, s.accent, 0.06f),
        surfaceCard = s.card,
        surfaceStrong = mix(s.canvas, s.ink, 0.075f),
        surfaceDark = s.darkCanvas,
        surfaceDarkElevated = s.darkCard,
        onPrimary = s.onAccent,
        onDark = s.darkInk,
        onDarkSoft = mix(s.darkInk, s.darkCanvas, 0.40f),
        brandBlue = a[0],
        brandPink = a[1],
        brandTeal = a[2],
        brandLavender = a[3],
        brandPeach = a[4],
        brandOchre = a[5],
        brandMint = a[6],
        brandCoral = a[7],
        success = success,
        warning = warning,
        error = error,
        tintGreen = mix(s.card, a[0], 0.12f),
        tintMint = mix(s.card, a[6], 0.12f),
        tintButter = mix(s.card, a[5], 0.14f),
        tintSky = mix(s.card, s.accent, 0.10f),
        tintRose = mix(s.card, a[1], 0.12f),
    )
}

private fun darkOf(s: Seeds): Palette {
    val (success, warning, error) = s.semantic
    val a = s.accents
    val accent = s.darkAccent
    return Palette(
        primary = accent,
        primaryActive = mix(accent, Color.White, 0.16f),
        primaryInk = mix(accent, Color.White, 0.30f),
        primaryDisabled = mix(s.darkCard, accent, 0.30f),
        ink = s.darkInk,
        body = mix(s.darkInk, s.darkCanvas, 0.20f),
        bodyStrong = mix(s.darkInk, s.darkCanvas, 0.08f),
        muted = mix(s.darkInk, s.darkCanvas, 0.40f),
        mutedSoft = mix(s.darkInk, s.darkCanvas, 0.60f),
        hairline = mix(s.darkCanvas, s.darkInk, 0.16f),
        hairlineSoft = mix(s.darkCanvas, s.darkInk, 0.08f),
        canvas = s.darkCanvas,
        stage = mix(s.darkCanvas, s.darkInk, 0.05f),
        surfaceSoft = mix(s.darkCard, accent, 0.05f),
        surfaceCard = s.darkCard,
        surfaceStrong = mix(s.darkCard, s.darkInk, 0.08f),
        surfaceDark = s.darkCanvas,
        surfaceDarkElevated = s.darkCard,
        onPrimary = mix(s.darkCanvas, Color.Black, 0.55f),
        onDark = s.darkInk,
        onDarkSoft = mix(s.darkInk, s.darkCanvas, 0.40f),
        brandBlue = a[0],
        brandPink = a[1],
        brandTeal = a[2],
        brandLavender = a[3],
        brandPeach = a[4],
        brandOchre = a[5],
        brandMint = a[6],
        brandCoral = a[7],
        success = mix(success, Color.White, 0.14f),
        warning = mix(warning, Color.White, 0.14f),
        error = mix(error, Color.White, 0.18f),
        tintGreen = mix(s.darkCard, a[0], 0.22f),
        tintMint = mix(s.darkCard, a[6], 0.22f),
        tintButter = mix(s.darkCard, a[5], 0.24f),
        tintSky = mix(s.darkCard, accent, 0.22f),
        tintRose = mix(s.darkCard, a[1], 0.22f),
    )
}

private fun theme(
    id: String,
    name: String,
    tagline: String,
    reference: String,
    style: ShapeStyle,
    seeds: Seeds,
    gradient: Pair<Color, Color>? = null,
): AppTheme = AppTheme(
    id = id,
    name = name,
    tagline = tagline,
    reference = reference,
    style = style,
    swatch = listOf(seeds.accent, seeds.accentInk, seeds.darkCanvas, seeds.darkAccent),
    light = lightOf(seeds),
    dark = darkOf(seeds),
    gradient = gradient,
)

/**
 * 16 套主题：默认豆儿 + 7 套参考市面产品设计语言 + 8 套用户渐变配色。
 *
 * 顺序即设置页里的展示顺序；第一项是旧版配色，保证老用户升级后不变。
 */
object AppThemes {

    private val soft = ShapeStyle(button = 8.dp, card = 12.dp)

    // 渐变主题的两端色，取自用户截图；主色再从这两端派生。
    private val limeZest = Color(0xFFDDFFC2)
    private val lilacGray = Color(0xFFEEE1FF)
    private val blushNude = Color(0xFFFFEAE1)
    private val skyBlue = Color(0xFFB6FAFF)
    private val springGreen = Color(0xFFCFFFFB)
    private val blueGray = Color(0xFFB9DFFF)
    private val lightBlue = Color(0xFFDBDDFF)
    private val blushPink = Color(0xFFFFD0D0)
    private val aqua = Color(0xFFACFFF1)
    private val roseDust = Color(0xFFFFDCDC)
    private val cream = Color(0xFFFFFFD0)
    private val powderBlueGray = Color(0xFFD8EFFF)
    private val dustyRose = Color(0xFFD89CA6)
    private val paleRose = Color(0xFFFFC5CE)
    private val lavender = Color(0xFFD2CDFF)

    val Douer: AppTheme = theme(
        id = "douer",
        name = "豆儿",
        tagline = "天蓝 · 当前默认",
        reference = "本应用原配色",
        style = soft,
        seeds = Seeds(
            accent = Color(0xFF5FBAFF),
            onAccent = Color(0xFF07293F),
            accentInk = Color(0xFF136FB4),
            canvas = Color(0xFFF7FAFD),
            card = Color(0xFFFFFFFF),
            ink = Color(0xFF16202B),
            muted = Color(0xFF75828F),
            hairline = Color(0xFFE5E9F1),
            darkCanvas = Color(0xFF0E1419),
            darkCard = Color(0xFF18212A),
            darkInk = Color(0xFFF4F8FC),
            darkAccent = Color(0xFF7EC4FF),
            accents = listOf(
                Color(0xFF5FBAFF), Color(0xFFD98BA0), Color(0xFF3FA085), Color(0xFFA9BCDA),
                Color(0xFFE2BE96), Color(0xFFC9A24E), Color(0xFF63B59A), Color(0xFFD28379),
            ),
        ),
    )

    /** Spotify：近黑底 + 单一荧光绿强调，胶囊按钮。 */
    val Spotify: AppTheme = theme(
        id = "spotify",
        name = "极夜绿",
        tagline = "近黑底 · 荧光绿强调",
        reference = "Spotify",
        style = ShapeStyle(button = 999.dp, card = 16.dp, pillButtons = true),
        seeds = Seeds(
            accent = Color(0xFF1ED760),
            onAccent = Color(0xFF062814),
            accentInk = Color(0xFF14833B),
            canvas = Color(0xFFFDFDFD),
            card = Color(0xFFFFFFFF),
            ink = Color(0xFF121212),
            muted = Color(0xFF6A6A6A),
            hairline = Color(0xFFE4E4E4),
            darkCanvas = Color(0xFF121212),
            darkCard = Color(0xFF181818),
            darkInk = Color(0xFFFFFFFF),
            darkAccent = Color(0xFF1ED760),
            accents = listOf(
                Color(0xFF539DF5), Color(0xFFF3727F), Color(0xFF1ED760), Color(0xFFB3B3B3),
                Color(0xFFFFA42B), Color(0xFFCBCBCB), Color(0xFF1DB954), Color(0xFFEEEEEE),
            ),
            semantic = Triple(Color(0xFF1ED760), Color(0xFFFFA42B), Color(0xFFF3727F)),
        ),
    )

    /** Stripe：靛蓝主色 + 冷白画布 + 深藏青文字。 */
    val Stripe: AppTheme = theme(
        id = "stripe",
        name = "支付靛蓝",
        tagline = "冷白画布 · 靛蓝主按钮",
        reference = "Stripe",
        style = ShapeStyle(button = 8.dp, card = 14.dp),
        seeds = Seeds(
            accent = Color(0xFF533AFD),
            onAccent = Color(0xFFFFFFFF),
            accentInk = Color(0xFF4434D4),
            canvas = Color(0xFFFFFFFF),
            card = Color(0xFFFFFFFF),
            ink = Color(0xFF0D253D),
            muted = Color(0xFF64748D),
            hairline = Color(0xFFE3E8EE),
            darkCanvas = Color(0xFF0B1020),
            darkCard = Color(0xFF141A2E),
            darkInk = Color(0xFFF6F9FC),
            darkAccent = Color(0xFF7A6BFF),
            accents = listOf(
                Color(0xFF533AFD), Color(0xFFEA2261), Color(0xFF2A9D99), Color(0xFFB9B9F9),
                Color(0xFFF5E9D4), Color(0xFF9B6829), Color(0xFF1AAE39), Color(0xFFF96BEE),
            ),
        ),
    )

    /** Linear：最深黑 + 淡紫单一强调，紧凑圆角。 */
    val Linear: AppTheme = theme(
        id = "linear",
        name = "深空紫",
        tagline = "近黑底 · 淡紫强调",
        reference = "Linear",
        style = ShapeStyle(button = 8.dp, card = 10.dp),
        seeds = Seeds(
            accent = Color(0xFF5E6AD2),
            onAccent = Color(0xFFFFFFFF),
            accentInk = Color(0xFF4B57C4),
            canvas = Color(0xFFFFFFFF),
            card = Color(0xFFF7F8FA),
            ink = Color(0xFF0B0B0F),
            muted = Color(0xFF62666D),
            hairline = Color(0xFFE6E7EA),
            darkCanvas = Color(0xFF010102),
            darkCard = Color(0xFF0F1011),
            darkInk = Color(0xFFF7F8F8),
            darkAccent = Color(0xFF828FFF),
            accents = listOf(
                Color(0xFF5E6AD2), Color(0xFFB06AB3), Color(0xFF27A644), Color(0xFF8A8F98),
                Color(0xFFD0D6E0), Color(0xFF7A7FAD), Color(0xFF3E3E44), Color(0xFF828FFF),
            ),
        ),
    )

    /** Notion：纸感白 + 墨紫，彩色便签式浅底。 */
    val Notion: AppTheme = theme(
        id = "notion",
        name = "纸感墨紫",
        tagline = "纸白卡面 · 墨紫按钮",
        reference = "Notion",
        style = ShapeStyle(button = 999.dp, card = 12.dp, pillButtons = true),
        seeds = Seeds(
            accent = Color(0xFF5645D4),
            onAccent = Color(0xFFFFFFFF),
            accentInk = Color(0xFF4534B3),
            canvas = Color(0xFFFFFFFF),
            card = Color(0xFFFFFFFF),
            ink = Color(0xFF0A1530),
            muted = Color(0xFF6B6B6B),
            hairline = Color(0xFFE9E9E7),
            darkCanvas = Color(0xFF191919),
            darkCard = Color(0xFF202020),
            darkInk = Color(0xFFFFFDFA),
            darkAccent = Color(0xFF9B8CFF),
            accents = listOf(
                Color(0xFF0075DE), Color(0xFFFF64C8), Color(0xFF2A9D99), Color(0xFFD6B6F6),
                Color(0xFFFFE8D4), Color(0xFFF5D75E), Color(0xFF1AAE39), Color(0xFFDD5B00),
            ),
        ),
    )

    /** Apple：白/浅灰双画布 + 单一 Action Blue，克制的圆角。 */
    val Apple: AppTheme = theme(
        id = "apple",
        name = "清透蓝",
        tagline = "白与浅灰 · 单一蓝",
        reference = "Apple",
        style = ShapeStyle(button = 10.dp, card = 16.dp),
        seeds = Seeds(
            accent = Color(0xFF0066CC),
            onAccent = Color(0xFFFFFFFF),
            accentInk = Color(0xFF0066CC),
            canvas = Color(0xFFFFFFFF),
            card = Color(0xFFFFFFFF),
            ink = Color(0xFF1D1D1F),
            muted = Color(0xFF7A7A7A),
            hairline = Color(0xFFE0E0E0),
            darkCanvas = Color(0xFF000000),
            darkCard = Color(0xFF1C1C1E),
            darkInk = Color(0xFFF5F5F7),
            darkAccent = Color(0xFF2997FF),
            accents = listOf(
                Color(0xFF0066CC), Color(0xFF2997FF), Color(0xFF2A9D99), Color(0xFFD2D2D7),
                Color(0xFFF5E9D4), Color(0xFF9B6829), Color(0xFF1AAE39), Color(0xFFEA2261),
            ),
        ),
    )

    /** Claude：暖砂纸画布 + 珊瑚主色，偏编辑感。 */
    val Claude: AppTheme = theme(
        id = "claude",
        name = "暖砂珊瑚",
        tagline = "暖白画布 · 珊瑚主色",
        reference = "Claude",
        style = ShapeStyle(button = 10.dp, card = 14.dp),
        seeds = Seeds(
            accent = Color(0xFFB85C3E),
            onAccent = Color(0xFFFFFFFF),
            accentInk = Color(0xFF9A4A31),
            canvas = Color(0xFFFAF9F5),
            card = Color(0xFFFFFFFF),
            ink = Color(0xFF141413),
            muted = Color(0xFF6C6A64),
            hairline = Color(0xFFE6DFD8),
            darkCanvas = Color(0xFF181715),
            darkCard = Color(0xFF252320),
            darkInk = Color(0xFFFAF9F5),
            darkAccent = Color(0xFFE08A6B),
            accents = listOf(
                Color(0xFF5DB8A6), Color(0xFFCC785C), Color(0xFF5DB872), Color(0xFFE8E0D2),
                Color(0xFFE8A55A), Color(0xFFD4A017), Color(0xFF5DB872), Color(0xFFC64545),
            ),
        ),
    )

    /** Airbnb：纯白画布 + Rausch 红，胶囊搜索/按钮。 */
    val Airbnb: AppTheme = theme(
        id = "airbnb",
        name = "珊瑚红",
        tagline = "纯白画布 · 品牌红",
        reference = "Airbnb",
        style = ShapeStyle(button = 999.dp, card = 14.dp, pillButtons = true),
        seeds = Seeds(
            accent = Color(0xFFE8264A),
            onAccent = Color(0xFFFFFFFF),
            accentInk = Color(0xFFC13515),
            canvas = Color(0xFFFFFFFF),
            card = Color(0xFFFFFFFF),
            ink = Color(0xFF222222),
            muted = Color(0xFF6A6A6A),
            hairline = Color(0xFFDDDDDD),
            darkCanvas = Color(0xFF121212),
            darkCard = Color(0xFF1C1C1C),
            darkInk = Color(0xFFFFFFFF),
            darkAccent = Color(0xFFFF5A76),
            accents = listOf(
                Color(0xFF428BFF), Color(0xFFFF385C), Color(0xFF2A9D99), Color(0xFFDDDDDD),
                Color(0xFFF2F2F2), Color(0xFF92174D), Color(0xFF1AAE39), Color(0xFFC13515),
            ),
        ),
    )

    /** 渐变 01：青柠 × 丁香，黄绿到淡紫的柔和过渡。 */
    val GradLimeLilac: AppTheme = run {
        val darkCanvas = mix(lilacGray, Color.Black, 0.90f)
        theme(
            id = "grad01",
            name = "青柠丁香",
            tagline = "青柠 × 丁香 · 柔和对比",
            reference = "用户配色",
            style = soft,
            gradient = limeZest to lilacGray,
            seeds = Seeds(
                accent = Color(0xFF5D7A33),
                onAccent = Color(0xFFFFFFFF),
                accentInk = Color(0xFF42591F),
                canvas = mix(limeZest, Color.White, 0.92f),
                card = Color(0xFFFFFFFF),
                ink = Color(0xFF1C2615),
                muted = Color(0xFF6C7364),
                hairline = Color(0xFFE4E8DD),
                darkCanvas = darkCanvas,
                darkCard = mix(darkCanvas, Color.White, 0.07f),
                darkInk = Color(0xFFF4F7EF),
                darkAccent = mix(limeZest, Color.White, 0.35f),
                accents = listOf(
                    Color(0xFF9CCB5A), Color(0xFFB99BE0), Color(0xFF4E9E86), Color(0xFFAEB7D8),
                    Color(0xFFE7B98F), Color(0xFFC6A049), Color(0xFF6FBF9E), Color(0xFFD0837A),
                ),
            ),
        )
    }

    /** 渐变 02：蜜桃 × 天青，暖粉到冷蓝的清透对比。 */
    val GradPeachSky: AppTheme = run {
        val darkCanvas = mix(skyBlue, Color.Black, 0.90f)
        theme(
            id = "grad02",
            name = "蜜桃天青",
            tagline = "蜜桃 × 天青 · 清透暖冷",
            reference = "用户配色",
            style = soft,
            gradient = blushNude to skyBlue,
            seeds = Seeds(
                accent = Color(0xFFB56A4C),
                onAccent = Color(0xFFFFFFFF),
                accentInk = Color(0xFF8E4B31),
                canvas = mix(blushNude, Color.White, 0.92f),
                card = Color(0xFFFFFFFF),
                ink = Color(0xFF2A1A14),
                muted = Color(0xFF786A64),
                hairline = Color(0xFFF0E6E1),
                darkCanvas = darkCanvas,
                darkCard = mix(darkCanvas, Color.White, 0.07f),
                darkInk = Color(0xFFFBF3EF),
                darkAccent = mix(blushNude, Color.White, 0.35f),
                accents = listOf(
                    Color(0xFFF0A07E), Color(0xFF6FC5E8), Color(0xFF4FA79A), Color(0xFFB9A6E0),
                    Color(0xFFE8C3A8), Color(0xFFC9933E), Color(0xFF77C4A4), Color(0xFFD26F63),
                ),
            ),
        )
    }

    /** 渐变 03：春绿 × 蓝灰，像清晨空气的冷调浅色。 */
    val GradSpringBlueGray: AppTheme = run {
        val darkCanvas = mix(blueGray, Color.Black, 0.90f)
        theme(
            id = "grad03",
            name = "春绿蓝灰",
            tagline = "春绿 × 蓝灰 · 清晨空气",
            reference = "用户配色",
            style = soft,
            gradient = springGreen to blueGray,
            seeds = Seeds(
                accent = Color(0xFF2E7D74),
                onAccent = Color(0xFFFFFFFF),
                accentInk = Color(0xFF1F5F58),
                canvas = mix(springGreen, Color.White, 0.92f),
                card = Color(0xFFFFFFFF),
                ink = Color(0xFF122624),
                muted = Color(0xFF64706F),
                hairline = Color(0xFFE0EBEA),
                darkCanvas = darkCanvas,
                darkCard = mix(darkCanvas, Color.White, 0.07f),
                darkInk = Color(0xFFEFF7F6),
                darkAccent = mix(springGreen, Color.White, 0.35f),
                accents = listOf(
                    Color(0xFF6FD6C4), Color(0xFF76AEE8), Color(0xFF3F9C92), Color(0xFFA9BCD8),
                    Color(0xFFE8C09A), Color(0xFFC49B4A), Color(0xFF6FC0A0), Color(0xFFD07A72),
                ),
            ),
        )
    }

    /** 渐变 04：淡蓝 × 粉泡，偏梦境感的紫粉过渡。 */
    val GradDreamPink: AppTheme = run {
        val darkCanvas = mix(blushPink, Color.Black, 0.90f)
        theme(
            id = "grad04",
            name = "梦境粉泡",
            tagline = "淡蓝 × 粉泡 · 梦境渐变",
            reference = "用户配色",
            style = soft,
            gradient = lightBlue to blushPink,
            seeds = Seeds(
                accent = Color(0xFF6366B0),
                onAccent = Color(0xFFFFFFFF),
                accentInk = Color(0xFF4A4D8C),
                canvas = mix(lightBlue, Color.White, 0.92f),
                card = Color(0xFFFFFFFF),
                ink = Color(0xFF1B1B2E),
                muted = Color(0xFF6E6E80),
                hairline = Color(0xFFE6E6F2),
                darkCanvas = darkCanvas,
                darkCard = mix(darkCanvas, Color.White, 0.07f),
                darkInk = Color(0xFFF2F2FA),
                darkAccent = mix(lightBlue, Color.White, 0.35f),
                accents = listOf(
                    Color(0xFF8C8FE0), Color(0xFFF09CB4), Color(0xFF5AA79A), Color(0xFFC4B6E8),
                    Color(0xFFEFC3B4), Color(0xFFC8A36A), Color(0xFF7CC4AE), Color(0xFFDE7F92),
                ),
            ),
        )
    }

    /** 渐变 05：水绿 × 玫瑰，轻盈通透的薄荷粉调。 */
    val GradAquaRose: AppTheme = run {
        val darkCanvas = mix(roseDust, Color.Black, 0.90f)
        theme(
            id = "grad05",
            name = "水绿玫瑰",
            tagline = "水绿 × 玫瑰 · 轻盈通透",
            reference = "用户配色",
            style = soft,
            gradient = aqua to roseDust,
            seeds = Seeds(
                accent = Color(0xFF24806F),
                onAccent = Color(0xFFFFFFFF),
                accentInk = Color(0xFF186354),
                canvas = mix(aqua, Color.White, 0.92f),
                card = Color(0xFFFFFFFF),
                ink = Color(0xFF102624),
                muted = Color(0xFF62706D),
                hairline = Color(0xFFDFEDEA),
                darkCanvas = darkCanvas,
                darkCard = mix(darkCanvas, Color.White, 0.07f),
                darkInk = Color(0xFFEFF9F7),
                darkAccent = mix(aqua, Color.White, 0.35f),
                accents = listOf(
                    Color(0xFF4FC9B8), Color(0xFFE89AA6), Color(0xFF3E9C8C), Color(0xFFE8C0C6),
                    Color(0xFFEFC0A8), Color(0xFFC7A05A), Color(0xFF6EC4A8), Color(0xFFD27A80),
                ),
            ),
        )
    }

    /** 渐变 06：奶油 × 蓝灰，低对比的温柔底色。 */
    val GradCreamBlueGray: AppTheme = run {
        val darkCanvas = mix(powderBlueGray, Color.Black, 0.90f)
        theme(
            id = "grad06",
            name = "奶油蓝灰",
            tagline = "奶油 × 蓝灰 · 温柔底色",
            reference = "用户配色",
            style = soft,
            gradient = cream to powderBlueGray,
            seeds = Seeds(
                accent = Color(0xFF8A7530),
                onAccent = Color(0xFFFFFFFF),
                accentInk = Color(0xFF67561F),
                canvas = mix(cream, Color.White, 0.92f),
                card = Color(0xFFFFFFFF),
                ink = Color(0xFF262310),
                muted = Color(0xFF75705C),
                hairline = Color(0xFFEDEAD5),
                darkCanvas = darkCanvas,
                darkCard = mix(darkCanvas, Color.White, 0.07f),
                darkInk = Color(0xFFF8F6E9),
                darkAccent = mix(cream, Color.White, 0.35f),
                accents = listOf(
                    Color(0xFFE8DE8A), Color(0xFF8FB4D8), Color(0xFF5AA79A), Color(0xFFC0C4E0),
                    Color(0xFFE8C7A0), Color(0xFFC4A04E), Color(0xFF7CC4A8), Color(0xFFD08A72),
                ),
            ),
        )
    }

    /** 渐变 07：粉黛 × 浅霞，低饱和的玫瑰甜感。 */
    val GradRoseMist: AppTheme = run {
        val darkCanvas = mix(paleRose, Color.Black, 0.90f)
        theme(
            id = "grad07",
            name = "粉黛",
            tagline = "粉黛 × 浅霞 · 低饱和甜感",
            reference = "用户配色",
            style = soft,
            gradient = dustyRose to paleRose,
            seeds = Seeds(
                accent = Color(0xFF93505D),
                onAccent = Color(0xFFFFFFFF),
                accentInk = Color(0xFF703A45),
                canvas = mix(dustyRose, Color.White, 0.92f),
                card = Color(0xFFFFFFFF),
                ink = Color(0xFF2B161C),
                muted = Color(0xFF7A686C),
                hairline = Color(0xFFEEDFE2),
                darkCanvas = darkCanvas,
                darkCard = mix(darkCanvas, Color.White, 0.07f),
                darkInk = Color(0xFFFAEFF1),
                darkAccent = mix(dustyRose, Color.White, 0.35f),
                accents = listOf(
                    Color(0xFFC98A96), Color(0xFFF0B6BE), Color(0xFF5AA79A), Color(0xFFC7AEC0),
                    Color(0xFFE8C0B0), Color(0xFFC49A62), Color(0xFF7CC4AE), Color(0xFFD07A86),
                ),
            ),
        )
    }

    /** 渐变 08：奶油 × 薰衣草，黄紫撞色。 */
    val GradCreamLavender: AppTheme = run {
        val darkCanvas = mix(lavender, Color.Black, 0.90f)
        theme(
            id = "grad08",
            name = "黄紫",
            tagline = "奶油 × 薰衣草 · 黄紫撞色",
            reference = "用户配色",
            style = soft,
            gradient = cream to lavender,
            seeds = Seeds(
                accent = Color(0xFF6C5FA8),
                onAccent = Color(0xFFFFFFFF),
                accentInk = Color(0xFF514585),
                canvas = mix(cream, Color.White, 0.92f),
                card = Color(0xFFFFFFFF),
                ink = Color(0xFF1F1C2E),
                muted = Color(0xFF6E6A7A),
                hairline = Color(0xFFEDEAD9),
                darkCanvas = darkCanvas,
                darkCard = mix(darkCanvas, Color.White, 0.07f),
                darkInk = Color(0xFFF4F1FC),
                darkAccent = mix(cream, Color.White, 0.35f),
                accents = listOf(
                    Color(0xFFE8DE8A), Color(0xFFA99FE0), Color(0xFF5AA79A), Color(0xFFB0B6E8),
                    Color(0xFFE8C7A0), Color(0xFFC4A04E), Color(0xFF7CC4A8), Color(0xFFD08A72),
                ),
            ),
        )
    }

    /** 8 套渐变主题，设置页可单独成组展示。 */
    val gradients: List<AppTheme> = listOf(
        GradLimeLilac, GradPeachSky, GradSpringBlueGray, GradDreamPink,
        GradAquaRose, GradCreamBlueGray, GradRoseMist, GradCreamLavender,
    )

    val all: List<AppTheme> = listOf(
        Douer, Spotify, Stripe, Linear, Notion, Apple, Claude, Airbnb,
    ) + gradients

    fun byId(id: String?): AppTheme =
        all.firstOrNull { it.id == id } ?: Douer
}
