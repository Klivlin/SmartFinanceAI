package ru.vogu.smartfinance.presentation.accounts

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.core.ui.components.DuotoneIconBadge
import ru.vogu.smartfinance.core.ui.components.PremiumCard
import ru.vogu.smartfinance.core.ui.components.categoryColorFor
import ru.vogu.smartfinance.core.ui.components.categoryIconFor
import ru.vogu.smartfinance.core.ui.icons.AppIcons
import ru.vogu.smartfinance.core.ui.theme.amountColor
import ru.vogu.smartfinance.domain.usecase.AccountTransactionRow
import ru.vogu.smartfinance.presentation.chat.components.EditTransactionBottomSheet
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * "Все операции счёта" — открывается тапом по строке счёта на [AccountsScreen] (запрос
 * пользователя: "при нажатии на счёт хочу видеть расходы по этому счёту"). Показывает ВСЮ
 * историю счёта (расходы, доходы, переводы в обе стороны — см. [GetAccountTransactionsUseCase]),
 * а не только за текущий месяц, по аналогии с [ru.vogu.smartfinance.presentation.category.CategoryDetailScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: AccountDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val account = state.account
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onIntent(AccountDetailIntent.ErrorMessageShown)
    }

    state.editingTransaction?.let { editing ->
        EditTransactionBottomSheet(
            transaction = editing,
            categories = state.editingCategories,
            accounts = state.editingAccounts,
            onDismiss = { viewModel.onIntent(AccountDetailIntent.DismissEdit) },
            onSave = { viewModel.onIntent(AccountDetailIntent.SaveEditedTransaction(it)) }
        )
    }

    if (state.pendingDeleteTransactionId != null) {
        val pendingRow = state.rows.firstOrNull { it.transaction.id == state.pendingDeleteTransactionId }
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(AccountDetailIntent.DeleteDismissed) },
            title = { Text("Удалить операцию?") },
            text = {
                Text(
                    "«${pendingRow?.transaction?.title ?: "Операция"}» будет удалена без возможности " +
                        "восстановления, баланс счёта пересчитается."
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.onIntent(AccountDetailIntent.DeleteConfirmed) }) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(AccountDetailIntent.DeleteDismissed) }) {
                    Text("Отмена")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(account?.name ?: "Счёт") },
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
                account?.let {
                    DuotoneIconBadge(color = MaterialTheme.colorScheme.primary, size = 52.dp) {
                        Icon(imageVector = iconForAccountType(it.type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        text = "Всего потрачено",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = account?.let { state.totalSpent.formatAsCurrency(it.currency) } ?: "—",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }

            if (state.rows.isEmpty() && !state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "По этому счёту пока нет операций",
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
                        AccountTransactionRowItem(
                            row = row,
                            currency = account?.currency ?: row.transaction.originalCurrency,
                            onEdit = { viewModel.onIntent(AccountDetailIntent.EditRequested(row.transaction.id)) },
                            onDelete = { viewModel.onIntent(AccountDetailIntent.DeleteRequested(row.transaction.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountTransactionRowItem(
    row: AccountTransactionRow,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val transaction = row.transaction
    val sign = if (row.isIncoming) "+" else "−"
    val category = row.category
    val categoryColor = category?.let { categoryColorFor(it.colorHex) } ?: MaterialTheme.colorScheme.onSurfaceVariant

    PremiumCard(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (category != null) {
                DuotoneIconBadge(color = categoryColor, size = 36.dp) {
                    Icon(imageVector = categoryIconFor(category.iconName), contentDescription = null, tint = categoryColor, modifier = Modifier.size(18.dp))
                }
            }
            Column(
                modifier = Modifier
                    .padding(start = if (category != null) 12.dp else 0.dp)
                    .weight(1f)
            ) {
                Text(text = transaction.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = listOfNotNull(
                        category?.name,
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                            .withLocale(Locale("ru", "RU"))
                            .format(transaction.timestamp.atZone(ZoneId.systemDefault()))
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "$sign${row.signedAmount.abs().formatAsCurrency(currency)}",
                style = MaterialTheme.typography.titleMedium,
                color = amountColor(isIncome = row.isIncoming)
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

private fun iconForAccountType(type: AccountType): ImageVector = when (type) {
    AccountType.DEBIT_CARD -> AppIcons.Card
    AccountType.CASH -> AppIcons.Cash
    AccountType.SAVINGS -> AppIcons.Coins
    AccountType.CREDIT_CARD -> AppIcons.Card
    AccountType.CRYPTO -> AppIcons.Crypto
}
