package com.ssk.myfinancehub.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssk.myfinancehub.data.database.FinanceDatabase
import com.ssk.myfinancehub.data.model.Transaction
import com.ssk.myfinancehub.data.model.TransactionType
import com.ssk.myfinancehub.data.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TransactionViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: TransactionRepository
    
    init {
        val database = FinanceDatabase.getDatabase(application)
        repository = TransactionRepository(database.transactionDao())
    }
    
    val allTransactions: Flow<List<Transaction>> = repository.getAllTransactions()
    
    val totalIncome: Flow<Double> = repository.getTotalIncome().map { it ?: 0.0 }
    val totalExpense: Flow<Double> = repository.getTotalExpense().map { it ?: 0.0 }
    val balance: Flow<Double> = combine(totalIncome, totalExpense) { income, expense ->
        income - expense
    }
    
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
            repository.insertTransaction(transaction)
        }
    }
    
    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
        }
    }
    
    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
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
}
