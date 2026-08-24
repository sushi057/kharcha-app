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

    /**
     * Secondary dedup gate for the backfill/live-receiver overlap window: the same message
     * seen through `SmsMessage.timestampMillis` and through `Telephony.Sms.DATE` carries
     * two slightly different timestamps, so its content hashes differ and the unique index
     * on `contentHash` does not fire. Matching on `(sender, body)` inside a narrow time
     * window catches exactly that pair without merging two genuinely distinct transactions
     * that happen to share a body on different days — see
     * [com.kharcha.app.ingest.MessageIngestor].
     */
    @Query(
        """
        SELECT * FROM raw_messages
        WHERE sender = :sender AND body = :body
          AND receivedAtEpochMillis BETWEEN :fromEpochMillis AND :toEpochMillis
        LIMIT 1
        """
    )
    suspend fun findNearDuplicate(
        sender: String,
        body: String,
        fromEpochMillis: Long,
        toEpochMillis: Long,
    ): RawMessage?

    @Query("UPDATE raw_messages SET ignored = 1, ignoreReason = :reason WHERE id = :id")
    suspend fun markIgnored(id: Long, reason: String)

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

    /**
     * Everything the user waved away from "needs review". Dismissing is a judgement call
     * made in a second on a message the parser could not read, so it has to be reviewable:
     * without this list a mis-tap silently and permanently hides a real transaction.
     * Messages that were dismissed and later linked to a transaction drop out, same rule
     * as [observeUnparsed].
     */
    @Query(
        """
        SELECT rm.* FROM raw_messages rm
        LEFT JOIN transactions t ON t.rawMessageId = rm.id
        WHERE rm.dismissed = 1 AND t.id IS NULL
        ORDER BY rm.id DESC
        """
    )
    fun observeDismissed(): Flow<List<RawMessage>>

    /** Puts a dismissed message back into "needs review". */
    @Query("UPDATE raw_messages SET dismissed = 0 WHERE id = :id")
    suspend fun undismiss(id: Long)

    @Query("SELECT * FROM raw_messages WHERE id = :id")
    suspend fun getById(id: Long): RawMessage?

    /** The bodies of every raw message a transaction can point at, keyed by id. */
    @Query("SELECT * FROM raw_messages")
    fun observeAll(): Flow<List<RawMessage>>

    /**
     * All ignored messages: those that produced ParseResult.Ignored during parsing.
     * Ordered newest-first for display. These messages are not shown in the unparsed inbox
     * but are kept for restoration in case they were incorrectly classified.
     */
    @Query("SELECT * FROM raw_messages WHERE ignored = 1 ORDER BY id DESC")
    fun observeIgnored(): Flow<List<RawMessage>>

    /**
     * Restore an ignored message by marking it as not ignored, clearing the ignore reason.
     * This allows wrongly-ignored messages (like a password-change alert that was actually
     * a real OTP) to be re-triaged as unrecognized/needs-review.
     */
    @Query("UPDATE raw_messages SET ignored = 0, ignoreReason = NULL WHERE id = :id")
    suspend fun restore(id: Long)
}
