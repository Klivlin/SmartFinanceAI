package ru.vogu.smartfinance.presentation.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.vogu.smartfinance.core.ui.components.PremiumCard
import ru.vogu.smartfinance.core.ui.components.TitleRule
import ru.vogu.smartfinance.core.ui.icons.AppIcons
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.core.ui.components.CATEGORY_COLOR_CHOICES
import ru.vogu.smartfinance.core.ui.components.CATEGORY_ICON_CHOICES
import ru.vogu.smartfinance.core.ui.components.categoryColorFor
import ru.vogu.smartfinance.core.ui.components.categoryIconFor
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.CategoryBudgetStatus
import ru.vogu.smartfinance.domain.usecase.ReorderDirection

/**
 * Экран "Категории" — CLAUDE.md §2.5/§5.2: список категорий расходов с суммой за месяц,
 * месячными лимитами (прогресс-бары) и кастомизацией иконки/цвета. Тап по строке категории
 * открывает [ru.vogu.smartfinance.presentation.category.CategoryDetailScreen] со всеми
 * операциями по ней.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onNavigateBack: () -> Unit,
    onOpenCategory: (Long) -> Unit,
    viewModel: BudgetViewModel = hiltViewModel()
) {
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

    state.editingAppearanceCategory?.let { category ->
        CategoryAppearanceSheet(
            category = category,
            isSaving = state.isSaving,
            onDismiss = { viewModel.onIntent(BudgetIntent.DismissCustomizeAppearance) },
            onSave = { iconName, colorHex ->
                viewModel.onIntent(BudgetIntent.SaveAppearance(iconName, colorHex))
            }
        )
    }

    if (state.isAddCategoryVisible) {
        AddCategorySheet(
            isSaving = state.isSaving,
            onDismiss = { viewModel.onIntent(BudgetIntent.DismissAddCategory) },
            onSave = { name, iconName, colorHex ->
                viewModel.onIntent(BudgetIntent.SaveNewCategory(name, iconName, colorHex))
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("КАТЕГОРИИ", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(AppIcons.Back, contentDescription = "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(
                onClick = { viewModel.onIntent(BudgetIntent.AddCategoryRequested) }
            ) {
                Icon(AppIcons.Plus, contentDescription = "Добавить категорию")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
        ru.vogu.smartfinance.core.ui.components.AppLogo(
            modifier = Modifier.align(Alignment.Center)
        )
        Column(modifier = Modifier.padding(innerPadding)) {
            CategoryTabSelector(
                selected = state.selectedTab,
                onSelected = { viewModel.onIntent(BudgetIntent.TabSelected(it)) }
            )

            if (state.items.isEmpty() && !state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.selectedTab == CategoryTab.EXPENSE) "Нет категорий расходов" else "Нет категорий дохода",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { TitleRule(modifier = Modifier.padding(bottom = 4.dp)) }
                itemsIndexed(state.items, key = { _, item -> item.category.id }) { index, item ->
                    BudgetCategoryRow(
                        item = item,
                        isIncomeTab = state.selectedTab == CategoryTab.INCOME,
                        baseCurrency = state.baseCurrency,
                        isFirst = index == 0,
                        isLast = index == state.items.lastIndex,
                        onClick = { viewModel.onIntent(BudgetIntent.EditBudgetRequested(item.category.id)) },
                        onIconClick = { viewModel.onIntent(BudgetIntent.CustomizeAppearanceRequested(item.category.id)) },
                        onOpenDetail = { onOpenCategory(item.category.id) },
                        onMoveUp = { viewModel.onIntent(BudgetIntent.MoveCategory(item.category.id, ReorderDirection.UP)) },
                        onMoveDown = { viewModel.onIntent(BudgetIntent.MoveCategory(item.category.id, ReorderDirection.DOWN)) }
                    )
                }
            }
        }
        }
    }
}

/**
 * Переключатель "Расходы"/"Доходы" — тот же визуальный язык, что и у нижней таб-панели
 * (см. [ru.vogu.smartfinance.presentation.navigation.SmartFinanceNavGraph]): тонкое кольцо
 * вокруг иконки активной вкладки вместо заливки, подпись под иконкой. Раньше здесь были
 * подчёркнутые текстовые вкладки без центрирования (Arrangement.spacedBy без выравнивания
 * "упаковывает" ряд к левому краю) — из-за этого вкладки съезжали влево, а сама страница
 * визуально не была похожа на остальной интерфейс.
 */
