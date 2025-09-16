package com.ssk.myfinancehub.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

object ThemeManager {
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode
    
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }
}

@Composable
fun ProvideThemeManager(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalThemeManager provides ThemeManager,
        content = content
    )
}

val LocalThemeManager = compositionLocalOf<ThemeManager> {
    error("No ThemeManager provided")
}
