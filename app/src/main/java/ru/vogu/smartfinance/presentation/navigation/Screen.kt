package ru.vogu.smartfinance.presentation.navigation

/**
 * Маршруты Navigation Compose — CLAUDE.md §8 (structure) / §5. Экраны подключаются
 * в [NavGraph] по мере готовности presentation-слоя (Шаг 4: Chat; Шаг 5: Scanner;
 * Шаг 6: Accounts/Analytics/Budget) — сама разметка маршрутов фиксируется здесь целиком
 * сразу, чтобы ссылки на них (например, кнопка камеры в ChatScreen) были стабильны между шагами.
 */
sealed class Screen(val route: String) {
    /** Пять основных разделов как страницы одного HorizontalPager — см. [ru.vogu.smartfinance.presentation.navigation.SmartFinanceNavGraph]. */
    data object MainTabs : Screen("main_tabs")
    data object Chat : Screen("chat")
    data object Scanner : Screen("scanner")
    data object Accounts : Screen("accounts")
    data object Analytics : Screen("analytics")
    /** Список категорий (переиспользует экран лимитов бюджета) — CLAUDE.md §5.2. */
    data object Budget : Screen("budget")
    data object Settings : Screen("settings")

    /** Полная история операций — CLAUDE.md список доработок, п.2 ("как в банковских приложениях"). */
    data object History : Screen("history")

    /** Все операции одной категории — CLAUDE.md §5.2 "зайти в категорию и увидеть все траты". */
    data object CategoryDetail : Screen("categories/{categoryId}") {
        fun createRoute(categoryId: Long) = "categories/$categoryId"
    }

    /** Все операции одного счёта — запрос пользователя "при нажатии на счёт видеть расходы по нему". */
    data object AccountDetail : Screen("accounts/{accountId}") {
        fun createRoute(accountId: Long) = "accounts/$accountId"
    }
}
