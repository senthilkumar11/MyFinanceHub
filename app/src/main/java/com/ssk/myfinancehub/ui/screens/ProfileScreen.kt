package com.ssk.myfinancehub.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssk.myfinancehub.auth.AuthManager
import com.ssk.myfinancehub.data.database.FinanceDatabase
import com.ssk.myfinancehub.data.model.Currency
import com.ssk.myfinancehub.data.repository.TransactionRepository
import com.ssk.myfinancehub.data.repository.BudgetRepository
import com.ssk.myfinancehub.repository.SyncRepository
import com.ssk.myfinancehub.ui.theme.ThemeManager
import com.ssk.myfinancehub.ui.theme.ThemeMode
import com.ssk.myfinancehub.ui.viewmodel.BudgetViewModel
import com.ssk.myfinancehub.ui.viewmodel.TransactionViewModel
import com.ssk.myfinancehub.utils.CurrencyManager
import com.zoho.catalyst.org.ZCatalystUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class UserDetails(
    val email: String = "",
    val firstName: String = "",
    val userId: String = "",
    val status: String = "",
    val isConfirmed: Boolean = false,
    val userType: String = "",
    val roleName: String = "",
    val createdTime: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onLogout: (() -> Unit)? = null,
    transactionViewModel: TransactionViewModel? = null,
    budgetViewModel: BudgetViewModel? = null
) {
    val themeMode by ThemeManager.themeMode.collectAsStateWithLifecycle()
    val selectedCurrency by CurrencyManager.selectedCurrency.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val authManager = remember { AuthManager.getInstance(context) }
    
    // Get repository instances from ViewModels when available
    val syncRepository = remember(transactionViewModel, budgetViewModel) { 
        if (transactionViewModel != null && budgetViewModel != null) {
            val database = FinanceDatabase.getDatabase(context)
            val transactionRepo = TransactionRepository(database.transactionDao())
            val budgetRepo = BudgetRepository(database.budgetDao())
            SyncRepository.getInstance(transactionRepo, budgetRepo)
        } else {
            null
        }
    }
    
    // Sync state variables
    var isSyncing by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf("Ready to sync") }
    val syncRepositoryStatus by (syncRepository?.syncStatus ?: flowOf("Sync not available")).collectAsStateWithLifecycle(initialValue = "Ready to sync")
    val syncIsLoading by (syncRepository?.isLoading ?: flowOf(false)).collectAsStateWithLifecycle(initialValue = false)
    
    // Update local sync status
    LaunchedEffect(syncRepositoryStatus, syncIsLoading) {
        if (syncIsLoading) {
            isSyncing = true
            syncStatus = syncRepositoryStatus
        } else {
            isSyncing = false
            syncStatus = if (syncRepositoryStatus.contains("successfully") || syncRepositoryStatus.contains("completed")) {
                "Last sync: ${syncRepositoryStatus}"
            } else {
                syncRepositoryStatus
            }
        }
    }
    
    // Enhanced sync functions
    val performIncrementalSync = {
        if (!isSyncing && syncRepository != null && transactionViewModel != null && budgetViewModel != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = syncRepository.performIncrementalSync()
                    CoroutineScope(Dispatchers.Main).launch {
                        if (result.isSuccess) {
                            Toast.makeText(context, "Incremental sync completed successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Incremental sync failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Incremental sync error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val performCleanSync = {
        if (!isSyncing && syncRepository != null && transactionViewModel != null && budgetViewModel != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = syncRepository.performCleanSync()
                    CoroutineScope(Dispatchers.Main).launch {
                        if (result.isSuccess) {
                            Toast.makeText(context, "Clean sync completed successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Clean sync failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Clean sync error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val performSmartSync = {
        if (!isSyncing && syncRepository != null && transactionViewModel != null && budgetViewModel != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = syncRepository.performSmartSync()
                    CoroutineScope(Dispatchers.Main).launch {
                        if (result.isSuccess) {
                            Toast.makeText(context, "Smart sync completed successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Smart sync failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Smart sync error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val retryFailedSync = {
        if (!isSyncing && syncRepository != null && transactionViewModel != null && budgetViewModel != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = syncRepository.retryFailedSync()
                    CoroutineScope(Dispatchers.Main).launch {
                        if (result.isSuccess) {
                            Toast.makeText(context, "Failed records retry completed", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Retry failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Retry error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Legacy sync function (full sync)
    val performSync = {
        if (!isSyncing && syncRepository != null && transactionViewModel != null && budgetViewModel != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Sync transactions from cloud
                    val transactionsResult = syncRepository.fetchAllTransactionsFromCatalyst()
                    if (transactionsResult.isSuccess) {
                        // Update local database with cloud data
                        transactionsResult.getOrNull()?.forEach { cloudTransaction ->
                            transactionViewModel.insertTransactionFromSync(cloudTransaction)
                        }
                    }
                    
                    // Sync budgets from cloud
                    val budgetsResult = syncRepository.fetchAllBudgetsFromCatalyst()
                    if (budgetsResult.isSuccess) {
                        // Update local database with cloud data
                        budgetsResult.getOrNull()?.forEach { cloudBudget ->
                            budgetViewModel.insertBudgetFromSync(cloudBudget)
                        }
                    }
                    
                    // Show success message
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Sync completed successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    // Show error message
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else if (transactionViewModel == null || budgetViewModel == null) {
            Toast.makeText(context, "Sync not available", Toast.LENGTH_SHORT).show()
        }
    }
    var userDetails by remember { mutableStateOf(UserDetails()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    
    // Get current user info from Zoho Catalyst
    LaunchedEffect(Unit) {
        authManager.getCurrentUserDetails(
            onSuccess = { user ->
                userDetails = UserDetails(
                    email = user.email ?: "",
                    firstName = user.firstName ?: "",
                    userId = user.email?.substringBefore("@") ?: "user", // Use email prefix as ID
                    status = "ACTIVE", // Default from your JSON
                    isConfirmed = user.isConfirmed,
                    userType = "App User", // Default from your JSON
                    roleName = "App User", // Default from your JSON
                    createdTime = user.createdTime ?: ""
                )
                isLoading = false
            },
            onFailure = { error ->
                // Fallback to cached data or defaults
                userDetails = UserDetails(
                    email = "senthilkumar30998@gmail.com",
                    firstName = "senthil s",
                    status = "ACTIVE",
                    userType = "App User"
                )
                isLoading = false
                Toast.makeText(context, "Using cached user info", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .imePadding(), // Add IME padding for keyboard handling
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Profile Avatar
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Name and Email
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Loading user information...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            } else {
                Text(
                    text = userDetails.firstName.ifEmpty { "User" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Text(
                    text = userDetails.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // User status badge
                Card(
                    modifier = Modifier.padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (userDetails.status == "ACTIVE") 
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                        else 
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (userDetails.status == "ACTIVE") 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.error,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${userDetails.status} • ${userDetails.userType}",
                            fontSize = 12.sp,
                            color = if (userDetails.status == "ACTIVE") 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Profile Options
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Account Information Section
                if (!isLoading) {
                    Text(
                        text = "Account Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    
                    ProfileInfoCard(
                        title = "User ID",
                        value = userDetails.userId.ifEmpty { "N/A" },
                        icon = Icons.Default.Person
                    )
                    
                    ProfileInfoCard(
                        title = "Role",
                        value = userDetails.roleName.ifEmpty { userDetails.userType },
                        icon = Icons.Default.Security
                    )
                    
                    ProfileInfoCard(
                        title = "Account Status",
                        value = if (userDetails.isConfirmed) "Verified" else "Pending Verification",
                        icon = if (userDetails.isConfirmed) Icons.Default.CheckCircle else Icons.Default.Schedule,
                        valueColor = if (userDetails.isConfirmed) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                    
                    if (userDetails.createdTime.isNotEmpty()) {
                        ProfileInfoCard(
                            title = "Member Since",
                            value = userDetails.createdTime,
                            icon = Icons.Default.DateRange
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                
                ProfileOptionCard(
                    icon = Icons.Default.Person,
                    title = "Edit Profile",
                    subtitle = "Update your personal information",
                    onClick = { }
                )
                
                ProfileOptionCard(
                    icon = Icons.Default.Security,
                    title = "Security",
                    subtitle = "Password and security settings",
                    onClick = { }
                )
                
                ProfileOptionCard(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "Manage notification preferences",
                    onClick = { }
                )
                
                // Theme Selection Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Theme",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Theme",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Choose your preferred theme",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Theme Selection Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeChip(
                                text = "Light",
                                isSelected = themeMode == ThemeMode.LIGHT,
                                onClick = { ThemeManager.setThemeMode(ThemeMode.LIGHT) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeChip(
                                text = "Dark",
                                isSelected = themeMode == ThemeMode.DARK,
                                onClick = { ThemeManager.setThemeMode(ThemeMode.DARK) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeChip(
                                text = "System",
                                isSelected = themeMode == ThemeMode.SYSTEM,
                                onClick = { ThemeManager.setThemeMode(ThemeMode.SYSTEM) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                
                // Currency Selection Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    onClick = { showCurrencyDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachMoney,
                            contentDescription = "Currency",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Currency",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${selectedCurrency.code} (${selectedCurrency.symbol}) - ${selectedCurrency.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Change Currency",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                // Sync Data Section
                Text(
                    text = "Data Synchronization",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                
                // Sync Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Sync Status",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sync Status",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = syncStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Sync Options Grid
                if (syncRepository != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Smart Sync Button (Most recommended)
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            onClick = { if (!isSyncing) performSmartSync() }
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Smart Sync",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Smart Sync",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    text = "Recommended",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                )
                            }
                        }
                        
                        // Clean Sync Button
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            onClick = { if (!isSyncing) performCleanSync() }
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = "Clean Sync",
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Clean Sync",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiary
                                )
                                Text(
                                    text = "Full Compare",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Incremental Sync Button
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            onClick = { if (!isSyncing) performIncrementalSync() }
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Update,
                                    contentDescription = "Incremental Sync",
                                    tint = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Quick Sync",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondary
                                )
                                Text(
                                    text = "Recent Only",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f)
                                )
                            }
                        }
                        
                        // Retry Failed Button
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            onClick = { if (!isSyncing) retryFailedSync() }
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry Failed",
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Retry Failed",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onError
                                )
                                Text(
                                    text = "Fix Errors",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onError.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                } else {
                    // Fallback if sync repository is not available
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Sync Unavailable",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Sync Unavailable",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Please ensure you're logged in",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                ProfileOptionCard(
                    icon = Icons.Default.Help,
                    title = "Help & Support",
                    subtitle = "Get help and contact support",
                    onClick = { }
                )
                
                ProfileOptionCard(
                    icon = Icons.Default.Info,
                    title = "About",
                    subtitle = "App version and information",
                    onClick = { }
                )
                
                ProfileOptionCard(
                    icon = Icons.Default.ExitToApp,
                    title = "Sign Out",
                    subtitle = "Sign out of your account",
                    onClick = {
                        authManager.logout(
                            onSuccess = {
                                onLogout?.invoke()
                            },
                            onFailure = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    isDestructive = true
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // Currency Selection Dialog
    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = {
                Text(
                    text = "Select Currency",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Choose your preferred currency for the app:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Currency options
                    Currency.values().forEach { currency ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (currency == selectedCurrency) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surface
                            ),
                            onClick = {
                                CurrencyManager.setCurrency(currency)
                                showCurrencyDialog = false
                                Toast.makeText(context, "Currency changed to ${currency.displayName}", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currency.symbol,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currency == selectedCurrency) 
                                        MaterialTheme.colorScheme.onPrimaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.onSurface
                                )
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currency.code,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (currency == selectedCurrency) 
                                            MaterialTheme.colorScheme.onPrimaryContainer 
                                        else 
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = currency.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (currency == selectedCurrency) 
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) 
                                        else 
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                if (currency == selectedCurrency) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun ProfileInfoCard(
    title: String,
    value: String,
    icon: ImageVector,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = valueColor,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun ProfileOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ThemeChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        onClick = onClick
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
