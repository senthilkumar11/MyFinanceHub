package com.ssk.myfinancehub.data.model

enum class Currency(
    val code: String,
    val symbol: String,
    val displayName: String
) {
    INR("INR", "₹", "Indian Rupee"),
    USD("USD", "$", "US Dollar"),
    EUR("EUR", "€", "Euro"),
    GBP("GBP", "£", "British Pound"),
    JPY("JPY", "¥", "Japanese Yen"),
    AUD("AUD", "A$", "Australian Dollar"),
    CAD("CAD", "C$", "Canadian Dollar"),
    CHF("CHF", "CHF", "Swiss Franc"),
    CNY("CNY", "¥", "Chinese Yuan"),
    SGD("SGD", "S$", "Singapore Dollar");

    companion object {
        val DEFAULT = INR
        
        fun fromCode(code: String): Currency {
            return values().find { it.code == code } ?: DEFAULT
        }
    }
}
