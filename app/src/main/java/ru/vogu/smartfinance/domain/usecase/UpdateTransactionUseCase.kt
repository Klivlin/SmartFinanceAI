package ru.vogu.smartfinance.domain.usecase

import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.common.isNonPositive
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import javax.inject.Inject

/**
 * "Изменить" на Action Card (открывает BottomSheet) — CLAUDE.md §5.1, валидация краевых
 * случаев — §9.4 (отрицательные/нулевые суммы, пустой ввод). Пересчёт баланса при правке
 * суммы/счёта/типа выполняется атомарно внутри [TransactionRepository.updateTransaction].
 */
class UpdateTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(transaction: Transaction): Resource<Transaction> {
        if (transaction.amount.isNonPositive()) {
            return Resource.Error("Сумма операции должна быть больше нуля.")
        }
        if (transaction.title.isBlank()) {
            return Resource.Error("Укажите название операции.")
        }
        if (transaction.transactionType == TransactionType.TRANSFER) {
            if (transaction.toAccountId == null) {
                return Resource.Error("Для перевода укажите счёт-получатель.")
            }
            if (transaction.toAccountId == transaction.accountId) {
                return Resource.Error("Счёт списания и счёт зачисления не могут совпадать.")
            }
        }

        return try {
            transactionRepository.updateTransaction(transaction)
            Resource.Success(transaction)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Не удалось сохранить изменения.", e)
        }
    }
}
