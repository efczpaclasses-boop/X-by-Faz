package com.faz.xbyfaz.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.faz.xbyfaz.core.model.DiagnosticEntry
import com.faz.xbyfaz.core.model.WatchEntry
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val videoId: String,
    val postId: String,
    val accountHandle: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val completed: Boolean,
    val source: String,
    val title: String,
    val authorName: String,
    val authorHandle: String,
    val posterUrl: String?,
    val mediaUrl: String?,
    val permalink: String,
)

@Entity(tableName = "diagnostics")
data class DiagnosticEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val message: String,
    val detail: String,
    val screen: String,
    val timestamp: Long,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY updatedAt DESC")
    fun observe(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE videoId = :videoId LIMIT 1")
    suspend fun get(videoId: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clear()
}

@Dao
interface DiagnosticsDao {
    @Query("SELECT * FROM diagnostics ORDER BY timestamp DESC LIMIT 200")
    fun observe(): Flow<List<DiagnosticEventEntity>>

    @Insert
    suspend fun insert(entity: DiagnosticEventEntity)

    @Query("DELETE FROM diagnostics WHERE id NOT IN (SELECT id FROM diagnostics ORDER BY timestamp DESC LIMIT 200)")
    suspend fun trim()

    @Query("DELETE FROM diagnostics")
    suspend fun clear()
}

@Database(
    entities = [WatchHistoryEntity::class, DiagnosticEventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class XDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
    abstract fun diagnostics(): DiagnosticsDao
}

fun WatchHistoryEntity.toEntry() = WatchEntry(
    videoId, postId, accountHandle, positionMs, durationMs, updatedAt, completed, source,
    title, authorName, authorHandle, posterUrl, mediaUrl, permalink,
)

fun WatchEntry.toEntity() = WatchHistoryEntity(
    videoId, postId, accountHandle, positionMs, durationMs, updatedAt, completed, source,
    title, authorName, authorHandle, posterUrl, mediaUrl, permalink,
)

fun DiagnosticEventEntity.toEntry() = DiagnosticEntry(id, code, message, detail, screen, timestamp)
