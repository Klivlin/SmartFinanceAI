package ru.vogu.smartfinance.domain.model

import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import java.math.BigDecimal
import java.time.Instant

data class Transaction(
    val id: Long,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
    val amount: BigDecimal,
    val originalCurrency: String,
    val exchangeRate: BigDecimal,
    val amountBase: BigDecimal,
    val transactionType: TransactionType,
    val title: String,
    val notes: String?,
    val timestamp: Instant,
    val source: TransactionSource,
    val rawInputText: String?,
    val transferFeeAmount: BigDecimal?,
    val recurringRuleId: Long?,
    val isSplit: Boolean,
    val splitItems: List<SplitItem> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant
)

data class SplitItem(
    val id: Long,
    val transactionId: Long,
    val categoryId: Long?,
    val name: String,
    val amount: BigDecimal
)
