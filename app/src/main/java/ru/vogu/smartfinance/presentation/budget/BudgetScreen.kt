package ru.vogu.smartfinance.presentation.budget

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.core.ui.components.categoryColorFor
import ru.vogu.smartfinance.core.ui.components.categoryIconFor
import ru.vogu.smartfinance.domain.model.CategoryBudgetStatus

/** Экран бюджета — CLAUDE.md §2.5/§5.2: месячные лимиты по категориям расходов с прогресс-барами. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(viewModel: BudgetViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onIntent(BudgetIntent.ErrorMessageShown)
    }

    state.editingCategory?.let { category ->
        EditBudgetDialog(
            categoryName = category.name,
            currentBudgetText = category.monthlyBudget?.toPlainString().orEmpty(),
            hasBudget = category.monthlyBudget != null,
            isSaving = state.isSaving,
            onDismiss = { viewModel.onIntent(BudgetIntent.DismissEditBudget) },
            onSave = { text -> viewModel.onIntent(BudgetIntent.SaveBudget(text)) },
            onRemove = { viewModel.onIntent(BudgetIntent.RemoveBudget(category.id)) }
        )
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Бюджет") }) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        if (state.items.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет категорий расходов",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            items(state.items, key = { it.category.id }) { item ->
                BudgetCategoryRow(
                    item = item,
                    baseCurrency = state.baseCurrency,
                    onClick = { viewModel.onIntent(BudgetIntent.EditBudgetRequested(item.category.id)) }
                )
            }
        }
    }
}

@Composable
private fun BudgetCategoryRow(item: CategoryBudgetStatus, baseCurrency: String, onClick: () -> Unit) {
    val category = item.category
    val color = categoryColorFor(category.colorHex)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(color = color.copy(alpha = 0.18f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = categoryIconFor(category.iconName), contentDescription = category.name, tint = color)
                }
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                ) {
                    Text(text = category.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (category.monthlyBudget != null) {
                            "${item.spentThisMonth.formatAsCurrency(baseCurrency)} из ${category.monthlyBudget.formatAsCurrency(baseCurrency)}"
                        } else {
                            "Потрачено: ${item.spentThisMonth.formatAsCurrency(baseCurrency)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onClick) {
                    Text(if (category.monthlyBudget != null) "Изменить" else "Задать лимит")
                }
            }

            if (item.usagePercent != null) {
                val progressColor = when {
                    item.isExceeded -> MaterialTheme.colorScheme.error
                    item.isWarning -> Color(0xFFF9A825)
                    else -> MaterialTheme.colorScheme.primary
                }
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    LinearProgressIndicator(
                        progress = { (item.usagePercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = "${item.usagePercent}%" + if (item.isExceeded) " — лимит превышен" else if (item.isWarning) " — приближается к лимиту" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = progressColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditBudgetDialog(
    categoryName: String,
    currentBudgetText: String,
    hasBudget: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onRemove: () -> Unit
) {
    var text by remember(categoryName) { mutableStateOf(currentBudgetText) }
    val amount = text.trim().replace(',', '.').toBigDecimalOrNull()
    val canSave = amount != null && amount.signum() > 0 && !isSaving

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Лимит: $categoryName") },
        text = {
            Column {
                Text(
                    text = "Месячный лимит расходов по этой категории. При достижении 80% появится предупреждение.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Сумма, ₽") },
                    singleLine = true,
                    isError = amount == null && text.isNotBlank(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text) }, enabled = canSave) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            Row {
                if (hasBudget) {
                    TextButton(onClick = onRemove) { Text("Снять лимит") }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}
