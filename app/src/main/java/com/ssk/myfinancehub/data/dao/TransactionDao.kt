package com.ssk.myfinancehub.data.dao

import androidx.room.*
import com.ssk.myfinancehub.data.model.Transaction
import com.ssk.myfinancehub.data.model.TransactionType
import com.ssk.myfinancehub.data.model.CategorySpending
import com.ssk.myfinancehub.data.model.MonthlySpending
import com.ssk.myfinancehub.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE catalystRowId = :catalystRowId")
    suspend fun getTransactionByCatalystRowId(catalystRowId: String): Transaction?

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date DESC")
    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME'")
    fun getTotalIncome(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE'")
    fun getTotalExpense(): Flow<Double?>

    @Insert
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    // Sync-related queries
    @Query("SELECT * FROM transactions WHERE syncStatus = :syncStatus")
    suspend fun getTransactionsBySyncStatus(syncStatus: SyncStatus): List<Transaction>

    // Analytics queries
    @Query("""
        SELECT category, 
               SUM(amount) as totalSpent, 
               COUNT(*) as transactionCount
        FROM transactions 
        WHERE type = 'EXPENSE' 
        AND date >= :startDate 
        AND date <= :endDate
        GROUP BY category
        ORDER BY totalSpent DESC
    """)
    suspend fun getCategorySpendingForPeriod(startDate: Long, endDate: Long): List<CategorySpending>

    @Query("""
        SELECT 
               strftime('%m', date/1000, 'unixepoch') as month,
               CAST(strftime('%Y', date/1000, 'unixepoch') AS INTEGER) as year,
               SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) as totalSpent,
               SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) as totalIncome,
               (SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) - SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END)) as netSavings
        FROM transactions 
        WHERE date >= :startDate 
        AND date <= :endDate
        GROUP BY strftime('%Y-%m', date/1000, 'unixepoch')
        ORDER BY year DESC, month DESC
    """)
    suspend fun getMonthlySpendingTrends(startDate: Long, endDate: Long): List<MonthlySpending>

    @Query("""
        SELECT SUM(amount) 
        FROM transactions 
        WHERE type = :type 
        AND date >= :startDate 
        AND date <= :endDate
    """)
    suspend fun getTotalAmountForPeriod(type: TransactionType, startDate: Long, endDate: Long): Double?

    @Query("""
        SELECT COUNT(*) 
        FROM transactions 
        WHERE date >= :startDate 
        AND date <= :endDate
    """)
    suspend fun getTransactionCountForPeriod(startDate: Long, endDate: Long): Int

    @Query("""
        SELECT AVG(daily_amount) FROM (
            SELECT SUM(amount) as daily_amount
            FROM transactions 
            WHERE type = 'EXPENSE'
            AND date >= :startDate 
            AND date <= :endDate
            GROUP BY DATE(date/1000, 'unixepoch')
        )
    """)
    suspend fun getAverageDailySpending(startDate: Long, endDate: Long): Double?

    @Query("""
        SELECT category, SUM(amount) as totalSpent, COUNT(*) as transactionCount
        FROM transactions 
        WHERE type = 'EXPENSE' 
        AND date >= :startDate 
        AND date <= :endDate
        GROUP BY category
        ORDER BY totalSpent DESC
        LIMIT 5
    """)
    suspend fun getTopExpenseCategories(startDate: Long, endDate: Long): List<CategorySpending>
}
