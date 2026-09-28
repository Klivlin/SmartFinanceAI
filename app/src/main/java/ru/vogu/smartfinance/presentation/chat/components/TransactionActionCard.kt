package ru.vogu.smartfinance.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.core.ui.components.categoryColorFor
import ru.vogu.smartfinance.core.ui.components.categoryIconFor
import ru.vogu.smartfinance.core.ui.theme.amountColor
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.presentation.chat.ChatMessage
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
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(category = card.category)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = transaction.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = subtitleFor(card),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = "$sign${transaction.amount.formatAsCurrency(transaction.originalCurrency)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = amountColor(isIncome = isIncome)
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
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onUndo) {
                            Text("Отменить")
                        }
                        TextButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Изменить")
                        }
                        TextButton(onClick = onConfirm) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Подтвердить")
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
    val icon = category?.iconName?.let { categoryIconFor(it) } ?: Icons.Filled.Category
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = color.copy(alpha = 0.18f), shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = category?.name, tint = color)
    }
}

private fun subtitleFor(card: ChatMessage.TransactionCard): String {
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Locale("ru", "RU"))
        .format(card.transaction.timestamp.atZone(ZoneId.systemDefault()))
    val accountName = card.account?.name ?: "Счёт удалён"
    val sourceLabel = sourceLabelFor(card)
    return listOfNotNull(accountName, time, sourceLabel).joinToString(" · ")
}

private fun sourceLabelFor(card: ChatMessage.TransactionCard): String? =
    when (card.transaction.source) {
        TransactionSource.QR -> "по QR-чеку"
        TransactionSource.OCR -> "по фото чека"
        TransactionSource.MANUAL -> "вручную"
        TransactionSource.NLI -> null
    }
