package ru.vogu.smartfinance.presentation.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.ui.components.CategoryPieChart
import ru.vogu.smartfinance.core.ui.components.DailySpendingLineChart
import ru.vogu.smartfinance.core.ui.components.categoryColorFor
import ru.vogu.smartfinance.core.ui.components.categoryIconFor
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
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Аналитика") }) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(selected: AnalyticsPeriod, onSelected: (AnalyticsPeriod) -> Unit) {
    val options = listOf(
        AnalyticsPeriod.WEEK to "Неделя",
        AnalyticsPeriod.MONTH to "Месяц",
        AnalyticsPeriod.YEAR to "Год"
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (period, label) ->
            SegmentedButton(
                selected = selected == period,
                onClick = { onSelected(period) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun TotalsCard(totalExpenses: BigDecimal?, totalIncome: BigDecimal?, baseCurrency: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun TopTransactionRow(transaction: Transaction, category: Category?, account: Account?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val color = category?.colorHex?.let { categoryColorFor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
            val icon = category?.iconName?.let { categoryIconFor(it) } ?: Icons.Filled.Category
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color = color.copy(alpha = 0.18f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
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