@Composable
private fun CategoryTabSelector(selected: CategoryTab, onSelected: (CategoryTab) -> Unit) {
    val options = listOf(
        Triple(CategoryTab.EXPENSE, "Расходы", AppIcons.Wallet),
        Triple(CategoryTab.INCOME, "Доходы", AppIcons.Coins)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally)
    ) {
        options.forEach { (tab, label, icon) ->
            val isSelected = selected == tab
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onSelected(tab) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .then(
                            if (isSelected) {
                                Modifier.border(1.2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun BudgetCategoryRow(
    item: CategoryBudgetStatus,
    isIncomeTab: Boolean,
    baseCurrency: String,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onIconClick: () -> Unit,
    onOpenDetail: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val category = item.category
    val color = categoryColorFor(category.colorHex)

    PremiumCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetail)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Единый плоский DuotoneIconBadge вместо собственного radial-градиента — тот же
                // визуальный язык, что и на "Счетах"/"Категории → детали" (PremiumSurfaces.kt);
                // отдельная реализация здесь и была источником "разностилья" на этой странице.
                Box(modifier = Modifier.clickable(onClick = onIconClick)) {
                    ru.vogu.smartfinance.core.ui.components.DuotoneIconBadge(color = color, size = 36.dp) {
                        Icon(imageVector = categoryIconFor(category.iconName), contentDescription = "Изменить оформление: ${category.name}", tint = color)
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                ) {
                    Text(text = category.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when {
                            isIncomeTab -> "Получено в этом месяце: ${item.spentThisMonth.formatAsCurrency(baseCurrency)}"
                            category.monthlyBudget != null ->
                                "${item.spentThisMonth.formatAsCurrency(baseCurrency)} из ${category.monthlyBudget.formatAsCurrency(baseCurrency)}"
                            else -> "Потрачено: ${item.spentThisMonth.formatAsCurrency(baseCurrency)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = !isFirst,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            AppIcons.ChevronUp,
                            contentDescription = "Поднять категорию",
                            tint = if (isFirst) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = !isLast,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            AppIcons.ChevronDown,
                            contentDescription = "Опустить категорию",
                            tint = if (isLast) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!isIncomeTab) {
                    TextButton(onClick = onClick) {
                        Text(if (category.monthlyBudget != null) "Изменить" else "Задать лимит")
                    }
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

/**
 * Выбор иконки и цвета категории — CLAUDE.md §5.2 "Управление категориями". Работает и для
 * системных категорий (id 1..9): их id/имя, на которые ссылается промпт Gemini (CLAUDE.md §3.3),
 * не меняются, только визуальное оформление.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryAppearanceSheet(
    category: Category,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (iconName: String, colorHex: String) -> Unit
) {
    var selectedIcon by remember(category.id) { mutableStateOf(category.iconName) }
    var selectedColor by remember(category.id) { mutableStateOf(category.colorHex) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                // Заголовок + ряд цветов + сетка иконок (220dp) + кнопка легко превышают
                // видимую высоту шторки на невысоких экранах — без скролла "Сохранить" и
                // нижние ряды сетки были физически недостижимы.
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(categoryColorFor(selectedColor).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIconFor(selectedIcon),
                        contentDescription = null,
                        tint = categoryColorFor(selectedColor)
                    )
                }
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 14.dp)
                )
            }

            Text(
                text = "Цвет",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(CATEGORY_COLOR_CHOICES) { hex ->
                    val color = categoryColorFor(hex)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { selectedColor = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (hex.equals(selectedColor, ignoreCase = true)) {
                            Icon(AppIcons.Check, contentDescription = "Выбрано", tint = Color.White)
                        }
                    }
                }
            }

            Text(
                text = "Иконка",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.height(220.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CATEGORY_ICON_CHOICES, key = { it.first }) { (name, icon) ->
                    val isSelected = name == selectedIcon
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) categoryColorFor(selectedColor).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { selectedIcon = name },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = name,
                            tint = if (isSelected) categoryColorFor(selectedColor) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = { onSave(selectedIcon, selectedColor) },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
            ) {
                Text("Сохранить")
            }
        }
    }
}

/**
 * "+" на экране "Категории" — CLAUDE.md §5.2: своя категория расходов (не системная,
 * можно переименовать/удалить позже — в отличие от предустановленных 1..9).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategorySheet(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, iconName: String, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(CATEGORY_ICON_CHOICES.first().first) }
    var selectedColor by remember { mutableStateOf(CATEGORY_COLOR_CHOICES.first()) }
    val canSave = name.isNotBlank() && !isSaving

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(text = "Новая категория", style = MaterialTheme.typography.titleLarge)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(categoryColorFor(selectedColor).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIconFor(selectedIcon),
                        contentDescription = null,
                        tint = categoryColorFor(selectedColor)
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .weight(1f)
                )
            }

            Text(
                text = "Цвет",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(CATEGORY_COLOR_CHOICES) { hex ->
                    val color = categoryColorFor(hex)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { selectedColor = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (hex.equals(selectedColor, ignoreCase = true)) {
                            Icon(AppIcons.Check, contentDescription = "Выбрано", tint = Color.White)
                        }
                    }
                }
            }

            Text(
                text = "Иконка",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.height(220.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CATEGORY_ICON_CHOICES, key = { it.first }) { (iconName, icon) ->
                    val isSelected = iconName == selectedIcon
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) categoryColorFor(selectedColor).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { selectedIcon = iconName },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = iconName,
                            tint = if (isSelected) categoryColorFor(selectedColor) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = { onSave(name, selectedIcon, selectedColor) },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
            ) {
                Text("Создать")
            }
        }
    }
}
