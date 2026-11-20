package com.pindou.app.core.pattern

/**
 * 档位 chips 上的「与当前结果差多少」标签。
 *
 * 比分只说明某一版自身好多少；用户真正要判断的是"换这版值不值"，
 * 所以要拿已应用的那一版做基准算差值。分与色数分开算，避免 1 分换 12 色
 * 这种取舍被一个数字抹平。
 */
object AutoTuneDelta {

    /**
     * @return 形如 `+2 分 · −4 色` 的差值标签；没有差异或基准分未知时返回空串。
     *   历史项目/编辑后的结果没有评分（score = 0），此时不给假差值。
     */
    fun label(baseScore: Int, baseColors: Int, candidateScore: Int, candidateColors: Int): String {
        if (baseScore <= 0 || candidateScore <= 0) return ""
        val scoreDelta = candidateScore - baseScore
        val colorDelta = candidateColors - baseColors
        if (scoreDelta == 0 && colorDelta == 0) return ""
        return buildList {
            if (scoreDelta != 0) add("${signed(scoreDelta)} 分")
            if (colorDelta != 0) add("${signed(colorDelta)} 色")
        }.joinToString(" · ")
    }

    /** 正数带 +，负数用 U+2212 减号（ASCII 连字符在数字里像破折号） */
    private fun signed(value: Int): String =
        if (value > 0) "+$value" else "\u2212${-value}"
}
