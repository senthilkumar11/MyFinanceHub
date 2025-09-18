package com.ssk.myfinancehub.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssk.myfinancehub.data.database.FinanceDatabase
import com.ssk.myfinancehub.data.model.Transaction
import com.ssk.myfinancehub.data.model.TransactionType
import com.ssk.myfinancehub.data.repository.TransactionRepository
import com.ssk.myfinancehub.data.repository.BudgetRepository
import com.ssk.myfinancehub.repository.SyncRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TransactionViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: TransactionRepository
    private val budgetRepository: BudgetRepository
    private val syncRepository: SyncRepository
    
    init {
        val database = FinanceDatabase.getDatabase(application)
        repository = TransactionRepository(database.transactionDao())
        budgetRepository = BudgetRepository(database.budgetDao())
        syncRepository = SyncRepository.getInstance(repository, budgetRepository)
    }
    
    val allTransactions: Flow<List<Transaction>> = repository.getAllTransactions()
    
    val totalIncome: Flow<Double> = repository.getTotalIncome().map { it ?: 0.0 }
    val totalExpense: Flow<Double> = repository.getTotalExpense().map { it ?: 0.0 }
    val balance: Flow<Double> = combine(totalIncome, totalExpense) { income, expense ->
        income - expense
    }
    
    // Sync repository states
    val syncStatus: StateFlow<String> = syncRepository.syncStatus
    val isLoading: StateFlow<Boolean> = syncRepository.isLoading
    val errorMessage: StateFlow<String?> = syncRepository.errorMessage
    val isConnected: StateFlow<Boolean> = syncRepository.isConnected
    val connectionError: StateFlow<String?> = syncRepository.connectionError
    val lastSyncTime: StateFlow<String?> = syncRepository.lastSyncTime
    val isEnabled: StateFlow<Boolean> = syncRepository.isEnabled
    
    data class FinancialSummary(
        val totalIncome: Double = 0.0,
        val totalExpense: Double = 0.0,
        val balance: Double = 0.0
    )
    
    val financialSummary: Flow<FinancialSummary> = combine(
        totalIncome,
        totalExpense,
        balance
    ) { income, expense, bal ->
        FinancialSummary(income, expense, bal)
    }
    
    fun insertTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                // First save locally
                val localId = repository.insertTransaction(transaction)
                Log.d("TransactionViewModel", "Transaction saved locally with ID $localId: ${transaction.category} - ${transaction.amount}")
                
                // Then sync to Catalyst if enabled
                if (syncRepository.isEnabled.value) {
                    val result = syncRepository.createTransactionViaCatalyst(transaction)
                    result.fold(
                        onSuccess = { syncedTransaction ->
                            // Update local record with ROWID and sync status
                            repository.updateTransaction(syncedTransaction.copy(id = localId))
                            Log.d("TransactionViewModel", "Transaction synced to Catalyst with ROWID: ${syncedTransaction.catalystRowId}")
                        },
                        onFailure = { error ->
                            Log.e("TransactionViewModel", "Failed to sync to Catalyst: ${error.message}")
                            // Mark transaction as sync failed
                            repository.updateTransaction(transaction.copy(
                                id = localId,
                                syncStatus = com.ssk.myfinancehub.data.model.SyncStatus.SYNC_FAILED
                            ))
                        }
                    )
                }
                
            } catch (e: Exception) {
                Log.e("TransactionViewModel", "Failed to insert transaction", e)
            }
        }
    }
    
    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                // Update locally
                repository.updateTransaction(transaction)
                Log.d("TransactionViewModel", "Transaction updated locally: ${transaction.category} - ${transaction.amount}")
                
                // Sync to Catalyst if enabled and has ROWID
                if (syncRepository.isEnabled.value && transaction.catalystRowId != null) {
                    val result = syncRepository.updateTransactionViaCatalyst(transaction)
                    result.fold(
                        onSuccess = { syncedTransaction ->
                            // Update sync status
                            repository.updateTransaction(syncedTransaction)
                            Log.d("TransactionViewModel", "Transaction updated in Catalyst")
                        },
                        onFailure = { error ->
                            Log.e("TransactionViewModel", "Failed to update in Catalyst: ${error.message}")
                            // Mark as sync pending
                            repository.updateTransaction(transaction.copy(
                                syncStatus = com.ssk.myfinancehub.data.model.SyncStatus.SYNC_FAILED
                            ))
                        }
                    )
                }
                
            } catch (e: Exception) {
                Log.e("TransactionViewModel", "Failed to update transaction", e)
            }
        }
    }
    
    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                // Delete from Catalyst first if it has ROWID
                if (syncRepository.isEnabled.value && transaction.catalystRowId != null) {
                    val result = syncRepository.deleteTransactionViaCatalyst(transaction)
                    result.fold(
                        onSuccess = {
                            // Delete locally after successful Catalyst deletion
                            repository.deleteTransaction(transaction)
                            Log.d("TransactionViewModel", "Transaction deleted from both Catalyst and local")
                        },
                        onFailure = { error ->
                            Log.e("TransactionViewModel", "Failed to delete from Catalyst: ${error.message}")
                            // Still delete locally but mark as sync issue
                            repository.deleteTransaction(transaction)
                        }
                    )
                } else {
                    // Just delete locally if no ROWID or sync disabled
                    repository.deleteTransaction(transaction)
                    Log.d("TransactionViewModel", "Transaction deleted locally")
                }
                
            } catch (e: Exception) {
                Log.e("TransactionViewModel", "Failed to delete transaction", e)
            }
        }
    }
    
    fun deleteTransactionById(id: Long) {
        viewModelScope.launch {
            repository.deleteTransactionById(id)
        }
    }
    
    suspend fun getTransactionById(id: Long): Transaction? {
        return repository.getTransactionById(id)
    }
    
    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> {
        return repository.getTransactionsByType(type)
    }
    
    // Catalyst sync methods
    fun testCatalystConnection() {
        Log.d("TransactionViewModel", "testCatalystConnection() called in ViewModel")
        viewModelScope.launch {
            Log.d("TransactionViewModel", "Launching coroutine for testCatalystConnection")
            try {
                val result = syncRepository.testCatalystConnection()
                Log.d("TransactionViewModel", "testCatalystConnection result: $result")
            } catch (e: Exception) {
                Log.e("TransactionViewModel", "Exception in testCatalystConnection", e)
            }
        }
    }
    
    fun enableCatalystSync() {
        syncRepository.enableCatalystSync()
    }
    
    fun disableCatalystSync() {
        syncRepository.disableCatalystSync()
    }
    
    fun performFullSync() {
        viewModelScope.launch {
            syncRepository.performFullSync()
        }
    }
    
    fun clearErrorMessage() {
        syncRepository.clearErrorMessage()
    }
    
    // Sync-specific methods for handling cloud data
    suspend fun insertTransactionFromSync(transaction: Transaction) {
        // Insert/update transaction from cloud sync without triggering another sync
        try {
            // Check if transaction already exists by catalystRowId to avoid duplicates
            val existingTransaction = transaction.catalystRowId?.let { 
                repository.getTransactionByCatalystRowId(it) 
            }
            
            if (existingTransaction == null) {
                // Insert new transaction from cloud
                repository.insertTransaction(transaction)
                Log.d("TransactionViewModel", "Inserted new transaction from sync: ${transaction.category} (ROWID: ${transaction.catalystRowId})")
            } else {
                // Update existing transaction if cloud version is newer
                if (transaction.lastSyncedAt != null && 
                    (existingTransaction.lastSyncedAt == null || 
                     transaction.lastSyncedAt!! > existingTransaction.lastSyncedAt!!)) {
                    val updatedTransaction = transaction.copy(id = existingTransaction.id)
                    repository.updateTransaction(updatedTransaction)
                    Log.d("TransactionViewModel", "Updated existing transaction from sync: ${transaction.category} (ROWID: ${transaction.catalystRowId})")
                } else {
                    Log.d("TransactionViewModel", "Skipped sync for transaction ${transaction.category} - local version is newer or same")
                }
            }
        } catch (e: Exception) {
            Log.e("TransactionViewModel", "Failed to insert/update transaction from sync", e)
        }
    }
}
