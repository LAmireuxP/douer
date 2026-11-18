package com.pindou.app.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PindouDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    // 历史库结构不再手写：MigrationTestHelper 直接读 app/schemas 里的 1/2/3.json
    // 建库。手写 CREATE TABLE 与 JSON 建库叠加会报 duplicate column，
    // 且两套结构容易和真实历史版本漂移。

    /** 插入一条完整 v1 行，迁移后校验用。 */
    private fun insertV1Row(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO projects (
                id, name, width, height, beadSize, paletteBrand, cells, locked,
                thumbnailPath, createdAt, updatedAt
            ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                "v1 猫",
                2,
                3,
                "5mm",
                "MARD",
                ByteArray(2 * 3 * 4) { 0 },
                ByteArray(2 * 3) { 1 },
                "thumbs/cat.png",
                1_000L,
                2_000L,
            ),
        )
    }

    /** 插入一条完整 v3 行：v2/v3 新增的 NOT NULL 列必须显式给值。 */
    private fun insertV3Row(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO projects (
                id, name, width, height, beadSize, paletteBrand, cells, locked,
                thumbnailPath, createdAt, updatedAt,
                doneCells, buildOrientation, buildCursor, pages, fav, album, refPath
            ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                "v3 猫",
                2,
                3,
                "5mm",
                "MARD",
                ByteArray(2 * 3 * 4) { 0 },
                ByteArray(2 * 3) { 1 },
                "thumbs/cat.png",
                1_000L,
                2_000L,
                ByteArray(2 * 3) { 1 },
                2,
                1,
                2,
                1,
                "专辑A",
                "refs/cat.jpg",
            ),
        )
    }

    @Test
    fun migrate1To4_preservesProjectRowAndAddsColumns() {
        val dbName = "migration-test-1-to-4.db"
        helper.createDatabase(dbName, 1).also { db ->
            insertV1Row(db)
            db.close()
        }

        helper.runMigrationsAndValidate(
            dbName,
            4,
            true,
            PindouDatabase.MIGRATION_1_2,
            PindouDatabase.MIGRATION_2_3,
            PindouDatabase.MIGRATION_3_4,
        ).use { activeDb ->
            activeDb.query(
                "SELECT name,width,height,beadSize,paletteBrand,cells,locked,thumbnailPath,createdAt,updatedAt," +
                    "doneCells,buildOrientation,buildCursor,pages,fav,album,refPath,studioPath FROM projects WHERE id=1",
            ).use { c ->
                assertTrue(c.moveToFirst())
                val cells = c.getBlob(5)
                val locked = c.getBlob(6)
                assertEquals("v1 猫", c.getString(0))
                assertEquals(2, c.getInt(1))
                assertEquals(3, c.getInt(2))
                assertEquals(2 * 3 * 4, cells.size)
                assertEquals(2 * 3, locked.size)
                // doneCells 可空：v1 老行迁移后为 NULL
                assertTrue(c.isNull(10))
                // 新增列默认值
                assertEquals(0, c.getInt(11))
                assertEquals(0, c.getInt(12))
                assertEquals(1, c.getInt(13))
                assertEquals(0, c.getInt(14))
                assertEquals("", c.getString(15))
                assertTrue(c.isNull(16))
                assertTrue(c.isNull(17))
            }
        }
    }

    @Test
    fun migrate2To3_defaultsAreValidForOldRows() {
        val dbName = "migration-test-2-to-3.db"
        helper.createDatabase(dbName, 2).close()

        helper.runMigrationsAndValidate(
            dbName,
            3,
            true,
            PindouDatabase.MIGRATION_2_3,
        ).use { activeDb ->
            activeDb.query("SELECT id FROM projects LIMIT 1").use { c ->
                // 空表也应能通过迁移与 schema 校验
                assertTrue(!c.moveToFirst())
            }
        }
    }

    @Test
    fun migrate3To4_addsStudioPathColumn() {
        val dbName = "migration-test-3-to-4.db"
        helper.createDatabase(dbName, 3).also { db ->
            insertV3Row(db)
            db.close()
        }

        helper.runMigrationsAndValidate(
            dbName,
            4,
            true,
            PindouDatabase.MIGRATION_3_4,
        ).use { activeDb ->
            activeDb.query(
                "SELECT studioPath,name,pages,fav,album,refPath,buildOrientation,buildCursor FROM projects WHERE id=1",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertTrue(c.isNull(0))
                // 迁移只加列，不动旧字段
                assertEquals("v3 猫", c.getString(1))
                assertEquals(2, c.getInt(2))
                assertEquals(1, c.getInt(3))
                assertEquals("专辑A", c.getString(4))
                assertEquals("refs/cat.jpg", c.getString(5))
                assertEquals(2, c.getInt(6))
                assertEquals(1, c.getInt(7))
            }
        }
    }
}
