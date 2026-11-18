package pl.brulion.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Where a child is in the user's order (lowest first). Keyed by the child, not the profile: a child
 * can have a parent and a student profile, and both must sort the same way.
 */
@Entity(tableName = "child_order")
data class ChildOrderEntity(
    @PrimaryKey val childKey: String,
    val position: Int,
)

@Dao
interface ChildOrderDao {
    @Query("SELECT * FROM child_order")
    fun observeAll(): Flow<List<ChildOrderEntity>>

    @Query("DELETE FROM child_order")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ChildOrderEntity>)
}
