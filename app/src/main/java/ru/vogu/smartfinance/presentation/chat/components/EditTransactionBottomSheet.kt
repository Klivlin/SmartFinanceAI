package ru.vogu.smartfinance.presentation.chat.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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
    var selectedDate by remember(transaction.id) {
        mutableStateOf(transaction.timestamp.atZone(ZoneId.systemDefault()).toLocalDate())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val amount = amountText.replace(',', '.').toBigDecimalOrNull()
    val canSave = amount != null && amount.signum() > 0 && title.isNotBlank() &&
        (type != TransactionType.TRANSFER || (selectedToAccountId != null && selectedToAccountId != selectedAccountId))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Дата операции",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                        .withLocale(Locale("ru", "RU"))
                        .format(selectedDate),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

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
                    // Дату меняем, время суток исходной операции сохраняем — иначе правка
                    // одной только даты незаметно "телепортировала" бы операцию на полночь.
                    val originalTime = transaction.timestamp.atZone(ZoneId.systemDefault()).toLocalTime()
                    val newTimestamp = selectedDate.atTime(originalTime).atZone(ZoneId.systemDefault()).toInstant()
                    onSave(
                        transaction.copy(
                            title = title.trim(),
                            amount = finalAmount,
                            amountBase = finalAmount,
                            transactionType = type,
                            categoryId = if (type == TransactionType.TRANSFER) null else selectedCategoryId,
                            accountId = selectedAccountId,
                            toAccountId = if (type == TransactionType.TRANSFER) selectedToAccountId else null,
                            timestamp = newTimestamp
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
