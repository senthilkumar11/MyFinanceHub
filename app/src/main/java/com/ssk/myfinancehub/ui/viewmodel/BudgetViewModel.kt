package com.ssk.myfinancehub.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssk.myfinancehub.data.database.FinanceDatabase
import com.ssk.myfinancehub.data.model.Budget
import com.ssk.myfinancehub.data.model.BudgetSummary
import com.ssk.myfinancehub.data.model.CategorySpending
import com.ssk.myfinancehub.data.repository.BudgetRepository
import com.ssk.myfinancehub.repository.SyncRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: BudgetRepository
    private val transactionRepository: com.ssk.myfinancehub.data.repository.TransactionRepository
    private val syncRepository: SyncRepository
    
    init {
        val database = FinanceDatabase.getDatabase(application)
        repository = BudgetRepository(database.budgetDao())
        transactionRepository = com.ssk.myfinancehub.data.repository.TransactionRepository(database.transactionDao())
        syncRepository = SyncRepository.getInstance(transactionRepository, repository)
    }
    
    // Sync repository states
    val syncStatus: StateFlow<String> = syncRepository.syncStatus
    val isLoading: StateFlow<Boolean> = syncRepository.isLoading
    val errorMessage: StateFlow<String?> = syncRepository.errorMessage
    val isConnected: StateFlow<Boolean> = syncRepository.isConnected
    val connectionError: StateFlow<String?> = syncRepository.connectionError
    val lastSyncTime: StateFlow<String?> = syncRepository.lastSyncTime
    val isEnabled: StateFlow<Boolean> = syncRepository.isEnabled
    
    private val _currentMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    private val _currentYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    
    val currentMonth: StateFlow<Int> = _currentMonth.asStateFlow()
    val currentYear: StateFlow<Int> = _currentYear.asStateFlow()
    
    val allActiveBudgets: Flow<List<Budget>> = repository.getAllActiveBudgets()
    
    val currentMonthBudgets: Flow<List<Budget>> = combine(
        currentMonth,
        currentYear
    ) { month, year ->
        repository.getBudgetsForMonth(month, year)
    }.flatMapLatest { it }
    
    private val _budgetSummaries = MutableStateFlow<List<BudgetSummary>>(emptyList())
    val budgetSummaries: StateFlow<List<BudgetSummary>> = _budgetSummaries.asStateFlow()
    
    private val _categorySpending = MutableStateFlow<List<CategorySpending>>(emptyList())
    val categorySpending: StateFlow<List<CategorySpending>> = _categorySpending.asStateFlow()
    
    init {
        // Load budget summaries when month/year changes
        viewModelScope.launch {
            combine(currentMonth, currentYear) { month, year ->
                Pair(month, year)
            }.collect { (month, year) ->
                loadBudgetSummaries(month, year)
                loadCategorySpending(month, year)
            }
        }
        
        // Also refresh when transactions change
        viewModelScope.launch {
            transactionRepository.getAllTransactions().collect {
                // Refresh budget summaries when transactions change
                loadBudgetSummaries(_currentMonth.value, _currentYear.value)
            }
        }
    }
    
    fun setCurrentMonth(month: Int, year: Int) {
        _currentMonth.value = month
        _currentYear.value = year
    }
    
    private fun loadBudgetSummaries(month: Int, year: Int) {
        viewModelScope.launch {
            try {
                println("Loading budget summaries for month: $month, year: $year")
                val summaries = repository.getAllBudgetSummariesForMonth(month, year)
                println("Found ${summaries.size} budget summaries")
                _budgetSummaries.value = summaries
            } catch (e: Exception) {
                println("Error loading budget summaries: ${e.message}")
                e.printStackTrace()
                _budgetSummaries.value = emptyList()
            }
        }
    }
    
    private fun loadCategorySpending(month: Int, year: Int) {
        viewModelScope.launch {
            try {
                val spending = repository.getCategorySpendingForMonth(month, year)
                _categorySpending.value = spending
            } catch (e: Exception) {
                _categorySpending.value = emptyList()
            }
        }
    }
    
    fun insertBudget(budget: Budget) {
        viewModelScope.launch {
            try {
                // First save locally
                val localId = repository.insertBudget(budget)
                println("Budget inserted locally with ID: $localId")
                
                // Then sync to Catalyst if enabled
                if (syncRepository.isEnabled.value) {
                    val result = syncRepository.createBudgetViaCatalyst(budget)
                    result.fold(
                        onSuccess = { syncedBudget ->
                            // Update local record with ROWID and sync status
                            repository.updateBudget(syncedBudget.copy(id = localId))
                            Log.d("BudgetViewModel", "Budget synced to Catalyst with ROWID: ${syncedBudget.catalystRowId}")
                        },
                        onFailure = { error ->
                            Log.e("BudgetViewModel", "Failed to sync budget to Catalyst: ${error.message}")
                            // Mark budget as sync failed
                            repository.updateBudget(budget.copy(
                                id = localId,
                                syncStatus = com.ssk.myfinancehub.data.model.SyncStatus.SYNC_FAILED
                            ))
                        }
                    )
                }
                
                // Refresh summaries
                loadBudgetSummaries(_currentMonth.value, _currentYear.value)
            } catch (e: Exception) {
                println("Error inserting budget: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    fun updateBudget(budget: Budget) {
        viewModelScope.launch {
            try {
                // Update locally
                repository.updateBudget(budget)
                println("Budget updated locally: ${budget.category}")
                
                // Sync to Catalyst if enabled and has ROWID
                if (syncRepository.isEnabled.value && budget.catalystRowId != null) {
                    val result = syncRepository.updateBudgetViaCatalyst(budget)
                    result.fold(
                        onSuccess = { syncedBudget ->
                            // Update sync status
                            repository.updateBudget(syncedBudget)
                            Log.d("BudgetViewModel", "Budget updated in Catalyst")
                        },
                        onFailure = { error ->
                            Log.e("BudgetViewModel", "Failed to update in Catalyst: ${error.message}")
                            // Mark as sync pending
                            repository.updateBudget(budget.copy(
                                syncStatus = com.ssk.myfinancehub.data.model.SyncStatus.SYNC_FAILED
                            ))
                        }
                    )
                }
                
                // Refresh summaries
                loadBudgetSummaries(_currentMonth.value, _currentYear.value)
            } catch (e: Exception) {
                println("Error updating budget: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            try {
                // Delete from Catalyst first if it has ROWID
                if (syncRepository.isEnabled.value && budget.catalystRowId != null) {
                    val result = syncRepository.deleteBudgetViaCatalyst(budget)
                    result.fold(
                        onSuccess = {
                            // Delete locally after successful Catalyst deletion
                            repository.deleteBudget(budget)
                            Log.d("BudgetViewModel", "Budget deleted from both Catalyst and local")
                        },
                        onFailure = { error ->
                            Log.e("BudgetViewModel", "Failed to delete from Catalyst: ${error.message}")
                            // Still delete locally but mark as sync issue
                            repository.deleteBudget(budget)
                        }
                    )
                } else {
                    // Just delete locally if no ROWID or sync disabled
                    repository.deleteBudget(budget)
                    Log.d("BudgetViewModel", "Budget deleted locally")
                }
                
                println("Budget deleted: ${budget.category}")
                // Refresh summaries
                loadBudgetSummaries(_currentMonth.value, _currentYear.value)
            } catch (e: Exception) {
                println("Error deleting budget: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    suspend fun getBudgetById(id: Long): Budget? {
        return repository.getBudgetById(id)
    }
    
    suspend fun getBudgetForCategory(category: String, month: Int, year: Int): Budget? {
        return repository.getBudgetForCategoryAndMonth(category, month, year)
    }
    
    fun getOverBudgetCategories(): Flow<List<BudgetSummary>> {
        return budgetSummaries.map { summaries ->
            summaries.filter { it.isOverBudget }
        }
    }
    
    fun getTotalBudgetAmount(): Flow<Double> {
        return budgetSummaries.map { summaries ->
            summaries.sumOf { it.budget.budgetAmount }
        }
    }
    
    fun getTotalSpentAmount(): Flow<Double> {
        return budgetSummaries.map { summaries ->
            summaries.sumOf { it.spentAmount }
        }
    }
    
    fun getBudgetUtilization(): Flow<Float> {
        return combine(getTotalBudgetAmount(), getTotalSpentAmount()) { total, spent ->
            if (total > 0) (spent / total).toFloat() else 0f
        }
    }
    
    // Helper function for testing
    fun refreshCurrentMonth() {
        loadBudgetSummaries(_currentMonth.value, _currentYear.value)
    }
    
    // Sync-specific methods for handling cloud data
    suspend fun insertBudgetFromSync(budget: Budget) {
        // Insert/update budget from cloud sync without triggering another sync
        try {
            // Check if budget already exists by catalystRowId to avoid duplicates
            val existingBudget = budget.catalystRowId?.let { 
                repository.getBudgetByCatalystRowId(it) 
            }
            
            if (existingBudget == null) {
                // Insert new budget from cloud
                repository.insertBudget(budget)
                println("Inserted new budget from sync: ${budget.category} (ROWID: ${budget.catalystRowId})")
            } else {
                // Update existing budget if cloud version is newer
                if (budget.lastSyncedAt != null && 
                    (existingBudget.lastSyncedAt == null || 
                     budget.lastSyncedAt!! > existingBudget.lastSyncedAt!!)) {
                    val updatedBudget = budget.copy(id = existingBudget.id)
                    repository.updateBudget(updatedBudget)
                    println("Updated existing budget from sync: ${budget.category} (ROWID: ${budget.catalystRowId})")
                } else {
                    println("Skipped sync for budget ${budget.category} - local version is newer or same")
                }
            }
            // Refresh summaries
            loadBudgetSummaries(_currentMonth.value, _currentYear.value)
        } catch (e: Exception) {
            println("Failed to insert/update budget from sync: ${e.message}")
            e.printStackTrace()
        }
    }
}
