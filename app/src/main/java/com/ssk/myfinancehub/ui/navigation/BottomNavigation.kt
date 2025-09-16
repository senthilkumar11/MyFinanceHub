package com.ssk.myfinancehub.ui.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    Home("home", "Home", Icons.Default.Home),
    Insight("insight", "Insight", Icons.Default.Assessment),
    Add("add", "Add", Icons.Default.Add),
    Budget("budget", "Budget", Icons.Default.AccountBalanceWallet),
    Profile("profile", "Profile", Icons.Default.Person)
}

@Composable
fun BottomNavigationBar(
    currentRoute: String?,
    onItemClick: (BottomNavItem) -> Unit
) {
    NavigationBar {
        BottomNavItem.values().forEach { item ->
            val isSelected = currentRoute == item.route
            
            NavigationBarItem(
                icon = { 
                    if (item == BottomNavItem.Add) {
                        FloatingActionButton(
                            onClick = { onItemClick(item) },
                            modifier = Modifier.size(48.dp),
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title
                        )
                    }
                },
                label = if (item != BottomNavItem.Add) {
                    { Text(item.title) }
                } else null,
                selected = isSelected,
                onClick = { onItemClick(item) }
            )
        }
    }
}
