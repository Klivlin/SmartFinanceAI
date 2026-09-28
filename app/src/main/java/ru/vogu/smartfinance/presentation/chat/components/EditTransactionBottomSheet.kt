package ru.vogu.smartfinance.presentation.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.Transaction

/**
 * BottomSheet кнопки "Изменить" на Action Card — CLAUDE.md §5.1. Правки применяются целиком
 * через [onSave], валидация (сумма > 0, обязательные поля) — в [ru.vogu.smartfinance.domain.usecase.UpdateTransactionUseCase].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionBottomSheet(
    transaction: Transaction,
    categories: List<Category>,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    var title by remember(transaction.id) { mutableStateOf(transaction.title) }
    var amountText by remember(transaction.id) { mutableStateOf(transaction.amount.toPlainString()) }
    var type by remember(transaction.id) { mutableStateOf(transaction.transactionType) }
    var selectedCategoryId by remember(transaction.id) { mutableStateOf(transaction.categoryId) }
    var selectedAccountId by remember(transaction.id) { mutableStateOf(transaction.accountId) }
    var selectedToAccountId by remember(transaction.id) { mutableStateOf(transaction.toAccountId) }

    val amount = amountText.replace(',', '.').toBigDecimalOrNull()
    val canSave = amount != null && amount.signum() > 0 && title.isNotBlank() &&
        (type != TransactionType.TRANSFER || (selectedToAccountId != null && selectedToAccountId != selectedAccountId))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(text = "Изменить операцию", style = MaterialTheme.typography.titleLarge)

            Row(modifier = Modifier.padding(top = 16.dp)) {
                TransactionType.entries
                    .filter { it != TransactionType.ADJUSTMENT }
                    .forEach { candidate ->
                        FilterChip(
                            selected = type == candidate,
                            onClick = { type = candidate },
                            label = { Text(labelFor(candidate)) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Сумма") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amount == null || amount.signum() <= 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )

            if (type != TransactionType.TRANSFER) {
                DropdownField(
                    label = "Категория",
                    options = categories.filter { it.isIncomeCategory == (type == TransactionType.INCOME) },
                    optionLabel = { it.name },
                    selectedId = selectedCategoryId,
                    optionId = { it.id },
                    onSelected = { selectedCategoryId = it.id },
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            DropdownField(
                label = if (type == TransactionType.TRANSFER) "Счёт списания" else "Счёт",
                options = accounts,
                optionLabel = { it.name },
                selectedId = selectedAccountId,
                optionId = { it.id },
                onSelected = { selectedAccountId = it.id },
                modifier = Modifier.padding(top = 12.dp)
            )

            if (type == TransactionType.TRANSFER) {
                DropdownField(
                    label = "Счёт зачисления",
                    options = accounts.filter { it.id != selectedAccountId },
                    optionLabel = { it.name },
                    selectedId = selectedToAccountId,
                    optionId = { it.id },
                    onSelected = { selectedToAccountId = it.id },
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Button(
                onClick = {
                    val finalAmount = amount ?: return@Button
                    onSave(
                        transaction.copy(
                            title = title.trim(),
                            amount = finalAmount,
                            amountBase = finalAmount,
                            transactionType = type,
                            categoryId = if (type == TransactionType.TRANSFER) null else selectedCategoryId,
                            accountId = selectedAccountId,
                            toAccountId = if (type == TransactionType.TRANSFER) selectedToAccountId else null
                        )
                    )
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            ) {
                Text("Сохранить")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownField(
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    optionId: (T) -> Long,
    selectedId: Long?,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options.firstOrNull { optionId(it) == selectedId }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedOption?.let(optionLabel) ?: "Не выбрано",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun labelFor(type: TransactionType): String = when (type) {
    TransactionType.EXPENSE -> "Расход"
    TransactionType.INCOME -> "Доход"
    TransactionType.TRANSFER -> "Перевод"
    TransactionType.ADJUSTMENT -> "Корректировка"
}
