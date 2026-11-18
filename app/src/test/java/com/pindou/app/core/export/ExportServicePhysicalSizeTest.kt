package com.pindou.app.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportServicePhysicalSizeTest {
    @Test
    fun `实物尺寸按豆距换算`() {
        assertEquals("10.0×10.0cm", ExportService.physicalCm(20, 20, 5.0))
        assertEquals("80.0×80.0cm", ExportService.physicalCm(100, 100, 8.0))
        assertEquals("26.0×26.0cm", ExportService.physicalCm(100, 100, 2.6))
    }

    @Test
    fun `半毫米保留一位小数`() {
        assertEquals("6.5×4.7cm", ExportService.physicalCm(25, 18, 2.6))
    }

    @Test
    fun `非法输入返回占位符`() {
        assertEquals("—", ExportService.physicalCm(0, 10, 5.0))
        assertEquals("—", ExportService.physicalCm(10, 0, 5.0))
        assertEquals("—", ExportService.physicalCm(10, 10, 0.0))
    }

    @Test
    fun `cm 值与机械换算一致`() {
        val out = ExportService.physicalCm(43, 41, 5.0)
        assertTrue(out.startsWith("21.5×20.5cm"))
    }
}
