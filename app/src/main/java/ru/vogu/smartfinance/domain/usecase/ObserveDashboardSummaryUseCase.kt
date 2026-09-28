package ru.vogu.smartfinance.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.core.common.startOfDay
import ru.vogu.smartfinance.core.common.startOfMonth
import ru.vogu.smartfinance.core.common.startOfNextDay
import ru.vogu.smartfinance.core.common.startOfNextMonth
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.DashboardSummary
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.CategoryRepository
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
    private val categoryRepository: CategoryRepository
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
            // Общий баланс считаем только по счетам в базовой валюте — конвертация остатков
            // мультивалютных счетов в реальном времени требует сервиса курсов ЦБ, которого
            // в проекте пока нет (каждая ОТДЕЛЬНАЯ операция уже конвертируется через
            // Transaction.amountBase на момент её создания — см. ProcessNliInputUseCase/
            // TransactionRepositoryImpl, это не связано с текущим остатком по счёту).
            val totalBalance = accounts
                .filter { it.currency == Constants.DEFAULT_BASE_CURRENCY }
                .fold(BigDecimal.ZERO) { sum, account -> sum + account.balance }

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
}
