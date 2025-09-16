package com.ssk.myfinancehub.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssk.myfinancehub.data.database.FinanceDatabase
import com.ssk.myfinancehub.data.model.Budget
import com.ssk.myfinancehub.data.model.BudgetSummary
import com.ssk.myfinancehub.data.model.CategorySpending
import com.ssk.myfinancehub.data.repository.BudgetRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: BudgetRepository
    private val transactionRepository: com.ssk.myfinancehub.data.repository.TransactionRepository
    
    init {
        val database = FinanceDatabase.getDatabase(application)
        repository = BudgetRepository(database.budgetDao())
        transactionRepository = com.ssk.myfinancehub.data.repository.TransactionRepository(database.transactionDao())
    }
    
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
                val id = repository.insertBudget(budget)
                println("Budget inserted with ID: $id")
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
                repository.updateBudget(budget)
                println("Budget updated: ${budget.category}")
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
                repository.deleteBudget(budget)
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
}
