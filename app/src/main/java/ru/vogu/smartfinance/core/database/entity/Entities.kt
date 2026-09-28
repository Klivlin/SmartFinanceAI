package ru.vogu.smartfinance.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.ForeignKey.Companion.RESTRICT
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Полная реляционная схема БД — см. CLAUDE.md §7.
 * Все денежные суммы хранятся как [BigDecimal] (конвертируются в String, см. Converters.kt),
 * чтобы избежать ошибок округления с плавающей точкой при работе с деньгами.
 */

// ──────────────────────────── Enums ────────────────────────────

/** Тип счёта — CLAUDE.md §2.1. */
enum class AccountType {
    DEBIT_CARD,
    CASH,
    SAVINGS,
    CREDIT_CARD
}

/** Тип операции — CLAUDE.md §2.2. */
enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER,
    ADJUSTMENT
}

/** Источник создания транзакции — из какого модуля она пришла. */
enum class TransactionSource {
    /** Natural Language Interface (чат, текст или голос) — CLAUDE.md §3. */
    NLI,

    /** Кассовый QR-код (ФНС) — CLAUDE.md §4.1. */
    QR,

    /** OCR-распознавание бумажного чека — CLAUDE.md §4.2. */
    OCR,

    /** Ручной ввод через форму (BottomSheet редактирования). */
    MANUAL
}

/** Периодичность регулярного платежа — CLAUDE.md §2.4. */
enum class RecurringPeriod {
    WEEKLY,
    MONTHLY,
    YEARLY
}

// ──────────────────────────── accounts ────────────────────────────

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: AccountType,

    /** Текущий баланс в валюте счёта (не в базовой валюте!). */
    @ColumnInfo(name = "balance")
    val balance: BigDecimal,

    /** ISO 4217, например "RUB", "USD", "EUR". */
    @ColumnInfo(name = "currency")
    val currency: String,

    /** true для CREDIT_CARD — дублирует [type], используется для быстрых выборок без сравнения enum. */
    @ColumnInfo(name = "is_credit")
    val isCredit: Boolean = false,

    /** Кредитный лимит (только для CREDIT_CARD). */
    @ColumnInfo(name = "credit_limit")
    val creditLimit: BigDecimal? = null,

    /** Длительность беспроцентного периода в днях (только для CREDIT_CARD). */
    @ColumnInfo(name = "grace_period_days")
    val gracePeriodDays: Int? = null,

    /** День месяца формирования выписки по кредитной карте (1..31), для расчёта grace period. */
    @ColumnInfo(name = "statement_day")
    val statementDay: Int? = null,

    /** Архивные счета скрываются из выбора, но их история и баланс сохраняются (см. FK RESTRICT ниже). */
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now()
)

// ──────────────────────────── categories ────────────────────────────

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    /** Имя иконки Material Symbols, см. core/ui/components. */
    @ColumnInfo(name = "icon_name")
    val iconName: String,

    /** HEX-цвет, например "#4CAF50". */
    @ColumnInfo(name = "color_hex")
    val colorHex: String,

    /** Месячный лимит бюджета по категории — CLAUDE.md §2.5. null = лимит не задан. */
    @ColumnInfo(name = "monthly_budget")
    val monthlyBudget: BigDecimal? = null,

    /** true — категория дохода (Зарплата, Кэшбэк...), false — категория расхода. */
    @ColumnInfo(name = "is_income_category")
    val isIncomeCategory: Boolean = false,

    /** Предустановленные категории (id 1..9, см. системный промпт Gemini в CLAUDE.md §3.3) нельзя удалить. */
    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = false,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)

