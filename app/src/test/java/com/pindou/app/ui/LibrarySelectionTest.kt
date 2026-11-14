package com.pindou.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySelectionTest {

    @Test
    fun `toggle 第一次加选，第二次取消`() {
        val once = LibrarySelection.toggle(emptySet(), 7L)
        assertEquals(setOf(7L), once)

        val twice = LibrarySelection.toggle(once, 7L)
        assertTrue(twice.isEmpty())
    }

    @Test
    fun `toggle 不修改传入集合`() {
        val original = setOf(1L, 2L)
        val next = LibrarySelection.toggle(original, 3L)

        assertEquals(setOf(1L, 2L), original)
        assertEquals(setOf(1L, 2L, 3L), next)
    }

    @Test
    fun `全选只加当前筛选结果，保留筛选外已选`() {
        val selected = setOf(1L, 2L)
        val next = LibrarySelection.toggleAll(selected, listOf(3L, 4L))

        assertEquals(setOf(1L, 2L, 3L, 4L), next)
    }

    @Test
    fun `再次全选只取消当前筛选结果，保留筛选外已选`() {
        val selected = setOf(1L, 2L, 3L, 4L)
        val next = LibrarySelection.toggleAll(selected, listOf(3L, 4L))

        assertEquals(setOf(1L, 2L), next)
    }

    @Test
    fun `空筛选结果不改变选择，也不算全选`() {
        val selected = setOf(1L)

        assertEquals(selected, LibrarySelection.toggleAll(selected, emptyList()))
        assertFalse(LibrarySelection.allSelected(selected, emptyList()))
    }

    @Test
    fun `筛选结果全部选中时 allSelected 为 true`() {
        assertTrue(LibrarySelection.allSelected(setOf(1L, 2L, 3L), listOf(1L, 3L)))
        assertFalse(LibrarySelection.allSelected(setOf(1L), listOf(1L, 2L)))
    }
}
