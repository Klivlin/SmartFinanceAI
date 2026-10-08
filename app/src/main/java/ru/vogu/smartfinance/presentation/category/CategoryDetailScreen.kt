package ru.vogu.smartfinance.presentation.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.vogu.smartfinance.core.ui.components.PremiumCard
import ru.vogu.smartfinance.core.ui.icons.AppIcons
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.core.ui.components.categoryColorFor
import ru.vogu.smartfinance.core.ui.components.categoryIconFor
import ru.vogu.smartfinance.core.ui.theme.amountColor
import ru.vogu.smartfinance.domain.usecase.CategoryTransactionRow
import ru.vogu.smartfinance.presentation.chat.components.EditTransactionBottomSheet
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * "Все траты категории" — CLAUDE.md §5.2: открывается тапом по категории на [ru.vogu.smartfinance.presentation.budget.BudgetScreen],
 * показывает ВСЕ операции этой категории (не только за текущий месяц) с возможностью удаления.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoryDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val category = state.category
    val color = category?.let { categoryColorFor(it.colorHex) } ?: MaterialTheme.colorScheme.primary
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onIntent(CategoryDetailIntent.ErrorMessageShown)
    }

    state.editingTransaction?.let { editing ->
        EditTransactionBottomSheet(
            transaction = editing,
            categories = state.editingCategories,
            accounts = state.editingAccounts,
            onDismiss = { viewModel.onIntent(CategoryDetailIntent.DismissEdit) },
            onSave = { viewModel.onIntent(CategoryDetailIntent.SaveEditedTransaction(it)) }
        )
    }

    if (state.pendingDeleteTransactionId != null) {
        val pendingRow = state.rows.firstOrNull { it.transaction.id == state.pendingDeleteTransactionId }
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(CategoryDetailIntent.DeleteDismissed) },
            title = { Text("Удалить операцию?") },
            text = {
                Text(
                    "«${pendingRow?.transaction?.title ?: "Операция"}» будет удалена без возможности " +
                        "восстановления, баланс счёта пересчитается."
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.onIntent(CategoryDetailIntent.DeleteConfirmed) }) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(CategoryDetailIntent.DeleteDismissed) }) {
                    Text("Отмена")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(category?.name ?: "Категория") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(AppIcons.Back, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                category?.let {
                    ru.vogu.smartfinance.core.ui.components.DuotoneIconBadge(color = color, size = 52.dp) {
                        Icon(imageVector = categoryIconFor(it.iconName), contentDescription = null, tint = color)
                    }
                }
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        text = "Всего потрачено",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = state.totalSpent.formatAsCurrency(state.baseCurrency),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }

            if (state.rows.isEmpty() && !state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "По этой категории пока нет операций",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.rows, key = { it.transaction.id }) { row ->
                        CategoryTransactionRowItem(
                            row = row,
                            baseCurrency = state.baseCurrency,
                            onEdit = { viewModel.onIntent(CategoryDetailIntent.EditRequested(row.transaction.id)) },
                            onDelete = { viewModel.onIntent(CategoryDetailIntent.DeleteRequested(row.transaction.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTransactionRowItem(
    row: CategoryTransactionRow,
    baseCurrency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val transaction = row.transaction
    val isIncome = transaction.transactionType == TransactionType.INCOME
    val sign = if (isIncome) "+" else "−"

    PremiumCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = transaction.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = listOfNotNull(
                        row.account?.name ?: "Счёт удалён",
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                            .withLocale(Locale("ru", "RU"))
                            .format(transaction.timestamp.atZone(ZoneId.systemDefault()))
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "$sign${transaction.amount.formatAsCurrency(transaction.originalCurrency)}",
                style = MaterialTheme.typography.titleMedium,
                color = amountColor(isIncome = isIncome)
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