// ──────────────────────────── transactions ────────────────────────────

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_account_id"],
            onDelete = RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = SET_NULL
        ),
        ForeignKey(
            entity = RecurringRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurring_rule_id"],
            onDelete = SET_NULL
        )
    ],
    indices = [
        Index("account_id"),
        Index("to_account_id"),
        Index("category_id"),
        Index("recurring_rule_id"),
        Index("timestamp"),
        Index("transaction_type")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** Счёт списания/зачисления (для TRANSFER — счёт-источник). */
    @ColumnInfo(name = "account_id")
    val accountId: Long,

    /** Только для TRANSFER — счёт-получатель. */
    @ColumnInfo(name = "to_account_id")
    val toAccountId: Long? = null,

    /** null для TRANSFER/ADJUSTMENT без категории. */
    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    /** Сумма в оригинальной валюте транзакции, всегда положительная. Знак определяется [transactionType]. */
    @ColumnInfo(name = "amount")
    val amount: BigDecimal,

    /** ISO 4217 валюты, в которой была совершена операция. */
    @ColumnInfo(name = "original_currency")
    val originalCurrency: String,

    /** Курс пересчёта original_currency -> базовая валюта пользователя на момент операции. */
    @ColumnInfo(name = "exchange_rate")
    val exchangeRate: BigDecimal = BigDecimal.ONE,

    /** Предрассчитанная сумма в базовой валюте (amount * exchangeRate) — для быстрой агрегации в аналитике. */
    @ColumnInfo(name = "amount_base")
    val amountBase: BigDecimal,

    @ColumnInfo(name = "transaction_type")
    val transactionType: TransactionType,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Instant,

    @ColumnInfo(name = "source")
    val source: TransactionSource,

    /** Исходный текст пользователя (для NLI) — используется для отладки и переобучения regex/алиасов. */
    @ColumnInfo(name = "raw_input_text")
    val rawInputText: String? = null,

    /** Комиссия перевода (только TRANSFER), в валюте счёта-источника. */
    @ColumnInfo(name = "transfer_fee_amount")
    val transferFeeAmount: BigDecimal? = null,

    /** Если транзакция создана автоматически по регулярному платежу. */
    @ColumnInfo(name = "recurring_rule_id")
    val recurringRuleId: Long? = null,

    /** true — у транзакции есть подпозиции в split_items (сумма распределена по нескольким категориям). */
    @ColumnInfo(name = "is_split")
    val isSplit: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now()
)

// ──────────────────────────── split_items ────────────────────────────

@Entity(
    tableName = "split_items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = SET_NULL
        )
    ],
    indices = [
        Index("transaction_id"),
        Index("category_id")
    ]
)
data class SplitItemEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,

    @ColumnInfo(name = "category_id")
    val categoryId: Long?,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "amount")
    val amount: BigDecimal
)

// ──────────────────────────── semantic_aliases ────────────────────────────

/** Tier 2 семантического кэша — CLAUDE.md §3.2. */
@Entity(
    tableName = "semantic_aliases",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["default_account_id"],
            onDelete = SET_NULL
        )
    ],
    indices = [
        Index(value = ["keyword"], unique = true),
        Index("category_id"),
        Index("default_account_id")
    ]
)
data class SemanticAliasEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** Нормализованный (lowercase, trim) ключ, например "вкусвилл". */
    @ColumnInfo(name = "keyword")
    val keyword: String,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    @ColumnInfo(name = "default_account_id")
    val defaultAccountId: Long? = null,

    /** Сколько раз алиас был применён — используется для ранжирования при коллизиях. */
    @ColumnInfo(name = "usage_count")
    val usageCount: Int = 1,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now()
)

// ──────────────────────────── recurring_rules ────────────────────────────

/** Регулярные платежи и подписки — CLAUDE.md §2.4. */
@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = SET_NULL
        )
    ],
    indices = [
        Index("account_id"),
        Index("category_id"),
        Index("next_execution_date")
    ]
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "amount")
    val amount: BigDecimal,

    @ColumnInfo(name = "account_id")
    val accountId: Long,

    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    @ColumnInfo(name = "period")
    val period: RecurringPeriod,

    /** Кратность периода, например intervalCount=2 + period=WEEKLY = "раз в две недели". */
    @ColumnInfo(name = "interval_count")
    val intervalCount: Int = 1,

    @ColumnInfo(name = "next_execution_date")
    val nextExecutionDate: LocalDate,

    @ColumnInfo(name = "last_executed_date")
    val lastExecutedDate: LocalDate? = null,

    /** За сколько дней до списания напоминать пользователю — CLAUDE.md §2.4. */
    @ColumnInfo(name = "reminder_days_before")
    val reminderDaysBefore: Int = 3,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now()
)
