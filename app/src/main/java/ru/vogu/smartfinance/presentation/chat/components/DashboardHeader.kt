package ru.vogu.smartfinance.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.domain.model.DashboardSummary

/**
 * Collapsible Dashboard Header — CLAUDE.md §5.1: баланс, расходы за сегодня/месяц
 * с индикатором бюджета. Сворачивается тапом, чтобы освободить место для ленты чата.
 */
@Composable
fun DashboardHeader(
    summary: DashboardSummary?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Общий баланс",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = summary?.totalBalance?.formatAsCurrency(summary.baseCurrency) ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Свернуть сводку" else "Развернуть сводку",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SpendingStat(
                            label = "Сегодня",
                            amountText = summary?.let { it.spentToday.formatAsCurrency(it.baseCurrency) } ?: "—"
                        )
                        SpendingStat(
                            label = "За месяц",
                            amountText = summary?.let { it.spentThisMonth.formatAsCurrency(it.baseCurrency) } ?: "—"
                        )
                    }

                    val budgetPercent = summary?.budgetUsagePercent
                    if (summary != null && budgetPercent != null) {
                        val progressColor = when {
                            summary.isBudgetExceeded -> MaterialTheme.colorScheme.error
                            summary.isBudgetWarning -> Color(0xFFF9A825)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Text(
                                text = "Бюджет по категориям: $budgetPercent%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            LinearProgressIndicator(
                                progress = { (budgetPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                color = progressColor,
                                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendingStat(label: String, amountText: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = amountText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
