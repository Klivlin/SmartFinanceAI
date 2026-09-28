package ru.vogu.smartfinance.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.vogu.smartfinance.presentation.chat.ChatScreen

/**
 * Единый NavHost приложения (single-Activity архитектура — CLAUDE.md §6).
 *
 * Сейчас (Шаг 4) в графе зарегистрирован только [Screen.Chat] — главный чат-экран.
 * [Screen.Scanner] подключается вместе со сканером чеков в Шаге 5, [Screen.Accounts]
 * и [Screen.Analytics] — вместе с соответствующими экранами в Шаге 6 (см. CLAUDE.md §9.5).
 * Кнопка камеры на ChatScreen уже вызывает переход на [Screen.Scanner] — маршрут станет
 * рабочим, как только граф Шага 5 зарегистрирует под него composable.
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
    }
}
