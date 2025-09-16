package com.ssk.myfinancehub.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import com.zoho.catalyst.org.ZCatalystUser
import com.zoho.catalyst.setup.ZCatalystApp
import java.util.HashMap

class AuthManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: AuthManager? = null
        
        fun getInstance(context: Context): AuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    
    private val _isLoggedIn = mutableStateOf(getStoredLoginState())
    val isLoggedIn: State<Boolean> = _isLoggedIn
    
    private fun getStoredLoginState(): Boolean {
        // Check both SharedPreferences and Zoho Catalyst state
        val storedState = prefs.getBoolean("is_logged_in", false)
        return try {
            storedState && ZCatalystApp.getInstance().isUserSignedIn()
        } catch (e: Exception) {
            // If Zoho SDK is not initialized yet, fall back to stored state
            storedState
        }
    }
    
    fun login(
        customParams: Map<String, Any>? = null,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            ZCatalystApp.getInstance().login(
                customParams = customParams as HashMap<String, String>?,
                success = {
                    _isLoggedIn.value = true
                    saveLoginState(true)
                    onSuccess()
                },
                failure = { exception ->
                    Log.i("Login", "Failed: ${exception.message}")
                    onFailure(exception.message ?: "Login failed")
                }
            )
        } catch (e: Exception) {
            Log.e("AuthManager", "Login error: ${e.message}")
            onFailure(e.message ?: "Login failed")
        }
    }
    
    fun signUp(
        firstName: String,
        lastName: String,
        email: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            val newUser = ZCatalystApp.getInstance().newUser(
                firstName = "$firstName $lastName",
                email = email
            )

            ZCatalystApp.getInstance().signUp(
                newUser = newUser,
                success = { user: ZCatalystUser ->
                    // Don't auto-login after signup - user needs to verify email first
                    onSuccess()
                },
                failure = { exception ->
                    Log.e("SignUp", "Sign up failed: ${exception.message}")
                    onFailure(exception.message ?: "Sign up failed")
                }
            )
        } catch (e: Exception) {
            Log.e("AuthManager", "Sign up error: ${e.message}")
            onFailure(e.message ?: "Sign up failed")
        }
    }
    
    fun logout(
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            ZCatalystApp.getInstance().logout(
                success = {
                    _isLoggedIn.value = false
                    saveLoginState(false)
                    onSuccess()
                },
                failure = { exception ->
                    Log.e("Logout", "Logout failed: ${exception.message}")
                    onFailure(exception.message ?: "Logout failed")
                }
            )
        } catch (e: Exception) {
            Log.e("AuthManager", "Logout error: ${e.message}")
            onFailure(e.message ?: "Logout failed")
        }
    }
    
    fun getCurrentUser(
        onSuccess: (String) -> Unit, // For now, return email as string
        onFailure: (String) -> Unit
    ) {
        try {
            ZCatalystApp.getInstance().getCurrentUser(
                success = { user ->
                    // Store user info in preferences for caching
                    prefs.edit()
                        .putString("user_email", user.email)
                        .putString("user_name", user.firstName + " " + user.lastName)
                        .apply()
                    onSuccess(user.email ?: "Unknown email")
                },
                failure = { exception ->
                    Log.e("GetCurrentUser", "Failed: ${exception?.message}")
                    // Fallback to cached data
                    val cachedEmail = prefs.getString("user_email", "demo@example.com") ?: "demo@example.com"
                    onSuccess(cachedEmail)
                }
            )
        } catch (e: Exception) {
            Log.e("AuthManager", "Get current user error: ${e.message}")
            // Fallback to cached data
            val cachedEmail = prefs.getString("user_email", "demo@example.com") ?: "demo@example.com"
            onSuccess(cachedEmail)
        }
    }
    
    fun getCurrentUserDetails(
        onSuccess: (ZCatalystUser) -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            ZCatalystApp.getInstance().getCurrentUser(
                success = { user ->
                    // Store user info in preferences for caching
                    prefs.edit()
                        .putString("user_email", user.email)
                        .putString("user_name", user.firstName + " " + user.lastName)
                        .putString("user_id", user.toString()) // Store user object string for now
                        .putString("user_status", "ACTIVE") // Default from JSON
                        .putBoolean("user_confirmed", user.isConfirmed)
                        .putString("user_type", "App User") // Default from JSON
                        .putString("user_role", "App User") // Default from JSON
                        .putString("user_created", user.createdTime)
                        .apply()
                    onSuccess(user)
                },
                failure = { exception ->
                    Log.e("GetCurrentUserDetails", "Failed: ${exception?.message}")
                    onFailure(exception?.message ?: "Failed to get current user details")
                }
            )
        } catch (e: Exception) {
            Log.e("AuthManager", "Get current user details error: ${e.message}")
            onFailure(e.message ?: "Failed to get current user details")
        }
    }
    
    fun checkLoginStatus(): Boolean {
        return ZCatalystApp.getInstance().isUserSignedIn() && _isLoggedIn.value
    }
    
    private fun saveLoginState(isLoggedIn: Boolean) {
        prefs.edit()
            .putBoolean("is_logged_in", isLoggedIn)
            .apply()
    }
}
