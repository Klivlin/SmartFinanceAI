package ru.vogu.smartfinance.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.AccountHint
import java.math.BigDecimal

interface AccountRepository {
    fun observeActive(): Flow<List<Account>>
    fun observeAll(): Flow<List<Account>>
    suspend fun getById(id: Long): Account?

    /** Счёт, свёрнуто отображаемый на главном Chat-экране (CLAUDE.md §5.1). */
    fun observePrimary(): Flow<Account?>

    /** Делает [accountId] основным для отображения на главном экране; снимает флаг с прежнего основного счёта. */
    suspend fun setPrimaryAccount(accountId: Long)

    /**
     * Находит наиболее подходящий счёт под подсказку из NLI ("нал", "тинька"...).
     * Если [hint] == null или совпадений нет — возвращает счёт по умолчанию (первый активный).
     */
    suspend fun findBestMatch(hint: AccountHint?): Account

    suspend fun createAccount(account: Account): Long
    suspend fun updateAccount(account: Account)
    suspend fun archiveAccount(id: Long)
    suspend fun adjustBalance(accountId: Long, delta: BigDecimal)
}
