package com.ssk.myfinancehub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ssk.myfinancehub.data.model.Budget
import com.ssk.myfinancehub.data.model.BudgetStatus
import com.ssk.myfinancehub.data.model.BudgetSummary
import com.ssk.myfinancehub.ui.viewmodel.BudgetViewModel
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetPlanningScreen(
    viewModel: BudgetViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val budgetSummaries by viewModel.budgetSummaries.collectAsStateWithLifecycle()
    val currentMonth by viewModel.currentMonth.collectAsStateWithLifecycle()
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    val totalBudget by viewModel.getTotalBudgetAmount().collectAsStateWithLifecycle(initialValue = 0.0)
    val totalSpent by viewModel.getTotalSpentAmount().collectAsStateWithLifecycle(initialValue = 0.0)
    val budgetUtilization by viewModel.getBudgetUtilization().collectAsStateWithLifecycle(initialValue = 0f)
    
    var showAddBudgetDialog by remember { mutableStateOf(false) }
    var selectedBudget by remember { mutableStateOf<Budget?>(null) }
    
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
    val monthNames = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Budget Planning",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddBudgetDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Budget")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .imePadding(), // Add IME padding for keyboard handling
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                MonthYearSelector(
                    currentMonth = currentMonth,
                    currentYear = currentYear,
                    monthNames = monthNames,
                    onMonthYearChanged = { month, year ->
                        viewModel.setCurrentMonth(month, year)
                    }
                )
            }
            
            item {
                BudgetOverviewCard(
                    totalBudget = totalBudget,
                    totalSpent = totalSpent,
                    utilization = budgetUtilization,
                    currencyFormatter = currencyFormatter
                )
            }
            
            item {
                Text(
                    text = "Category Budgets",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            if (budgetSummaries.isEmpty()) {
                item {
                    EmptyBudgetCard(onAddBudget = { showAddBudgetDialog = true })
                }
            } else {
                items(budgetSummaries) { summary ->
                    BudgetSummaryCard(
                        summary = summary,
                        currencyFormatter = currencyFormatter,
                        onEdit = { selectedBudget = summary.budget; showAddBudgetDialog = true },
                        onDelete = { viewModel.deleteBudget(summary.budget) }
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
    
    if (showAddBudgetDialog) {
        AddEditBudgetDialog(
            budget = selectedBudget,
            currentMonth = currentMonth,
            currentYear = currentYear,
            onDismiss = { 
                showAddBudgetDialog = false
                selectedBudget = null
            },
            onSave = { budget ->
                if (selectedBudget != null) {
                    viewModel.updateBudget(budget)
                } else {
                    viewModel.insertBudget(budget)
                }
                showAddBudgetDialog = false
                selectedBudget = null
            }
        )
    }
}

@Composable
fun MonthYearSelector(
    currentMonth: Int,
    currentYear: Int,
    monthNames: Array<String>,
    onMonthYearChanged: (Int, Int) -> Unit
) {
    var showMonthPicker by remember { mutableStateOf(false) }
    var showYearPicker by remember { mutableStateOf(false) }
    
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Budget Period",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showMonthPicker = true }
                ) {
                    Text("${monthNames[currentMonth - 1]}")
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                
                OutlinedButton(
                    onClick = { showYearPicker = true }
                ) {
                    Text("$currentYear")
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
        }
    }
    
    if (showMonthPicker) {
        AlertDialog(
            onDismissRequest = { showMonthPicker = false },
            title = { Text("Select Month") },
            text = {
                LazyColumn {
                    items(monthNames.size) { index ->
                        TextButton(
                            onClick = {
                                onMonthYearChanged(index + 1, currentYear)
                                showMonthPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(monthNames[index])
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMonthPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showYearPicker) {
        AlertDialog(
            onDismissRequest = { showYearPicker = false },
            title = { Text("Select Year") },
            text = {
                LazyColumn {
                    items((2020..2030).toList()) { year ->
                        TextButton(
                            onClick = {
                                onMonthYearChanged(currentMonth, year)
                                showYearPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$year")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showYearPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BudgetOverviewCard(
    totalBudget: Double,
    totalSpent: Double,
    utilization: Float,
    currencyFormatter: NumberFormat
) {
    val remaining = totalBudget - totalSpent
    val statusColor = when {
        utilization >= 1.0f -> Color(0xFFF44336)
        utilization >= 0.8f -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Budget Overview",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BudgetInfoItem(
                    label = "Total Budget",
                    amount = currencyFormatter.format(totalBudget),
                    color = MaterialTheme.colorScheme.primary
                )
                BudgetInfoItem(
                    label = "Spent",
                    amount = currencyFormatter.format(totalSpent),
                    color = Color(0xFFF44336)
                )
                BudgetInfoItem(
                    label = "Remaining",
                    amount = currencyFormatter.format(remaining),
                    color = if (remaining >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Overall Progress",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            LinearProgressIndicator(
                progress = { utilization.coerceAtMost(1.0f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = statusColor,
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "${(utilization * 100).toInt()}% utilized",
                style = MaterialTheme.typography.bodySmall,
                color = statusColor
            )
        }
    }
}

@Composable
fun BudgetInfoItem(
    label: String,
    amount: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun BudgetSummaryCard(
    summary: BudgetSummary,
    currencyFormatter: NumberFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    val statusColor = when (summary.budgetStatus) {
        BudgetStatus.SAFE -> Color(0xFF4CAF50)
        BudgetStatus.GOOD -> Color(0xFF8BC34A)
        BudgetStatus.WARNING -> Color(0xFFFF9800)
        BudgetStatus.OVER_BUDGET -> Color(0xFFF44336)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = summary.budget.category,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFF44336)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Budget: ${currencyFormatter.format(summary.budget.budgetAmount)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Spent: ${currencyFormatter.format(summary.spentAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { summary.progressPercentage.coerceAtMost(1.0f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = statusColor,
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (summary.isOverBudget) "Over budget" else "Remaining: ${currencyFormatter.format(summary.remainingAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
                Text(
                    text = "${(summary.progressPercentage * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Budget") },
            text = { Text("Are you sure you want to delete the budget for ${summary.budget.category}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EmptyBudgetCard(onAddBudget: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.PieChart,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No budgets set",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Create budgets to track your spending",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAddBudget) {
                Text("Add Budget")
            }
        }
    }
}

@Composable
fun AddEditBudgetDialog(
    budget: Budget?,
    currentMonth: Int,
    currentYear: Int,
    onDismiss: () -> Unit,
    onSave: (Budget) -> Unit
) {
    var category by remember { mutableStateOf(budget?.category ?: "") }
    var amount by remember { mutableStateOf(budget?.budgetAmount?.toString() ?: "") }
    
    val expenseCategories = listOf(
        "Food & Dining", "Transportation", "Shopping", "Entertainment",
        "Bills & Utilities", "Healthcare", "Education", "Travel",
        "EMI", "Rent", "Savings", "Other"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (budget != null) "Edit Budget" else "Add Budget") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Category Selection
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                
                expenseCategories.chunked(2).forEach { rowCategories ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowCategories.forEach { cat ->
                            FilterChip(
                                onClick = { category = cat },
                                label = { Text(cat, style = MaterialTheme.typography.bodySmall) },
                                selected = category == cat,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowCategories.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                
                OutlinedTextField(
                    value = if (expenseCategories.contains(category)) "" else category,
                    onValueChange = { category = it },
                    label = { Text("Custom category") }
                )
                
                // Amount Input
                OutlinedTextField(
                    value = amount,
                    onValueChange = { 
                        if (it.all { char -> char.isDigit() || char == '.' }) {
                            amount = it
                        }
                    },
                    label = { Text("Budget amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    leadingIcon = { Text("$") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (category.isNotBlank() && amount.isNotBlank()) {
                        val budgetToSave = Budget(
                            id = budget?.id ?: 0L,
                            category = category,
                            budgetAmount = amount.toDoubleOrNull() ?: 0.0,
                            month = currentMonth,
                            year = currentYear
                        )
                        println("Saving budget: $budgetToSave")
                        onSave(budgetToSave)
                    }
                },
                enabled = category.isNotBlank() && amount.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
