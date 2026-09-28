package ru.vogu.smartfinance.domain.usecase

import ru.vogu.smartfinance.core.common.DefaultCategories
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.common.normalizeAsAliasKeyword
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.ParsedExpense
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.NliExpenseRepository
import ru.vogu.smartfinance.domain.repository.SemanticAliasRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Точка входа для главного Chat-экрана (Шаг 4) — CLAUDE.md §9.5 Шаг 3.
 * Полный путь: сырой текст пользователя → [NliExpenseRepository] (каскад Tier1/2/3) →
 * резолв счёта/категории в конкретные id → атомарное создание транзакции(й) →
 * дообучение семантического кэша.
 *
 * Транзакции создаются сразу (оптимистично) — это соответствует UX из §5.1, где Action Card
 * с кнопкой "Отменить" уже показывает СОЗДАННУЮ операцию, а "Отменить" удаляет её (Undo),
 * а не отклоняет ещё не сохранённый черновик.
 */
class ProcessNliInputUseCase @Inject constructor(
    private val nliExpenseRepository: NliExpenseRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val semanticAliasRepository: SemanticAliasRepository
) {
    suspend operator fun invoke(rawInputText: String): Resource<List<Transaction>> {
        return when (val parseResult = nliExpenseRepository.parseUserInput(rawInputText)) {
            is Resource.Error -> Resource.Error(parseResult.message, parseResult.cause)
            Resource.Loading -> Resource.Loading
            is Resource.Success -> persistAll(parseResult.data)
        }
    }

    private suspend fun persistAll(parsedExpenses: List<ParsedExpense>): Resource<List<Transaction>> {
        if (parsedExpenses.isEmpty()) {
            return Resource.Error("Не удалось распознать ни одной операции.")
        }

        val created = mutableListOf<Transaction>()
        for (expense in parsedExpenses) {
            created += persistOne(expense)
        }
        return Resource.Success(created)
    }

    private suspend fun persistOne(expense: ParsedExpense): Transaction {
        // NLI-парсеры (Tier1/regex, Tier3/LLM) сегодня извлекают только ОДНУ подсказку счёта
        // из фразы, поэтому TRANSFER (требующий счёт-источник И счёт-получатель одновременно)
        // через чат надёжно не резолвится — понижаем до EXPENSE в категорию "Переводы", чтобы
        // не создать в БД перевод с отсутствующим счётом-получателем. Полноценные переводы
        // между своими счетами — через экран "Счета" (ManageAccountUseCase, Шаг 6).
        val effectiveType = if (expense.transactionType == TransactionType.TRANSFER) {
            TransactionType.EXPENSE
        } else {
            expense.transactionType
        }

        val account = accountRepository.findBestMatch(expense.accountHint)
        val categoryId = expense.categoryId ?: defaultCategoryIdFor(effectiveType)

        // У Gemini/regex нет времени суток — только дата. Для "сегодня" берём текущий момент
        // (чтобы операция встала в ленте по реальному времени ввода), для прошлых дат — 00:00.
        val timestamp = if (expense.date == LocalDate.now()) {
            Instant.now()
        } else {
            expense.date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        }

        val transaction = transactionRepository.createTransaction(
            accountId = account.id,
            categoryId = categoryId,
            amount = expense.amount,
            transactionType = effectiveType,
            title = expense.title,
            timestamp = timestamp,
            source = expense.source,
            rawInputText = expense.rawInputText
        )

        // Дообучение Tier 2: если категорию определил Tier 3 (LLM), запоминаем связку
        // "ключевое слово -> категория" на будущее — CLAUDE.md §3.2. Для Tier 1 такого шага
        // нет, т.к. он категорию не резолвит (только Tier 2/Tier 3 это делают).
        if (expense.source == TransactionSource.NLI && expense.categoryId != null) {
            val keyword = expense.title.normalizeAsAliasKeyword()
            if (keyword.isNotEmpty()) {
                semanticAliasRepository.upsert(
                    normalizedKeyword = keyword,
                    categoryId = categoryId,
                    defaultAccountId = account.id
                )
            }
        }

        return transaction
    }

    private fun defaultCategoryIdFor(type: TransactionType): Long = when (type) {
        TransactionType.INCOME -> DefaultCategories.INCOME_CATEGORY_ID
        TransactionType.TRANSFER -> DefaultCategories.TRANSFER_CATEGORY_ID
        TransactionType.EXPENSE, TransactionType.ADJUSTMENT -> DefaultCategories.OTHER_CATEGORY_ID
    }
}
