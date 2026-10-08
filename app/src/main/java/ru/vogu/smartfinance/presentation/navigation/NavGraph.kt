package ru.vogu.smartfinance.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import ru.vogu.smartfinance.core.ui.icons.AppIcons
import ru.vogu.smartfinance.presentation.accounts.AccountDetailScreen
import ru.vogu.smartfinance.presentation.accounts.AccountsScreen
import ru.vogu.smartfinance.presentation.analytics.AnalyticsScreen
import ru.vogu.smartfinance.presentation.budget.BudgetScreen
import ru.vogu.smartfinance.presentation.category.CategoryDetailScreen
import ru.vogu.smartfinance.presentation.chat.ChatScreen
import ru.vogu.smartfinance.presentation.history.HistoryScreen
import ru.vogu.smartfinance.presentation.scanner.ScannerScreen
import ru.vogu.smartfinance.presentation.settings.SettingsScreen

private data class BottomNavTab(val screen: Screen, val label: String, val icon: ImageVector)

/**
 * Вкладки нижней навигации — CLAUDE.md §5.2: постоянный доступ ко всем основным разделам.
 * Собственные геометричные иконки ([AppIcons]) вместо типового Material-набора — референс
 * пользователя. Одна и та же иконка на всех состояниях: активная вкладка выделяется цветом
 * текста/иконки и подложкой-кружком, а не сменой формы. Порядок списка = порядок страниц
 * [HorizontalPager] в [MainTabsPager] — индекс вкладки здесь ЕСТЬ номер страницы пейджера.
 */
private val bottomNavTabs = listOf(
    BottomNavTab(Screen.Chat, "Чат", AppIcons.Chat),
    BottomNavTab(Screen.Analytics, "Аналитика", AppIcons.Analytics),
    BottomNavTab(Screen.Accounts, "Счета", AppIcons.Wallet),
    BottomNavTab(Screen.Budget, "Категории", AppIcons.Categories),
    BottomNavTab(Screen.Settings, "Настройки", AppIcons.Settings)
)

