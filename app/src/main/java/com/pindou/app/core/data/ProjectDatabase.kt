package com.pindou.app.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 项目：图纸的可再编辑存档 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val width: Int,
    val height: Int,
    val beadSize: String,
    val paletteBrand: String,
    /** 每格色卡索引，int32 小端序列化（-1 = 空格） */
    val cells: ByteArray,
    /** 每格锁定标记，1 字节一格 */
    val locked: ByteArray,
    val thumbnailPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** 施工进度：每格 1 字节（1 = 已拼） */
    val doneCells: ByteArray? = null,
    /** 施工引导方向：0 = 按行，1 = 按列 */
    val buildOrientation: Int = 0,
    /** 当前施工行/列号 */
    val buildCursor: Int = 0,
    /** 页数（多页图纸；cells/locked 长度 = pages × w × h） */
    val pages: Int = 1,
    /** 收藏标记 */
    val fav: Boolean = false,
    /** 专辑名（空 = 未分组） */
    val album: String = "",
    /** 参考原图路径（编辑器对比叠加用） */
    val refPath: String? = null,
    /** 画像素分层工程 sidecar 路径（null = 普通图纸或旧项目） */
    val studioPath: String? = null,
    /** 累计施工时长（秒）；只记「在施工页动手」的时间，单次最多 2 小时 */
    val buildSeconds: Long = 0,
)

/**
 * 项目库列表用的轻量投影。
 *
 * cells / locked / doneCells 是整张图纸的 BLOB，一份 60×60 图纸就有 14KB+；
 * 项目一多，SELECT * 会在切到项目库时把几十兆数据搬进内存并全部反序列化，
 * 这就是切页卡一下的来源。列表只取卡片需要的字段，打开项目时再按 id 读整行。
 */
data class ProjectSummary(
    val id: Long,
    val name: String,
    val width: Int,
    val height: Int,
    val paletteBrand: String,
    val thumbnailPath: String?,
    val updatedAt: Long,
    val fav: Boolean,
    val album: String,
    val pages: Int,
    val buildSeconds: Long = 0,
)

