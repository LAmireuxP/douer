package com.pindou.app.core.studio

import com.pindou.app.core.editor.ShapeTool
import com.pindou.app.core.editor.SymmetryMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioEngineTest {

    private val palette = intArrayOf(
        0xFF000000.toInt(),
        0xFFFF0000.toInt(),
        0xFF00FF00.toInt(),
        0xFF0000FF.toInt(),
        0xFFFFFFFF.toInt(),
    )

    private fun engine(w: Int = 8, h: Int = 8): StudioEngine = StudioCodec.blank(w, h, palette)

    @Test
    fun `内容包围盒忽略四周空白`() {
        val e = engine(6, 6)
        e.setCell(1 * e.width + 1, 1)
        e.setCell(3 * e.width + 4, 2)

        assertArrayEquals(intArrayOf(1, 1, 4, 3), e.contentBounds())
    }

    @Test
    fun `全空画布没有内容包围盒`() {
        assertNull(engine(4, 4).contentBounds())
    }

    @Test
    fun `按内容包围盒裁剪后宽高与格子一致`() {
        val e = engine(6, 6)
        e.setCell(1 * e.width + 1, 1)
        e.setCell(3 * e.width + 4, 2)
        val b = e.contentBounds()!!

        assertTrue(e.crop(b[0], b[1], b[2] - b[0] + 1, b[3] - b[1] + 1))
        assertEquals(4, e.width)
        assertEquals(3, e.height)
        assertEquals(1, e.activeLayer.cells[0])
        assertEquals(2, e.activeLayer.cells[2 * 4 + 3])
    }

    @Test
    fun `笔画可撤销重做且只记录一次`() {
        val e = engine()
        val stroke = e.beginStroke()
        stroke.setCell(0, 1)
        stroke.setCell(1, 1)
        e.endStroke(stroke)
        assertEquals(1, e.activeLayer.cells[0])
        assertEquals(1, e.activeLayer.cells[1])

        assertTrue(e.undo())
        assertEquals(-1, e.activeLayer.cells[0])
        assertEquals(-1, e.activeLayer.cells[1])
        assertTrue(e.redo())
        assertEquals(1, e.activeLayer.cells[0])
    }

    @Test
    fun `取消未提交笔画会恢复原格`() {
        val e = engine()
        e.setCell(0, 2)
        val stroke = e.beginStroke()
        stroke.setCell(0, 3)
        assertEquals(3, e.activeLayer.cells[0])

        e.cancelStroke(stroke)

        assertEquals(2, e.activeLayer.cells[0])
        // 取消当前笔画不能额外压入撤销栈；一次 undo 应回到 setCell 之前。
        assertTrue(e.undo())
        assertEquals(-1, e.activeLayer.cells[0])
    }

    @Test
    fun `图层隐藏与向下合并`() {
        val e = engine()
        e.addLayer()
        e.setCell(0, 2)
        e.setActiveLayer(0)
        e.setCell(0, 1)
        assertEquals(2, e.composite()[0])
        e.setLayerVisible(1, false)
        assertEquals(1, e.composite()[0])
        e.setLayerVisible(1, true)
        e.setActiveLayer(1)
        assertTrue(e.mergeDown(1))
        assertEquals(1, e.layerCount)
        assertEquals(2, e.composite()[0])
    }

    @Test
    fun `导入原图参考不进入色卡图层也不自动转像素`() {
        val e = engine(4, 4)
        val reference = StudioReference(
            2,
            2,
            intArrayOf(
                0xFFFF0000.toInt(),
                0xFF00FF00.toInt(),
                0xFF0000FF.toInt(),
                0xFFFFFFFF.toInt(),
            ),
        )

        val drawIndex = e.importReferenceImage(reference)

        assertEquals(1, e.layerCount)
        assertEquals(0, drawIndex)
        assertEquals("绘制层", e.activeLayer.name)
        assertEquals(-1, e.activeLayer.cells[0])
        assertTrue(e.reference === reference)
        assertTrue(e.reference!!.visible)
        assertEquals(4, e.reference!!.argb.size)
        assertEquals(-1, e.composite()[0])
        assertTrue(e.undo())
        assertNull(e.reference)
    }

    @Test
    fun `已有内容导入原图参考时保留原图层并新建绘制层`() {
        val e = engine(4, 4)
        e.setCell(0, 2)
        val reference = StudioReference(1, 1, intArrayOf(0xFFFF00FF.toInt()))
        val drawIndex = e.importReferenceImage(reference)

        assertEquals(2, e.layerCount)
        assertEquals(2, e.layers[0].cells[0])
        assertEquals("绘制层", e.layers[1].name)
        assertEquals(1, drawIndex)
        assertEquals(2, e.composite()[0])
        assertTrue(e.reference === reference)
    }

    @Test
    fun `向下合并到隐藏图层后结果可见`() {
        val e = engine(4, 4)
        e.setCell(0, 1)
        e.addLayer()
        e.setCell(1, 2)
        e.setLayerVisible(0, false)
        assertTrue(e.mergeDown(1))
        assertEquals(1, e.layerCount)
        assertTrue(e.layers[0].visible)
        assertEquals(1, e.composite()[0])
        assertEquals(2, e.composite()[1])
    }

    @Test
    fun `矩形选区限制绘制并支持填充复制粘贴`() {
        val e = engine()
        assertTrue(e.selectRect(1, 1, 3, 3))
        assertTrue(e.hasSelection)
        // 选区外的格子画不进去
        e.setCell(0, 1)
        assertEquals(-1, e.activeLayer.cells[0])
        e.setCell(2 * e.width + 2, 1)
        assertEquals(1, e.activeLayer.cells[2 * e.width + 2])

        assertTrue(e.copySelection())
        assertTrue(e.pasteClipboard(0))
        assertEquals(2, e.layerCount)
        // 粘贴层左上角是原选区 (1,1) 的内容
        assertEquals(1, e.activeLayer.cells[9])
    }

    @Test
    fun `选区移动保留内容并可撤销`() {
        val e = engine()
        e.setCell(0, 1)
        e.selectRect(0, 0, 1, 1)
        assertTrue(e.moveSelection(2, 1))
        assertEquals(-1, e.activeLayer.cells[0])
        assertEquals(1, e.activeLayer.cells[e.width + 2])
        assertTrue(e.undo())
        assertEquals(1, e.activeLayer.cells[0])
    }

    @Test
    fun `魔棒选中连通同色区域`() {
        val e = engine()
        val s = e.beginStroke()
        s.setCell(0, 1)
        s.setCell(1, 1)
        s.setCell(8, 1)
        s.setCell(20, 2)
        e.endStroke(s)
        assertTrue(e.selectMagicWand(0, 0, useComposite = false))
        val selection = e.selection
        assertNotNull(selection)
        assertEquals(true, selection!![0])
        assertEquals(true, selection[1])
        assertEquals(true, selection[8])
        assertEquals(false, selection[20])
    }

    @Test
    fun `对称形状与多边形`() {
        val e = engine(9, 9)
        e.setCell(4, 4)
        e.selectAll()
        e.clearSelection()
        assertTrue(
            e.drawShape(
                ShapeTool.LINE, 0, 80, 1, false,
                SymmetryMode.BOTH,
            ),
        )
        assertEquals(1, e.activeLayer.cells[0])
        assertEquals(1, e.activeLayer.cells[8])
        assertEquals(1, e.activeLayer.cells[72])
        assertEquals(1, e.activeLayer.cells[80])

        assertTrue(e.drawPolygon(intArrayOf(1, 1, 5, 1, 5, 5, 1, 5), 2, filled = true))
        assertEquals(2, e.activeLayer.cells[2 * 9 + 2])
        assertEquals(-1, e.activeLayer.cells[7])
    }

    @Test
    fun `渐变按主副色量化到色卡`() {
        val e = engine(16, 16)
        assertTrue(e.gradient(0, 0, 15, 0, 1, 3, radial = false))
        val row = e.activeLayer.cells
        assertTrue((row[0] == 1 || row[0] == 3))
        assertTrue(row[15] == 1 || row[15] == 3)
        assertTrue(row.count { it >= 0 } > 10)
    }

    @Test
    fun `抖动填充按密度分布两种颜色`() {
        val e = engine(8, 8)
        assertTrue(e.ditherFill(1, 3, 50))
        val colors = e.composite().toSet()
        assertTrue(1 in colors)
        assertTrue(3 in colors)
    }

    @Test
    fun `变换与改画布尺寸保留内容`() {
        val e = engine(4, 3)
        e.setCell(0, 1)
        assertTrue(e.rotate90(true))
        assertEquals(3, e.width)
        assertEquals(4, e.height)
        assertEquals(1, e.activeLayer.cells[2])
        assertTrue(e.undo())
        assertEquals(4, e.width)
        assertEquals(3, e.height)

        assertTrue(e.resizeCanvas(6, 6, 0.5f, 0.5f))
        assertEquals(6, e.width)
        assertEquals(1, e.activeLayer.cells[2 * 6 + 1])
    }

    @Test
    fun `多帧独立保存并支持复制倒序`() {
        val e = engine(4, 4)
        e.setCell(0, 1)
        e.addFrame(false)
        e.setCell(0, 2)
        assertEquals(2, e.frameCount)
        assertEquals(1, e.composite(0)[0])
        assertEquals(2, e.composite(1)[0])
        e.duplicateFrame(1)
        assertEquals(3, e.frameCount)
        assertTrue(e.reverseFrames())
        assertEquals(2, e.composite(0)[0])
    }

    @Test
    fun `半透明图层合成会按色卡重新量化`() {
        val e = engine(1, 1)
        e.setCell(0, 1)
        e.addLayer()
        e.setCell(0, 3)
        e.setLayerOpacity(1, 0.5f)
        val c = e.composite()[0]
        val argb = e.renderArgb()[0]
        val r = (argb shr 16) and 0xFF
        val b = argb and 0xFF
        assertTrue(c >= 0)
        // 红色底 + 50% 蓝色：渲染结果应该是紫色，而不是纯红/纯蓝
        assertTrue("r=$r b=$b argb=${argb.toUInt().toString(16)}", r in 120..136)
        assertTrue("r=$r b=$b argb=${argb.toUInt().toString(16)}", b in 120..136)
    }

    @Test
    fun `文字掩码按原点落格`() {
        val e = engine(6, 6)
        val mask = booleanArrayOf(
            true, false,
            false, true,
        )
        assertTrue(e.stampMask(mask, 2, 2, 2, 2, 1))
        assertEquals(1, e.activeLayer.cells[2 * 6 + 2])
        assertEquals(-1, e.activeLayer.cells[2 * 6 + 3])
        assertEquals(1, e.activeLayer.cells[3 * 6 + 3])
    }

    @Test
    fun `清空和填充整层`() {
        val e = engine(3, 3)
        assertTrue(e.fillSelection(2, selectionOnly = false))
        assertArrayEquals(IntArray(9) { 2 }, e.composite())
        assertTrue(e.clearLayer())
        assertArrayEquals(IntArray(9) { -1 }, e.composite())
    }

    @Test
    fun `锁定图层拒绝绘制`() {
        val e = engine()
        e.setLayerLocked(0, true)
        assertFalse(e.setCell(0, 1))
        assertEquals(-1, e.activeLayer.cells[0])
    }

    @Test
    fun `渲染结果包含已绘制颜色`() {
        val e = engine(4, 4)
        e.setCell(0, 1)
        // 背景：顶栏先 composite、画布再 renderArgb，缓存不能互相复用
        assertEquals(1, e.composite()[0])
        val argb = e.renderArgb()
        assertEquals(0xFFFF0000.toInt(), argb[0])
        assertEquals(0, argb[1])
    }

    @Test
    fun `500x500 画布可用`() {
        val e = StudioCodec.blank(500, 500, palette)
        assertEquals(250_000, e.activeLayer.cells.size)
        assertEquals(500, e.width)
        assertEquals(500, e.height)
        assertTrue(e.setCell(499 * 500 + 499, 1))
        assertEquals(1, e.composite()[249_999])
        assertEquals(0xFFFF0000.toInt(), e.renderArgb()[249_999])
    }
}
