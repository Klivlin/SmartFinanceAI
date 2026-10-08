package ru.vogu.smartfinance.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.vogu.smartfinance.core.database.dao.AccountDao
import ru.vogu.smartfinance.core.database.entity.AccountEntity
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.domain.model.AccountHint
import java.math.BigDecimal
import java.time.Instant

class AccountRepositoryImplTest {

    private val accountDao: AccountDao = mockk()
    private val repository = AccountRepositoryImpl(accountDao)

    private fun account(id: Long, name: String, type: AccountType, currency: String, isPrimary: Boolean = false) =
        AccountEntity(
            id = id, name = name, type = type, balance = BigDecimal("1000"), currency = currency,
            isCredit = false, isPrimary = isPrimary, createdAt = Instant.now()
        )

    @Test
    fun `specific crypto ticker resolves to the account with that exact currency`() = runTest {
        val btcAccount = account(1L, "Крипто BTC", AccountType.CRYPTO, "BTC")
        val usdtAccount = account(2L, "Крипто USDT", AccountType.CRYPTO, "USDT")
        val mainAccount = account(3L, "Основной", AccountType.DEBIT_CARD, "RUB")
        coEvery { accountDao.getActiveOnce() } returns listOf(mainAccount, btcAccount, usdtAccount)

        val result = repository.findBestMatch(AccountHint(rawKeyword = "юсдт", normalizedKeyword = "usdt"))

        assertEquals(usdtAccount.id, result.id)
    }

    @Test
    fun `crypto ticker with no matching account falls back to the default account rather than a mismatched coin`() = runTest {
        val btcAccount = account(1L, "Крипто BTC", AccountType.CRYPTO, "BTC")
        val mainAccount = account(2L, "Основной", AccountType.DEBIT_CARD, "RUB")
        coEvery { accountDao.getActiveOnce() } returns listOf(mainAccount, btcAccount)

        val result = repository.findBestMatch(AccountHint(rawKeyword = "юсдт", normalizedKeyword = "usdt"))

        assertEquals(mainAccount.id, result.id)
    }

    @Test
    fun `generic crypto hint resolves to any active crypto account`() = runTest {
        val btcAccount = account(1L, "Крипто", AccountType.CRYPTO, "BTC")
        val mainAccount = account(2L, "Основной", AccountType.DEBIT_CARD, "RUB")
        coEvery { accountDao.getActiveOnce() } returns listOf(mainAccount, btcAccount)

        val result = repository.findBestMatch(AccountHint(rawKeyword = "крипта", normalizedKeyword = "crypto"))

        assertEquals(btcAccount.id, result.id)
    }
}
