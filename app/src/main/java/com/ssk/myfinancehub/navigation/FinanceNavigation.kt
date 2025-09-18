package com.ssk.myfinancehub.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.ssk.myfinancehub.ui.screens.AddEditTransactionScreen
import com.ssk.myfinancehub.ui.screens.AnalyticsScreen
import com.ssk.myfinancehub.ui.screens.AuthScreen
import com.ssk.myfinancehub.ui.screens.BudgetPlanningScreen
import com.ssk.myfinancehub.ui.screens.HomeScreen
import com.ssk.myfinancehub.ui.screens.ProfileScreen
import com.ssk.myfinancehub.ui.viewmodel.BudgetViewModel
import com.ssk.myfinancehub.ui.viewmodel.TransactionViewModel

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Home : Screen("home")
    object AddTransaction : Screen("add_transaction")
    object EditTransaction : Screen("edit_transaction/{transactionId}") {
        fun createRoute(transactionId: Long) = "edit_transaction/$transactionId"
    }
    object BudgetPlanning : Screen("budget_planning")
    object Analytics : Screen("analytics")
    object Profile : Screen("profile")
}

@Composable
fun FinanceNavigation(
    navController: NavHostController,
    viewModel: TransactionViewModel,
    budgetViewModel: BudgetViewModel,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Auth.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onAddTransaction = {
                    navController.navigate(Screen.AddTransaction.route)
                },
                onEditTransaction = { transactionId ->
                    navController.navigate(Screen.EditTransaction.createRoute(transactionId))
                },
                onBudgetPlanning = {
                    navController.navigate(Screen.BudgetPlanning.route)
                },
                onAnalytics = {
                    navController.navigate(Screen.Analytics.route)
                },
                onProfile = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }
        
        composable(Screen.AddTransaction.route) {
            AddEditTransactionScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.EditTransaction.route,
            arguments = listOf(
                navArgument("transactionId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getLong("transactionId") ?: 0L
            AddEditTransactionScreen(
                transactionId = transactionId,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.BudgetPlanning.route) {
            BudgetPlanningScreen(
                viewModel = budgetViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Analytics.route) {
            AnalyticsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                transactionViewModel = viewModel,
                budgetViewModel = budgetViewModel
            )
        }
    }
}
