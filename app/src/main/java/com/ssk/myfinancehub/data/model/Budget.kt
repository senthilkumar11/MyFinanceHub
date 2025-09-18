package com.ssk.myfinancehub.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val category: String,
    val budgetAmount: Double,
    val month: Int, // 1-12
    val year: Int,
    val createdDate: Date = Date(),
    val isActive: Boolean = true,
    val catalystRowId: String? = null, // ROWID from Catalyst API
    val syncStatus: SyncStatus = SyncStatus.LOCAL,
    val lastSyncedAt: Date? = null
)

data class BudgetSummary(
    val budget: Budget,
    val spentAmount: Double,
    val remainingAmount: Double,
    val progressPercentage: Float,
    val isOverBudget: Boolean
) {
    val budgetStatus: BudgetStatus
        get() = when {
            isOverBudget -> BudgetStatus.OVER_BUDGET
            progressPercentage >= 0.9f -> BudgetStatus.WARNING
            progressPercentage >= 0.7f -> BudgetStatus.GOOD
            else -> BudgetStatus.SAFE
        }
}

enum class BudgetStatus {
    SAFE, GOOD, WARNING, OVER_BUDGET
}

data class CategorySpending(
    val category: String,
    val totalSpent: Double,
    val transactionCount: Int
)
