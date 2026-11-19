package com.pindou.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OrphanFilePlanTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val now = 10_000_000_000L
    private val old = 60 * 60 * 1000L
    private val fresh = 60 * 1000L

    private fun make(dir: File, name: String, ageMs: Long): File =
        File(dir, name).apply {
            writeText("x")
            setLastModified(now - ageMs)
        }

    @Test
    fun `缩略图只删未被引用且足够旧的`() {
        val dir = tmp.newFolder("thumbs")
        val referenced = make(dir, "thumb_ref.png", old)
        val orphanOld = make(dir, "thumb_orphan_old.png", old)
        val orphanNew = make(dir, "thumb_orphan_new.png", fresh)

        val doomed = OrphanFilePlan.orphans(
            thumbs = listOf(referenced, orphanOld, orphanNew),
            studioFiles = emptyList(),
            refFiles = emptyList(),
            referencedPaths = setOf(referenced.absolutePath),
            projectIds = emptySet(),
            now = now,
        )

        assertEquals(listOf(orphanOld), doomed)
    }

    @Test
    fun `studio 文件按文件名里的 id 对账`() {
        val dir = tmp.newFolder("studio")
        val existing = make(dir, "project_1.studio", old)
        val missing = make(dir, "project_2.studio", old)
        val malformed = make(dir, "project_x.studio", old)

        val doomed = OrphanFilePlan.orphans(
            thumbs = emptyList(),
            studioFiles = listOf(existing, missing, malformed),
            refFiles = emptyList(),
            referencedPaths = emptySet(),
            projectIds = setOf(1L),
            now = now,
        )

        assertEquals(listOf(missing, malformed), doomed)
    }

    @Test
    fun `参考图只删项目已不存在的`() {
        val dir = tmp.newFolder("refs")
        val existing = make(dir, "ref_1.jpg", old)
        val missing = make(dir, "ref_9.jpg", old)
        val malformed = make(dir, "ref_x.jpg", old)

        val doomed = OrphanFilePlan.orphans(
            thumbs = emptyList(),
            studioFiles = emptyList(),
            refFiles = listOf(existing, missing, malformed),
            referencedPaths = emptySet(),
            projectIds = setOf(1L),
            now = now,
        )

        assertEquals(listOf(missing, malformed), doomed)
    }

    @Test
    fun `新文件不动避免和当前写入打架`() {
        val dir = tmp.newFolder("refs")
        val freshMissing = make(dir, "ref_9.jpg", fresh)

        val doomed = OrphanFilePlan.orphans(
            thumbs = emptyList(),
            studioFiles = emptyList(),
            refFiles = listOf(freshMissing),
            referencedPaths = emptySet(),
            projectIds = emptySet(),
            now = now,
        )

        assertEquals(emptyList<File>(), doomed)
    }

    @Test
    fun `文件名解析拒绝畸形与空 id`() {
        assertEquals(1L, OrphanFilePlan.idFrom("project_1.studio", "project_", ".studio"))
        assertEquals(9L, OrphanFilePlan.idFrom("ref_9.jpg", "ref_", ".jpg"))
        assertNull(OrphanFilePlan.idFrom("project_.studio", "project_", ".studio"))
        assertNull(OrphanFilePlan.idFrom("project_x.studio", "project_", ".studio"))
        assertNull(OrphanFilePlan.idFrom("ref_9.png", "ref_", ".jpg"))
    }
}
