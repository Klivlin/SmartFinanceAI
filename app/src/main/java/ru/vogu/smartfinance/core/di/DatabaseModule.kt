package ru.vogu.smartfinance.core.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.core.common.DefaultCategories
import ru.vogu.smartfinance.core.database.AppDatabase
import ru.vogu.smartfinance.core.database.dao.AccountDao
import ru.vogu.smartfinance.core.database.dao.CategoryDao
import ru.vogu.smartfinance.core.database.dao.RecurringRuleDao
import ru.vogu.smartfinance.core.database.dao.SemanticAliasDao
import ru.vogu.smartfinance.core.database.dao.SplitItemDao
import ru.vogu.smartfinance.core.database.dao.TransactionDao
import ru.vogu.smartfinance.core.security.DatabasePassphraseProvider
import java.math.BigDecimal
import javax.inject.Singleton

/**
 * DI-модуль базы данных. База физически зашифрована через SQLCipher
 * ([SupportFactory] вместо стандартного FrameworkSQLiteOpenHelperFactory) —
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
        val supportFactory = SupportFactory(
            net.sqlcipher.database.SQLiteDatabase.getBytes(passphraseProvider.getOrCreatePassphrase())
        )

        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .openHelperFactory(supportFactory)
            .addCallback(SeedDatabaseCallback)
            // Явные Migration обязательны при любом изменении схемы — деструктивный fallback
            // недопустим в production-приложении с финансовыми данными пользователя.
            .build()
    }

    @Provides
    fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()

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
                     statement_day, is_archived, sort_order, created_at)
                VALUES (1, ?, 'DEBIT_CARD', ?, ?, 0, NULL, NULL, NULL, 0, 0, ?)
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
