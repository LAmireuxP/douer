package com.pindou.app.core.build

/**
 * 施工页常亮策略：常亮 / 关 / 限时 10·30·60 分钟。
 *
 * 限时只作用于「这一次进施工页」，不是全局倒计时——每次进来重新计时。
 * 这里只管判断，不碰 View/Window，JVM 单测能锁住边界与非法值。
 */
object KeepScreenPolicy {

    const val ON = "on"
    const val OFF = "off"
    const val LIMIT_10 = "10"
    const val LIMIT_30 = "30"
    const val LIMIT_60 = "60"

    /** 老版本布尔迁移与非法值都落到这一档：既不永亮，也不会一进页面就黑 */
    const val FALLBACK = LIMIT_30

    private val limitMinutes = mapOf(LIMIT_10 to 10, LIMIT_30 to 30, LIMIT_60 to 60)

    fun normalize(mode: String): String = when {
        mode == ON || mode == OFF -> mode
        limitMinutes.containsKey(mode) -> mode
        else -> FALLBACK
    }

    /** null = 不限时（on/off），非空 = 限时分钟数 */
    fun limitFor(mode: String): Int? = limitMinutes[normalize(mode)]

    fun shouldKeep(mode: String, elapsedMs: Long): Boolean {
        val normalized = normalize(mode)
        if (normalized == OFF) return false
        val limit = limitMinutes[normalized] ?: return true
        return elapsedMs < limit * 60_000L
    }

    fun label(mode: String): String = when (normalize(mode)) {
        ON -> "常亮"
        OFF -> "关"
        LIMIT_10 -> "10 分钟"
        LIMIT_60 -> "60 分钟"
        else -> "30 分钟"
    }

    /** 设置页分段顺序（含「关」，保证用户随时能彻底关掉） */
    val OPTIONS = listOf(ON, LIMIT_10, LIMIT_30, LIMIT_60, OFF)
}
