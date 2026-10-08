package com.pulsedrop.telemetry.data

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Pending upload queue item
@Entity(tableName = "pending_uploads")
data class PendingUpload(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val payloadJson: String,
    val timestamp: Long,
    val retryCount: Int = 0
)

@Dao
interface PendingUploadDao {
    @Insert
    suspend fun insert(item: PendingUpload): Long

    @Query("SELECT * FROM pending_uploads ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 50): List<PendingUpload>

    @Delete
    suspend fun delete(item: PendingUpload)

    @Query("DELETE FROM pending_uploads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_uploads SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Query("SELECT COUNT(*) FROM pending_uploads")
    suspend fun count(): Int
}

@Database(entities = [PendingUpload::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingUploadDao(): PendingUploadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pulsedrop.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

// Helper to queue any data type
class UploadQueue(private val dao: PendingUploadDao) {
    private val gson = Gson()

    suspend fun enqueue(type: String, data: Any) {
        dao.insert(
            PendingUpload(
                type = type,
                payloadJson = gson.toJson(data),
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun getBatch(limit: Int = 50): List<PendingUpload> = dao.getPending(limit)

    suspend fun remove(item: PendingUpload) = dao.delete(item)

    suspend fun markRetry(item: PendingUpload) = dao.incrementRetry(item.id)

    suspend fun count(): Int = dao.count()
}
