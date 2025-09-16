package com.ssk.myfinancehub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ssk.myfinancehub.auth.AuthManager
import com.ssk.myfinancehub.navigation.FinanceNavigation
import com.ssk.myfinancehub.navigation.Screen
import com.ssk.myfinancehub.ui.navigation.BottomNavItem
import com.ssk.myfinancehub.ui.navigation.BottomNavigationBar
import com.ssk.myfinancehub.ui.theme.MyFinanceHubTheme
import com.ssk.myfinancehub.ui.viewmodel.BudgetViewModel
import com.ssk.myfinancehub.ui.viewmodel.TransactionViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyFinanceHubTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FinanceApp()
                }
            }
        }
    }
}

@Composable
fun FinanceApp() {
    val context = LocalContext.current
    val authManager = remember { AuthManager.getInstance(context) }
    val isLoggedIn by authManager.isLoggedIn
    
    val navController = rememberNavController()
    val transactionViewModel: TransactionViewModel = viewModel()
    val budgetViewModel: BudgetViewModel = viewModel()
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val showBottomBar = isLoggedIn && currentRoute != Screen.Auth.route
    
    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onItemClick = { item ->
                        when (item) {
                            BottomNavItem.Home -> navController.navigate("home") {
                                popUpTo("home") { inclusive = true }
                            }
                            BottomNavItem.Insight -> navController.navigate("analytics") {
                                popUpTo("home")
                            }
                            BottomNavItem.Add -> navController.navigate("add_transaction")
                            BottomNavItem.Budget -> navController.navigate("budget_planning") {
                                popUpTo("home")
                            }
                            BottomNavItem.Profile -> navController.navigate("profile") {
                                popUpTo("home")
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        FinanceNavigation(
            navController = navController,
            viewModel = transactionViewModel,
            budgetViewModel = budgetViewModel,
            modifier = Modifier.padding(if (showBottomBar) paddingValues else PaddingValues(0.dp)),
            startDestination = if (isLoggedIn) Screen.Home.route else Screen.Auth.route
        )
    }
}