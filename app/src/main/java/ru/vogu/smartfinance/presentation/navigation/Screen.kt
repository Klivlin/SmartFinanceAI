package ru.vogu.smartfinance.presentation.navigation

/**
 * Маршруты Navigation Compose — CLAUDE.md §8 (structure) / §5. Экраны подключаются
 * в [NavGraph] по мере готовности presentation-слоя (Шаг 4: Chat; Шаг 5: Scanner;
 * Шаг 6: Accounts/Analytics) — сама разметка маршрутов фиксируется здесь целиком сразу,
 * чтобы ссылки на них (например, кнопка камеры в ChatScreen) были стабильны между шагами.
 */
sealed class Screen(val route: String) {
    data object Chat : Screen("chat")
    data object Scanner : Screen("scanner")
    data object Accounts : Screen("accounts")
    data object Analytics : Screen("analytics")
}
