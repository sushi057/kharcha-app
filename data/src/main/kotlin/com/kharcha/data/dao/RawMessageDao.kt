package com.kharcha.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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

    /**
     * The unparsed inbox, authoritatively: raw messages that are not [RawMessage.ignored]
     * (OTPs/purchase codes never surface here), not [RawMessage.dismissed], and have no
     * linked row in `transactions` (a LEFT JOIN on `rawMessageId`, so a message that later
     * gets a transaction — via ingest categorization or "add as transaction" — drops out
     * automatically without a second Kotlin-side filter). This is the one place that
     * invariant is expressed; do not re-derive it elsewhere.
     */
    @Query(
        """
        SELECT rm.* FROM raw_messages rm
        LEFT JOIN transactions t ON t.rawMessageId = rm.id
        WHERE rm.ignored = 0 AND rm.dismissed = 0 AND t.id IS NULL
        ORDER BY rm.id DESC
        """
    )
    fun observeUnparsed(): Flow<List<RawMessage>>

    @Query("UPDATE raw_messages SET dismissed = 1 WHERE id = :id")
    suspend fun markDismissed(id: Long)
}
