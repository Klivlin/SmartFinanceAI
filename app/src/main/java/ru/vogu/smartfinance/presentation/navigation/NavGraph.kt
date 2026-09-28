package ru.vogu.smartfinance.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.vogu.smartfinance.presentation.chat.ChatScreen
import ru.vogu.smartfinance.presentation.scanner.ScannerScreen

/**
 * Единый NavHost приложения (single-Activity архитектура — CLAUDE.md §6).
 *
 * Сейчас (Шаг 5) в графе зарегистрированы [Screen.Chat] и [Screen.Scanner]. [Screen.Accounts]
 * и [Screen.Analytics] подключаются вместе с соответствующими экранами в Шаге 6 (см. CLAUDE.md
 * §9.5). Сканер возвращается в чат через [androidx.navigation.NavController.popBackStack] —
 * новая транзакция уже видна там как обычная Action Card (реактивная лента ChatViewModel,
 * Шаг 4), отдельный экран-подтверждение не нужен.
 */
@Composable
fun SmartFinanceNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route
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
    }
}
