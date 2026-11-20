package com.pindou.app.core.build

/**
 * 施工时长累计：把「这次进施工页到现在」折算成要写库的秒数。
 *
 * 单次最多记 2 小时：挂机忘退不该算成拼豆时长。不足 1 秒的间隔不结算，
 * 起点也不动——连续点格时几十毫秒的碎片会攒到下一次一起记，不会被截断吃掉。
 */
object BuildTimer {

    const val MAX_DELTA_SECONDS = 2L * 60 * 60

    fun clampSeconds(rawSeconds: Long): Long = rawSeconds.coerceIn(0L, MAX_DELTA_SECONDS)

    /**
     * @return 本次要结算的秒数；不足 1 秒返回 0（调用方据此决定是否重置起点）
     */
    fun settleSeconds(elapsedMs: Long): Long = clampSeconds(elapsedMs / 1000)

    /** 展示用：<1 分钟给秒，<1 小时给分钟，再往上给「X 小时 Y 分」 */
    fun format(totalSeconds: Long): String {
        if (totalSeconds <= 0L) return "—"
        return when {
            totalSeconds < 60 -> "$totalSeconds 秒"
            totalSeconds < 3600 -> "${totalSeconds / 60} 分钟"
            else -> "${totalSeconds / 3600} 小时 ${totalSeconds % 3600 / 60} 分"
        }
    }
}
