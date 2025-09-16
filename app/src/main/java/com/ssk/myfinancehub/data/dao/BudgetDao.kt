package com.ssk.myfinancehub.data.dao

import androidx.room.*
import com.ssk.myfinancehub.data.model.Budget
import com.ssk.myfinancehub.data.model.CategorySpending
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE isActive = 1 ORDER BY category ASC")
    fun getAllActiveBudgets(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE month = :month AND year = :year AND isActive = 1")
    fun getBudgetsForMonth(month: Int, year: Int): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE month = :month AND year = :year AND isActive = 1")
    suspend fun getBudgetsForMonthDirect(month: Int, year: Int): List<Budget>

    @Query("SELECT * FROM budgets WHERE category = :category AND month = :month AND year = :year AND isActive = 1 LIMIT 1")
    suspend fun getBudgetForCategoryAndMonth(category: String, month: Int, year: Int): Budget?

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getBudgetById(id: Long): Budget?

    @Insert
    suspend fun insertBudget(budget: Budget): Long

    @Update
    suspend fun updateBudget(budget: Budget)

    @Delete
    suspend fun deleteBudget(budget: Budget)

    @Query("UPDATE budgets SET isActive = 0 WHERE id = :id")
    suspend fun deactivateBudget(id: Long)

    // Get category spending for current month
    @Query("""
        SELECT category, 
               SUM(amount) as totalSpent, 
               COUNT(*) as transactionCount
        FROM transactions 
        WHERE type = 'EXPENSE' 
        AND strftime('%m', date/1000, 'unixepoch') = :month 
        AND strftime('%Y', date/1000, 'unixepoch') = :year
        GROUP BY category
    """)
    suspend fun getCategorySpendingForMonth(month: String, year: String): List<CategorySpending>

    @Query("""
        SELECT SUM(amount) 
        FROM transactions 
        WHERE type = 'EXPENSE' 
        AND category = :category
        AND strftime('%m', date/1000, 'unixepoch') = :month 
        AND strftime('%Y', date/1000, 'unixepoch') = :year
    """)
    suspend fun getSpentAmountForCategory(category: String, month: String, year: String): Double?
    
    // Debug query to see all transactions for a category
    @Query("""
        SELECT * 
        FROM transactions 
        WHERE type = 'EXPENSE' 
        AND category = :category
        AND strftime('%m', date/1000, 'unixepoch') = :month 
        AND strftime('%Y', date/1000, 'unixepoch') = :year
    """)
    suspend fun getTransactionsForCategoryAndMonth(category: String, month: String, year: String): List<com.ssk.myfinancehub.data.model.Transaction>
    
    // Debug query to see all expense transactions
    @Query("SELECT * FROM transactions WHERE type = 'EXPENSE' ORDER BY date DESC")
    suspend fun getAllExpenseTransactions(): List<com.ssk.myfinancehub.data.model.Transaction>
}
