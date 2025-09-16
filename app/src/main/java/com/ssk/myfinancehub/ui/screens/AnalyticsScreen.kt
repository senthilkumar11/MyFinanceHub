package com.ssk.myfinancehub.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ssk.myfinancehub.data.model.*
import com.ssk.myfinancehub.ui.viewmodel.AnalyticsViewModel
import java.text.NumberFormat
import java.util.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val analytics by viewModel.spendingAnalytics.collectAsStateWithLifecycle()
    val comparison by viewModel.spendingComparison.collectAsStateWithLifecycle()
    val insights by viewModel.spendingInsights.collectAsStateWithLifecycle()
    val budgetAnalysis by viewModel.budgetAnalysis.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Analytics",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshAnalytics() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    PeriodSelector(
                        selectedPeriod = selectedPeriod,
                        onPeriodSelected = { viewModel.selectPeriod(it) }
                    )
                }
                
                item {
                    SpendingOverviewCard(
                        analytics = analytics,
                        comparison = comparison,
                        currencyFormatter = currencyFormatter
                    )
                }
                
                item {
                    CategoryBreakdownSection(
                        categories = analytics.categoryBreakdown,
                        totalSpent = analytics.totalSpent,
                        currencyFormatter = currencyFormatter
                    )
                }
                
                item {
                    SpendingTrendsCard(
                        monthlyTrends = analytics.monthlyTrends,
                        currencyFormatter = currencyFormatter
                    )
                }
                
                item {
                    InsightsSection(
                        insights = insights,
                        currencyFormatter = currencyFormatter
                    )
                }
                
                item {
                    BudgetAnalysisSection(
                        budgetAnalysis = budgetAnalysis,
                        currencyFormatter = currencyFormatter
                    )
                }
                
                item {
                    SavingsAnalysisCard(
                        analytics = analytics,
                        currencyFormatter = currencyFormatter
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
fun PeriodSelector(
    selectedPeriod: AnalyticsPeriod,
    onPeriodSelected: (AnalyticsPeriod) -> Unit
) {
    val periods = listOf(
        AnalyticsPeriod.THIS_WEEK to "This Week",
        AnalyticsPeriod.THIS_MONTH to "This Month",
        AnalyticsPeriod.LAST_MONTH to "Last Month",
        AnalyticsPeriod.LAST_3_MONTHS to "Last 3 Months",
        AnalyticsPeriod.THIS_YEAR to "This Year"
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(periods) { (period, label) ->
            FilterChip(
                onClick = { onPeriodSelected(period) },
                label = { Text(label) },
                selected = selectedPeriod == period,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
fun SpendingOverviewCard(
    analytics: SpendingAnalytics,
    comparison: SpendingComparison,
    currencyFormatter: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Spending Overview",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OverviewItem(
                    icon = Icons.Default.TrendingDown,
                    label = "Total Spent",
                    amount = currencyFormatter.format(analytics.totalSpent),
                    color = Color(0xFFF44336),
                    change = if (comparison.changePercentage != 0.0) {
                        "${if (comparison.isIncrease) "+" else ""}${comparison.changePercentage.toInt()}%"
                    } else null
                )
                
                OverviewItem(
                    icon = Icons.Default.TrendingUp,
                    label = "Total Income",
                    amount = currencyFormatter.format(analytics.totalIncome),
                    color = Color(0xFF4CAF50)
                )
                
                OverviewItem(
                    icon = Icons.Default.Savings,
                    label = "Savings Rate",
                    amount = "${analytics.savingsRate.toInt()}%",
                    color = if (analytics.savingsRate > 0) Color(0xFF2196F3) else Color(0xFFFF9800)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Daily Average: ${currencyFormatter.format(analytics.averageDailySpending)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun OverviewItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    amount: String,
    color: Color,
    change: String? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        
        Text(
            text = amount,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        
        change?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("+")) Color(0xFFF44336) else Color(0xFF4CAF50)
            )
        }
    }
}

@Composable
fun CategoryBreakdownSection(
    categories: List<CategorySpending>,
    totalSpent: Double,
    currencyFormatter: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Spending by Category",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (categories.isNotEmpty()) {
                // Pie Chart
                SpendingPieChart(
                    categories = categories,
                    totalSpent = totalSpent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Category List
                categories.take(5).forEach { category ->
                    CategoryItem(
                        category = category,
                        totalSpent = totalSpent,
                        currencyFormatter = currencyFormatter
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                EmptyAnalyticsMessage("No spending data available")
            }
        }
    }
}

@Composable
fun SpendingPieChart(
    categories: List<CategorySpending>,
    totalSpent: Double,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800),
        Color(0xFFF44336), Color(0xFF9C27B0), Color(0xFF00BCD4),
        Color(0xFF8BC34A), Color(0xFFE91E63), Color(0xFF607D8B)
    )
    
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1000)
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = minOf(centerX, centerY) * 0.8f
            
            var startAngle = -90f
            
            categories.forEachIndexed { index, category ->
                val sweepAngle = ((category.totalSpent / totalSpent) * 360f * animatedProgress).toFloat()
                val color = colors[index % colors.size]
                
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    topLeft = Offset(centerX - radius, centerY - radius),
                    size = Size(radius * 2, radius * 2)
                )
                
                startAngle += sweepAngle
            }
            
            // Inner circle for donut effect
            drawCircle(
                color = Color.White,
                radius = radius * 0.5f,
                center = Offset(centerX, centerY)
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = NumberFormat.getCurrencyInstance().format(totalSpent),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CategoryItem(
    category: CategorySpending,
    totalSpent: Double,
    currencyFormatter: NumberFormat
) {
    val percentage = if (totalSpent > 0) (category.totalSpent / totalSpent * 100) else 0.0
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(getCategoryColor(category.category))
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = category.category,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${category.transactionCount} transactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = currencyFormatter.format(category.totalSpent),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${percentage.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SpendingTrendsCard(
    monthlyTrends: List<MonthlySpending>,
    currencyFormatter: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Monthly Trends",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (monthlyTrends.isNotEmpty()) {
                SpendingTrendsChart(
                    trends = monthlyTrends.takeLast(6),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            } else {
                EmptyAnalyticsMessage("No trend data available")
            }
        }
    }
}

@Composable
fun SpendingTrendsChart(
    trends: List<MonthlySpending>,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1000)
    )
    
    Canvas(modifier = modifier) {
        if (trends.isEmpty()) return@Canvas
        
        val maxAmount = trends.maxOfOrNull { maxOf(it.totalSpent, it.totalIncome) } ?: 0.0
        if (maxAmount == 0.0) return@Canvas
        
        val chartWidth = size.width - 40.dp.toPx()
        val chartHeight = size.height - 40.dp.toPx()
        val barWidth = chartWidth / (trends.size * 2)
        
        trends.forEachIndexed { index, trend ->
            val x = 20.dp.toPx() + index * barWidth * 2
            
            // Expense bar
            val expenseHeight = (trend.totalSpent / maxAmount * chartHeight * animatedProgress).toFloat()
            drawRect(
                color = Color(0xFFF44336),
                topLeft = Offset(x, size.height - 20.dp.toPx() - expenseHeight),
                size = Size(barWidth * 0.8f, expenseHeight)
            )
            
            // Income bar
            val incomeHeight = (trend.totalIncome / maxAmount * chartHeight * animatedProgress).toFloat()
            drawRect(
                color = Color(0xFF4CAF50),
                topLeft = Offset(x + barWidth * 0.9f, size.height - 20.dp.toPx() - incomeHeight),
                size = Size(barWidth * 0.8f, incomeHeight)
            )
        }
    }
}

