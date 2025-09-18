package com.ssk.myfinancehub.utils

import com.ssk.myfinancehub.data.model.Currency
import java.text.NumberFormat
import java.util.*

object CurrencyFormatter {
    
    fun format(amount: Double, currency: Currency? = null): String {
        val currencyToUse = currency ?: CurrencyManager.getCurrentCurrency()
        return when (currencyToUse) {
            Currency.INR -> {
                // Indian number formatting
                val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
                formatter.format(amount)
            }
            Currency.USD -> {
                val formatter = NumberFormat.getCurrencyInstance(Locale.US)
                formatter.format(amount)
            }
            Currency.EUR -> {
                val formatter = NumberFormat.getCurrencyInstance(Locale.GERMANY)
                formatter.format(amount)
            }
            Currency.GBP -> {
                val formatter = NumberFormat.getCurrencyInstance(Locale.UK)
                formatter.format(amount)
            }
            else -> {
                "${currencyToUse.symbol}${String.format("%.2f", amount)}"
            }
        }
    }
    
    fun formatWithoutSymbol(amount: Double): String {
        return String.format("%.2f", amount)
    }
    
    fun getSymbol(currency: Currency? = null): String {
        val currencyToUse = currency ?: CurrencyManager.getCurrentCurrency()
        return currencyToUse.symbol
    }
}
