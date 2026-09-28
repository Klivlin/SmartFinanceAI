package ru.vogu.smartfinance.domain.model

import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Промежуточный результат разбора пользовательского ввода (NLI) любым из трёх уровней
 * каскада — CLAUDE.md §3. Не привязан к конкретному счёту/категории по id: их
 * резолвит [ru.vogu.smartfinance.domain.usecase.ProcessNliInputUseCase] (Шаг 3),
 * т.к. парсеры — чистые функции без доступа к БД (Clean Architecture).
 */
data class ParsedExpense(
    val amount: BigDecimal,
    val title: String,
    val transactionType: TransactionType,

    /** Уровень каскада, который дал результат — для аналитики "сколько % операций стоят 0 токенов". */
    val source: TransactionSource,

    /** Сырое ключевое слово счёта ("тинька", "нал", "сбера"...), если упомянуто во вводе. */
    val accountHint: AccountHint? = null,

    /**
     * ID категории, если уже определена (Tier 2 semantic cache) или прямо возвращена LLM (Tier 3).
     * null — категория ещё не резолвлена (обычный случай для Tier 1 regex).
     */
    val categoryId: Long? = null,

    /** Дата операции. Regex-парсер всегда ставит "сегодня"; относительные даты ("вчера") — только через LLM. */
    val date: LocalDate = LocalDate.now(),

    /** Исходный текст пользователя (для сохранения в TransactionEntity.rawInputText). */
    val rawInputText: String? = null
)

/**
 * Ключевое слово, которым пользователь сослался на счёт списания, без резолва в конкретный
 * accountId — это делает репозиторий, сопоставляя с реальными именами/типами счетов.
 */
data class AccountHint(
    val rawKeyword: String,
    val normalizedKeyword: String
)
