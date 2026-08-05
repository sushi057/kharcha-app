package com.kharcha.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RawMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicates(message: RawMessage): Long

    @Query("SELECT COUNT(*) FROM raw_messages")
    suspend fun count(): Int
}
