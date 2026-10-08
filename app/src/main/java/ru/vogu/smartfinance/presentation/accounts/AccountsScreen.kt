package ru.vogu.smartfinance.presentation.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import ru.vogu.smartfinance.core.ui.components.PremiumCard
import ru.vogu.smartfinance.core.ui.components.SavingsProjectionBottomSheet
import ru.vogu.smartfinance.core.ui.components.TitleRule
import ru.vogu.smartfinance.core.ui.icons.AppIcons
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.CryptoAsset

/** Экран счетов — CLAUDE.md §5.2: управление балансами, перевод между счетами. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onNavigateBack: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel()
) {
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
        AccountFormBottomSheet(
            existingAccount = null,
            isSubmitting = state.isSubmitting,
            defaultCurrency = state.baseCurrency,
            onDismiss = { viewModel.onIntent(AccountsIntent.DismissAddAccount) },
            onSubmit = { form -> viewModel.onIntent(AccountsIntent.CreateAccount(form)) }
        )
    }

    state.editingAccountId?.let { editingAccountId ->
        val editingAccount = state.accounts.firstOrNull { it.id == editingAccountId }
        if (editingAccount != null) {
            AccountFormBottomSheet(
                existingAccount = editingAccount,
                isSubmitting = state.isSubmitting,
                defaultCurrency = state.baseCurrency,
                onDismiss = { viewModel.onIntent(AccountsIntent.DismissEditAccount) },
                onSubmit = { form -> viewModel.onIntent(AccountsIntent.SaveEditedAccount(editingAccountId, form)) }
            )
        }
    }

    state.projectionAccountId?.let { projectionAccountId ->
        val account = state.accounts.firstOrNull { it.id == projectionAccountId }
        if (account != null) {
            SavingsProjectionBottomSheet(
                account = account,
                onDismiss = { viewModel.onIntent(AccountsIntent.DismissProjection) }
            )
        }
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
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("СЧЕТА", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(AppIcons.Back, contentDescription = "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onIntent(AccountsIntent.AddAccountRequested) }) {
                Icon(AppIcons.Plus, contentDescription = "Добавить счёт")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
        ru.vogu.smartfinance.core.ui.components.AppLogo(
            modifier = Modifier.align(Alignment.Center)
        )
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
            item { TitleRule(modifier = Modifier.padding(bottom = 4.dp)) }
            items(state.accounts, key = { it.id }) { account ->
                AccountRow(
                    account = account,
                    canTransfer = state.accounts.size > 1,
                    onOpen = { onOpenAccount(account.id) },
                    onTransfer = { viewModel.onIntent(AccountsIntent.TransferRequested(account.id)) },
                    onArchive = { viewModel.onIntent(AccountsIntent.ArchiveAccount(account.id)) },
                    onProjection = { viewModel.onIntent(AccountsIntent.ProjectionRequested(account.id)) },
                    onEdit = { viewModel.onIntent(AccountsIntent.EditAccountRequested(account.id)) },
                    onSetPrimary = { viewModel.onIntent(AccountsIntent.SetPrimaryAccount(account.id)) }
                )
            }
        }
        }
    }
}

@Composable
private fun AccountRow(
    account: Account,
    canTransfer: Boolean,
    onOpen: () -> Unit,
    onTransfer: () -> Unit,
    onArchive: () -> Unit,
    onProjection: () -> Unit,
    onEdit: () -> Unit,
    onSetPrimary: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    PremiumCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ru.vogu.smartfinance.core.ui.components.DuotoneIconBadge(
                color = MaterialTheme.colorScheme.primary,
                size = 40.dp
            ) {
                Icon(
                    imageVector = iconForAccountType(account.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(text = account.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = typeLabelFor(account.type) + if (account.isPrimary) " · Основной" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (account.isPrimary) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (account.type == AccountType.CREDIT_CARD && account.creditLimit != null) {
                    Text(
                        text = "Доступно: ${(account.availableCredit ?: account.creditLimit).formatAsCurrency(account.currency)} из ${account.creditLimit.formatAsCurrency(account.currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (account.type == AccountType.SAVINGS && account.interestRatePercent != null) {
                    Text(
                        text = "${account.interestRatePercent.stripTrailingZeros().toPlainString()}% годовых",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = account.balance.formatAsCurrency(account.currency), style = MaterialTheme.typography.titleMedium)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(AppIcons.MoreVertical, contentDescription = "Действия со счётом")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Редактировать") },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    if (!account.isPrimary) {
                        DropdownMenuItem(
                            text = { Text("Сделать основным") },
                            onClick = { menuExpanded = false; onSetPrimary() }
                        )
                    }
                    if (account.type == AccountType.SAVINGS) {
                        DropdownMenuItem(
                            text = { Text("Прогноз накоплений") },
                            leadingIcon = { Icon(AppIcons.Coins, contentDescription = null) },
                            onClick = { menuExpanded = false; onProjection() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Перевести") },
                        leadingIcon = { Icon(AppIcons.Swap, contentDescription = null) },
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

/**
 * Форма счёта — используется и для создания ("+"), и для редактирования существующего счёта
 * (пункт "Редактировать" в меню строки счёта, CLAUDE.md §5.2 "управление балансами"). Тип счёта
 * и начальный баланс при редактировании не меняются: тип определяет набор остальных полей
 * (см. [ru.vogu.smartfinance.domain.usecase.ManageAccountUseCase]), а баланс — производная от
 * истории операций, а не значение, которое можно перезаписать вручную задним числом.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountFormBottomSheet(
    existingAccount: Account?,
    isSubmitting: Boolean,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSubmit: (NewAccountForm) -> Unit
) {
    val isEditing = existingAccount != null
    var name by remember { mutableStateOf(existingAccount?.name ?: "") }
    val type = existingAccount?.type ?: AccountType.DEBIT_CARD
    var editableType by remember { mutableStateOf(type) }
    var currency by remember { mutableStateOf(existingAccount?.currency ?: defaultCurrency) }
    var initialBalanceText by remember { mutableStateOf("0") }
    var creditLimitText by remember { mutableStateOf(existingAccount?.creditLimit?.toPlainString() ?: "") }
    var gracePeriodDaysText by remember { mutableStateOf(existingAccount?.gracePeriodDays?.toString() ?: "") }
    var statementDayText by remember { mutableStateOf(existingAccount?.statementDay?.toString() ?: "") }
    var interestRatePercentText by remember { mutableStateOf(existingAccount?.interestRatePercent?.toPlainString() ?: "") }

    val effectiveType = if (isEditing) type else editableType

    // Переключение на криптосчёт (CLAUDE.md §2.1) заменяет поле "Валюта" выбором тикера —
    // без дефолта первый рендер формы показывал бы пустой/невалидный выбор ("RUB" не тикер).
    LaunchedEffect(effectiveType) {
        if (effectiveType == AccountType.CRYPTO && CryptoAsset.fromCode(currency) == null) {
            currency = CryptoAsset.BTC.code
        } else if (effectiveType != AccountType.CRYPTO && CryptoAsset.fromCode(currency) != null) {
            currency = defaultCurrency
        }
    }

    val isCurrencyValid = if (effectiveType == AccountType.CRYPTO) {
        CryptoAsset.fromCode(currency) != null
    } else {
        currency.trim().length == 3
    }
    val canCreate = name.isNotBlank() && isCurrencyValid && !isSubmitting

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Без скролла и imePadding нижние поля/кнопка "Создать" уходили за пределы
                // экрана при развёрнутой клавиатуре или включённых доп.полях (кредитка) —
                // ModalBottomSheet сам по себе контент не скроллит.
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(text = if (isEditing) "Редактировать счёт" else "Новый счёт", style = MaterialTheme.typography.titleLarge)

            if (!isEditing) {
                Row(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    AccountType.entries.forEach { candidate ->
                        FilterChip(
                            selected = editableType == candidate,
                            onClick = { editableType = candidate },
                            label = { Text(typeLabelFor(candidate)) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = typeLabelFor(type),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
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

            if (effectiveType == AccountType.CRYPTO) {
                Text(
                    text = "Криптовалюта",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Row(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    CryptoAsset.entries.forEach { asset ->
                        FilterChip(
                            selected = currency == asset.code,
                            onClick = { currency = asset.code },
                            label = { Text(asset.code) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                if (!isEditing) {
                    OutlinedTextField(
                        value = initialBalanceText,
                        onValueChange = { initialBalanceText = it },
                        label = { Text("Начальный баланс, $currency") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }
            } else {
                Row(modifier = Modifier.padding(top = 12.dp)) {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase().take(3) },
                        label = { Text("Валюта") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isEditing) {
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
                }
            }

            if (effectiveType == AccountType.SAVINGS) {
                OutlinedTextField(
                    value = interestRatePercentText,
                    onValueChange = { interestRatePercentText = it },
                    label = { Text("Ставка, % годовых (необязательно)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }

            if (effectiveType == AccountType.CREDIT_CARD) {
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
                    onSubmit(
                        NewAccountForm(
                            name = name,
                            type = effectiveType,
                            currency = currency,
                            initialBalanceText = initialBalanceText,
                            creditLimitText = creditLimitText,
                            gracePeriodDaysText = gracePeriodDaysText,
                            statementDayText = statementDayText,
                            interestRatePercentText = interestRatePercentText
                        )
                    )
                },
                enabled = canCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            ) {
                Text(if (isEditing) "Сохранить" else "Создать")
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
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
    AccountType.DEBIT_CARD -> AppIcons.Card
    AccountType.CASH -> AppIcons.Cash
    AccountType.SAVINGS -> AppIcons.Coins
    AccountType.CREDIT_CARD -> AppIcons.Card
    AccountType.CRYPTO -> AppIcons.Crypto
}

private fun typeLabelFor(type: AccountType): String = when (type) {
    AccountType.DEBIT_CARD -> "Дебетовая карта"
    AccountType.CASH -> "Наличные"
    AccountType.SAVINGS -> "Накопительный"
    AccountType.CREDIT_CARD -> "Кредитная карта"
    AccountType.CRYPTO -> "Криптовалюта"
}
