package ru.vogu.smartfinance.presentation.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.domain.model.Account

/** Экран счетов — CLAUDE.md §5.2: управление балансами, перевод между счетами. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(viewModel: AccountsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AccountsEffect.ShowToast -> coroutineScope.launch { snackbarHostState.showSnackbar(effect.message) }
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onIntent(AccountsIntent.ErrorMessageShown)
    }

    if (state.isAddAccountVisible) {
        AddAccountBottomSheet(
            isSubmitting = state.isSubmitting,
            defaultCurrency = state.baseCurrency,
            onDismiss = { viewModel.onIntent(AccountsIntent.DismissAddAccount) },
            onCreate = { form -> viewModel.onIntent(AccountsIntent.CreateAccount(form)) }
        )
    }

    state.transferFromAccountId?.let { fromAccountId ->
        val fromAccount = state.accounts.firstOrNull { it.id == fromAccountId }
        if (fromAccount != null) {
            TransferBottomSheet(
                fromAccount = fromAccount,
                otherAccounts = state.accounts.filter { it.id != fromAccountId },
                isSubmitting = state.isSubmitting,
                onDismiss = { viewModel.onIntent(AccountsIntent.DismissTransfer) },
                onSubmit = { toAccountId, amountText, feeText ->
                    viewModel.onIntent(AccountsIntent.SubmitTransfer(toAccountId, amountText, feeText))
                }
            )
        }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Счета") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onIntent(AccountsIntent.AddAccountRequested) }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить счёт")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        if (state.accounts.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет активных счетов. Нажмите «+», чтобы добавить.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.accounts, key = { it.id }) { account ->
                AccountRow(
                    account = account,
                    canTransfer = state.accounts.size > 1,
                    onTransfer = { viewModel.onIntent(AccountsIntent.TransferRequested(account.id)) },
                    onArchive = { viewModel.onIntent(AccountsIntent.ArchiveAccount(account.id)) }
                )
            }
        }
    }
}

@Composable
private fun AccountRow(account: Account, canTransfer: Boolean, onTransfer: () -> Unit, onArchive: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconForAccountType(account.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(text = account.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = typeLabelFor(account.type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (account.type == AccountType.CREDIT_CARD && account.creditLimit != null) {
                    Text(
                        text = "Доступно: ${(account.availableCredit ?: account.creditLimit).formatAsCurrency(account.currency)} из ${account.creditLimit.formatAsCurrency(account.currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = account.balance.formatAsCurrency(account.currency), style = MaterialTheme.typography.titleMedium)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Действия со счётом")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Перевести") },
                        leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
                        enabled = canTransfer,
                        onClick = { menuExpanded = false; onTransfer() }
                    )
                    DropdownMenuItem(
                        text = { Text("Архивировать") },
                        onClick = { menuExpanded = false; onArchive() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountBottomSheet(
    isSubmitting: Boolean,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onCreate: (NewAccountForm) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.DEBIT_CARD) }
    var currency by remember { mutableStateOf(defaultCurrency) }
    var initialBalanceText by remember { mutableStateOf("0") }
    var creditLimitText by remember { mutableStateOf("") }
    var gracePeriodDaysText by remember { mutableStateOf("") }
    var statementDayText by remember { mutableStateOf("") }

    val canCreate = name.isNotBlank() && currency.trim().length == 3 && !isSubmitting

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(text = "Новый счёт", style = MaterialTheme.typography.titleLarge)

            Row(modifier = Modifier.padding(top = 16.dp)) {
                AccountType.entries.forEach { candidate ->
                    FilterChip(
                        selected = type == candidate,
                        onClick = { type = candidate },
                        label = { Text(typeLabelFor(candidate)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название счёта") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )

            Row(modifier = Modifier.padding(top = 12.dp)) {
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it.uppercase().take(3) },
                    label = { Text("Валюта") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = initialBalanceText,
                    onValueChange = { initialBalanceText = it },
                    label = { Text("Начальный баланс") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(2f)
                        .padding(start = 8.dp)
                )
            }

            if (type == AccountType.CREDIT_CARD) {
                OutlinedTextField(
                    value = creditLimitText,
                    onValueChange = { creditLimitText = it },
                    label = { Text("Кредитный лимит") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
                Row(modifier = Modifier.padding(top = 12.dp)) {
                    OutlinedTextField(
                        value = gracePeriodDaysText,
                        onValueChange = { gracePeriodDaysText = it },
                        label = { Text("Льготный период, дн.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = statementDayText,
                        onValueChange = { statementDayText = it },
                        label = { Text("День выписки") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                }
            }

            Button(
                onClick = {
                    onCreate(
                        NewAccountForm(
                            name = name,
                            type = type,
                            currency = currency,
                            initialBalanceText = initialBalanceText,
                            creditLimitText = creditLimitText,
                            gracePeriodDaysText = gracePeriodDaysText,
                            statementDayText = statementDayText
                        )
                    )
                },
                enabled = canCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            ) {
                Text("Создать")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferBottomSheet(
    fromAccount: Account,
    otherAccounts: List<Account>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (toAccountId: Long, amountText: String, feeText: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedAccount by remember(otherAccounts) { mutableStateOf(otherAccounts.firstOrNull()) }
    var amountText by remember { mutableStateOf("") }
    var feeText by remember { mutableStateOf("") }

    val amount = amountText.trim().replace(',', '.').toBigDecimalOrNull()
    val canSubmit = selectedAccount != null && amount != null && amount.signum() > 0 && !isSubmitting

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(text = "Перевод со счёта «${fromAccount.name}»", style = MaterialTheme.typography.titleLarge)

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                OutlinedTextField(
                    value = selectedAccount?.name ?: "Нет доступных счетов",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Счёт зачисления") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    otherAccounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.name) },
                            onClick = { selectedAccount = account; expanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Сумма") },
                singleLine = true,
                isError = amount == null && amountText.isNotBlank(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )

            OutlinedTextField(
                value = feeText,
                onValueChange = { feeText = it },
                label = { Text("Комиссия (необязательно)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )

            Button(
                onClick = { selectedAccount?.let { onSubmit(it.id, amountText, feeText) } },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            ) {
                Text("Перевести")
            }
        }
    }
}

private fun iconForAccountType(type: AccountType): ImageVector = when (type) {
    AccountType.DEBIT_CARD -> Icons.Filled.CreditCard
    AccountType.CASH -> Icons.Filled.AttachMoney
    AccountType.SAVINGS -> Icons.Filled.Savings
    AccountType.CREDIT_CARD -> Icons.Filled.CreditCard
}

private fun typeLabelFor(type: AccountType): String = when (type) {
    AccountType.DEBIT_CARD -> "Дебетовая карта"
    AccountType.CASH -> "Наличные"
    AccountType.SAVINGS -> "Накопительный счёт"
    AccountType.CREDIT_CARD -> "Кредитная карта"
}
