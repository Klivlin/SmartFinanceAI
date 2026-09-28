package ru.vogu.smartfinance.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.vogu.smartfinance.presentation.accounts.AccountsScreen
import ru.vogu.smartfinance.presentation.analytics.AnalyticsScreen
import ru.vogu.smartfinance.presentation.budget.BudgetScreen
import ru.vogu.smartfinance.presentation.chat.ChatScreen
import ru.vogu.smartfinance.presentation.scanner.ScannerScreen

/** Вкладки нижней навигации — CLAUDE.md §5.2 (Chat уже занимает центральное место в §5.1). */
private data class BottomTab(val screen: Screen, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Screen.Chat, "Чат", Icons.Filled.Chat),
    BottomTab(Screen.Analytics, "Аналитика", Icons.Filled.PieChart),
    BottomTab(Screen.Budget, "Бюджет", Icons.Filled.Savings),
    BottomTab(Screen.Accounts, "Счета", Icons.Filled.AccountBalanceWallet)
)

/**
 * Единый NavHost приложения (single-Activity архитектура — CLAUDE.md §6).
 *
 * Все экраны Шага 6 подключены: [Screen.Chat] (§5.1), [Screen.Scanner] (§4), [Screen.Analytics]/
 * [Screen.Budget]/[Screen.Accounts] (§5.2/§2.5/§2.1) — CLAUDE.md §9.5 завершён. Нижняя навигация
 * объединяет 4 основных экрана; [Screen.Scanner] — полноэкранный модальный поток (камера),
 * открывается кнопкой камеры в [ChatScreen] и не входит в нижнюю навигацию, поэтому бар
 * скрывается на нём тем же способом, каким Chat возвращается на него через popBackStack.
 */
@Composable
fun SmartFinanceNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = bottomTabs.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.screen.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(tab.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Chat.route) {
                ChatScreen(
                    onNavigateToScanner = { navController.navigate(Screen.Scanner.route) }
                )
            }
            composable(Screen.Scanner.route) {
                ScannerScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Analytics.route) {
                AnalyticsScreen()
            }
            composable(Screen.Budget.route) {
                BudgetScreen()
            }
            composable(Screen.Accounts.route) {
                AccountsScreen()
            }
        }
    }
}
