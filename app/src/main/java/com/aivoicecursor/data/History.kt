package com.aivoicecursor.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.aivoicecursor.core.ActionJsonParser
import com.aivoicecursor.core.ActionPlan
import com.aivoicecursor.core.ExecutionResult
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val command: String,
    val planJson: String,
    val success: Boolean,
    val message: String
)

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Query("SELECT * FROM history ORDER BY timestamp DESC, id DESC LIMIT 200")
    fun observe(): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}

/** Commands and results are stored only in this local database. */
class HistoryRepository(
    private val dao: HistoryDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    val entries: Flow<List<HistoryEntity>> = dao.observe()

    suspend fun record(command: String, plan: ActionPlan?, result: ExecutionResult) {
        dao.insert(
            HistoryEntity(
                timestamp = clock(),
                command = command,
                planJson = plan?.let { ActionJsonParser.toJson(it) } ?: "",
                success = result.success,
                message = result.message
            )
        )
    }

    suspend fun clear() = dao.clear()
}
