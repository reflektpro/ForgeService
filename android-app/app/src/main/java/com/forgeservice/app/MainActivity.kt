package com.forgeservice.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.forgeservice.app.ui.screens.*
import com.forgeservice.app.ui.theme.ForgeServiceTheme

/**
 * ForgeService — Professional Auto Service CRM
 * Main entry point.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ForgeServiceTheme {
                ForgeServiceApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgeServiceApp() {
    val navController = rememberNavController()
    val currentRoute by navController.currentBackStackEntryAsState()
    val currentDestination = currentRoute?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    BottomNavItem("dashboard", "Дашборд", Icons.Default.Dashboard),
                    BottomNavItem("vehicles", "Авто", Icons.Default.DirectionsCar),
                    BottomNavItem("orders", "Заказы", Icons.Default.Assignment), // custom ic_assignment used in list cards for assets demo
                    BottomNavItem("warehouse", "Склад", Icons.Default.Inventory),
                    BottomNavItem("analytics", "Аналитика", Icons.Default.Analytics),
                )

                items.forEach { item ->
                    NavigationBarItem(
                        selected = currentDestination == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo("dashboard") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") { DashboardScreen(navController) }
            composable("vehicles") { VehiclesScreen() }
            composable("orders") { WorkOrdersScreen(navController) }
            composable("warehouse") { WarehouseScreen() }
            composable("analytics") { AnalyticsScreen() }

            // Work Order Detail
            composable(
                route = "work_order/{orderId}",
                arguments = listOf(navArgument("orderId") { type = NavType.IntType })
            ) { backStackEntry ->
                val orderId = backStackEntry.arguments?.getInt("orderId") ?: 0
                WorkOrderDetailScreen(
                    workOrderId = orderId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)