/**
 * Единый NavHost приложения (single-Activity архитектура — CLAUDE.md §6).
 *
 * Пять основных разделов ([bottomNavTabs]) живут на ОДНОМ маршруте [Screen.MainTabs] внутри
 * [HorizontalPager] — CLAUDE.md список доработок ("переход между страницами по свайпу"). Тап по
 * нижней вкладке и свайп по экрану — два равноправных способа перелистывания одного и того же
 * пейджера, поэтому состояние выбранной вкладки — это просто [androidx.compose.foundation.pager.PagerState.currentPage],
 * без отдельного "текущий маршрут" для этих пяти экранов. [Screen.Scanner], [Screen.CategoryDetail]
 * и [Screen.History] остаются модальными push-экранами поверх пейджера (панель скрывается на
 * них, см. [currentRoute] ниже), а не страницами пейджера — у них нет соседей для свайпа.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartFinanceNavGraph(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    navController: NavHostController = rememberNavController()
) {
    // Простой crossfade вместо дефолтной slide-анимации Navigation Compose — она тянет за
    // собой system windowBackground сквозь щель между экранами при сдвиге. Fade без сдвига
    // никогда не открывает эту щель.
    val fadeSpec = tween<Float>(220)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Один PagerState на все 5 вкладок — хостится здесь (а не внутри MainTabsPager), чтобы
    // нижняя панель могла и подсвечивать активную вкладку по pagerState.currentPage, и сама
    // управлять пейджером (animateScrollToPage) по тапу.
    val pagerState = rememberPagerState(pageCount = { bottomNavTabs.size })
    val coroutineScope = rememberCoroutineScope()

    // Пока открыта клавиатура, таб-бар прячем — иначе Scaffold продолжает резервировать под
    // него место (он просто визуально уезжает под клавиатуру), и это резервирование складывается
    // с Modifier.imePadding() у поля ввода в ChatScreen: между полем и реальной клавиатурой
    // появляется пустой зазор высотой ровно с таб-бар. Без вкладки снизу это резервирование не
    // нужно, а клавиатура и так однозначно говорит пользователю, что он всё ещё в этом же экране.
    val imeVisible = WindowInsets.isImeVisible

    Scaffold(
        bottomBar = {
            if (!imeVisible && currentRoute == Screen.MainTabs.route) {
                Column {
                    // Тонкая линия вместо тяжёлой Material-тени — граница таб-бара в iOS
                    // рисуется хairline-разделителем, а не elevation.
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    // Свой Row вместо Material3 NavigationBar — круглая icon-button подложка
                    // + подпись под ней, тот же язык, что и круглые кнопки действий на главном
                    // экране (референс пользователя: 外卖/自提/快递 — иконка в кружке, текст ниже).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .navigationBarsPadding()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        bottomNavTabs.forEachIndexed { index, tab ->
                            val selected = pagerState.currentPage == index
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        if (!selected) {
                                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                // Тонкое кольцо вокруг активной вкладки вместо закрашенного
                                // кружка — приём "нарисованного от руки" выделения из референса
                                // (японский минимализм: акцент линией, а не заливкой).
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (selected) {
                                                Modifier.border(
                                                    width = 1.2.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = CircleShape
                                                )
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = tab.label,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.3.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.MainTabs.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            enterTransition = { fadeIn(fadeSpec) },
            exitTransition = { fadeOut(fadeSpec) },
            popEnterTransition = { fadeIn(fadeSpec) },
            popExitTransition = { fadeOut(fadeSpec) }
        ) {
            composable(Screen.MainTabs.route) {
                MainTabsPager(
                    pagerState = pagerState,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigateToScanner = { navController.navigate(Screen.Scanner.route) },
                    onOpenCategory = { categoryId -> navController.navigate(Screen.CategoryDetail.createRoute(categoryId)) },
                    onOpenAccount = { accountId -> navController.navigate(Screen.AccountDetail.createRoute(accountId)) },
                    onNavigateToHistory = { navController.navigate(Screen.History.route) },
                    onReturnToChat = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }
                )
            }
            composable(Screen.Scanner.route) {
                ScannerScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.CategoryDetail.route,
                arguments = listOf(navArgument("categoryId") { type = NavType.LongType })
            ) {
                CategoryDetailScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.AccountDetail.route,
                arguments = listOf(navArgument("accountId") { type = NavType.LongType })
            ) {
                AccountDetailScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * Пять основных экранов как страницы одного [HorizontalPager] — CLAUDE.md список доработок
 * ("переход между страницами чат/аналитика/счета/категории/настройки по свайпу"). У
 * Analytics/Accounts/Budget была стрелка "назад" в TopAppBar, когда они были push-экранами
 * поверх Chat; теперь это вкладки-соседи, а не стек, поэтому та же стрелка ведёт на вкладку
 * "Чат" ([onReturnToChat]) — так и жест "смахнуть влево до упора", и кнопка "назад" одинаково
 * понятны и никуда не проваливаются в пустоту.
 */
@Composable
private fun MainTabsPager(
    pagerState: androidx.compose.foundation.pager.PagerState,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onOpenCategory: (Long) -> Unit,
    onOpenAccount: (Long) -> Unit,
    onNavigateToHistory: () -> Unit,
    onReturnToChat: () -> Unit
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> ChatScreen(onNavigateToScanner = onNavigateToScanner)
            1 -> AnalyticsScreen(onNavigateBack = onReturnToChat)
            2 -> AccountsScreen(onNavigateBack = onReturnToChat, onOpenAccount = onOpenAccount)
            3 -> BudgetScreen(onNavigateBack = onReturnToChat, onOpenCategory = onOpenCategory)
            4 -> SettingsScreen(
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onNavigateToHistory = onNavigateToHistory
            )
        }
    }
}
