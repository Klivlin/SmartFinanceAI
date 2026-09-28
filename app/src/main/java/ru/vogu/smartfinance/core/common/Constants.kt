package ru.vogu.smartfinance.core.common

import ru.vogu.smartfinance.core.database.entity.CategoryEntity

object Constants {

    /** Базовая валюта по умолчанию (меняется пользователем в настройках) — CLAUDE.md §2.1. */
    const val DEFAULT_BASE_CURRENCY = "RUB"

    /** Название счёта, создаваемого при первом запуске (используется как Default Account в промпте Gemini). */
    const val DEFAULT_ACCOUNT_NAME = "Основной"

    /** Пороги предупреждений о превышении бюджета категории — CLAUDE.md §2.5. */
    const val BUDGET_WARNING_THRESHOLD_PERCENT = 80
    const val BUDGET_EXCEEDED_THRESHOLD_PERCENT = 100

    /** Максимальный возраст записи в Tier 2 семантическом кэше не ограничен — но лимит по количеству записей есть. */
    const val SEMANTIC_CACHE_MAX_ENTRIES = 2000

    /** Дефолтное количество дней напоминания о регулярном платеже — CLAUDE.md §2.4. */
    const val DEFAULT_REMINDER_DAYS_BEFORE = 3

    /** Alias — EncryptedSharedPreferences / Android Keystore. */
    const val KEYSTORE_DB_PASSPHRASE_PREFS_NAME = "smartfinance_secure_prefs"
    const val KEYSTORE_DB_PASSPHRASE_KEY = "db_passphrase"
    const val KEYSTORE_GEMINI_USER_API_KEY = "user_gemini_api_key"

    /** WorkManager tags. */
    const val WORK_TAG_RECURRING_CHECK = "recurring_payments_check"
}

/**
 * Предустановленные категории (id жёстко зафиксированы 1..9), т.к. на эти же ID
 * ссылается системный промпт Gemini — CLAUDE.md §3.3:
 * `[1:Food, 2:Transport, 3:Cafe, 4:Utilities, 5:Fun, 6:Health, 7:Transfer, 8:Income, 9:Other]`
 *
 * ВАЖНО: список и порядок id не менять без синхронной правки промпта в GeminiClient (Шаг 3).
 */
object DefaultCategories {

    val seed: List<CategoryEntity> = listOf(
        CategoryEntity(
            id = 1,
            name = "Еда",
            iconName = "restaurant",
            colorHex = "#FF7043",
            isIncomeCategory = false,
            isSystem = true,
            sortOrder = 1
        ),
        CategoryEntity(
            id = 2,
            name = "Транспорт",
            iconName = "directions_car",
            colorHex = "#42A5F5",
            isIncomeCategory = false,
            isSystem = true,
            sortOrder = 2
        ),
        CategoryEntity(
            id = 3,
            name = "Кафе",
            iconName = "local_cafe",
            colorHex = "#AB47BC",
            isIncomeCategory = false,
            isSystem = true,
            sortOrder = 3
        ),
        CategoryEntity(
            id = 4,
            name = "Коммунальные платежи",
            iconName = "bolt",
            colorHex = "#FFCA28",
            isIncomeCategory = false,
            isSystem = true,
            sortOrder = 4
        ),
        CategoryEntity(
            id = 5,
            name = "Развлечения",
            iconName = "theater_comedy",
            colorHex = "#EC407A",
            isIncomeCategory = false,
            isSystem = true,
            sortOrder = 5
        ),
        CategoryEntity(
            id = 6,
            name = "Здоровье",
            iconName = "favorite",
            colorHex = "#66BB6A",
            isIncomeCategory = false,
            isSystem = true,
            sortOrder = 6
        ),
        CategoryEntity(
            id = 7,
            name = "Переводы",
            iconName = "swap_horiz",
            colorHex = "#78909C",
            isIncomeCategory = false,
            isSystem = true,
            sortOrder = 7
        ),
        CategoryEntity(
            id = 8,
            name = "Доход",
            iconName = "payments",
            colorHex = "#26A69A",
            isIncomeCategory = true,
            isSystem = true,
            sortOrder = 8
        ),
        CategoryEntity(
            id = 9,
            name = "Другое",
            iconName = "category",
            colorHex = "#8D6E63",
            isIncomeCategory = false,
            isSystem = true,
            sortOrder = 9
        )
    )

    /** ID категории "Переводы" — используется для служебной привязки TRANSFER-операций. */
    const val TRANSFER_CATEGORY_ID = 7L

    /** ID категории "Доход" — дефолтная категория для INCOME, если LLM/regex не определили точнее. */
    const val INCOME_CATEGORY_ID = 8L

    /** ID категории "Другое" — fallback для нераспознанных EXPENSE. */
    const val OTHER_CATEGORY_ID = 9L
}
