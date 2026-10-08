package ru.vogu.smartfinance.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.core.ui.components.DuotoneIconBadge
import ru.vogu.smartfinance.core.ui.components.PremiumCard
import ru.vogu.smartfinance.core.ui.components.categoryColorFor
import ru.vogu.smartfinance.core.ui.components.categoryIconFor
import ru.vogu.smartfinance.core.ui.icons.AppIcons
import ru.vogu.smartfinance.core.ui.theme.amountColor
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.SplitItem
import ru.vogu.smartfinance.presentation.chat.ChatMessage
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Action Card — CLAUDE.md §5.1: интерактивная карточка вместо текстового ответа системы.
 * Появляется с анимацией масштаба+прозрачности (эффект "система только что ответила"),
 * кнопки действий сворачиваются с анимацией после "Подтвердить".
 */
@Composable
fun TransactionActionCard(
    card: ChatMessage.TransactionCard,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transaction = card.transaction
    val isIncome = transaction.transactionType == TransactionType.INCOME
    val sign = when (transaction.transactionType) {
        TransactionType.INCOME -> "+"
        TransactionType.EXPENSE, TransactionType.TRANSFER, TransactionType.ADJUSTMENT -> "−"
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.9f)
    ) {
        PremiumCard(modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Для сплит-чека (сканер, CLAUDE.md §4.2) сама транзакция не хранит
                    // category_id (§2.3) — если все позиции всё же попали в одну категорию,
                    // это полезнее показать иконкой бейджа, чем универсальный "чек".
                    CategoryBadge(
                        category = card.category
                            ?: card.splitItemCategories.filterNotNull().distinctBy { it.id }.singleOrNull()
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = transaction.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitleFor(card),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "$sign${transaction.amount.formatAsCurrency(transaction.originalCurrency)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = amountColor(isIncome = isIncome)
                    )
                }

                // Разбивка сплит-чека по категориям (сканер, CLAUDE.md §2.3/§4.2) — без этого
                // пользователь видел бы только обезличенное "Чек (N позиций)" на общую сумму,
                // хотя каждая позиция уже категоризирована через Gemini.
                if (transaction.isSplit && transaction.splitItems.isNotEmpty()) {
                    SplitCategoryBreakdown(
                        splitItems = transaction.splitItems,
                        itemCategories = card.splitItemCategories,
                        currency = transaction.originalCurrency,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                AnimatedVisibility(
                    visible = !card.isConfirmed,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                .clickable(onClick = onUndo),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Отменить",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = onEdit),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Изменить",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        androidx.compose.material3.Button(
                            onClick = onConfirm,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Подтвердить", maxLines = 1)
                        }
                    }
                }

                // После "Подтвердить" полный ряд кнопок сворачивается (выше), но удалить
                // операцию нужно уметь всегда — иначе ошибочную/дублирующую запись, которую
                // уже успели подтвердить, было бы физически нечем убрать из ленты чата.
                AnimatedVisibility(
                    visible = card.isConfirmed,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    // Box+clickable вместо IconButton/TextButton — у них есть скрытая область
                    // "минимального тап-таргета" (48dp) вокруг маленькой иконки, которая
                    // раздвигала ряд кнопок визуально ниже, чем задано отступом top-padding.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = onEdit),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Изменить",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onUndo)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Удалить",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: Category?) {
    val color = category?.colorHex?.let { categoryColorFor(it) } ?: Color(0xFF9E9E9E)
    val icon = category?.iconName?.let { categoryIconFor(it) } ?: AppIcons.Other
    DuotoneIconBadge(color = color, size = 44.dp) {
        Icon(imageVector = icon, contentDescription = category?.name, tint = color, modifier = Modifier.size(22.dp))
    }
}

/** Группирует позиции сплит-чека по категории и показывает по одному чипу с суммой на категорию. */
@Composable
private fun SplitCategoryBreakdown(
    splitItems: List<SplitItem>,
    itemCategories: List<Category?>,
    currency: String,
    modifier: Modifier = Modifier
) {
    val groups = splitItems.indices
        .groupBy { index -> itemCategories.getOrNull(index)?.id }
        .map { (_, indices) ->
            val category = itemCategories.getOrNull(indices.first())
            val total = indices.fold(BigDecimal.ZERO) { sum, index -> sum + splitItems[index].amount }
            category to total
        }
        .sortedByDescending { it.second }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(groups) { (category, total) ->
            CategoryChip(category = category, amount = total, currency = currency)
        }
    }
}

@Composable
private fun CategoryChip(category: Category?, amount: BigDecimal, currency: String) {
    val color = category?.colorHex?.let { categoryColorFor(it) } ?: Color(0xFF9E9E9E)
    val icon = category?.iconName?.let { categoryIconFor(it) } ?: AppIcons.Other
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${category?.name ?: "Другое"} · ${amount.formatAsCurrency(currency)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

private fun subtitleFor(card: ChatMessage.TransactionCard): String {
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Locale("ru", "RU"))
        .format(card.transaction.timestamp.atZone(ZoneId.systemDefault()))
    // Название категории рядом с её иконкой — иначе по одной иконке-бейджу не всегда понятно,
    // в какую именно категорию ушла операция (особенно для похожих по цвету пользовательских).
    val categoryName = card.category?.name
        ?: card.splitItemCategories.filterNotNull().distinctBy { it.id }.singleOrNull()?.name
    val accountName = card.account?.name ?: "Счёт удалён"
    val sourceLabel = sourceLabelFor(card)
    return listOfNotNull(categoryName, accountName, time, sourceLabel).joinToString(" · ")
}

private fun sourceLabelFor(card: ChatMessage.TransactionCard): String? =
    when (card.transaction.source) {
        TransactionSource.QR -> "по QR-чеку"
        TransactionSource.OCR -> "по фото чека"
        TransactionSource.MANUAL -> "вручную"
        TransactionSource.NLI -> null
    }
