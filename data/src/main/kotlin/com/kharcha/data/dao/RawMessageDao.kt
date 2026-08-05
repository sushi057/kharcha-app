package com.kharcha.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RawMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicates(message: RawMessage): Long

    @Query("UPDATE raw_messages SET ignored = 1 WHERE id = :id")
    suspend fun markIgnored(id: Long)

    @Query("SELECT COUNT(*) FROM raw_messages")
    suspend fun count(): Int

    @Query("SELECT * FROM raw_messages ORDER BY id ASC")
    suspend fun getAll(): List<RawMessage>
}
