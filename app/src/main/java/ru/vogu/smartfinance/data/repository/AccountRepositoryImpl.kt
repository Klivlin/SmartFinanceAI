package ru.vogu.smartfinance.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.core.database.dao.AccountDao
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.data.mapper.toDomain
import ru.vogu.smartfinance.data.mapper.toEntity
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.AccountHint
import ru.vogu.smartfinance.domain.repository.AccountRepository
import java.math.BigDecimal
import javax.inject.Inject

class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao
) : AccountRepository {

    override fun observeActive(): Flow<List<Account>> =
        accountDao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Account>> =
        accountDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Account? = accountDao.getById(id)?.toDomain()

    override suspend fun findBestMatch(hint: AccountHint?): Account {
        val activeAccounts = accountDao.getActiveOnce()
        val fallback = activeAccounts.firstOrNull { it.name == Constants.DEFAULT_ACCOUNT_NAME }
            ?: activeAccounts.firstOrNull()
            ?: error("В базе нет ни одного активного счёта — нарушен инвариант сидирования (Шаг 1).")

        if (hint == null) return fallback.toDomain()

        val match = when (hint.normalizedKeyword) {
            "cash" -> activeAccounts.firstOrNull { it.type == AccountType.CASH }
            else -> activeAccounts.firstOrNull { account ->
                val nameLower = account.name.lowercase()
                nameLower.contains(hint.normalizedKeyword) || nameLower.contains(hint.rawKeyword.lowercase())
            }
        }

        return (match ?: fallback).toDomain()
    }

    override suspend fun createAccount(account: Account): Long = accountDao.insert(account.toEntity())

    override suspend fun updateAccount(account: Account) = accountDao.update(account.toEntity())

    override suspend fun archiveAccount(id: Long) = accountDao.archive(id)

    override suspend fun adjustBalance(accountId: Long, delta: BigDecimal) =
        accountDao.adjustBalance(accountId, delta)
}
