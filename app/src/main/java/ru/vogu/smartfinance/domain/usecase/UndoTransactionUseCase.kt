package ru.vogu.smartfinance.domain.usecase

import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import javax.inject.Inject

/**
 * "Отменить" на Action Card — CLAUDE.md §5.1. Транзакция уже сохранена оптимистично в момент
 * создания (см. [ProcessNliInputUseCase]), поэтому Undo — это полноценное удаление записи
 * с атомарным откатом баланса затронутого(ых) счёта(ов) (см. TransactionRepositoryImpl),
 * а не отклонение ещё не сохранённого черновика.
 */
class UndoTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(transactionId: Long): Resource<Unit> = try {
        transactionRepository.deleteTransaction(transactionId)
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Не удалось отменить операцию.", e)
    }
}
