package com.kharcha.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = RawMessage::class,
            parentColumns = ["id"],
            childColumns = ["rawMessageId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["rawMessageId"]), Index(value = ["categoryId"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawMessageId: Long?,
    val sourceAccount: String,
    val amountMinorUnits: Long,
    val currency: Currency,
    val direction: Direction,
    val occurredAtEpochMillis: Long,
    val remark: String,
    val merchant: String?,
    val balanceAfterMinorUnits: Long?,
    val categoryId: Long?,
    val categoryIsManualOverride: Boolean,
    val excludedFromSpending: Boolean,
    val isManualEntry: Boolean
)
