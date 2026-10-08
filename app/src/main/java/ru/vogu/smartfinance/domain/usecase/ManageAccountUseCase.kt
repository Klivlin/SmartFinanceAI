package ru.vogu.smartfinance.domain.usecase

import kotlinx.coroutines.flow.first
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.CryptoAsset
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.ExchangeRateRepository
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
    private val transactionRepository: TransactionRepository,
    private val exchangeRateRepository: ExchangeRateRepository
) {
    /** true — код валюты валиден для данного типа счёта (тикер из [CryptoAsset] для CRYPTO, иначе 3-буквенный ISO 4217). */
    private fun isValidCurrencyCode(type: AccountType, code: String): Boolean =
        if (type == AccountType.CRYPTO) CryptoAsset.fromCode(code) != null else code.length == 3

    private fun currencyErrorMessage(type: AccountType): String =
        if (type == AccountType.CRYPTO) {
            "Выберите одну из поддерживаемых криптовалют."
        } else {
            "Код валюты должен состоять из 3 букв (например, RUB)."
        }

    suspend fun createAccount(
        name: String,
        type: AccountType,
        currency: String,
        initialBalance: BigDecimal,
        creditLimit: BigDecimal? = null,
        gracePeriodDays: Int? = null,
        statementDay: Int? = null,
        interestRatePercent: BigDecimal? = null
    ): Resource<Account> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Resource.Error("Укажите название счёта.")
        }
        val trimmedCurrency = currency.trim().uppercase()
        if (!isValidCurrencyCode(type, trimmedCurrency)) {
            return Resource.Error(currencyErrorMessage(type))
        }
        if (type == AccountType.CREDIT_CARD && (creditLimit == null || creditLimit.signum() <= 0)) {
            return Resource.Error("Для кредитной карты укажите кредитный лимит больше нуля.")
        }
        if (type == AccountType.CREDIT_CARD && statementDay != null && statementDay !in 1..31) {
            return Resource.Error("День формирования выписки должен быть от 1 до 31.")
        }
        if (type == AccountType.SAVINGS && interestRatePercent != null && interestRatePercent.signum() < 0) {
            return Resource.Error("Процентная ставка не может быть отрицательной.")
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
                interestRatePercent = if (type == AccountType.SAVINGS) interestRatePercent else null,
                isArchived = false,
                sortOrder = 0,
                createdAt = Instant.now()
            )
            val id = accountRepository.createAccount(account)
            // Если на момент создания не было ни одного основного счёта (например, все прежние
            // архивированы) — новый счёт становится основным сам, иначе Dashboard Header
            // (CLAUDE.md §5.1) остался бы без счёта для отображения до ручного выбора.
            val becamePrimary = accountRepository.observePrimary().first() == null
            if (becamePrimary) {
                accountRepository.setPrimaryAccount(id)
            }
            Resource.Success(account.copy(id = id, isPrimary = becamePrimary))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Не удалось создать счёт.", e)
        }
    }

    /**
     * Правка существующего счёта — CLAUDE.md §5.2 "Экран счетов: управление балансами".
     * В отличие от [createAccount] не трогает [Account.balance]: баланс — производная от
     * истории операций, а не редактируемое поле, чтобы правка названия/лимита случайно не
     * "обнулила" фактический остаток по счёту.
     */
    suspend fun updateAccount(
        accountId: Long,
        name: String,
        currency: String,
        creditLimit: BigDecimal? = null,
        gracePeriodDays: Int? = null,
        statementDay: Int? = null,
        interestRatePercent: BigDecimal? = null
    ): Resource<Account> {
        val existing = accountRepository.getById(accountId)
            ?: return Resource.Error("Счёт не найден.")

        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Resource.Error("Укажите название счёта.")
        }
        val trimmedCurrency = currency.trim().uppercase()
        if (!isValidCurrencyCode(existing.type, trimmedCurrency)) {
            return Resource.Error(currencyErrorMessage(existing.type))
        }
        if (existing.type == AccountType.CREDIT_CARD && (creditLimit == null || creditLimit.signum() <= 0)) {
            return Resource.Error("Для кредитной карты укажите кредитный лимит больше нуля.")
        }
        if (existing.type == AccountType.CREDIT_CARD && statementDay != null && statementDay !in 1..31) {
            return Resource.Error("День формирования выписки должен быть от 1 до 31.")
        }
        if (existing.type == AccountType.SAVINGS && interestRatePercent != null && interestRatePercent.signum() < 0) {
            return Resource.Error("Процентная ставка не может быть отрицательной.")
        }

        return try {
            // Смена валюты счёта (CLAUDE.md §2.1) пересчитывает остаток по РЕАЛЬНОМУ курсу на
            // момент сохранения, а не просто переклеивает ярлык валюты на прежнее число — иначе
            // "500" на долларовом счету после смены на RUB осталось бы "500 ₽" вместо ~47000 ₽.
            val convertedBalance = if (trimmedCurrency == existing.currency || existing.balance.signum() == 0) {
                existing.balance
            } else {
                exchangeRateRepository.convert(existing.balance, existing.currency, trimmedCurrency)
            }
            val updated = existing.copy(
                name = trimmedName,
                currency = trimmedCurrency,
                balance = convertedBalance,
                creditLimit = if (existing.type == AccountType.CREDIT_CARD) creditLimit else null,
                gracePeriodDays = if (existing.type == AccountType.CREDIT_CARD) gracePeriodDays else null,
                statementDay = if (existing.type == AccountType.CREDIT_CARD) statementDay else null,
                interestRatePercent = if (existing.type == AccountType.SAVINGS) interestRatePercent else null
            )
            accountRepository.updateAccount(updated)
            Resource.Success(updated)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Не удалось сохранить изменения счёта.", e)
        }
    }

    /** "Сделать основным" — CLAUDE.md §5.1: этот счёт будет свёрнуто показан на главном экране. */
    suspend fun setPrimaryAccount(accountId: Long): Resource<Unit> = try {
        accountRepository.setPrimaryAccount(accountId)
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Не удалось изменить основной счёт.", e)
    }

    suspend fun archiveAccount(accountId: Long): Resource<Unit> = try {
        val archived = accountRepository.getById(accountId)
        accountRepository.archiveAccount(accountId)
        // Если в архив ушёл ИМЕННО основной счёт — на главном экране иначе не осталось бы
        // никакого "основного" счёта для отображения; переносим флаг на следующий активный.
        if (archived?.isPrimary == true) {
            accountRepository.observeActive().first().firstOrNull()?.let { next ->
                accountRepository.setPrimaryAccount(next.id)
            }
        }
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
            val fromAccount = accountRepository.getById(fromAccountId)
                ?: return Resource.Error("Счёт списания не найден.")
            val toAccount = accountRepository.getById(toAccountId)
                ?: return Resource.Error("Счёт зачисления не найден.")

            // Перевод между счетами в разных валютах (в т.ч. крипто → рубли, CLAUDE.md §2.1) —
            // на счёт зачисления приходит НЕ та же цифра, а её эквивалент по реальному курсу.
            val toAmount = if (fromAccount.currency == toAccount.currency) {
                null
            } else {
                exchangeRateRepository.convert(amount, fromAccount.currency, toAccount.currency)
            }

            val transaction = transactionRepository.createTransaction(
                accountId = fromAccountId,
                toAccountId = toAccountId,
                categoryId = null,
                amount = amount,
                transactionType = TransactionType.TRANSFER,
                title = "Перевод между счетами",
                notes = note,
                source = TransactionSource.MANUAL,
                originalCurrency = fromAccount.currency,
                transferFeeAmount = feeAmount,
                toAmount = toAmount
            )
            Resource.Success(transaction)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Не удалось выполнить перевод.", e)
        }
    }
}