/** 只取文件引用，供启动时的孤儿文件对账；不读 cells/locked BLOB */
data class ProjectFileRefs(
    val id: Long,
    val thumbnailPath: String?,
    val studioPath: String?,
    val refPath: String?,
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query(
        "SELECT id, name, width, height, paletteBrand, thumbnailPath, updatedAt, fav, album, pages, buildSeconds " +
            "FROM projects ORDER BY updatedAt DESC",
    )
    fun observeSummaries(): Flow<List<ProjectSummary>>

    @Query("SELECT id, thumbnailPath, studioPath, refPath FROM projects")
    suspend fun fileRefs(): List<ProjectFileRefs>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    // 编辑器完成：格子、锁、页数、宽高一起写。裁剪会改尺寸，旧版漏写 width/height
    // 正是“打开失败：数据尺寸不符”的来源；尺寸变化时施工进度也作废。
    @Query(
        "UPDATE projects SET width = :width, height = :height, cells = :cells, " +
            "locked = :locked, pages = :pages, thumbnailPath = :thumb, " +
            "doneCells = CASE WHEN :resetProgress = 0 AND width = :width AND height = :height " +
            "THEN doneCells ELSE NULL END, " +
            "buildCursor = CASE WHEN :resetProgress = 0 AND width = :width AND height = :height " +
            "THEN buildCursor ELSE 0 END, " +
            "updatedAt = :now WHERE id = :id",
    )
    suspend fun updateCells(
        id: Long,
        width: Int,
        height: Int,
        cells: ByteArray,
        locked: ByteArray,
        pages: Int,
        thumb: String?,
        now: Long,
        resetProgress: Boolean,
    )

    /** 修复旧项目元数据：格子 BLOB 不变，只纠正宽高。 */
    @Query("UPDATE projects SET width = :width, height = :height, updatedAt = :now WHERE id = :id")
    suspend fun updateSize(id: Long, width: Int, height: Int, now: Long)

    // 重新生成：图纸整体变化（尺寸、色卡、格数据），施工进度随图纸作废（置空、游标归零），
    // 避免 doneCells 与新 cells 尺寸错位
    @Query("UPDATE projects SET width = :width, height = :height, beadSize = :beadSize, paletteBrand = :paletteBrand, cells = :cells, locked = :locked, pages = 1, thumbnailPath = :thumb, doneCells = NULL, buildCursor = 0, buildSeconds = 0, updatedAt = :now WHERE id = :id")
    suspend fun updateContent(
        id: Long,
        width: Int,
        height: Int,
        beadSize: String,
        paletteBrand: String,
        cells: ByteArray,
        locked: ByteArray,
        thumb: String?,
        now: Long,
    )

    @Query("UPDATE projects SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE projects SET doneCells = :done, buildOrientation = :orientation, buildCursor = :cursor, updatedAt = :now WHERE id = :id")
    suspend fun updateBuildProgress(id: Long, done: ByteArray, orientation: Int, cursor: Int, now: Long)

    /** 施工时长累加；与进度同一次写库，避免两笔事务中间被杀进程丢时间 */
    @Query("UPDATE projects SET buildSeconds = buildSeconds + :seconds WHERE id = :id")
    suspend fun addBuildSeconds(id: Long, seconds: Long)

    @Query("UPDATE projects SET fav = :fav, updatedAt = :now WHERE id = :id")
    suspend fun setFav(id: Long, fav: Boolean, now: Long)

    // 批量收藏走一条 UPDATE，避免多选几十份时逐条开事务
    @Query("UPDATE projects SET fav = :fav, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setFavAll(ids: List<Long>, fav: Boolean, now: Long)

    @Query("UPDATE projects SET album = :album, updatedAt = :now WHERE id = :id")
    suspend fun setAlbum(id: Long, album: String, now: Long)

    @Query("UPDATE projects SET album = :album, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setAlbumAll(ids: List<Long>, album: String, now: Long)

    @Query("UPDATE projects SET refPath = :path, updatedAt = :now WHERE id = :id")
    suspend fun setRefPath(id: Long, path: String?, now: Long)

    @Query("UPDATE projects SET studioPath = :path, updatedAt = :now WHERE id = :id")
    suspend fun setStudioPath(id: Long, path: String?, now: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [ProjectEntity::class], version = 5, exportSchema = true)
abstract class PindouDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        /** v1 → v2：新增施工进度三列（老数据保持默认值） */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN doneCells BLOB")
                db.execSQL("ALTER TABLE projects ADD COLUMN buildOrientation INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE projects ADD COLUMN buildCursor INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2 → v3：多页、收藏、专辑、参考图四列（老数据保持默认值） */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN pages INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE projects ADD COLUMN fav INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE projects ADD COLUMN album TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE projects ADD COLUMN refPath TEXT")
            }
        }

        /** v3 → v4：画像素分层工程 sidecar 路径（旧项目为 null） */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN studioPath TEXT")
            }
        }

        /** v4 → v5：累计施工时长（老项目从 0 开始记，不追溯） */
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN buildSeconds INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}

/** IntArray / BooleanArray 与字节数组互转（仅数据层使用） */
object GridCodec {

    fun encodeCells(cells: IntArray): ByteArray {
        val buf = ByteBuffer.allocate(cells.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (c in cells) buf.putInt(c)
        return buf.array()
    }

    fun decodeCells(bytes: ByteArray): IntArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(bytes.size / 4) { buf.getInt() }
    }

    fun encodeLocks(locks: BooleanArray): ByteArray =
        ByteArray(locks.size) { if (locks[it]) 1 else 0 }

    fun decodeLocks(bytes: ByteArray): BooleanArray =
        BooleanArray(bytes.size) { bytes[it] != 0.toByte() }
}
