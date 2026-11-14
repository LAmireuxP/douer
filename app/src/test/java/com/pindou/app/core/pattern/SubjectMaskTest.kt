package com.pindou.app.core.pattern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectMaskTest {

    @Test
    fun `越界坐标按背景处理`() {
        val mask = SubjectMask(
            width = 3,
            height = 2,
            data = byteArrayOf(0, 1, 0, 1, 0, 0),
        )

        assertTrue(mask.isSubject(1, 0))
        assertTrue(mask.isSubject(0, 1))
        assertFalse(mask.isSubject(-1, 0))
        assertFalse(mask.isSubject(3, 0))
        assertFalse(mask.isSubject(0, -1))
        assertFalse(mask.isSubject(0, 2))
    }

    @Test
    fun `数据短于声明尺寸时按背景处理`() {
        val mask = SubjectMask(2, 2, byteArrayOf(1))

        assertTrue(mask.isSubject(0, 0))
        assertFalse(mask.isSubject(1, 1))
        assertEquals(1, mask.subjectCount())
    }

    @Test
    fun `subjectCount 只统计非零格`() {
        val mask = SubjectMask(
            width = 4,
            height = 1,
            data = byteArrayOf(0, 1, 2, 0),
        )

        assertEquals(2, mask.subjectCount())
    }
}
