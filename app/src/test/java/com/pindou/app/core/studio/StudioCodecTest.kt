package com.pindou.app.core.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StudioCodecTest {

    private val palette = intArrayOf(
        0xFF000000.toInt(),
        0xFFFF0000.toInt(),
        0xFF00FF00.toInt(),
    )

    @Test
    fun `分层工程往返保留帧图层属性与像素`() {
        val e = StudioCodec.blank(6, 4, palette)
        e.renameLayer(0, "线稿")
        e.setCell(0, 1)
        e.addLayer()
        e.setCell(5, 2)
        e.setLayerOpacity(1, 0.35f)
        e.setLayerBlend(1, StudioBlendMode.SCREEN)
        e.setLayerLocked(1, true)
        e.setLayerVisible(1, true)
        e.frames[0].reference = StudioReference(
            2,
            1,
            intArrayOf(0xFF112233.toInt(), 0xFF445566.toInt()),
            visible = false,
        )
        e.addFrame(true)
        e.setFrameHold(1, 240)
        e.setCell(3, 2)

        val file = File.createTempFile("studio", ".studio")
        try {
            assertTrue(StudioCodec.save(e, file))
            val loaded = StudioCodec.load(file, palette)
            assertNotNull(loaded)
            loaded!!
            assertEquals(6, loaded.width)
            assertEquals(4, loaded.height)
            assertEquals(2, loaded.frameCount)
            assertEquals(2, loaded.frames[0].layers.size)
            assertEquals("线稿", loaded.frames[0].layers[0].name)
            assertEquals(1, loaded.frames[0].layers[0].cells[0])
            assertEquals(2, loaded.frames[0].layers[1].cells[5])
            assertEquals(0.35f, loaded.frames[0].layers[1].opacity, 0.001f)
            assertEquals(StudioBlendMode.SCREEN, loaded.frames[0].layers[1].blend)
            assertTrue(loaded.frames[0].layers[1].locked)
            assertEquals(2, loaded.frames[0].reference!!.width)
            assertEquals(1, loaded.frames[0].reference!!.height)
            assertFalse(loaded.frames[0].reference!!.visible)
            assertEquals(
                0xFF445566.toInt(),
                loaded.frames[0].reference!!.argb[1],
            )
            assertEquals(240, loaded.frames[1].holdMs)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `损坏文件返回 null`() {
        val file = File.createTempFile("studio-bad", ".studio")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            assertEquals(null, StudioCodec.load(file, palette))
            assertFalse(file.length() == 0L)
        } finally {
            file.delete()
        }
    }
}
