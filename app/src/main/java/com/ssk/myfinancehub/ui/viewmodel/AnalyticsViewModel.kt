package com.ssk.myfinancehub.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssk.myfinancehub.data.database.FinanceDatabase
import com.ssk.myfinancehub.data.model.*
import com.ssk.myfinancehub.data.repository.AnalyticsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: AnalyticsRepository
    private val budgetRepository: com.ssk.myfinancehub.data.repository.BudgetRepository
    
    init {
        val database = FinanceDatabase.getDatabase(application)
        repository = AnalyticsRepository(database.transactionDao())
        budgetRepository = com.ssk.myfinancehub.data.repository.BudgetRepository(database.budgetDao())
    }
    
    private val _selectedPeriod = MutableStateFlow(AnalyticsPeriod.THIS_MONTH)
    val selectedPeriod: StateFlow<AnalyticsPeriod> = _selectedPeriod.asStateFlow()
    
    private val _spendingAnalytics = MutableStateFlow(SpendingAnalytics())
    val spendingAnalytics: StateFlow<SpendingAnalytics> = _spendingAnalytics.asStateFlow()
    
    private val _spendingComparison = MutableStateFlow(SpendingComparison(0.0, 0.0, 0.0, 0.0, false))
    val spendingComparison: StateFlow<SpendingComparison> = _spendingComparison.asStateFlow()
    
    private val _categoryTrends = MutableStateFlow<List<CategoryTrend>>(emptyList())
    val categoryTrends: StateFlow<List<CategoryTrend>> = _categoryTrends.asStateFlow()
    
    private val _spendingInsights = MutableStateFlow<List<SpendingInsight>>(emptyList())
    val spendingInsights: StateFlow<List<SpendingInsight>> = _spendingInsights.asStateFlow()
    
    private val _budgetAnalysis = MutableStateFlow<List<CategoryBudgetAnalysis>>(emptyList())
    val budgetAnalysis: StateFlow<List<CategoryBudgetAnalysis>> = _budgetAnalysis.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        loadAnalytics()
    }
    
    fun selectPeriod(period: AnalyticsPeriod) {
        _selectedPeriod.value = period
        loadAnalytics()
    }
    
    fun refreshAnalytics() {
        loadAnalytics()
    }
    
    private fun loadAnalytics() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val period = _selectedPeriod.value
                
                // Load all analytics data
                val analytics = repository.getSpendingAnalytics(period)
                val comparison = repository.getSpendingComparison(period)
                val trends = repository.getCategoryTrends()
                val insights = repository.getSpendingInsights(period)
                
                _spendingAnalytics.value = analytics
                _spendingComparison.value = comparison
                _categoryTrends.value = trends
                _spendingInsights.value = insights
                
                // Load budget analysis
                loadBudgetAnalysis()
                
            } catch (e: Exception) {
                // Handle error - could emit to an error state
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun loadBudgetAnalysis() {
        viewModelScope.launch {
            try {
                val calendar = java.util.Calendar.getInstance()
                val currentMonth = calendar.get(java.util.Calendar.MONTH) + 1
                val currentYear = calendar.get(java.util.Calendar.YEAR)
                
                val budgetSummaries = budgetRepository.getAllBudgetSummariesForMonth(currentMonth, currentYear)
                
                val budgetAnalysis = budgetSummaries.map { summary ->
                    val savedAmount = summary.remainingAmount.coerceAtLeast(0.0)
                    val overspentAmount = if (summary.isOverBudget) summary.spentAmount - summary.budget.budgetAmount else 0.0
                    
                    CategoryBudgetAnalysis(
                        category = summary.budget.category,
                        budgetAmount = summary.budget.budgetAmount,
                        spentAmount = summary.spentAmount,
                        savedAmount = savedAmount,
                        overspentAmount = overspentAmount,
                        savingsPercentage = if (summary.budget.budgetAmount > 0) {
                            (savedAmount / summary.budget.budgetAmount * 100).toFloat()
                        } else 0f,
                        isOverBudget = summary.isOverBudget
                    )
                }
                
                _budgetAnalysis.value = budgetAnalysis
            } catch (e: Exception) {
                e.printStackTrace()
                _budgetAnalysis.value = emptyList()
            }
        }
    }
    
    // Helper functions for UI
    fun getTopSpendingCategories(limit: Int = 5): Flow<List<CategorySpending>> {
        return spendingAnalytics.map { analytics ->
            analytics.categoryBreakdown.take(limit)
        }
    }
    
    fun getSpendingPercentages(): Flow<List<Pair<String, Float>>> {
        return spendingAnalytics.map { analytics ->
            if (analytics.totalSpent > 0) {
                analytics.categoryBreakdown.map { category ->
                    category.category to (category.totalSpent / analytics.totalSpent * 100).toFloat()
                }
            } else emptyList()
        }
    }
    
    fun getSavingsData(): Flow<Triple<Double, Double, Double>> {
        return spendingAnalytics.map { analytics ->
            Triple(analytics.totalIncome, analytics.totalSpent, analytics.totalIncome - analytics.totalSpent)
        }
    }
}
