package com.pindou.app.core.studio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioReferenceTest {

    @Test
    fun `原图参考复制后互不影响`() {
        val original = StudioReference(
            width = 2,
            height = 1,
            argb = intArrayOf(0xFF112233.toInt(), 0xFF445566.toInt()),
        )
        val copy = original.copy()
        copy.argb[0] = 0
        copy.visible = false

        assertArrayEquals(
            intArrayOf(0xFF112233.toInt(), 0xFF445566.toInt()),
            original.argb,
        )
        assertTrue(original.visible)
        assertFalse(copy.visible)
    }
}
