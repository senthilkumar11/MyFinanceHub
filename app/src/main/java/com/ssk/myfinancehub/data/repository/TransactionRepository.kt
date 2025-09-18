package com.ssk.myfinancehub.data.repository

import com.ssk.myfinancehub.data.dao.TransactionDao
import com.ssk.myfinancehub.data.model.SyncStatus
import com.ssk.myfinancehub.data.model.Transaction
import com.ssk.myfinancehub.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {
    
    fun getAllTransactions(): Flow<List<Transaction>> = transactionDao.getAllTransactions()
    
    suspend fun getTransactionById(id: Long): Transaction? = transactionDao.getTransactionById(id)
    
    suspend fun getTransactionByCatalystRowId(catalystRowId: String): Transaction? = 
        transactionDao.getTransactionByCatalystRowId(catalystRowId)
    
    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> = 
        transactionDao.getTransactionsByType(type)
    
    fun getTotalIncome(): Flow<Double?> = transactionDao.getTotalIncome()
    
    fun getTotalExpense(): Flow<Double?> = transactionDao.getTotalExpense()
    
    suspend fun insertTransaction(transaction: Transaction): Long = 
        transactionDao.insertTransaction(transaction)
    
    suspend fun updateTransaction(transaction: Transaction) = 
        transactionDao.updateTransaction(transaction)
    
    suspend fun deleteTransaction(transaction: Transaction) = 
        transactionDao.deleteTransaction(transaction)
    
    suspend fun deleteTransactionById(id: Long) = 
        transactionDao.deleteTransactionById(id)
    
    // Sync-related methods for enhanced sync strategies
    suspend fun getAllSyncedTransactions(): List<Transaction> = 
        transactionDao.getTransactionsBySyncStatus(SyncStatus.SYNCED)
    
    suspend fun getFailedSyncTransactions(): List<Transaction> = 
        transactionDao.getTransactionsBySyncStatus(SyncStatus.SYNC_FAILED)
    
    suspend fun getPendingSyncTransactions(): List<Transaction> = 
        transactionDao.getTransactionsBySyncStatus(SyncStatus.SYNC_PENDING)
}