@Composable
fun InsightsSection(
    insights: List<SpendingInsight>,
    currencyFormatter: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Smart Insights",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (insights.isNotEmpty()) {
                insights.forEach { insight ->
                    InsightItem(insight = insight, currencyFormatter = currencyFormatter)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else {
                EmptyAnalyticsMessage("No insights available")
            }
        }
    }
}

@Composable
fun InsightItem(
    insight: SpendingInsight,
    currencyFormatter: NumberFormat
) {
    val icon = when (insight.type) {
        InsightType.SPENDING_INCREASE -> Icons.Default.TrendingUp
        InsightType.SPENDING_DECREASE -> Icons.Default.TrendingDown
        InsightType.TOP_CATEGORY -> Icons.Default.Star
        InsightType.BUDGET_WARNING -> Icons.Default.Warning
        InsightType.SAVINGS_ACHIEVEMENT -> Icons.Default.EmojiEvents
        InsightType.UNUSUAL_SPENDING -> Icons.Default.Info
        InsightType.RECOMMENDATION -> Icons.Default.Lightbulb
    }
    
    val color = when (insight.type) {
        InsightType.SPENDING_INCREASE, InsightType.BUDGET_WARNING -> Color(0xFFF44336)
        InsightType.SPENDING_DECREASE, InsightType.SAVINGS_ACHIEVEMENT -> Color(0xFF4CAF50)
        InsightType.TOP_CATEGORY -> Color(0xFFFF9800)
        InsightType.UNUSUAL_SPENDING -> Color(0xFF2196F3)
        InsightType.RECOMMENDATION -> Color(0xFF9C27B0)
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = insight.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = insight.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SavingsAnalysisCard(
    analytics: SpendingAnalytics,
    currencyFormatter: NumberFormat
) {
    val savingsAmount = analytics.totalIncome - analytics.totalSpent
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (savingsAmount >= 0) 
                Color(0xFF4CAF50).copy(alpha = 0.1f) 
            else 
                Color(0xFFF44336).copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (savingsAmount >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = null,
                    tint = if (savingsAmount >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Savings Analysis",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (savingsAmount >= 0) 
                    "Great! You saved ${currencyFormatter.format(savingsAmount)}" 
                else 
                    "You spent ${currencyFormatter.format(abs(savingsAmount))} more than you earned",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Savings rate: ${analytics.savingsRate.toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyAnalyticsMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.BarChart,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun BudgetAnalysisSection(
    budgetAnalysis: List<CategoryBudgetAnalysis>,
    currencyFormatter: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Budget vs Spending",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (budgetAnalysis.isNotEmpty()) {
                val totalSaved = budgetAnalysis.sumOf { it.savedAmount }
                val totalOverspent = budgetAnalysis.sumOf { it.overspentAmount }
                
                // Overall Summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        BudgetSummaryItem(
                            icon = Icons.Default.TrendingUp,
                            label = "Total Saved",
                            amount = currencyFormatter.format(totalSaved),
                            color = Color(0xFF4CAF50)
                        )
                        
                        BudgetSummaryItem(
                            icon = Icons.Default.TrendingDown,
                            label = "Overspent",
                            amount = currencyFormatter.format(totalOverspent),
                            color = Color(0xFFF44336)
                        )
                        
                        BudgetSummaryItem(
                            icon = Icons.Default.Balance,
                            label = "Net Savings",
                            amount = currencyFormatter.format(totalSaved - totalOverspent),
                            color = if (totalSaved >= totalOverspent) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Category-wise Analysis
                budgetAnalysis.forEach { analysis ->
                    BudgetAnalysisItem(
                        analysis = analysis,
                        currencyFormatter = currencyFormatter
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                EmptyAnalyticsMessage("Set up budgets to see your savings analysis")
            }
        }
    }
}

@Composable
fun BudgetSummaryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    amount: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = amount,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BudgetAnalysisItem(
    analysis: CategoryBudgetAnalysis,
    currencyFormatter: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (analysis.isOverBudget) 
                Color(0xFFF44336).copy(alpha = 0.05f) 
            else 
                Color(0xFF4CAF50).copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(getCategoryColor(analysis.category))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = analysis.category,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (analysis.isOverBudget) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (analysis.isOverBudget) Color(0xFFF44336) else Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${analysis.savingsPercentage.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (analysis.isOverBudget) Color(0xFFF44336) else Color(0xFF4CAF50)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Budget Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val progress = if (analysis.budgetAmount > 0) {
                    (analysis.spentAmount / analysis.budgetAmount).coerceAtMost(1.0).toFloat()
                } else 0f
                
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(
                            if (analysis.isOverBudget) Color(0xFFF44336) else Color(0xFF4CAF50),
                            RoundedCornerShape(4.dp)
                        )
                )
                
                if (analysis.isOverBudget) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .background(
                                Color(0xFFF44336),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Budget",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currencyFormatter.format(analysis.budgetAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Column {
                    Text(
                        text = "Spent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currencyFormatter.format(analysis.spentAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = if (analysis.isOverBudget) "Overspent" else "Saved",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (analysis.isOverBudget) 
                            currencyFormatter.format(analysis.overspentAmount)
                        else 
                            currencyFormatter.format(analysis.savedAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (analysis.isOverBudget) Color(0xFFF44336) else Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

fun getCategoryColor(category: String): Color {
    val colors = listOf(
        Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800),
        Color(0xFFF44336), Color(0xFF9C27B0), Color(0xFF00BCD4),
        Color(0xFF8BC34A), Color(0xFFE91E63), Color(0xFF607D8B)
    )
    return colors[category.hashCode().mod(colors.size)]
}
