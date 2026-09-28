package ru.vogu.smartfinance.data.mapper

import ru.vogu.smartfinance.core.database.entity.AccountEntity
import ru.vogu.smartfinance.core.database.entity.CategoryEntity
import ru.vogu.smartfinance.core.database.entity.SplitItemEntity
import ru.vogu.smartfinance.core.database.entity.TransactionEntity
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.SplitItem
import ru.vogu.smartfinance.domain.model.Transaction

// Room Entity <-> Domain model. Держим их раздельными типами (а не переиспользуем Entity как
// domain-модель) сознательно — это позволяет менять схему БД, не трогая presentation/usecase слои.

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    type = type,
    balance = balance,
    currency = currency,
    isCredit = isCredit,
    creditLimit = creditLimit,
    gracePeriodDays = gracePeriodDays,
    statementDay = statementDay,
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAt = createdAt
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    type = type,
    balance = balance,
    currency = currency,
    isCredit = isCredit,
    creditLimit = creditLimit,
    gracePeriodDays = gracePeriodDays,
    statementDay = statementDay,
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAt = createdAt
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    iconName = iconName,
    colorHex = colorHex,
    monthlyBudget = monthlyBudget,
    isIncomeCategory = isIncomeCategory,
    isSystem = isSystem,
    sortOrder = sortOrder
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    iconName = iconName,
    colorHex = colorHex,
    monthlyBudget = monthlyBudget,
    isIncomeCategory = isIncomeCategory,
    isSystem = isSystem,
    sortOrder = sortOrder
)

fun SplitItemEntity.toDomain(): SplitItem = SplitItem(
    id = id,
    transactionId = transactionId,
    categoryId = categoryId,
    name = name,
    amount = amount
)

fun SplitItem.toEntity(): SplitItemEntity = SplitItemEntity(
    id = id,
    transactionId = transactionId,
    categoryId = categoryId,
    name = name,
    amount = amount
)

fun TransactionEntity.toDomain(splitItems: List<SplitItem> = emptyList()): Transaction = Transaction(
    id = id,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    amount = amount,
    originalCurrency = originalCurrency,
    exchangeRate = exchangeRate,
    amountBase = amountBase,
    transactionType = transactionType,
    title = title,
    notes = notes,
    timestamp = timestamp,
    source = source,
    rawInputText = rawInputText,
    transferFeeAmount = transferFeeAmount,
    recurringRuleId = recurringRuleId,
    isSplit = isSplit,
    splitItems = splitItems,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    amount = amount,
    originalCurrency = originalCurrency,
    exchangeRate = exchangeRate,
    amountBase = amountBase,
    transactionType = transactionType,
    title = title,
    notes = notes,
    timestamp = timestamp,
    source = source,
    rawInputText = rawInputText,
    transferFeeAmount = transferFeeAmount,
    recurringRuleId = recurringRuleId,
    isSplit = isSplit,
    createdAt = createdAt,
    updatedAt = updatedAt
)
