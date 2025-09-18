package com.ssk.myfinancehub.utils

import android.content.Context
import android.content.SharedPreferences
import com.ssk.myfinancehub.data.model.Currency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CurrencyManager {
    private const val PREFS_NAME = "currency_prefs"
    private const val KEY_SELECTED_CURRENCY = "selected_currency"
    
    private lateinit var prefs: SharedPreferences
    private val _selectedCurrency = MutableStateFlow(Currency.DEFAULT)
    val selectedCurrency: StateFlow<Currency> = _selectedCurrency.asStateFlow()
    
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSavedCurrency()
    }
    
    private fun loadSavedCurrency() {
        val savedCurrencyCode = prefs.getString(KEY_SELECTED_CURRENCY, Currency.DEFAULT.code)
        val currency = Currency.fromCode(savedCurrencyCode ?: Currency.DEFAULT.code)
        _selectedCurrency.value = currency
    }
    
    fun setCurrency(currency: Currency) {
        _selectedCurrency.value = currency
        prefs.edit()
            .putString(KEY_SELECTED_CURRENCY, currency.code)
            .apply()
    }
    
    fun getCurrentCurrency(): Currency {
        return _selectedCurrency.value
    }
    
    fun formatAmount(amount: Double, currency: Currency? = null): String {
        val currencyToUse = currency ?: getCurrentCurrency()
        return CurrencyFormatter.format(amount, currencyToUse)
    }
    
    fun formatAmountWithCurrentCurrency(amount: Double): String {
        return formatAmount(amount, getCurrentCurrency())
    }
    
    fun getCurrentSymbol(): String {
        return getCurrentCurrency().symbol
    }
}
