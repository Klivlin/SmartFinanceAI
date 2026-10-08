package ru.vogu.smartfinance.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.core.ui.icons.AppIcons
import ru.vogu.smartfinance.core.ui.theme.heroContentColor
import ru.vogu.smartfinance.core.ui.theme.heroGradientBrush
import ru.vogu.smartfinance.core.ui.theme.warningColor
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.DashboardSummary
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Collapsible Dashboard Header — CLAUDE.md §5.1: на главном экране в свёрнутом виде показывает
 * только ОСНОВНОЙ счёт (выбирается на экране "Счета"), а по тапу разворачивается и показывает
 * ВСЕ счета пользователя вместе с расходами за сегодня/месяц и индикатором бюджета. Оформлена
 * как плавающая "hero"-карточка (в духе Apple Wallet): градиентный фон, крупная сумма,
 * скругление и мягкая тень.
 */
@Composable
fun DashboardHeader(
    summary: DashboardSummary?,
    primaryAccount: Account?,
    accounts: List<Account>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(elevation = 20.dp, shape = RoundedCornerShape(28.dp), ambientColor = Color(0x400A84FF), spotColor = Color(0x400A84FF))
            .clip(RoundedCornerShape(28.dp))
            .background(heroGradientBrush())
            // Лёгкий диагональный "глянец" сверху — вместо плоской заливки создаёт ощущение
            // стекла/премиального материала, а не флэт-дизайна по умолчанию.
            .background(
                Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                    start = Offset(0f, 0f),
                    end = Offset(0f, 260f)
                )
            )
            .clickable(onClick = onToggleExpand)
    ) {
        val content = heroContentColor()
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (primaryAccount != null) "ОСНОВНОЙ СЧЁТ · ${primaryAccount.name}" else "ОСНОВНОЙ СЧЁТ",
                        style = MaterialTheme.typography.labelSmall,
                        color = content.copy(alpha = 0.75f)
                    )
                    Text(
                        text = primaryAccount?.let { it.balance.formatAsCurrency(it.currency) } ?: "—",
                        style = MaterialTheme.typography.displaySmall,
                        color = content
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color = content.copy(alpha = 0.18f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Свернуть сводку" else "Развернуть сводку",
                        tint = content,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    if (accounts.size > 1) {
                        Column(modifier = Modifier.padding(bottom = 14.dp)) {
                            Text(
                                text = "ВСЕ СЧЕТА",
                                style = MaterialTheme.typography.labelSmall,
                                color = content.copy(alpha = 0.75f)
                            )
                            Column(modifier = Modifier.padding(top = 6.dp)) {
                                accounts.forEach { account ->
                                    AccountSummaryRow(account = account, contentColor = content)
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SpendingStat(
                            modifier = Modifier.weight(1f),
                            label = "Сегодня",
                            amountText = summary?.let { it.spentToday.formatAsCurrency(it.baseCurrency) } ?: "—",
                            contentColor = content
                        )
                        SpendingStat(
                            modifier = Modifier.weight(1f),
                            label = "За месяц",
                            amountText = summary?.let { it.spentThisMonth.formatAsCurrency(it.baseCurrency) } ?: "—",
                            contentColor = content
                        )
                    }

                    val budgetPercent = summary?.budgetUsagePercent
                    if (summary != null && budgetPercent != null) {
                        val progressColor = when {
                            summary.isBudgetExceeded -> Color(0xFFFF6961)
                            summary.isBudgetWarning -> warningColor()
                            else -> content
                        }
                        Column(modifier = Modifier.padding(top = 14.dp)) {
                            Text(
                                text = "Бюджет по категориям: $budgetPercent%",
                                style = MaterialTheme.typography.labelMedium,
                                color = content.copy(alpha = 0.85f)
                            )
                            LinearProgressIndicator(
                                progress = { (budgetPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = progressColor,
                                trackColor = content.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Строка одного счёта в развёрнутом Dashboard Header — CLAUDE.md §5.1 ("при раскрытии — все счета"). */
@Composable
private fun AccountSummaryRow(account: Account, contentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = iconForAccountType(account.type),
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (account.isPrimary) "${account.name} · Основной" else account.name,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.9f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Text(
            text = account.balance.formatAsCurrency(account.currency),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
    }
}

private fun iconForAccountType(type: AccountType): ImageVector = when (type) {
    AccountType.DEBIT_CARD -> AppIcons.Card
    AccountType.CASH -> AppIcons.Cash
    AccountType.SAVINGS -> AppIcons.Coins
    AccountType.CREDIT_CARD -> AppIcons.Card
    AccountType.CRYPTO -> AppIcons.Crypto
}

@Composable
private fun SpendingStat(label: String, amountText: String, contentColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = contentColor.copy(alpha = 0.14f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.75f)
            )
            Text(
                text = amountText,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
        }
    }
}
