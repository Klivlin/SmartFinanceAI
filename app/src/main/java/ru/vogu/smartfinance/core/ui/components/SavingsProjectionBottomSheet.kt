package ru.vogu.smartfinance.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.vogu.smartfinance.core.common.SAVINGS_PROJECTION_HORIZONS_MONTHS
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.core.common.projectSavingsGrowth
import ru.vogu.smartfinance.domain.model.Account

/**
 * Калькулятор прогноза накоплений — CLAUDE.md §2.1: "сколько денег накапает через месяц,
 * 2, 3 и т.д." при текущем балансе и ставке счёта. Переиспользуется и на экране "Счета",
 * и прямо в "Аналитике" — CLAUDE.md §5.2 (не заставлять пользователя искать накопительный
 * счёт по всему приложению).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsProjectionBottomSheet(account: Account, onDismiss: () -> Unit) {
    val rate = account.interestRatePercent

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(text = "Прогноз накоплений", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "«${account.name}» · текущий баланс ${account.balance.formatAsCurrency(account.currency)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (rate == null || rate.signum() <= 0) {
                Text(
                    text = "У счёта не задана процентная ставка — прогноз недоступен. " +
                        "Укажите её в поле «Ставка, % годовых» при создании накопительного счёта.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 20.dp)
                )
                return@ModalBottomSheet
            }

            Text(
                text = "Ставка ${rate.stripTrailingZeros().toPlainString()}% годовых, ежемесячная капитализация. " +
                    "Прогноз не учитывает будущие пополнения/списания — только рост текущего баланса.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            val projections = remember(account.balance, rate) {
                projectSavingsGrowth(account.balance, rate, SAVINGS_PROJECTION_HORIZONS_MONTHS)
            }

            Column(modifier = Modifier.padding(top = 16.dp)) {
                projections.forEach { point ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = savingsMonthsLabel(point.months),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = point.projectedBalance.formatAsCurrency(account.currency),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

fun savingsMonthsLabel(months: Int): String = when {
    months == 1 -> "Через 1 месяц"
    months % 12 == 0 -> "Через ${months / 12} " + if (months / 12 == 1) "год" else "года"
    else -> "Через $months месяцев"
}
