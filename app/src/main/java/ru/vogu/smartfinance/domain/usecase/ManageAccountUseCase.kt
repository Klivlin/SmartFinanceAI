package ru.vogu.smartfinance.domain.usecase

import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

/**
 * Управление счетами — CLAUDE.md §5.2 ("Экран счетов: управление балансами, перевод между
 * счетами"). Создание/архивация — тонкие обёртки над [AccountRepository] с валидацией
 * (CLAUDE.md §9.4); перевод делегирует [TransactionRepository.createTransaction] с
 * transactionType = TRANSFER, которая уже атомарно списывает/зачисляет на оба счёта.
 */
class ManageAccountUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) {
    suspend fun createAccount(
        name: String,
        type: AccountType,
        currency: String,
        initialBalance: BigDecimal,
        creditLimit: BigDecimal? = null,
        gracePeriodDays: Int? = null,
        statementDay: Int? = null
    ): Resource<Account> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Resource.Error("Укажите название счёта.")
        }
        val trimmedCurrency = currency.trim().uppercase()
        if (trimmedCurrency.length != 3) {
            return Resource.Error("Код валюты должен состоять из 3 букв (например, RUB).")
        }
        if (type == AccountType.CREDIT_CARD && (creditLimit == null || creditLimit.signum() <= 0)) {
            return Resource.Error("Для кредитной карты укажите кредитный лимит больше нуля.")
        }
        if (type == AccountType.CREDIT_CARD && statementDay != null && statementDay !in 1..31) {
            return Resource.Error("День формирования выписки должен быть от 1 до 31.")
        }

        return try {
            val account = Account(
                id = 0L,
                name = trimmedName,
                type = type,
                balance = initialBalance,
                currency = trimmedCurrency,
                isCredit = type == AccountType.CREDIT_CARD,
                creditLimit = if (type == AccountType.CREDIT_CARD) creditLimit else null,
                gracePeriodDays = if (type == AccountType.CREDIT_CARD) gracePeriodDays else null,
                statementDay = if (type == AccountType.CREDIT_CARD) statementDay else null,
                isArchived = false,
                sortOrder = 0,
                createdAt = Instant.now()
            )
            val id = accountRepository.createAccount(account)
            Resource.Success(account.copy(id = id))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Не удалось создать счёт.", e)
        }
    }

    suspend fun archiveAccount(accountId: Long): Resource<Unit> = try {
        accountRepository.archiveAccount(accountId)
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Не удалось архивировать счёт.", e)
    }

    suspend fun transfer(
        fromAccountId: Long,
        toAccountId: Long,
        amount: BigDecimal,
        feeAmount: BigDecimal? = null,
        note: String? = null
    ): Resource<Transaction> {
        if (amount.signum() <= 0) {
            return Resource.Error("Сумма перевода должна быть больше нуля.")
        }
        if (fromAccountId == toAccountId) {
            return Resource.Error("Счёт списания и счёт зачисления не могут совпадать.")
        }
        if (feeAmount != null && feeAmount.signum() < 0) {
            return Resource.Error("Комиссия перевода не может быть отрицательной.")
        }

        return try {
            val transaction = transactionRepository.createTransaction(
                accountId = fromAccountId,
                toAccountId = toAccountId,
                categoryId = null,
                amount = amount,
                transactionType = TransactionType.TRANSFER,
                title = "Перевод между счетами",
                notes = note,
                source = TransactionSource.MANUAL,
                transferFeeAmount = feeAmount
            )
            Resource.Success(transaction)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Не удалось выполнить перевод.", e)
        }
    }
}
