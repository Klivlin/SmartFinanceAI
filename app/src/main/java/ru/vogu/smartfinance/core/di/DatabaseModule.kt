package ru.vogu.smartfinance.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.core.common.DefaultCategories
import ru.vogu.smartfinance.core.database.AppDatabase
import ru.vogu.smartfinance.core.database.dao.AccountDao
import ru.vogu.smartfinance.core.database.dao.CategoryDao
import ru.vogu.smartfinance.core.database.dao.ExchangeRateDao
import ru.vogu.smartfinance.core.database.dao.RecurringRuleDao
import ru.vogu.smartfinance.core.database.dao.SemanticAliasDao
import ru.vogu.smartfinance.core.database.dao.SplitItemDao
import ru.vogu.smartfinance.core.database.dao.TransactionDao
import ru.vogu.smartfinance.core.security.DatabasePassphraseProvider
import java.math.BigDecimal
import javax.inject.Singleton

/**
 * DI-модуль базы данных. База физически зашифрована через SQLCipher
 * ([SupportOpenHelperFactory] вместо стандартного FrameworkSQLiteOpenHelperFactory) —
 * см. CLAUDE.md §2.6. При первом создании файла БД сидируются системные
 * категории (id 1..9, синхронизированы с промптом Gemini — CLAUDE.md §3.3)
 * и счёт по умолчанию.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: DatabasePassphraseProvider
    ): AppDatabase {
        val supportFactory = SupportOpenHelperFactory(
            String(passphraseProvider.getOrCreatePassphrase()).toByteArray(Charsets.UTF_8)
        )

        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .openHelperFactory(supportFactory)
            .addCallback(SeedDatabaseCallback)
            // Явные Migration обязательны при любом изменении схемы — деструктивный fallback
            // недопустим в production-приложении с финансовыми данными пользователя.
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }

    @Provides
    fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideExchangeRateDao(db: AppDatabase): ExchangeRateDao = db.exchangeRateDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideSplitItemDao(db: AppDatabase): SplitItemDao = db.splitItemDao()

    @Provides
    fun provideSemanticAliasDao(db: AppDatabase): SemanticAliasDao = db.semanticAliasDao()

    @Provides
    fun provideRecurringRuleDao(db: AppDatabase): RecurringRuleDao = db.recurringRuleDao()
}

/**
 * v1 → v2: накопительные счета (CLAUDE.md §2.1) получают годовую процентную ставку —
 * используется калькулятором прогноза накоплений на экране "Счета" (Шаг 6+).
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN interest_rate_percent TEXT DEFAULT NULL")
    }
}

/**
 * v2 → v3: схема не меняется — переводит уже засеянные системные категории (id 1..9) на
 * приглушённую "земляную" палитру (см. [DefaultCategories.seed]) вместо ярких Material-цветов.
 * Без этой миграции обновление стиля было бы видно только на новых установках: сидирование
 * ([SeedDatabaseCallback]) выполняется ровно один раз, при первом создании файла БД.
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DefaultCategories.seed.forEach { category ->
            db.execSQL(
                "UPDATE categories SET color_hex = ? WHERE id = ? AND is_system = 1",
                arrayOf(category.colorHex, category.id)
            )
        }
    }
}

/**
 * v3 → v4: счёт типа CRYPTO (CLAUDE.md §2.1 расширено), выбор "основного" счёта для
 * Collapsible Dashboard Header (CLAUDE.md §5.1) и конвертация по реальному курсу
 * (кросс-валютные переводы + кэш курсов) — [ru.vogu.smartfinance.domain.repository.ExchangeRateRepository].
 * `is_primary` получает первый (по sort_order/id) активный счёт — чтобы после обновления
 * приложения на главном экране сразу было что показать, а не пустой "нет основного счёта".
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN is_primary INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN to_amount TEXT DEFAULT NULL")
        db.execSQL(
            """
            UPDATE accounts SET is_primary = 1 WHERE id = (
                SELECT id FROM accounts WHERE is_archived = 0 ORDER BY sort_order ASC, id ASC LIMIT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exchange_rates (
                currency_code TEXT NOT NULL PRIMARY KEY,
                rate_to_rub TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * Выполняется Room синхронно на фоновом потоке ровно один раз, в момент физического
 * создания файла БД — поэтому сидирование сделано через "сырой" SQL, а не через DAO
 * (в момент вызова [onCreate] инстанс [AppDatabase] ещё не полностью инициализирован).
 */
private object SeedDatabaseCallback : androidx.room.RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        db.beginTransaction()
        try {
            DefaultCategories.seed.forEach { category ->
                db.execSQL(
                    """
                    INSERT INTO categories
                        (id, name, icon_name, color_hex, monthly_budget, is_income_category, is_system, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        category.id,
                        category.name,
                        category.iconName,
                        category.colorHex,
                        category.monthlyBudget?.toPlainString(),
                        if (category.isIncomeCategory) 1 else 0,
                        if (category.isSystem) 1 else 0,
                        category.sortOrder
                    )
                )
            }

            db.execSQL(
                """
                INSERT INTO accounts
                    (id, name, type, balance, currency, is_credit, credit_limit, grace_period_days,
                     statement_day, is_archived, is_primary, sort_order, created_at)
                VALUES (1, ?, 'DEBIT_CARD', ?, ?, 0, NULL, NULL, NULL, 0, 1, 0, ?)
                """.trimIndent(),
                arrayOf(
                    Constants.DEFAULT_ACCOUNT_NAME,
                    BigDecimal.ZERO.toPlainString(),
                    Constants.DEFAULT_BASE_CURRENCY,
                    System.currentTimeMillis()
                )
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
