package ru.vogu.smartfinance.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.vogu.smartfinance.core.database.converter.Converters
import ru.vogu.smartfinance.core.database.dao.AccountDao
import ru.vogu.smartfinance.core.database.dao.CategoryDao
import ru.vogu.smartfinance.core.database.dao.ExchangeRateDao
import ru.vogu.smartfinance.core.database.dao.RecurringRuleDao
import ru.vogu.smartfinance.core.database.dao.SemanticAliasDao
import ru.vogu.smartfinance.core.database.dao.SplitItemDao
import ru.vogu.smartfinance.core.database.dao.TransactionDao
import ru.vogu.smartfinance.core.database.entity.AccountEntity
import ru.vogu.smartfinance.core.database.entity.CategoryEntity
import ru.vogu.smartfinance.core.database.entity.ExchangeRateEntity
import ru.vogu.smartfinance.core.database.entity.RecurringRuleEntity
import ru.vogu.smartfinance.core.database.entity.SemanticAliasEntity
import ru.vogu.smartfinance.core.database.entity.SplitItemEntity
import ru.vogu.smartfinance.core.database.entity.TransactionEntity

/**
 * Room-база SmartFinance AI. Физически зашифрована через SQLCipher —
 * см. [ru.vogu.smartfinance.core.di.DatabaseModule] (SupportFactory с ключом
 * из [ru.vogu.smartfinance.core.security.DatabasePassphraseProvider]).
 *
 * При изменении схемы: увеличить version, добавить Migration в DatabaseModule
 * (Migration'ы обязательны — fallbackToDestructiveMigration запрещён в production).
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        SplitItemEntity::class,
        SemanticAliasEntity::class,
        RecurringRuleEntity::class,
        ExchangeRateEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun splitItemDao(): SplitItemDao
    abstract fun semanticAliasDao(): SemanticAliasDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun exchangeRateDao(): ExchangeRateDao

    companion object {
        const val DATABASE_NAME = "smartfinance.db"
    }
}
