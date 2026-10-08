package ru.vogu.smartfinance.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.core.ui.components.DuotoneIconBadge
import ru.vogu.smartfinance.core.ui.components.PremiumCard
import ru.vogu.smartfinance.core.ui.components.TitleRule
import ru.vogu.smartfinance.core.ui.components.categoryColorFor
import ru.vogu.smartfinance.core.ui.components.categoryIconFor
import ru.vogu.smartfinance.core.ui.icons.AppIcons
import ru.vogu.smartfinance.core.ui.theme.amountColor
import ru.vogu.smartfinance.domain.usecase.HistoryRow
import ru.vogu.smartfinance.presentation.chat.components.EditTransactionBottomSheet
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * "История операций" — CLAUDE.md список доработок, п.2: вся лента расходов/доходов, а не
 * только последние N сообщений чата, сгруппированная по дням в духе банковских приложений.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onIntent(HistoryIntent.ErrorMessageShown)
    }

    state.editingTransaction?.let { editing ->
        EditTransactionBottomSheet(
            transaction = editing,
            categories = state.editingCategories,
            accounts = state.editingAccounts,
            onDismiss = { viewModel.onIntent(HistoryIntent.DismissEdit) },
            onSave = { viewModel.onIntent(HistoryIntent.SaveEditedTransaction(it)) }
        )
    }

    if (state.pendingDeleteTransactionId != null) {
        val pendingRow = state.rows.firstOrNull { it.transaction.id == state.pendingDeleteTransactionId }
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(HistoryIntent.DeleteDismissed) },
            title = { Text("Удалить операцию?") },
            text = {
                Text(
                    "«${pendingRow?.transaction?.title ?: "Операция"}» будет удалена без возможности " +
                        "восстановления, баланс счёта пересчитается."
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.onIntent(HistoryIntent.DeleteConfirmed) }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(HistoryIntent.DeleteDismissed) }) { Text("Отмена") }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("ИСТОРИЯ", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(AppIcons.Back, contentDescription = "Назад")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        val filteredRows = state.filteredRows
        val grouped = filteredRows.groupBy { it.transaction.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() }
            .toSortedMap(compareByDescending { it })

        Column(modifier = Modifier.padding(innerPadding)) {
            TitleRule(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.filter == null,
                    onClick = { viewModel.onIntent(HistoryIntent.FilterChanged(null)) },
                    label = { Text("Все", maxLines = 1, softWrap = false) }
                )
                FilterChip(
                    selected = state.filter == TransactionType.EXPENSE,
                    onClick = { viewModel.onIntent(HistoryIntent.FilterChanged(TransactionType.EXPENSE)) },
                    label = { Text("Расходы", maxLines = 1, softWrap = false) }
                )
                FilterChip(
                    selected = state.filter == TransactionType.INCOME,
                    onClick = { viewModel.onIntent(HistoryIntent.FilterChanged(TransactionType.INCOME)) },
                    label = { Text("Доходы", maxLines = 1, softWrap = false) }
                )
                FilterChip(
                    selected = state.filter == TransactionType.TRANSFER,
                    onClick = { viewModel.onIntent(HistoryIntent.FilterChanged(TransactionType.TRANSFER)) },
                    label = { Text("Переводы", maxLines = 1, softWrap = false) }
                )
            }

            if (filteredRows.isEmpty() && !state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Операций пока нет",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (date, rowsForDate) ->
                    item(key = "header_$date") {
                        Text(
                            text = dateHeaderLabel(date),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(rowsForDate, key = { it.transaction.id }) { row ->
                        HistoryRowItem(
                            row = row,
                            onEdit = { viewModel.onIntent(HistoryIntent.EditRequested(row.transaction.id)) },
                            onDelete = { viewModel.onIntent(HistoryIntent.DeleteRequested(row.transaction.id)) }
                        )
                    }
                }
            }
        }
    }
}

private fun dateHeaderLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale("ru", "RU")).format(date)
    }
}

@Composable
private fun HistoryRowItem(
    row: HistoryRow,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val transaction = row.transaction
    val isIncome = transaction.transactionType == TransactionType.INCOME
    val isTransfer = transaction.transactionType == TransactionType.TRANSFER
    val sign = if (isIncome) "+" else if (isTransfer) "" else "−"
    val color = row.category?.colorHex?.let { categoryColorFor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    val icon = row.category?.iconName?.let { categoryIconFor(it) } ?: AppIcons.Swap

    PremiumCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DuotoneIconBadge(color = color, size = 40.dp) {
                Icon(imageVector = icon, contentDescription = null, tint = color)
            }
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(text = transaction.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = listOfNotNull(
                        row.category?.name,
                        row.account?.name ?: "Счёт удалён",
                        DateTimeFormatter.ofPattern("HH:mm").format(transaction.timestamp.atZone(ZoneId.systemDefault()))
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "$sign${transaction.amount.formatAsCurrency(transaction.originalCurrency)}",
                style = MaterialTheme.typography.titleMedium,
                color = if (isTransfer) MaterialTheme.colorScheme.onSurface else amountColor(isIncome = isIncome)
            )
            IconButton(onClick = onDelete) {
                Icon(
                    AppIcons.Trash,
                    contentDescription = "Удалить операцию",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
