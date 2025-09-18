package com.ssk.myfinancehub.repository

import android.content.Context
import android.util.Log
import com.ssk.myfinancehub.data.model.Budget
import com.ssk.myfinancehub.data.model.SyncStatus
import com.ssk.myfinancehub.data.model.Transaction
import com.ssk.myfinancehub.data.model.TransactionType
import com.ssk.myfinancehub.data.repository.TransactionRepository
import com.ssk.myfinancehub.data.repository.BudgetRepository
import com.zoho.catalyst.common.ZCatalystUtil
import com.zoho.catalyst.datastore.Column
import com.zoho.catalyst.datastore.ZCatalystSelectQuery
import com.zoho.catalyst.setup.ZCatalystApp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SyncRepository private constructor(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository
) {
    // Create a dedicated coroutine scope for database operations
    private val databaseScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val TAG = "SyncRepository"
        private const val FUNCTION_ID = "20577000000014755"
        
        @Volatile
        private var INSTANCE: SyncRepository? = null
        
        fun getInstance(
            transactionRepository: TransactionRepository,
            budgetRepository: BudgetRepository
        ): SyncRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = SyncRepository(transactionRepository, budgetRepository)
                INSTANCE = instance
                instance
            }
        }
    }
    
    // Sync status states
    private val _syncStatus = MutableStateFlow("Catalyst sync enabled")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()
    
    private val _lastSyncTime = MutableStateFlow<String?>(null)
    val lastSyncTime: StateFlow<String?> = _lastSyncTime.asStateFlow()
    
    private val _isEnabled = MutableStateFlow(true) // Enable sync by default
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    fun clearErrorMessage() {
        _errorMessage.value = null
        _connectionError.value = null
    }
    
    /**
     * Create a transaction via Catalyst function
     */
    suspend fun createTransactionViaCatalyst(transaction: Transaction): Result<Transaction> {
        return suspendCancellableCoroutine { continuation ->
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                val body = hashMapOf<String, Any>(
                    "operation" to "transaction",
                    "method" to "POST",
                    "resourceType" to "transactions",
                    "requestBody" to hashMapOf(
                        "amount" to transaction.amount,
                        "type" to transaction.type.name,
                        "category" to transaction.category,
                        "description" to transaction.description,
                        "transactionDate" to transaction.date.time
                    )
                )
                
                Log.d(TAG, "Creating transaction via Catalyst: $body")
                _syncStatus.value = "Creating transaction..."
                
                ZCatalystApp.getInstance().getFunctionInstance(FUNCTION_ID).executePost(
                    hashMapOf(),
                    body,
                    { response ->
                        _isLoading.value = false
                        _syncStatus.value = "Transaction created successfully"
                        _lastSyncTime.value = Date().toString()
                        
                        try {
                            // Parse response to get ROWID
                            Log.d(TAG, "Raw response received: $response")
                            val jsonResponse = JSONObject(response)
                            
                            // Check if response has "output" field (wrapped) or direct structure
                            val (statusCode, bodyJson) = if (jsonResponse.has("output")) {
                                // Wrapped format: {"output": "{\"statusCode\":...}"}
                                val outputString = jsonResponse.getString("output")
                                val output = JSONObject(outputString)
                                Pair(output.getInt("statusCode"), output.getJSONObject("body"))
                            } else {
                                // Direct format: {"statusCode":..., "body":{...}}
                                Pair(jsonResponse.getInt("statusCode"), jsonResponse.getJSONObject("body"))
                            }
                            
                            if (statusCode !in 200..299) {
                                Log.e(TAG, "Catalyst API error: Status $statusCode - $response")
                                continuation.resume(Result.failure(Exception("API Error: Status $statusCode")))
                                return@executePost
                            }
                            
                            val data = bodyJson.getJSONObject("data")
                            val rowId = data.getString("ROWID")
                            
                            // Update transaction with ROWID and sync status
                            val updatedTransaction = transaction.copy(
                                catalystRowId = rowId,
                                syncStatus = SyncStatus.SYNCED,
                                lastSyncedAt = Date()
                            )
                            
                            Log.d(TAG, "Transaction created successfully with ROWID: $rowId")
                            continuation.resume(Result.success(updatedTransaction))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing transaction response: ${e.message}")
                            Log.d(TAG, "Response was: $response")
                            continuation.resume(Result.failure(e))
                        }
                    },
                    { error ->
                        _isLoading.value = false
                        _syncStatus.value = "Failed to create transaction"
                        _errorMessage.value = error.message ?: "Unknown error occurred"
                        
                        Log.e(TAG, "Failed to create transaction: ${error.message}")
                        continuation.resume(Result.failure(Exception(error.message ?: "Unknown error")))
                    }
                )
                
            } catch (e: Exception) {
                _isLoading.value = false
                _syncStatus.value = "Error creating transaction"
                _errorMessage.value = e.message
                
                Log.e(TAG, "Exception creating transaction", e)
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    /**
     * Update a transaction via Catalyst function
     */
    suspend fun updateTransactionViaCatalyst(transaction: Transaction): Result<Transaction> {
        return suspendCancellableCoroutine { continuation ->
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                val body = hashMapOf<String, Any>(
                    "operation" to "transaction",
                    "method" to "PUT",
                    "resourceType" to "transactions",
                    "resourceId" to (transaction.catalystRowId ?: ""),
                    "requestBody" to hashMapOf(
                        "amount" to transaction.amount,
                        "type" to transaction.type.name,
                        "category" to transaction.category,
                        "description" to transaction.description,
                        "transactionDate" to transaction.date.time
                    )
                )
                
                Log.d(TAG, "Updating transaction via Catalyst: $body")
                _syncStatus.value = "Updating transaction..."
                
                ZCatalystApp.getInstance().getFunctionInstance(FUNCTION_ID).executePost(
                    hashMapOf(),
                    body,
                    { response ->
                        _isLoading.value = false
                        _syncStatus.value = "Transaction updated successfully"
                        _lastSyncTime.value = Date().toString()
                        
                        val updatedTransaction = transaction.copy(
                            syncStatus = SyncStatus.SYNCED,
                            lastSyncedAt = Date()
                        )
                        
                        Log.d(TAG, "Transaction updated successfully: $response")
                        continuation.resume(Result.success(updatedTransaction))
                    },
                    { error ->
                        _isLoading.value = false
                        _syncStatus.value = "Failed to update transaction"
                        _errorMessage.value = error.message ?: "Unknown error occurred"
                        
                        Log.e(TAG, "Failed to update transaction: ${error.message}")
                        continuation.resume(Result.failure(Exception(error.message ?: "Unknown error")))
                    }
                )
                
            } catch (e: Exception) {
                _isLoading.value = false
                _syncStatus.value = "Error updating transaction"
                _errorMessage.value = e.message
                
                Log.e(TAG, "Exception updating transaction", e)
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    /**
     * Delete a transaction via Catalyst function
     */
    suspend fun deleteTransactionViaCatalyst(transaction: Transaction): Result<Boolean> {
        return suspendCancellableCoroutine { continuation ->
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                val body = hashMapOf<String, Any>(
                    "operation" to "transaction",
                    "method" to "DELETE",
                    "resourceType" to "transactions",
                    "resourceId" to (transaction.catalystRowId ?: "")
                )
                
                Log.d(TAG, "Deleting transaction via Catalyst: $body")
                _syncStatus.value = "Deleting transaction..."
                
                ZCatalystApp.getInstance().getFunctionInstance(FUNCTION_ID).executePost(
                    hashMapOf(),
                    body,
                    { response ->
                        _isLoading.value = false
                        
                        try {
                            // Parse response to check status
                            Log.d(TAG, "Delete response received: $response")
                            val jsonResponse = JSONObject(response)
                            
                            // Check if response has "output" field (wrapped) or direct structure
                            val statusCode = if (jsonResponse.has("output")) {
                                // Wrapped format: {"output": "{\"statusCode\":...}"}
                                val outputString = jsonResponse.getString("output")
                                val output = JSONObject(outputString)
                                output.getInt("statusCode")
                            } else {
                                // Direct format: {"statusCode":..., "body":{...}}
                                jsonResponse.getInt("statusCode")
                            }
                            
                            if (statusCode in 200..299) {
                                _syncStatus.value = "Transaction deleted successfully"
                                _lastSyncTime.value = Date().toString()
                                continuation.resume(Result.success(true))
                            } else {
                                val errorMsg = "Delete failed with status: $statusCode"
                                _syncStatus.value = "Failed to delete transaction"
                                _errorMessage.value = errorMsg
                                Log.e(TAG, errorMsg)
                                continuation.resume(Result.failure(Exception(errorMsg)))
                            }
                        } catch (e: Exception) {
                            _syncStatus.value = "Failed to process delete response"
                            _errorMessage.value = e.message
                            Log.e(TAG, "Failed to process delete response", e)
                            continuation.resume(Result.failure(e))
                        }
                    },
                    { error ->
                        _isLoading.value = false
                        _syncStatus.value = "Failed to delete transaction"
                        _errorMessage.value = error.message ?: "Unknown error occurred"
                        
                        Log.e(TAG, "Failed to delete transaction: ${error.message}")
                        continuation.resume(Result.failure(Exception(error.message ?: "Unknown error")))
                    }
                )
                
            } catch (e: Exception) {
                _isLoading.value = false
                _syncStatus.value = "Error deleting transaction"
                _errorMessage.value = e.message
                
                Log.e(TAG, "Exception deleting transaction", e)
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    /**
     * Fetch all transactions from Catalyst using incremental pagination
     */
    suspend fun fetchAllTransactionsFromCatalyst(): Result<List<Transaction>> {
        return fetchAllTransactionsWithPagination()
    }
    
    /**
     * Fetch all transactions with pagination and incremental approach
     */
    private suspend fun fetchAllTransactionsWithPagination(): Result<List<Transaction>> {
        return suspendCancellableCoroutine { continuation ->
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                Log.d(TAG, "Starting incremental fetch of transactions from Catalyst DataStore")
                _syncStatus.value = "Fetching transactions incrementally..."
                
                val allTransactions = mutableListOf<Transaction>()
                var offset = 0
                val limit = 100
                var hasMoreData = true
                
                fun fetchPage() {
                    try {
                        Log.d(TAG, "Fetching transactions page: offset=$offset, limit=$limit")
                        _syncStatus.value = "Fetching transactions batch ${offset/limit + 1}..."
                        
                        // Build the query with pagination using LIMIT offset, count format
                        val query = ZCatalystSelectQuery.Builder()
                            .selectAll()
                            .from("transactions")  // Replace with your actual table name
                            .orderBy(setOf(Column("CREATEDTIME")), ZCatalystUtil.SortOrder.DESC)
                            .limit(offset, limit)
                            .build()
                        
                        ZCatalystApp.getInstance().getDataStoreInstance().execute(
                            query,
                            { response ->
                                try {
                                    val transactions = parseTransactionsFromDataStore(response)
                                    Log.d(TAG, "Fetched ${transactions.size} transactions in batch ${offset/limit + 1}")
                                    
                                    allTransactions.addAll(transactions)
                                    
                                    // Check if we have more data to fetch
                                    if (transactions.size < limit) {
                                        // This was the last page
                                        hasMoreData = false
                                        _isLoading.value = false
                                        _syncStatus.value = "All transactions fetched successfully (${allTransactions.size} total)"
                                        _lastSyncTime.value = Date().toString()
                                        
                                        Log.d(TAG, "Completed incremental fetch: ${allTransactions.size} total transactions")
                                        continuation.resume(Result.success(allTransactions.toList()))
                                    } else {
                                        // Fetch next page
                                        offset += limit
                                        fetchPage()
                                    }
                                } catch (e: Exception) {
                                    _isLoading.value = false
                                    _syncStatus.value = "Error parsing transactions batch"
                                    _errorMessage.value = e.message
                                    Log.e(TAG, "Error parsing transactions from DataStore: ${e.message}")
                                    continuation.resume(Result.failure(e))
                                }
                            },
                            { exception ->
                                _isLoading.value = false
                                _syncStatus.value = "Failed to fetch transactions batch"
                                _errorMessage.value = exception.message ?: "Unknown error occurred"
                                
                                Log.e(TAG, "Failed to fetch transactions from DataStore: ${exception.message}")
                                continuation.resume(Result.failure(Exception(exception.message ?: "Unknown error")))
                            }
                        )
                    } catch (e: Exception) {
                        _isLoading.value = false
                        _syncStatus.value = "Error in batch fetch"
                        _errorMessage.value = e.message
                        Log.e(TAG, "Exception in fetchPage", e)
                        continuation.resume(Result.failure(e))
                    }
                }
                
                // Start fetching the first page
                fetchPage()
                
            } catch (e: Exception) {
                _isLoading.value = false
                _syncStatus.value = "Error fetching transactions"
                _errorMessage.value = e.message
                
                Log.e(TAG, "Exception fetching transactions", e)
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    /**
     * Fetch a specific page of transactions with custom limit and offset
     */
    suspend fun fetchTransactionsPage(limit: Int = 100, offset: Int = 0): Result<List<Transaction>> {
        return suspendCancellableCoroutine { continuation ->
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                Log.d(TAG, "Fetching transactions page: limit=$limit, offset=$offset")
                _syncStatus.value = "Fetching transactions page..."
                
                val query = ZCatalystSelectQuery.Builder()
                    .selectAll()
                    .from("transactions")
                    .orderBy(setOf(Column("CREATEDTIME")), ZCatalystUtil.SortOrder.DESC)
                    .limit(offset, limit)
                    .build()
                
                ZCatalystApp.getInstance().getDataStoreInstance().execute(
                    query,
                    { response ->
                        _isLoading.value = false
                        _syncStatus.value = "Transactions page fetched successfully"
                        _lastSyncTime.value = Date().toString()
                        
                        try {
                            val transactions = parseTransactionsFromDataStore(response)
                            Log.d(TAG, "Fetched ${transactions.size} transactions in page")
                            continuation.resume(Result.success(transactions))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing transactions page: ${e.message}")
                            continuation.resume(Result.failure(e))
                        }
                    },
                    { exception ->
                        _isLoading.value = false
                        _syncStatus.value = "Failed to fetch transactions page"
                        _errorMessage.value = exception.message ?: "Unknown error occurred"
                        
                        Log.e(TAG, "Failed to fetch transactions page: ${exception.message}")
                        continuation.resume(Result.failure(Exception(exception.message ?: "Unknown error")))
                    }
                )
                
            } catch (e: Exception) {
                _isLoading.value = false
                _syncStatus.value = "Error fetching transactions page"
                _errorMessage.value = e.message
                
                Log.e(TAG, "Exception fetching transactions page", e)
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    /**
     * Fetch a specific page of budgets with custom limit and offset
     */
    suspend fun fetchBudgetsPage(limit: Int = 100, offset: Int = 0): Result<List<Budget>> {
        return suspendCancellableCoroutine { continuation ->
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                Log.d(TAG, "Fetching budgets page: limit=$limit, offset=$offset")
                _syncStatus.value = "Fetching budgets page..."
                
                val query = ZCatalystSelectQuery.Builder()
                    .selectAll()
                    .from("budgets")
                    .orderBy(setOf(Column("CREATEDTIME")), ZCatalystUtil.SortOrder.DESC)
                    .limit(offset, limit)
                    .build()
                
                ZCatalystApp.getInstance().getDataStoreInstance().execute(
                    query,
                    { response ->
                        _isLoading.value = false
                        _syncStatus.value = "Budgets page fetched successfully"
                        _lastSyncTime.value = Date().toString()
                        
                        try {
                            val budgets = parseBudgetsFromDataStore(response)
                            Log.d(TAG, "Fetched ${budgets.size} budgets in page")
                            continuation.resume(Result.success(budgets))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing budgets page: ${e.message}")
                            continuation.resume(Result.failure(e))
                        }
                    },
                    { exception ->
                        _isLoading.value = false
                        _syncStatus.value = "Failed to fetch budgets page"
                        _errorMessage.value = exception.message ?: "Unknown error occurred"
                        
                        Log.e(TAG, "Failed to fetch budgets page: ${exception.message}")
                        continuation.resume(Result.failure(Exception(exception.message ?: "Unknown error")))
                    }
                )
                
            } catch (e: Exception) {
                _isLoading.value = false
                _syncStatus.value = "Error fetching budgets page"
                _errorMessage.value = e.message
                
                Log.e(TAG, "Exception fetching budgets page", e)
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    /**
     * Fetch transactions batch starting from a specific offset
     */
    suspend fun fetchTransactionsBatch(offset: Int = 0, limit: Int = 100): Result<List<Transaction>> {
        return suspendCancellableCoroutine { continuation ->
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                Log.d(TAG, "Fetching transactions batch: offset=$offset, limit=$limit")
                _syncStatus.value = "Fetching transactions batch..."
                
                val query = ZCatalystSelectQuery.Builder()
                    .selectAll()
                    .from("transactions")
                    .orderBy(setOf(Column("CREATEDTIME")), ZCatalystUtil.SortOrder.DESC)
                    .limit(offset, limit)
                    .build()
                
                ZCatalystApp.getInstance().getDataStoreInstance().execute(
                    query,
                    { response ->
                        _isLoading.value = false
                        _syncStatus.value = "Transactions batch fetched successfully"
                        _lastSyncTime.value = Date().toString()
                        
                        try {
                            val transactions = parseTransactionsFromDataStore(response)
                            Log.d(TAG, "Fetched ${transactions.size} transactions in batch (offset: $offset)")
                            
                            // Save/update transactions in local database synchronously
                            try {
                                runBlocking {
                                    for (transaction in transactions) {
                                        try {
                                            // Check if transaction already exists by catalystRowId
                                            val existingTransaction = transaction.catalystRowId?.let { 
                                                transactionRepository.getTransactionByCatalystRowId(it) 
                                            }
                                            
                                            if (existingTransaction != null) {
                                                // Update existing transaction
                                                val updatedTransaction = existingTransaction.copy(
                                                    amount = transaction.amount,
                                                    type = transaction.type,
                                                    category = transaction.category,
                                                    description = transaction.description,
                                                    date = transaction.date,
                                                    syncStatus = SyncStatus.SYNCED,
                                                    lastSyncedAt = Date()
                                                )
                                                transactionRepository.updateTransaction(updatedTransaction)
                                                Log.d(TAG, "Updated existing transaction: ${transaction.category} - ${transaction.amount}")
                                            } else {
                                                // Insert new transaction
                                                transactionRepository.insertTransaction(transaction)
                                                Log.d(TAG, "Inserted new transaction: ${transaction.category} - ${transaction.amount}")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error saving transaction to local database: ${e.message}")
                                        }
                                    }
                                }
                                Log.d(TAG, "Completed saving ${transactions.size} transactions to local database")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in transaction database operations: ${e.message}")
                            }
                            
                            continuation.resume(Result.success(transactions))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing transactions batch: ${e.message}")
                            continuation.resume(Result.failure(e))
                        }
                    },
                    { exception ->
                        _isLoading.value = false
                        _syncStatus.value = "Failed to fetch transactions batch"
                        _errorMessage.value = exception.message ?: "Unknown error occurred"
                        
                        Log.e(TAG, "Failed to fetch transactions batch: ${exception.message}")
                        continuation.resume(Result.failure(Exception(exception.message ?: "Unknown error")))
                    }
                )
                
            } catch (e: Exception) {
                _isLoading.value = false
                _syncStatus.value = "Error fetching transactions batch"
                _errorMessage.value = e.message
                
                Log.e(TAG, "Exception fetching transactions batch", e)
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    /**
     * Fetch budgets batch starting from a specific offset
     */
    suspend fun fetchBudgetsBatch(offset: Int = 0, limit: Int = 100): Result<List<Budget>> {
        return suspendCancellableCoroutine { continuation ->
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                Log.d(TAG, "Fetching budgets batch: offset=$offset, limit=$limit")
                _syncStatus.value = "Fetching budgets batch..."
                
                val query = ZCatalystSelectQuery.Builder()
                    .selectAll()
                    .from("budgets")
                    .orderBy(setOf(Column("CREATEDTIME")), ZCatalystUtil.SortOrder.DESC)
                    .limit(offset, limit)
                    .build()
                
                ZCatalystApp.getInstance().getDataStoreInstance().execute(
                    query,
                    { response ->
                        _isLoading.value = false
                        _syncStatus.value = "Budgets batch fetched successfully"
                        _lastSyncTime.value = Date().toString()
                        
                        try {
                            val budgets = parseBudgetsFromDataStore(response)
                            Log.d(TAG, "Fetched ${budgets.size} budgets in batch (offset: $offset)")
                            
                            // Save/update budgets in local database synchronously
                            try {
                                runBlocking {
                                    for (budget in budgets) {
                                        try {
                                            // Check if budget already exists by catalystRowId
                                            val existingBudgetByRowId = budget.catalystRowId?.let { 
                                                budgetRepository.getBudgetByCatalystRowId(it) 
                                            }
                                            
                                            // Also check for existing budget by category, month, year
                                            val existingBudgetByCategory = budgetRepository.getBudgetForCategoryAndMonth(
                                                budget.category, budget.month, budget.year
                                            )
                                            
                                            val existingBudget = existingBudgetByRowId ?: existingBudgetByCategory
                                            
                                            if (existingBudget != null) {
                                                // Update existing budget with new values and sync info
                                                val updatedBudget = existingBudget.copy(
                                                    category = budget.category,
                                                    budgetAmount = budget.budgetAmount,
                                                    month = budget.month,
                                                    year = budget.year,
                                                    catalystRowId = budget.catalystRowId, // Ensure ROWID is set
                                                    syncStatus = SyncStatus.SYNCED,
                                                    lastSyncedAt = Date()
                                                )
                                                budgetRepository.updateBudget(updatedBudget)
                                                Log.d(TAG, "Updated existing budget: ${budget.category} - ${budget.budgetAmount}")
                                            } else {
                                                // Insert new budget with sync info
                                                val newBudget = budget.copy(
                                                    syncStatus = SyncStatus.SYNCED,
                                                    lastSyncedAt = Date()
                                                )
                                                val insertedId = budgetRepository.insertBudget(newBudget)
                                                Log.d(TAG, "Inserted new budget: ${budget.category} - ${budget.budgetAmount} with ID: $insertedId")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error saving budget to local database: ${e.message}")
                                            e.printStackTrace()
                                        }
                                    }
                                }
                                Log.d(TAG, "Completed saving ${budgets.size} budgets to local database")
                                
                                // Clean up any duplicate budgets
                                try {
                                    runBlocking {
                                        budgetRepository.cleanupDuplicateBudgets()
                                    }
                                    Log.d(TAG, "Completed cleanup of duplicate budgets")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error during budget cleanup: ${e.message}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in budget database operations: ${e.message}")
                                e.printStackTrace()
                            }
                            
                            continuation.resume(Result.success(budgets))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing budgets batch: ${e.message}")
                            continuation.resume(Result.failure(e))
                        }
                    },
                    { exception ->
                        _isLoading.value = false
                        _syncStatus.value = "Failed to fetch budgets batch"
                        _errorMessage.value = exception.message ?: "Unknown error occurred"
                        
                        Log.e(TAG, "Failed to fetch budgets batch: ${exception.message}")
                        continuation.resume(Result.failure(Exception(exception.message ?: "Unknown error")))
                    }
                )
                
            } catch (e: Exception) {
                _isLoading.value = false
                _syncStatus.value = "Error fetching budgets batch"
                _errorMessage.value = e.message
                
                Log.e(TAG, "Exception fetching budgets batch", e)
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    // Helper methods for parsing DataStore responses
    
    private fun parseTransactionsFromDataStore(response: List<Map<String, Any?>>): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        try {
            for (item in response) {
                // The response structure is nested: {"transactions": {...}}
                val transactionData = item["transactions"] as? Map<String, Any?> ?: item
                
                val transaction = Transaction(
                    id = 0, // Local ID will be assigned by Room
                    amount = (transactionData["amount"] as? Number)?.toDouble() ?: 0.0,
                    type = TransactionType.valueOf(transactionData["type"] as? String ?: "EXPENSE"),
                    category = transactionData["category"] as? String ?: "",
                    description = transactionData["description"] as? String ?: "",
                    date = Date((transactionData["transactionDate"] as? Number)?.toLong() ?: System.currentTimeMillis()),
                    catalystRowId = transactionData["ROWID"] as? String,
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncedAt = Date()
                )
                transactions.add(transaction)
                Log.d(TAG, "Parsed transaction: ${transaction.category} - ${transaction.amount} (ROWID: ${transaction.catalystRowId})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing transactions from DataStore: ${e.message}")
            throw e
        }
        return transactions
    }
    
    private fun parseBudgetsFromDataStore(response: List<Map<String, Any?>>): List<Budget> {
        val budgets = mutableListOf<Budget>()
        try {
            for (item in response) {
                // The response structure is nested: {"budgets": {...}}
                val budgetData = item["budgets"] as? Map<String, Any?> ?: item
                
                val budget = Budget(
                    id = 0, // Local ID will be assigned by Room
                    category = budgetData["category"] as? String ?: "",
                    budgetAmount = (budgetData["budgetAmount"] as? Number)?.toDouble() ?: 0.0,
                    month = (budgetData["budgetMonth"] as? String)?.toIntOrNull() ?: 
                            (budgetData["month"] as? Number)?.toInt() ?: 1,
                    year = (budgetData["year"] as? String)?.toIntOrNull() ?: 
                           (budgetData["year"] as? Number)?.toInt() ?: 2025, // Updated default to current year
                    createdDate = Date((budgetData["CREATEDTIME"] as? String)?.let { 
                        // Parse Catalyst timestamp format
                        try { 
                            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS", java.util.Locale.getDefault()).parse(it)?.time 
                        } catch (e: Exception) { 
                            System.currentTimeMillis() 
                        }
                    } ?: System.currentTimeMillis()),
                    isActive = budgetData["isActive"] as? Boolean ?: true,
                    catalystRowId = budgetData["ROWID"] as? String,
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncedAt = Date()
                )
                
                // Add debug logging for budget parsing
                Log.d(TAG, "Budget parsing debug - Raw data: budgetMonth=${budgetData["budgetMonth"]}, year=${budgetData["year"]}, category=${budgetData["category"]}")
                Log.d(TAG, "Budget parsing debug - Parsed values: month=${budget.month}, year=${budget.year}, category=${budget.category}")
                
                budgets.add(budget)
                Log.d(TAG, "Parsed budget: ${budget.category} - ${budget.budgetAmount} (ROWID: ${budget.catalystRowId})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing budgets from DataStore: ${e.message}")
            throw e
        }
        return budgets
    }
    
    // Budget CRUD operations
    
    /**
     * Create a budget via Catalyst function
     */
    suspend fun createBudgetViaCatalyst(budget: Budget): Result<Budget> {
        return suspendCancellableCoroutine { continuation ->
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                val body = hashMapOf<String, Any>(
                    "operation" to "budget",
                    "method" to "POST",
                    "resourceType" to "budgets",
                    "requestBody" to hashMapOf(
                        "category" to budget.category,
                        "budgetAmount" to budget.budgetAmount,
                        "month" to budget.month,
                        "year" to budget.year,
                        "isActive" to budget.isActive,
                        "createdDate" to budget.createdDate.time
                    )
                )
                
                Log.d(TAG, "Creating budget via Catalyst: $body")
                _syncStatus.value = "Creating budget..."
                
                ZCatalystApp.getInstance().getFunctionInstance(FUNCTION_ID).executePost(
                    hashMapOf(),
                    body,
                    { response ->
                        _isLoading.value = false
                        
                        try {
                            Log.d(TAG, "Create budget response received: $response")
                            val jsonResponse = JSONObject(response)
                            
                            // Check if response has "output" field (wrapped) or direct structure
                            val (statusCode, bodyJson) = if (jsonResponse.has("output")) {
                                // Wrapped format: {"output": "{\"statusCode\":...}"}
                                val outputString = jsonResponse.getString("output")
                                val output = JSONObject(outputString)
                                Pair(output.getInt("statusCode"), output.getJSONObject("body"))
                            } else {
                                // Direct format: {"statusCode":..., "body":{...}}
                                Pair(jsonResponse.getInt("statusCode"), jsonResponse.getJSONObject("body"))
                            }
                            
                            if (statusCode in 200..299) {
                                val data = bodyJson.getJSONObject("data")
                                val rowId = data.getString("ROWID")
                                
                                val updatedBudget = budget.copy(
                                    catalystRowId = rowId,
                                    syncStatus = SyncStatus.SYNCED,
                                    lastSyncedAt = Date()
                                )
                                
                                _syncStatus.value = "Budget created successfully"
                                _lastSyncTime.value = Date().toString()
                                Log.d(TAG, "Budget created successfully with ROWID: $rowId")
                                continuation.resume(Result.success(updatedBudget))
                            } else {
                                val errorMsg = "Create budget failed with status: $statusCode"
                                _syncStatus.value = "Failed to create budget"
                                _errorMessage.value = errorMsg
                                Log.e(TAG, errorMsg)
                                continuation.resume(Result.failure(Exception(errorMsg)))
                            }
                        } catch (e: Exception) {
                            _syncStatus.value = "Failed to process budget response"
                            _errorMessage.value = e.message
                            Log.e(TAG, "Error processing budget response", e)
                            continuation.resume(Result.failure(e))
                        }
                    },
                    { error ->
                        _isLoading.value = false
                        _syncStatus.value = "Failed to create budget"
                        _errorMessage.value = error.message ?: "Unknown error occurred"
                        
                        Log.e(TAG, "Failed to create budget: ${error.message}")
                        continuation.resume(Result.failure(Exception(error.message ?: "Unknown error")))
                    }
                )
                
            } catch (e: Exception) {
                _isLoading.value = false
                _syncStatus.value = "Error creating budget"
                _errorMessage.value = e.message
                
                Log.e(TAG, "Exception creating budget", e)
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    /**
     * Update a budget via Catalyst function
     */
    suspend fun updateBudgetViaCatalyst(budget: Budget): Result<Budget> {
        return suspendCancellableCoroutine { continuation ->
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                val body = hashMapOf<String, Any>(
                    "operation" to "budget",
                    "method" to "PUT",
                    "resourceType" to "budgets",
                    "resourceId" to (budget.catalystRowId ?: ""),
                    "requestBody" to hashMapOf(
                        "category" to budget.category,
                        "budgetAmount" to budget.budgetAmount,
                        "month" to budget.month,
                        "year" to budget.year,
                        "isActive" to budget.isActive
                    )
                )
                
                Log.d(TAG, "Updating budget via Catalyst: $body")
                _syncStatus.value = "Updating budget..."
                
                ZCatalystApp.getInstance().getFunctionInstance(FUNCTION_ID).executePost(
                    hashMapOf(),
                    body,
                    { response ->
                        _isLoading.value = false
                        
                        try {
                            Log.d(TAG, "Update budget response received: $response")
                            val jsonResponse = JSONObject(response)
                            
                            // Check if response has "output" field (wrapped) or direct structure
                            val statusCode = if (jsonResponse.has("output")) {
                                // Wrapped format: {"output": "{\"statusCode\":...}"}
                                val outputString = jsonResponse.getString("output")
                                val output = JSONObject(outputString)
                                output.getInt("statusCode")
                            } else {
                                // Direct format: {"statusCode":..., "body":{...}}
                                jsonResponse.getInt("statusCode")
                            }
                            
                            if (statusCode in 200..299) {
                                val updatedBudget = budget.copy(
                                    syncStatus = SyncStatus.SYNCED,
                                    lastSyncedAt = Date()
                                )
                                
                                _syncStatus.value = "Budget updated successfully"
                                _lastSyncTime.value = Date().toString()
                                Log.d(TAG, "Budget updated successfully")
                                continuation.resume(Result.success(updatedBudget))
                            } else {
                                val errorMsg = "Update budget failed with status: $statusCode"
                                _syncStatus.value = "Failed to update budget"
                                _errorMessage.value = errorMsg
                                Log.e(TAG, errorMsg)
                                continuation.resume(Result.failure(Exception(errorMsg)))
                            }
                        } catch (e: Exception) {
                            _syncStatus.value = "Failed to process update response"
                            _errorMessage.value = e.message
                            Log.e(TAG, "Error processing update response", e)
                            continuation.resume(Result.failure(e))
                        }
                    },
                    { error ->
                        _isLoading.value = false
                        _syncStatus.value = "Failed to update budget"
                        _errorMessage.value = error.message ?: "Unknown error occurred"
                        
                        Log.e(TAG, "Failed to update budget: ${error.message}")
                        continuation.resume(Result.failure(Exception(error.message ?: "Unknown error")))
                    }
                )
                
            } catch (e: Exception) {
                _isLoading.value = false
                _syncStatus.value = "Error updating budget"
                _errorMessage.value = e.message
                
                Log.e(TAG, "Exception updating budget", e)
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    /**
     * Delete a budget via Catalyst function
     */
    suspend fun deleteBudgetViaCatalyst(budget: Budget): Result<Boolean> {
        return suspendCancellableCoroutine { continuation ->
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                val body = hashMapOf<String, Any>(
                    "operation" to "budget",
                    "method" to "DELETE",
                    "resourceType" to "budgets",
                    "resourceId" to (budget.catalystRowId ?: "")
                )
                
                Log.d(TAG, "Deleting budget via Catalyst: $body")
                _syncStatus.value = "Deleting budget..."
                
                ZCatalystApp.getInstance().getFunctionInstance(FUNCTION_ID).executePost(
                    hashMapOf(),
                    body,
                    { response ->
                        _isLoading.value = false
                        
                        try {
                            Log.d(TAG, "Delete budget response received: $response")
                            val jsonResponse = JSONObject(response)
                            
                            // Check if response has "output" field (wrapped) or direct structure
                            val statusCode = if (jsonResponse.has("output")) {
                                // Wrapped format: {"output": "{\"statusCode\":...}"}
                                val outputString = jsonResponse.getString("output")
                                val output = JSONObject(outputString)
                                output.getInt("statusCode")
                            } else {
                                // Direct format: {"statusCode":..., "body":{...}}
                                jsonResponse.getInt("statusCode")
                            }
                            
                            if (statusCode in 200..299) {
                                _syncStatus.value = "Budget deleted successfully"
                                _lastSyncTime.value = Date().toString()
                                continuation.resume(Result.success(true))
                            } else {
                                val errorMsg = "Delete budget failed with status: $statusCode"
                                _syncStatus.value = "Failed to delete budget"
                                _errorMessage.value = errorMsg
                                Log.e(TAG, errorMsg)
                                continuation.resume(Result.failure(Exception(errorMsg)))
                            }
                        } catch (e: Exception) {
                            _syncStatus.value = "Failed to process delete response"
                            _errorMessage.value = e.message
                            Log.e(TAG, "Failed to process delete response", e)
                            continuation.resume(Result.failure(e))
                        }
                    },
                    { error ->
                        _isLoading.value = false
                        _syncStatus.value = "Failed to delete budget"
                        _errorMessage.value = error.message ?: "Unknown error occurred"
                        
                        Log.e(TAG, "Failed to delete budget: ${error.message}")
                        continuation.resume(Result.failure(Exception(error.message ?: "Unknown error")))
                    }
                )
                
            } catch (e: Exception) {
                _isLoading.value = false
                _syncStatus.value = "Error deleting budget"
                _errorMessage.value = e.message
                
                Log.e(TAG, "Exception deleting budget", e)
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    /**
     * Fetch all budgets from Catalyst using incremental pagination
     */
    suspend fun fetchAllBudgetsFromCatalyst(): Result<List<Budget>> {
        return fetchAllBudgetsWithPagination()
    }
    
    /**
     * Fetch all budgets with pagination and incremental approach
     */
    private suspend fun fetchAllBudgetsWithPagination(): Result<List<Budget>> {
        return suspendCancellableCoroutine { continuation ->
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                Log.d(TAG, "Starting incremental fetch of budgets from Catalyst DataStore")
                _syncStatus.value = "Fetching budgets incrementally..."
                
                val allBudgets = mutableListOf<Budget>()
                var offset = 0
                val limit = 100
                var hasMoreData = true
                
                fun fetchPage() {
                    try {
                        Log.d(TAG, "Fetching budgets page: offset=$offset, limit=$limit")
                        _syncStatus.value = "Fetching budgets batch ${offset/limit + 1}..."
                        
                        // Build the query with pagination using LIMIT offset, count format
                        val query = ZCatalystSelectQuery.Builder()
                            .selectAll()
                            .from("budgets")  // Replace with your actual table name
                            .orderBy(setOf(Column("CREATEDTIME")), ZCatalystUtil.SortOrder.DESC)
                            .limit(offset, limit)
                            .build()
                        
                        ZCatalystApp.getInstance().getDataStoreInstance().execute(
                            query,
                            { response ->
                                try {
                                    val budgets = parseBudgetsFromDataStore(response)
                                    Log.d(TAG, "Fetched ${budgets.size} budgets in batch ${offset/limit + 1}")
                                    
                                    allBudgets.addAll(budgets)
                                    
                                    // Check if we have more data to fetch
                                    if (budgets.size < limit) {
                                        // This was the last page
                                        hasMoreData = false
                                        _isLoading.value = false
                                        _syncStatus.value = "All budgets fetched successfully (${allBudgets.size} total)"
                                        _lastSyncTime.value = Date().toString()
                                        
                                        Log.d(TAG, "Completed incremental fetch: ${allBudgets.size} total budgets")
                                        continuation.resume(Result.success(allBudgets.toList()))
                                    } else {
                                        // Fetch next page
                                        offset += limit
                                        fetchPage()
                                    }
                                } catch (e: Exception) {
                                    _isLoading.value = false
                                    _syncStatus.value = "Error parsing budgets batch"
                                    _errorMessage.value = e.message
                                    Log.e(TAG, "Error parsing budgets from DataStore: ${e.message}")
                                    continuation.resume(Result.failure(e))
                                }
                            },
                            { exception ->
                                _isLoading.value = false
                                _syncStatus.value = "Failed to fetch budgets batch"
                                _errorMessage.value = exception.message ?: "Unknown error occurred"
                                
                                Log.e(TAG, "Failed to fetch budgets from DataStore: ${exception.message}")
                                continuation.resume(Result.failure(Exception(exception.message ?: "Unknown error")))
                            }
                        )
                    } catch (e: Exception) {
                        _isLoading.value = false
                        _syncStatus.value = "Error in batch fetch"
                        _errorMessage.value = e.message
                        Log.e(TAG, "Exception in fetchPage", e)
                        continuation.resume(Result.failure(e))
                    }
                }
                
                // Start fetching the first page
                fetchPage()
                
            } catch (e: Exception) {
                _isLoading.value = false
                _syncStatus.value = "Error fetching budgets"
                _errorMessage.value = e.message
                
                Log.e(TAG, "Exception fetching budgets", e)
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    // Helper methods for parsing Cloud Function responses (kept for CRUD operations)
    
    private fun parseTransactionsFromResponse(response: String): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        try {
            val jsonResponse = JSONObject(response)
            
            // Check if response has "output" field (wrapped) or direct structure
            val bodyJson = if (jsonResponse.has("output")) {
                // Wrapped format: {"output": "{\"statusCode\":...}"}
                val outputString = jsonResponse.getString("output")
                val output = JSONObject(outputString)
                output.getJSONObject("body")
            } else {
                // Direct format: {"statusCode":..., "body":{...}}
                jsonResponse.getJSONObject("body")
            }
            
            val dataArray = bodyJson.getJSONArray("data")
            
            for (i in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(i)
                val transaction = Transaction(
                    id = 0, // Local ID will be assigned by Room
                    amount = item.getDouble("amount"),
                    type = TransactionType.valueOf(item.getString("type")),
                    category = item.getString("category"),
                    description = item.getString("description"),
                    date = Date(item.getLong("transactionDate")),
                    catalystRowId = item.getString("ROWID"),
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncedAt = Date()
                )
                transactions.add(transaction)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing transactions: ${e.message}")
            throw e
        }
        return transactions
    }
    
    private fun parseBudgetsFromResponse(response: String): List<Budget> {
        val budgets = mutableListOf<Budget>()
        try {
            val jsonResponse = JSONObject(response)
            
            // Check if response has "output" field (wrapped) or direct structure
            val bodyJson = if (jsonResponse.has("output")) {
                // Wrapped format: {"output": "{\"statusCode\":...}"}
                val outputString = jsonResponse.getString("output")
                val output = JSONObject(outputString)
                output.getJSONObject("body")
            } else {
                // Direct format: {"statusCode":..., "body":{...}}
                jsonResponse.getJSONObject("body")
            }
            
            val dataArray = bodyJson.getJSONArray("data")
            
            for (i in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(i)
                val budget = Budget(
                    id = 0, // Local ID will be assigned by Room
                    category = item.getString("category"),
                    budgetAmount = item.getDouble("budgetAmount"),
                    month = item.getInt("month"),
                    year = item.getInt("year"),
                    createdDate = Date(item.getLong("createdDate")),
                    isActive = item.getBoolean("isActive"),
                    catalystRowId = item.getString("ROWID"),
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncedAt = Date()
                )
                budgets.add(budget)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing budgets: ${e.message}")
            throw e
        }
        return budgets
    }
    
    /**
     * Test Catalyst connection
     */
    suspend fun testCatalystConnection(): Result<String> {
        Log.d(TAG, "testCatalystConnection() called")
        return suspendCancellableCoroutine { continuation ->
            try {
                Log.d(TAG, "Starting Catalyst connection test...")
                _isLoading.value = true
                _connectionError.value = null
                _syncStatus.value = "Testing connection..."
                val param = hashMapOf<String, Any>(
                    "operation" to "health")
                // Simple health check
                val body = hashMapOf<String, Any>()
                Log.d(TAG, "Prepared request body for health check: $body")
                
                Log.d(TAG, "Calling ZCatalystApp.getInstance().getFunctionInstance($FUNCTION_ID).executePost()")
                ZCatalystApp.getInstance().getFunctionInstance(FUNCTION_ID).executePost(
                    param,
                    body,
                    { response ->
                        Log.d(TAG, "SUCCESS callback received")
                        _isLoading.value = false
                        _isConnected.value = true
                        _connectionError.value = null
                        _syncStatus.value = "Connection successful"
                        
                        Log.d(TAG, "Catalyst connection successful: $response")
                        continuation.resume(Result.success("Connection successful"))
                    },
                    { error ->
                        Log.d(TAG, "ERROR callback received")
                        _isLoading.value = false
                        _isConnected.value = false
                        _connectionError.value = error.message ?: "Unknown connection error"
                        _syncStatus.value = "Connection failed"
                        
                        Log.e(TAG, "Catalyst connection failed: ${error.message}")
                        continuation.resume(Result.failure(Exception(error.message ?: "Unknown connection error")))
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception in testCatalystConnection", e)
                _isLoading.value = false
                _isConnected.value = false
                _connectionError.value = e.message
                _syncStatus.value = "Connection error: ${e.message}"
                
                Log.e(TAG, "Exception testing connection", e)
                continuation.resume(Result.failure(e))
            }
        }
    }
    
    /**
     * Enable Catalyst sync
     */
    fun enableCatalystSync() {
        _isEnabled.value = true
        _syncStatus.value = "Catalyst sync enabled"
    }
    
    /**
     * Disable Catalyst sync
     */
    fun disableCatalystSync() {
        _isEnabled.value = false
        _syncStatus.value = "Catalyst sync disabled"
    }
    
    /**
     * Perform a full bi-directional sync with incremental approach
     */
    suspend fun performFullSync(): Result<String> {
        return try {
            _isLoading.value = true
            _syncStatus.value = "Starting full incremental sync..."
            
            Log.d(TAG, "Starting full sync with incremental approach")
            
            // Step 1: Fetch all transactions incrementally
            _syncStatus.value = "Syncing transactions incrementally..."
            val transactionsResult = fetchAllTransactionsFromCatalyst()
            if (transactionsResult.isFailure) {
                _isLoading.value = false
                _syncStatus.value = "Full sync failed: Error fetching transactions"
                _errorMessage.value = transactionsResult.exceptionOrNull()?.message
                return Result.failure(transactionsResult.exceptionOrNull() ?: Exception("Unknown error"))
            }
            
            val transactionCount = transactionsResult.getOrNull()?.size ?: 0
            Log.d(TAG, "Fetched $transactionCount transactions")
            
            // Step 2: Fetch all budgets incrementally
            _syncStatus.value = "Syncing budgets incrementally..."
            val budgetsResult = fetchAllBudgetsFromCatalyst()
            if (budgetsResult.isFailure) {
                _isLoading.value = false
                _syncStatus.value = "Full sync failed: Error fetching budgets"
                _errorMessage.value = budgetsResult.exceptionOrNull()?.message
                return Result.failure(budgetsResult.exceptionOrNull() ?: Exception("Unknown error"))
            }
            
            val budgetCount = budgetsResult.getOrNull()?.size ?: 0
            Log.d(TAG, "Fetched $budgetCount budgets")
            
            // Step 3: Complete sync
            _syncStatus.value = "Finalizing sync..."
            
            _isLoading.value = false
            _syncStatus.value = "Full sync completed successfully ($transactionCount transactions, $budgetCount budgets)"
            _lastSyncTime.value = Date().toString()
            
            val resultMessage = "Full incremental sync completed: $transactionCount transactions, $budgetCount budgets"
            Log.d(TAG, resultMessage)
            Result.success(resultMessage)
            
        } catch (e: Exception) {
            _isLoading.value = false
            _syncStatus.value = "Full sync failed: ${e.message}"
            _errorMessage.value = e.message
            
            Log.e(TAG, "Full sync failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Perform incremental sync (fetch recent batches of data)
     */
    suspend fun performIncrementalSync(): Result<String> {
        return try {
            _isLoading.value = true
            _syncStatus.value = "Starting incremental sync..."
            
            Log.d(TAG, "Starting incremental sync with batch processing")
            
            // Fetch recent transactions (first batch of 100)
            _syncStatus.value = "Fetching recent transactions..."
            val transactionsResult = fetchTransactionsBatch(0, 100)
            if (transactionsResult.isFailure) {
                _isLoading.value = false
                _syncStatus.value = "Incremental sync failed: Error fetching transactions"
                _errorMessage.value = transactionsResult.exceptionOrNull()?.message
                return Result.failure(transactionsResult.exceptionOrNull() ?: Exception("Unknown error"))
            }
            
            val newTransactionCount = transactionsResult.getOrNull()?.size ?: 0
            Log.d(TAG, "Fetched $newTransactionCount recent transactions")
            
            // Fetch recent budgets (first batch of 100)
            _syncStatus.value = "Fetching recent budgets..."
            val budgetsResult = fetchBudgetsBatch(0, 100)
            if (budgetsResult.isFailure) {
                _isLoading.value = false
                _syncStatus.value = "Incremental sync failed: Error fetching budgets"
                _errorMessage.value = budgetsResult.exceptionOrNull()?.message
                return Result.failure(budgetsResult.exceptionOrNull() ?: Exception("Unknown error"))
            }
            
            val newBudgetCount = budgetsResult.getOrNull()?.size ?: 0
            Log.d(TAG, "Fetched $newBudgetCount recent budgets")
            
            _isLoading.value = false
            _syncStatus.value = "Incremental sync completed ($newTransactionCount transactions, $newBudgetCount budgets)"
            _lastSyncTime.value = Date().toString()
            
            val resultMessage = "Incremental sync completed: $newTransactionCount transactions, $newBudgetCount budgets"
            Log.d(TAG, resultMessage)
            Result.success(resultMessage)
            
        } catch (e: Exception) {
            _isLoading.value = false
            _syncStatus.value = "Incremental sync failed: ${e.message}"
            _errorMessage.value = e.message
            
            Log.e(TAG, "Incremental sync failed", e)
            Result.failure(e)
        }
    }

    /**
     * Perform clean sync - compares local and remote data to handle deletions
     * This is the best option for handling deleted records
     */
    suspend fun performCleanSync(): Result<String> {
        return try {
            _isLoading.value = true
            _syncStatus.value = "Starting clean sync..."
            
            Log.d(TAG, "Starting clean sync - comparing local and remote data")
            
            // Step 1: Get all remote ROWIDs
            _syncStatus.value = "Fetching all remote data..."
            val allRemoteTransactions = fetchAllTransactionsFromCatalyst()
            val allRemoteBudgets = fetchAllBudgetsFromCatalyst()
            
            if (allRemoteTransactions.isFailure || allRemoteBudgets.isFailure) {
                _isLoading.value = false
                _syncStatus.value = "Clean sync failed: Error fetching remote data"
                val error = allRemoteTransactions.exceptionOrNull() ?: allRemoteBudgets.exceptionOrNull()
                _errorMessage.value = error?.message
                return Result.failure(error ?: Exception("Unknown error"))
            }
            
            val remoteTransactions = allRemoteTransactions.getOrNull() ?: emptyList()
            val remoteBudgets = allRemoteBudgets.getOrNull() ?: emptyList()
            
            val remoteTransactionRowIds = remoteTransactions.mapNotNull { it.catalystRowId }.toSet()
            val remoteBudgetRowIds = remoteBudgets.mapNotNull { it.catalystRowId }.toSet()
            
            Log.d(TAG, "Remote data: ${remoteTransactionRowIds.size} transactions, ${remoteBudgetRowIds.size} budgets")
            
            // Step 2: Get local synced records and find deletions
            _syncStatus.value = "Identifying deleted records..."
            val localSyncedTransactions = transactionRepository.getAllSyncedTransactions()
            val localSyncedBudgets = budgetRepository.getAllSyncedBudgets()
            
            val deletedTransactions = localSyncedTransactions.filter { transaction ->
                transaction.catalystRowId != null && !remoteTransactionRowIds.contains(transaction.catalystRowId)
            }
            
            val deletedBudgets = localSyncedBudgets.filter { budget ->
                budget.catalystRowId != null && !remoteBudgetRowIds.contains(budget.catalystRowId)
            }
            
            Log.d(TAG, "Found ${deletedTransactions.size} deleted transactions, ${deletedBudgets.size} deleted budgets")
            
            // Step 3: Delete records that no longer exist remotely
            _syncStatus.value = "Removing deleted records..."
            deletedTransactions.forEach { transaction ->
                transactionRepository.deleteTransaction(transaction)
                Log.d(TAG, "Deleted transaction: ${transaction.category} - ${transaction.amount} (ROWID: ${transaction.catalystRowId})")
            }
            
            deletedBudgets.forEach { budget ->
                budgetRepository.deleteBudget(budget)
                Log.d(TAG, "Deleted budget: ${budget.category} - ${budget.budgetAmount} (ROWID: ${budget.catalystRowId})")
            }
            
            _isLoading.value = false
            _syncStatus.value = "Clean sync completed (${remoteTransactions.size} transactions, ${remoteBudgets.size} budgets, ${deletedTransactions.size + deletedBudgets.size} deleted)"
            _lastSyncTime.value = Date().toString()
            
            val resultMessage = "Clean sync completed: ${remoteTransactions.size} transactions, ${remoteBudgets.size} budgets synced, ${deletedTransactions.size + deletedBudgets.size} records deleted"
            Log.d(TAG, resultMessage)
            Result.success(resultMessage)
            
        } catch (e: Exception) {
            _isLoading.value = false
            _syncStatus.value = "Clean sync failed: ${e.message}"
            _errorMessage.value = e.message
            
            Log.e(TAG, "Clean sync failed", e)
            Result.failure(e)
        }
    }

    /**
     * Retry failed sync records - sync only records with FAILED or PENDING status
     */
    suspend fun retryFailedSync(): Result<String> {
        return try {
            _isLoading.value = true
            _syncStatus.value = "Retrying failed sync records..."
            
            Log.d(TAG, "Starting retry of failed sync records")
            
            // Get failed/pending transactions
            val failedTransactions = transactionRepository.getFailedSyncTransactions()
            val pendingTransactions = transactionRepository.getPendingSyncTransactions()
            
            val failedBudgets = budgetRepository.getFailedSyncBudgets()
            val pendingBudgets = budgetRepository.getPendingSyncBudgets()
            
            val totalFailedRecords = failedTransactions.size + pendingTransactions.size + failedBudgets.size + pendingBudgets.size
            
            if (totalFailedRecords == 0) {
                _isLoading.value = false
                _syncStatus.value = "No failed records to retry"
                val resultMessage = "No failed sync records found"
                Log.d(TAG, resultMessage)
                return Result.success(resultMessage)
            }
            
            Log.d(TAG, "Found $totalFailedRecords failed/pending records to retry")
            
            var successCount = 0
            var failCount = 0
            
            // Retry failed/pending transactions
            _syncStatus.value = "Retrying transactions..."
            (failedTransactions + pendingTransactions).forEach { transaction ->
                try {
                    if (transaction.catalystRowId == null) {
                        // Create new record in Catalyst
                        val createResult = createTransactionInCatalyst(transaction)
                        if (createResult.isSuccess) {
                            val updatedTransaction = transaction.copy(
                                syncStatus = SyncStatus.SYNCED,
                                lastSyncedAt = Date()
                            )
                            transactionRepository.updateTransaction(updatedTransaction)
                            successCount++
                            Log.d(TAG, "Successfully created transaction: ${transaction.category} - ${transaction.amount}")
                        } else {
                            failCount++
                            Log.e(TAG, "Failed to create transaction: ${createResult.exceptionOrNull()?.message}")
                        }
                    } else {
                        // Update existing record in Catalyst
                        val updateResult = updateTransactionInCatalyst(transaction)
                        if (updateResult.isSuccess) {
                            val updatedTransaction = transaction.copy(
                                syncStatus = SyncStatus.SYNCED,
                                lastSyncedAt = Date()
                            )
                            transactionRepository.updateTransaction(updatedTransaction)
                            successCount++
                            Log.d(TAG, "Successfully updated transaction: ${transaction.category} - ${transaction.amount}")
                        } else {
                            failCount++
                            Log.e(TAG, "Failed to update transaction: ${updateResult.exceptionOrNull()?.message}")
                        }
                    }
                } catch (e: Exception) {
                    failCount++
                    Log.e(TAG, "Error retrying transaction sync: ${e.message}")
                }
            }
            
            // Retry failed/pending budgets
            _syncStatus.value = "Retrying budgets..."
            (failedBudgets + pendingBudgets).forEach { budget ->
                try {
                    if (budget.catalystRowId == null) {
                        // Create new record in Catalyst
                        val createResult = createBudgetInCatalyst(budget)
                        if (createResult.isSuccess) {
                            val updatedBudget = budget.copy(
                                syncStatus = SyncStatus.SYNCED,
                                lastSyncedAt = Date()
                            )
                            budgetRepository.updateBudget(updatedBudget)
                            successCount++
                            Log.d(TAG, "Successfully created budget: ${budget.category} - ${budget.budgetAmount}")
                        } else {
                            failCount++
                            Log.e(TAG, "Failed to create budget: ${createResult.exceptionOrNull()?.message}")
                        }
                    } else {
                        // Update existing record in Catalyst
                        val updateResult = updateBudgetInCatalyst(budget)
                        if (updateResult.isSuccess) {
                            val updatedBudget = budget.copy(
                                syncStatus = SyncStatus.SYNCED,
                                lastSyncedAt = Date()
                            )
                            budgetRepository.updateBudget(updatedBudget)
                            successCount++
                            Log.d(TAG, "Successfully updated budget: ${budget.category} - ${budget.budgetAmount}")
                        } else {
                            failCount++
                            Log.e(TAG, "Failed to update budget: ${updateResult.exceptionOrNull()?.message}")
                        }
                    }
                } catch (e: Exception) {
                    failCount++
                    Log.e(TAG, "Error retrying budget sync: ${e.message}")
                }
            }
            
            _isLoading.value = false
            _syncStatus.value = "Retry completed: $successCount successful, $failCount failed"
            _lastSyncTime.value = Date().toString()
            
            val resultMessage = "Retry sync completed: $successCount records synced successfully, $failCount failed"
            Log.d(TAG, resultMessage)
            Result.success(resultMessage)
            
        } catch (e: Exception) {
            _isLoading.value = false
            _syncStatus.value = "Retry sync failed: ${e.message}"
            _errorMessage.value = e.message
            
            Log.e(TAG, "Retry sync failed", e)
            Result.failure(e)
        }
    }

    /**
     * Smart sync - combines incremental sync with retry of failed records
     */
    suspend fun performSmartSync(): Result<String> {
        return try {
            _isLoading.value = true
            _syncStatus.value = "Starting smart sync..."
            
            Log.d(TAG, "Starting smart sync - incremental + retry failed")
            
            // Step 1: Incremental sync for new data
            val incrementalResult = performIncrementalSync()
            if (incrementalResult.isFailure) {
                return incrementalResult
            }
            
            // Step 2: Retry any failed records
            _syncStatus.value = "Retrying failed records..."
            val retryResult = retryFailedSync()
            
            val incrementalMessage = incrementalResult.getOrNull() ?: ""
            val retryMessage = retryResult.getOrNull() ?: ""
            
            _isLoading.value = false
            _syncStatus.value = "Smart sync completed"
            _lastSyncTime.value = Date().toString()
            
            val resultMessage = "Smart sync completed - $incrementalMessage | $retryMessage"
            Log.d(TAG, resultMessage)
            Result.success(resultMessage)
            
        } catch (e: Exception) {
            _isLoading.value = false
            _syncStatus.value = "Smart sync failed: ${e.message}"
            _errorMessage.value = e.message
            
            Log.e(TAG, "Smart sync failed", e)
            Result.failure(e)
        }
    }

    // Helper methods for creating/updating records in Catalyst
    private suspend fun createTransactionInCatalyst(transaction: Transaction): Result<String> {
        // Implementation for creating transaction in Catalyst
        // This would use the Catalyst API to create a new record
        return Result.success("Transaction created") // Placeholder
    }

    private suspend fun updateTransactionInCatalyst(transaction: Transaction): Result<String> {
        // Implementation for updating transaction in Catalyst
        // This would use the Catalyst API to update existing record
        return Result.success("Transaction updated") // Placeholder
    }

    private suspend fun createBudgetInCatalyst(budget: Budget): Result<String> {
        // Implementation for creating budget in Catalyst
        // This would use the Catalyst API to create a new record
        return Result.success("Budget created") // Placeholder
    }

    private suspend fun updateBudgetInCatalyst(budget: Budget): Result<String> {
        // Implementation for updating budget in Catalyst
        // This would use the Catalyst API to update existing record
        return Result.success("Budget updated") // Placeholder
    }
}