package com.ssk.myfinancehub.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val description: String,
    val date: Date = Date(),
    val catalystRowId: String? = null, // ROWID from Catalyst API
    val syncStatus: SyncStatus = SyncStatus.LOCAL,
    val lastSyncedAt: Date? = null
)

enum class TransactionType {
    INCOME, EXPENSE
}
