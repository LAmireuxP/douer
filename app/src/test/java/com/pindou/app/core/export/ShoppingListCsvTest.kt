package com.pindou.app.core.export

import com.pindou.app.core.palette.BeadPalette
import com.pindou.app.core.palette.PaletteJson
import com.pindou.app.core.pattern.PatternResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingListCsvTest {

    private val palette: BeadPalette = PaletteJson.parse(
        """
        {
          "brand": "T", "brandLabel": "测试", "beadSize": "5mm", "version": 1,
          "colors": [
            {"code": "W", "hex": "#FFFFFF", "name": "白, 常规"},
            {"code": "K", "hex": "#000000", "name": "黑"},
            {"code": "R", "hex": "#FF0000", "name": "红\"亮"}
          ]
        }
        """.trimIndent(),
    )

    private fun result(): PatternResult {
        val w = palette.indexOfCode("W")
        val k = palette.indexOfCode("K")
        val r = palette.indexOfCode("R")
        // 白 4、红 2、黑 1，按用量降序输出
        return PatternResult(7, 1, intArrayOf(w, w, w, w, r, r, k), palette, 0)
    }

    @Test
    fun `表头与颜色数正确`() {
        val lines = ShoppingList.renderCsv(listOf(result()), "猫").trim().lines()
        assertEquals("色号,名称,颗数,建议购买", lines[0])
        assertEquals(4, lines.size)
        assertTrue(lines[1].startsWith("W,"))
        assertTrue(lines[2].startsWith("R,"))
        assertTrue(lines[3].startsWith("K,"))
    }

    @Test
    fun `含逗号和引号的名字按规范转义`() {
        val lines = ShoppingList.renderCsv(listOf(result()), "猫").trim().lines()
        // 名称 "白, 常规" 里的逗号必须被整体加引号
        assertTrue(lines[1].contains("\"白, 常规\""))
        // 名称 红"亮 里的引号要翻倍
        assertTrue(lines[2].contains("\"红\"\"亮\""))
    }

    @Test
    fun `颗数列与合计一致`() {
        val lines = ShoppingList.renderCsv(listOf(result()), "猫").trim().lines()
        // 名称里可能有逗号，不能按 split(',') 取列；直接校验行尾三段
        assertTrue(lines[1].endsWith(",4,500粒装×1"))
        assertTrue(lines[2].endsWith(",2,500粒装×1"))
        assertTrue(lines[3].endsWith(",1,500粒装×1"))
    }
}
