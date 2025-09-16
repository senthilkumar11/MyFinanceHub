package com.ssk.myfinancehub.data.repository

import com.ssk.myfinancehub.data.dao.TransactionDao
import com.ssk.myfinancehub.data.model.*
import java.util.*
import kotlin.math.abs

class AnalyticsRepository(private val transactionDao: TransactionDao) {
    
    suspend fun getSpendingAnalytics(period: AnalyticsPeriod): SpendingAnalytics {
        val (startDate, endDate) = getPeriodDates(period)
        
        val totalSpent = transactionDao.getTotalAmountForPeriod(
            TransactionType.EXPENSE, startDate, endDate
        ) ?: 0.0
        
        val totalIncome = transactionDao.getTotalAmountForPeriod(
            TransactionType.INCOME, startDate, endDate
        ) ?: 0.0
        
        val categoryBreakdown = transactionDao.getCategorySpendingForPeriod(startDate, endDate)
        val monthlyTrends = transactionDao.getMonthlySpendingTrends(startDate, endDate)
        val topExpenseCategories = transactionDao.getTopExpenseCategories(startDate, endDate)
        val averageDailySpending = transactionDao.getAverageDailySpending(startDate, endDate) ?: 0.0
        
        val savingsRate = if (totalIncome > 0) {
            ((totalIncome - totalSpent) / totalIncome) * 100
        } else 0.0
        
        return SpendingAnalytics(
            totalSpent = totalSpent,
            totalIncome = totalIncome,
            categoryBreakdown = categoryBreakdown,
            monthlyTrends = monthlyTrends,
            topExpenseCategories = topExpenseCategories,
            averageDailySpending = averageDailySpending,
            savingsRate = savingsRate,
            period = period
        )
    }
    
    suspend fun getSpendingComparison(period: AnalyticsPeriod): SpendingComparison {
        val (currentStart, currentEnd) = getPeriodDates(period)
        val (previousStart, previousEnd) = getPreviousPeriodDates(period)
        
        val currentSpending = transactionDao.getTotalAmountForPeriod(
            TransactionType.EXPENSE, currentStart, currentEnd
        ) ?: 0.0
        
        val previousSpending = transactionDao.getTotalAmountForPeriod(
            TransactionType.EXPENSE, previousStart, previousEnd
        ) ?: 0.0
        
        val changeAmount = currentSpending - previousSpending
        val changePercentage = if (previousSpending > 0) {
            (changeAmount / previousSpending) * 100
        } else 0.0
        
        return SpendingComparison(
            currentPeriod = currentSpending,
            previousPeriod = previousSpending,
            changeAmount = changeAmount,
            changePercentage = changePercentage,
            isIncrease = changeAmount > 0
        )
    }
    
    suspend fun getCategoryTrends(): List<CategoryTrend> {
        val currentMonth = getPeriodDates(AnalyticsPeriod.THIS_MONTH)
        val previousMonth = getPreviousPeriodDates(AnalyticsPeriod.THIS_MONTH)
        
        val currentCategories = transactionDao.getCategorySpendingForPeriod(
            currentMonth.first, currentMonth.second
        )
        
        val previousCategories = transactionDao.getCategorySpendingForPeriod(
            previousMonth.first, previousMonth.second
        ).associateBy { it.category }
        
        return currentCategories.map { current ->
            val previous = previousCategories[current.category]?.totalSpent ?: 0.0
            val change = current.totalSpent - previous
            val changePercentage = if (previous > 0) {
                (change / previous) * 100
            } else if (current.totalSpent > 0) 100.0 else 0.0
            
            CategoryTrend(
                category = current.category,
                currentMonth = current.totalSpent,
                previousMonth = previous,
                changePercentage = changePercentage,
                isIncreasing = change > 0
            )
        }
    }
    
    suspend fun getSpendingInsights(period: AnalyticsPeriod): List<SpendingInsight> {
        val insights = mutableListOf<SpendingInsight>()
        val analytics = getSpendingAnalytics(period)
        val comparison = getSpendingComparison(period)
        val categoryTrends = getCategoryTrends()
        
        // Top spending category insight
        analytics.topExpenseCategories.firstOrNull()?.let { topCategory ->
            val percentage = (topCategory.totalSpent / analytics.totalSpent) * 100
            insights.add(SpendingInsight(
                title = "Top Spending Category",
                description = "${topCategory.category} accounts for ${percentage.toInt()}% of your expenses",
                type = InsightType.TOP_CATEGORY,
                category = topCategory.category,
                amount = topCategory.totalSpent,
                percentage = percentage
            ))
        }
        
        // Spending change insight
        if (abs(comparison.changePercentage) > 10) {
            val changeType = if (comparison.isIncrease) "increased" else "decreased"
            insights.add(SpendingInsight(
                title = "Spending Change",
                description = "Your spending has $changeType by ${abs(comparison.changePercentage).toInt()}% compared to last period",
                type = if (comparison.isIncrease) InsightType.SPENDING_INCREASE else InsightType.SPENDING_DECREASE,
                amount = abs(comparison.changeAmount),
                percentage = abs(comparison.changePercentage)
            ))
        }
        
        // Savings rate insight
        if (analytics.savingsRate > 20) {
            insights.add(SpendingInsight(
                title = "Great Savings!",
                description = "You're saving ${analytics.savingsRate.toInt()}% of your income. Keep it up!",
                type = InsightType.SAVINGS_ACHIEVEMENT,
                percentage = analytics.savingsRate
            ))
        } else if (analytics.savingsRate < 5) {
            insights.add(SpendingInsight(
                title = "Improve Savings",
                description = "Consider reducing expenses to increase your savings rate",
                type = InsightType.RECOMMENDATION,
                percentage = analytics.savingsRate
            ))
        }
        
        // Category trend insights
        categoryTrends.filter { abs(it.changePercentage) > 50 }.take(2).forEach { trend ->
            val changeType = if (trend.isIncreasing) "increased" else "decreased"
            insights.add(SpendingInsight(
                title = "Category Trend",
                description = "${trend.category} spending has $changeType by ${abs(trend.changePercentage).toInt()}%",
                type = if (trend.isIncreasing) InsightType.SPENDING_INCREASE else InsightType.SPENDING_DECREASE,
                category = trend.category,
                percentage = abs(trend.changePercentage)
            ))
        }
        
        return insights
    }
    
    private fun getPeriodDates(period: AnalyticsPeriod): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis
        
        when (period) {
            AnalyticsPeriod.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            AnalyticsPeriod.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            AnalyticsPeriod.LAST_MONTH -> {
                calendar.add(Calendar.MONTH, -1)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis
                
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                return Pair(startDate, calendar.timeInMillis)
            }
            AnalyticsPeriod.LAST_3_MONTHS -> {
                calendar.add(Calendar.MONTH, -3)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            AnalyticsPeriod.THIS_YEAR -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            AnalyticsPeriod.CUSTOM -> {
                // Default to this month for custom
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
        }
        
        return Pair(calendar.timeInMillis, endDate)
    }
    
    private fun getPreviousPeriodDates(period: AnalyticsPeriod): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        
        when (period) {
            AnalyticsPeriod.THIS_WEEK -> {
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis
                
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                return Pair(startDate, calendar.timeInMillis)
            }
            AnalyticsPeriod.THIS_MONTH -> {
                calendar.add(Calendar.MONTH, -1)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis
                
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                return Pair(startDate, calendar.timeInMillis)
            }
            else -> {
                // For other periods, calculate relative to current implementation
                return getPeriodDates(period)
            }
        }
    }
}
