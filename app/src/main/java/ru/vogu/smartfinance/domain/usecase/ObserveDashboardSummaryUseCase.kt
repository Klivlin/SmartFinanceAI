package ru.vogu.smartfinance.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.core.common.startOfDay
import ru.vogu.smartfinance.core.common.startOfMonth
import ru.vogu.smartfinance.core.common.startOfNextDay
import ru.vogu.smartfinance.core.common.startOfNextMonth
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.DashboardSummary
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.repository.ExchangeRateRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

/**
 * Данные для Collapsible Dashboard Header (CLAUDE.md §5.1): баланс, расходы за сегодня
 * и за текущий месяц с индикатором бюджета. Границы "сегодня"/"этот месяц" фиксируются
 * в момент подписки на [invoke] — полноценный live-пересчёт границ по таймеру на полуночи
 * выходит за рамки Шага 4 (экран пересоздаётся при возврате на него, чего достаточно
 * для учебного ВКР-проекта).
 */
class ObserveDashboardSummaryUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val exchangeRateRepository: ExchangeRateRepository
) {
    operator fun invoke(now: Instant = Instant.now()): Flow<DashboardSummary> {
        val dayStart = now.startOfDay()
        val dayEnd = now.startOfNextDay()
        val monthStart = now.startOfMonth()
        val monthEnd = now.startOfNextMonth()

        return combine(
            accountRepository.observeActive(),
            transactionRepository.observeByDateRange(dayStart, dayEnd),
            transactionRepository.observeByDateRange(monthStart, monthEnd),
            categoryRepository.observeBudgeted()
        ) { accounts, todayTransactions, monthTransactions, budgetedCategories ->
            val totalBalance = totalBalanceInBaseCurrency(accounts)

            val spentToday = todayTransactions.sumExpensesBase()
            val spentThisMonth = monthTransactions.sumExpensesBase()

            val monthlyBudgetTotal = budgetedCategories
                .mapNotNull { it.monthlyBudget }
                .takeIf { it.isNotEmpty() }
                ?.fold(BigDecimal.ZERO) { sum, budget -> sum + budget }

            DashboardSummary(
                totalBalance = totalBalance,
                baseCurrency = Constants.DEFAULT_BASE_CURRENCY,
                spentToday = spentToday,
                spentThisMonth = spentThisMonth,
                monthlyBudgetTotal = monthlyBudgetTotal
            )
        }
    }

    private fun List<Transaction>.sumExpensesBase(): BigDecimal =
        this.filter { it.transactionType == TransactionType.EXPENSE }
            .fold(BigDecimal.ZERO) { sum, transaction -> sum + transaction.amountBase }

    /**
     * Остаток по каждому счёту конвертируется в базовую валюту по реальному курсу (CLAUDE.md
     * §2.1) — в т.ч. крипто-счета. Курс кэшируется офлайн (см. [ExchangeRateRepository]), а
     * при сбое сети/отсутствии кэша для конкретной валюты счёт просто не учитывается в сумме,
     * а не роняет весь дашборд.
     */
    private suspend fun totalBalanceInBaseCurrency(accounts: List<Account>): BigDecimal =
        accounts.fold(BigDecimal.ZERO) { sum, account ->
            val converted = if (account.currency == Constants.DEFAULT_BASE_CURRENCY) {
                account.balance
            } else {
                runCatching {
                    exchangeRateRepository.convert(account.balance, account.currency, Constants.DEFAULT_BASE_CURRENCY)
                }.getOrNull()
            }
            sum + (converted ?: BigDecimal.ZERO)
        }
}
