package ru.vogu.smartfinance.presentation.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.vogu.smartfinance.core.ui.components.PremiumCard
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.ui.components.CategoryPieChart
import ru.vogu.smartfinance.core.ui.components.DailySpendingLineChart
import ru.vogu.smartfinance.core.ui.components.DuotoneIconBadge
import ru.vogu.smartfinance.core.ui.components.SavingsProjectionBottomSheet
import ru.vogu.smartfinance.core.ui.components.TitleRule
import ru.vogu.smartfinance.core.ui.components.categoryColorFor
import ru.vogu.smartfinance.core.ui.components.categoryIconFor
import ru.vogu.smartfinance.core.ui.icons.AppIcons
import ru.vogu.smartfinance.core.ui.theme.amountColor
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.AnalyticsPeriod
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.Transaction
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Экран аналитики — CLAUDE.md §5.2: Pie Chart расходов, график динамики по дням, топ трат. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var projectionAccount by remember { mutableStateOf<Account?>(null) }

    projectionAccount?.let { account ->
        SavingsProjectionBottomSheet(account = account, onDismiss = { projectionAccount = null })
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("АНАЛИТИКА", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(AppIcons.Back, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
        ru.vogu.smartfinance.core.ui.components.AppLogo(
            modifier = Modifier.align(Alignment.Center)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { TitleRule() }
            item {
                PeriodSelector(
                    selected = state.period,
                    onSelected = { viewModel.onIntent(AnalyticsIntent.PeriodSelected(it)) }
                )
            }

            item {
                TotalsCard(
                    totalExpenses = state.summary?.totalExpenses,
                    totalIncome = state.summary?.totalIncome,
                    baseCurrency = state.baseCurrency
                )
            }

            if (state.forecast.isNotEmpty()) {
                item {
                    ForecastCard(forecast = state.forecast, baseCurrency = state.baseCurrency)
                }
            }

            if (state.tips.isNotEmpty()) {
                item {
                    SectionCard(title = "Советы") {
                        TipsList(tips = state.tips)
                    }
                }
            }

            val accounts = state.accountsById.values.sortedBy { it.sortOrder }
            if (accounts.isNotEmpty()) {
                item {
                    SectionCard(title = "Счета") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            accounts.forEach { account ->
                                AccountOverviewRow(
                                    account = account,
                                    onOpenProjection = { projectionAccount = account }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Расходы по категориям") {
                    CategoryPieChart(
                        breakdown = state.summary?.categoryBreakdown ?: emptyList(),
                        baseCurrency = state.baseCurrency
                    )
                }
            }

            item {
                SectionCard(title = "Динамика расходов") {
                    DailySpendingLineChart(
                        points = state.summary?.dailySpending ?: emptyList(),
                        baseCurrency = state.baseCurrency
                    )
                }
            }

            val topTransactions = state.summary?.topTransactions.orEmpty()
            if (topTransactions.isNotEmpty()) {
                item {
                    Text(
                        text = "Топ трат за период",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(topTransactions, key = { it.id }) { transaction ->
                    TopTransactionRow(
                        transaction = transaction,
                        category = transaction.categoryId?.let { state.categoriesById[it] },
                        account = state.accountsById[transaction.accountId]
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun PeriodSelector(selected: AnalyticsPeriod, onSelected: (AnalyticsPeriod) -> Unit) {
    val options = listOf(
        AnalyticsPeriod.WEEK to "Неделя",
        AnalyticsPeriod.MONTH to "Месяц",
        AnalyticsPeriod.YEAR to "Год"
    )
    // Тонкие подчёркнутые вкладки вместо тяжёлого закрашенного SegmentedButton — тот же
    // минималистичный язык, что и в нижней навигации (акцент линией, а не заливкой).
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        options.forEach { (period, label) ->
            val isSelected = selected == period
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onSelected(period) }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .height(1.5.dp)
                        .width(if (isSelected) 24.dp else 0.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun TotalsCard(totalExpenses: BigDecimal?, totalIncome: BigDecimal?, baseCurrency: String) {
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Расходы", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = totalExpenses?.formatAsCurrency(baseCurrency) ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Доходы", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = totalIncome?.formatAsCurrency(baseCurrency) ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        color = amountColor(isIncome = true)
                    )
                }
            }

            if (totalExpenses != null && totalIncome != null) {
                // Остаток за период — CLAUDE.md список доработок, п.6 ("разность между расходами
                // и доходами"). Оба слагаемых уже посчитаны для верхних колонок — просто вычитаем.
                val net = totalIncome - totalExpenses
                HorizontalDivider(
                    modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Остаток за период",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = net.formatAsCurrency(baseCurrency),
                        style = MaterialTheme.typography.titleMedium,
                        color = amountColor(isIncome = net.signum() >= 0)
                    )
                }
            }
        }
    }
}

/**
 * Прогноз на ближайшие месяцы — CLAUDE.md список доработок: отдельный, заметный блок (не общая
 * "SectionCard", как у Советов/Счетов — раньше он визуально терялся среди остальных карточек) И
 * с разбивкой по КАЖДОЙ категории, где были траты, а не одной обезличенной суммой "Расходы" —
 * иначе прогноз ничего не говорит о том, где реально можно сократить (см. doc
 * [ru.vogu.smartfinance.domain.usecase.GetSpendingForecastUseCase]). НЕ про накопительный счёт.
 * Разбивка по категориям одинакова для всех месяцев вперёд (среднее, не индивидуальный тренд на
 * категорию — слишком шумно на 3 точках), поэтому показана один раз для ближайшего месяца;
 * дальнейшие месяцы в развороте — только итоговыми строками.
 */
@Composable
private fun ForecastCard(forecast: List<ru.vogu.smartfinance.domain.usecase.MonthForecast>, baseCurrency: String) {
    var isExpanded by remember(forecast) { mutableStateOf(false) }
    val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru", "RU"))
    val nextMonth = forecast.first()
    val laterMonths = forecast.drop(1)

    PremiumCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
    ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = AppIcons.Analytics,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ПРОГНОЗ НА СЛЕДУЮЩИЙ МЕСЯЦ",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = monthFormatter.format(nextMonth.yearMonth).replaceFirstChar { it.uppercaseChar() },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (nextMonth.categoryForecasts.isNotEmpty()) {
            Text(
                text = "Расходы по категориям",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                nextMonth.categoryForecasts.forEach { category ->
                    ForecastRow(category.categoryName, category.predictedAmount.formatAsCurrency(baseCurrency), MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ForecastRow("Всего расходов", nextMonth.predictedExpense.formatAsCurrency(baseCurrency), MaterialTheme.colorScheme.onSurface)
            ForecastRow("Доходы", nextMonth.predictedIncome.formatAsCurrency(baseCurrency), amountColor(isIncome = true))
            ForecastRow(
                "Отложится",
                nextMonth.predictedSavings.formatAsCurrency(baseCurrency),
                amountColor(isIncome = nextMonth.predictedSavings.signum() >= 0)
            )
        }

        if (isExpanded) {
            laterMonths.forEach { month ->
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = monthFormatter.format(month.yearMonth).replaceFirstChar { it.uppercaseChar() },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ForecastRow("Всего расходов", month.predictedExpense.formatAsCurrency(baseCurrency), MaterialTheme.colorScheme.onSurface)
                        ForecastRow("Доходы", month.predictedIncome.formatAsCurrency(baseCurrency), amountColor(isIncome = true))
                        ForecastRow(
                            "Отложится",
                            month.predictedSavings.formatAsCurrency(baseCurrency),
                            amountColor(isIncome = month.predictedSavings.signum() >= 0)
                        )
                    }
                }
            }
        }

        Text(
            text = "Среднее по последним месяцам — не точная сумма, а ориентир.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        if (laterMonths.isNotEmpty()) {
            Text(
                text = if (isExpanded) "Свернуть ↑" else "Показать ещё ${laterMonths.size} мес. →",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { isExpanded = !isExpanded }
            )
        }
    }
    }
}

@Composable
private fun ForecastRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}

/**
 * Список советов с раскрытием — CLAUDE.md список доработок ("блок советов сделать
 * раскрываемым, чтобы можно было побольше прочитать"). Свёрнуто показываются первые 2 —
 * этого достаточно, чтобы понять ценность блока, не занимая весь экран под текст.
 */
@Composable
private fun TipsList(tips: List<String>) {
    var isExpanded by remember(tips) { mutableStateOf(false) }
    val collapsedCount = 2
    val visibleTips = if (isExpanded || tips.size <= collapsedCount) tips else tips.take(collapsedCount)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        visibleTips.forEach { tip ->
            Row {
                Icon(
                    imageVector = AppIcons.Other,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(12.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = tip, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (tips.size > collapsedCount) {
            Text(
                text = if (isExpanded) "Свернуть ↑" else "Показать ещё ${tips.size - collapsedCount} →",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { isExpanded = !isExpanded }
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * Строка счёта в "Аналитике" — CLAUDE.md §2.1/§5.2: чтобы не искать накопительный счёт
 * (и вообще состояние всех счетов) отдельно на экране "Счета", здесь виден весь список
 * сразу, а для накопительных — ставка и кнопка "Прогноз" (см. [SavingsProjectionBottomSheet]).
 */
@Composable
private fun AccountOverviewRow(account: Account, onOpenProjection: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DuotoneIconBadge(color = MaterialTheme.colorScheme.primary, size = 36.dp) {
            Icon(
                imageVector = iconForAccountType(account.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(text = account.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = accountTypeLabel(account.type) +
                    if (account.type == AccountType.SAVINGS && account.interestRatePercent != null) {
                        " · ${account.interestRatePercent.stripTrailingZeros().toPlainString()}% год."
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = account.balance.formatAsCurrency(account.currency), style = MaterialTheme.typography.titleMedium)
            if (account.type == AccountType.SAVINGS) {
                TextButton(onClick = onOpenProjection, contentPadding = PaddingValues(0.dp)) {
                    Text("Прогноз", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun iconForAccountType(type: AccountType): ImageVector = when (type) {
    AccountType.DEBIT_CARD -> AppIcons.Card
    AccountType.CASH -> AppIcons.Cash
    AccountType.SAVINGS -> AppIcons.Coins
    AccountType.CREDIT_CARD -> AppIcons.Card
    AccountType.CRYPTO -> AppIcons.Crypto
}

private fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.DEBIT_CARD -> "Дебетовая карта"
    AccountType.CASH -> "Наличные"
    AccountType.SAVINGS -> "Накопительный счёт"
    AccountType.CREDIT_CARD -> "Кредитная карта"
    AccountType.CRYPTO -> "Криптовалюта"
}

@Composable
private fun TopTransactionRow(transaction: Transaction, category: Category?, account: Account?) {
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val color = category?.colorHex?.let { categoryColorFor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
            val icon = category?.iconName?.let { categoryIconFor(it) } ?: AppIcons.Other
            ru.vogu.smartfinance.core.ui.components.DuotoneIconBadge(color = color, size = 36.dp) {
                Icon(imageVector = icon, contentDescription = category?.name, tint = color)
            }
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(text = transaction.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitleFor(transaction, category, account),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "−${transaction.amount.formatAsCurrency(transaction.originalCurrency)}",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private fun subtitleFor(transaction: Transaction, category: Category?, account: Account?): String {
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale("ru", "RU"))
        .format(transaction.timestamp.atZone(ZoneId.systemDefault()))
    val categoryLabel = category?.name ?: "Без категории"
    val accountLabel = account?.name ?: "Счёт удалён"
    val sourceLabel = when (transaction.source) {
        TransactionSource.QR -> "по QR"
        TransactionSource.OCR -> "по фото"
        TransactionSource.MANUAL -> "вручную"
        TransactionSource.NLI -> null
    }
    return listOfNotNull(categoryLabel, accountLabel, date, sourceLabel).joinToString(" · ")
}
