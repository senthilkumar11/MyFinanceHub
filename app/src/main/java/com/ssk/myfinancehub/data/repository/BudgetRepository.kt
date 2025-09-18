package com.ssk.myfinancehub.data.repository

import com.ssk.myfinancehub.data.dao.BudgetDao
import com.ssk.myfinancehub.data.model.Budget
import com.ssk.myfinancehub.data.model.BudgetSummary
import com.ssk.myfinancehub.data.model.CategorySpending
import com.ssk.myfinancehub.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.*

class BudgetRepository(private val budgetDao: BudgetDao) {
    
    fun getAllActiveBudgets(): Flow<List<Budget>> = budgetDao.getAllActiveBudgets()
    
    fun getBudgetsForMonth(month: Int, year: Int): Flow<List<Budget>> = 
        budgetDao.getBudgetsForMonth(month, year)
    
    suspend fun getBudgetForCategoryAndMonth(category: String, month: Int, year: Int): Budget? = 
        budgetDao.getBudgetForCategoryAndMonth(category, month, year)
    
    suspend fun getBudgetById(id: Long): Budget? = budgetDao.getBudgetById(id)
    
    suspend fun getBudgetByCatalystRowId(catalystRowId: String): Budget? = 
        budgetDao.getBudgetByCatalystRowId(catalystRowId)
    
    suspend fun insertBudget(budget: Budget): Long {
        println("Inserting budget: ${budget.category}, amount: ${budget.budgetAmount}, month: ${budget.month}, year: ${budget.year}")
        return budgetDao.insertBudget(budget)
    }
    
    suspend fun updateBudget(budget: Budget) = budgetDao.updateBudget(budget)
    
    suspend fun deleteBudget(budget: Budget) = budgetDao.deleteBudget(budget)
    
    suspend fun deactivateBudget(id: Long) = budgetDao.deactivateBudget(id)
    
    suspend fun getCategorySpendingForMonth(month: Int, year: Int): List<CategorySpending> {
        val monthStr = String.format("%02d", month)
        val yearStr = year.toString()
        return budgetDao.getCategorySpendingForMonth(monthStr, yearStr)
    }
    
    suspend fun getSpentAmountForCategory(category: String, month: Int, year: Int): Double {
        val monthStr = String.format("%02d", month)
        val yearStr = year.toString()
        println("Getting spent amount for category: '$category', month: '$monthStr', year: '$yearStr'")
        val amount = budgetDao.getSpentAmountForCategory(category, monthStr, yearStr) ?: 0.0
        println("Found spent amount: $amount for category: $category")
        return amount
    }
    
    suspend fun getBudgetSummaryForCategory(category: String, month: Int, year: Int): BudgetSummary? {
        val budget = getBudgetForCategoryAndMonth(category, month, year) ?: return null
        val spentAmount = getSpentAmountForCategory(category, month, year)
        
        // Debug: Show which budget is being used
        println("Budget for summary - Category: ${budget.category}, Amount: ${budget.budgetAmount}, ID: ${budget.id}, RowID: ${budget.catalystRowId}, LastSynced: ${budget.lastSyncedAt}")
        
        // Debug: Check transactions for this category
        val monthStr = String.format("%02d", month)
        val yearStr = year.toString()
        val transactions = budgetDao.getTransactionsForCategoryAndMonth(category, monthStr, yearStr)
        println("Debug: Found ${transactions.size} transactions for category '$category' in $monthStr/$yearStr")
        transactions.forEach { 
            println("  Transaction: ${it.amount} on ${it.date} - ${it.description}")
        }
        
        val remainingAmount = budget.budgetAmount - spentAmount
        val progressPercentage = if (budget.budgetAmount > 0) {
            (spentAmount / budget.budgetAmount).toFloat()
        } else 0f
        val isOverBudget = spentAmount > budget.budgetAmount
        
        return BudgetSummary(
            budget = budget,
            spentAmount = spentAmount,
            remainingAmount = remainingAmount,
            progressPercentage = progressPercentage,
            isOverBudget = isOverBudget
        )
    }
    
    suspend fun getAllBudgetSummariesForMonth(month: Int, year: Int): List<BudgetSummary> {
        // Get budgets directly, not as Flow
        println("Getting budgets for month: $month, year: $year")
        val budgets = getBudgetsForMonthDirect(month, year)
        println("Found ${budgets.size} budgets")
        
        // Debug: Show all expense transactions
        val allTransactions = budgetDao.getAllExpenseTransactions()
        println("Debug: Total expense transactions in database: ${allTransactions.size}")
        allTransactions.take(5).forEach { // Show first 5
            val calendar = java.util.Calendar.getInstance()
            calendar.time = it.date
            println("  Transaction: ${it.category} - ${it.amount} on ${calendar.get(java.util.Calendar.MONTH)+1}/${calendar.get(java.util.Calendar.YEAR)}")
        }
        
        val summaries = mutableListOf<BudgetSummary>()
        
        budgets.forEach { budget ->
            println("Processing budget for category: ${budget.category}")
            getBudgetSummaryForCategory(budget.category, month, year)?.let { summary ->
                println("Created summary for ${budget.category}: spent=${summary.spentAmount}, budget=${summary.budget.budgetAmount}")
                summaries.add(summary)
            }
        }
        
        println("Returning ${summaries.size} summaries")
        return summaries
    }
    
    // Helper function to get budgets directly
    private suspend fun getBudgetsForMonthDirect(month: Int, year: Int): List<Budget> {
        return budgetDao.getBudgetsForMonthDirect(month, year)
    }
    
    // Sync-related methods for enhanced sync strategies
    suspend fun getAllSyncedBudgets(): List<Budget> = 
        budgetDao.getBudgetsBySyncStatus(SyncStatus.SYNCED)
    
    suspend fun getFailedSyncBudgets(): List<Budget> = 
        budgetDao.getBudgetsBySyncStatus(SyncStatus.SYNC_FAILED)
    
    suspend fun getPendingSyncBudgets(): List<Budget> = 
        budgetDao.getBudgetsBySyncStatus(SyncStatus.SYNC_PENDING)
    
    // Method to clean up duplicate budgets for the same category/month/year
    suspend fun cleanupDuplicateBudgets() {
        try {
            // Get all active budgets directly without Flow
            val allBudgets = budgetDao.getAllActiveBudgetsDirect()
            
            // Group by category, month, year
            val groupedBudgets = mutableMapOf<String, MutableList<Budget>>()
            
            allBudgets.forEach { budget ->
                val key = "${budget.category}_${budget.month}_${budget.year}"
                groupedBudgets.getOrPut(key) { mutableListOf() }.add(budget)
            }
            
            // For each group with duplicates, keep the latest one and delete the rest
            groupedBudgets.values.forEach { budgetList ->
                if (budgetList.size > 1) {
                    // Sort by lastSyncedAt (newest first), then by id (newest first)
                    val sortedBudgets = budgetList.sortedWith(
                        compareByDescending<Budget> { it.lastSyncedAt }
                            .thenByDescending { it.id }
                    )
                    
                    // Keep the first (newest) budget, delete the rest
                    val toDelete = sortedBudgets.drop(1)
                    if (toDelete.isNotEmpty()) {
                        val idsToDelete = toDelete.map { it.id }
                        budgetDao.deleteBudgetsByIds(idsToDelete)
                        println("Cleaned up ${idsToDelete.size} duplicate budgets for ${sortedBudgets.first().category}")
                    }
                }
            }
        } catch (e: Exception) {
            println("Error cleaning up duplicate budgets: ${e.message}")
            e.printStackTrace()
        }
    }
}
