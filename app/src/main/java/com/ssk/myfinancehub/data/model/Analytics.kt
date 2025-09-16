package com.ssk.myfinancehub.data.model

import java.util.Date

data class SpendingAnalytics(
    val totalSpent: Double = 0.0,
    val totalIncome: Double = 0.0,
    val categoryBreakdown: List<CategorySpending> = emptyList(),
    val monthlyTrends: List<MonthlySpending> = emptyList(),
    val topExpenseCategories: List<CategorySpending> = emptyList(),
    val averageDailySpending: Double = 0.0,
    val savingsRate: Double = 0.0,
    val period: AnalyticsPeriod = AnalyticsPeriod.THIS_MONTH
)

data class MonthlySpending(
    val month: String,
    val year: Int,
    val totalSpent: Double,
    val totalIncome: Double,
    val netSavings: Double
)

data class DailySpending(
    val date: Date,
    val totalSpent: Double,
    val totalIncome: Double,
    val transactionCount: Int
)

data class CategoryTrend(
    val category: String,
    val currentMonth: Double,
    val previousMonth: Double,
    val changePercentage: Double,
    val isIncreasing: Boolean
)

data class SpendingInsight(
    val title: String,
    val description: String,
    val type: InsightType,
    val category: String? = null,
    val amount: Double? = null,
    val percentage: Double? = null
)

enum class InsightType {
    SPENDING_INCREASE,
    SPENDING_DECREASE,
    TOP_CATEGORY,
    BUDGET_WARNING,
    SAVINGS_ACHIEVEMENT,
    UNUSUAL_SPENDING,
    RECOMMENDATION
}

enum class AnalyticsPeriod {
    THIS_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    LAST_3_MONTHS,
    THIS_YEAR,
    CUSTOM
}

data class SpendingComparison(
    val currentPeriod: Double,
    val previousPeriod: Double,
    val changeAmount: Double,
    val changePercentage: Double,
    val isIncrease: Boolean
)

data class CategoryBudgetAnalysis(
    val category: String,
    val budgetAmount: Double,
    val spentAmount: Double,
    val savedAmount: Double,
    val overspentAmount: Double,
    val savingsPercentage: Float,
    val isOverBudget: Boolean
)
