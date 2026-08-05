package com.kharcha.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.security.MessageDigest

@Entity(
    tableName = "raw_messages",
    indices = [Index(value = ["contentHash"], unique = true)]
)
data class RawMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAtEpochMillis: Long,
    val contentHash: String,
    val ignored: Boolean = false,
    val dismissed: Boolean = false
)

fun contentHashOf(sender: String, body: String, receivedAtEpochMillis: Long): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(sender.toByteArray(Charsets.UTF_8))
    digest.update(body.toByteArray(Charsets.UTF_8))
    digest.update(receivedAtEpochMillis.toString().toByteArray(Charsets.UTF_8))
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}
