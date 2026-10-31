package pl.brulion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Query("DELETE FROM accounts WHERE id NOT IN (SELECT accountId FROM profiles)")
    suspend fun deleteOrphans()
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY studentName")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE synergiaLogin = :login")
    suspend fun findByLogin(login: String): ProfileEntity?

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("UPDATE profiles SET lastSyncAt = :time WHERE id = :id")
    suspend fun setLastSyncAt(id: Long, time: Long)

    @Query("UPDATE profiles SET markedReadAt = :time")
    suspend fun markAllRead(time: Long)

    @Query("UPDATE profiles SET markedReadAt = :time WHERE id = :id")
    suspend fun setMarkedReadAt(id: Long, time: Long)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
}
