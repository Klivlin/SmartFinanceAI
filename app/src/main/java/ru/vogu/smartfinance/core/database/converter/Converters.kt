package ru.vogu.smartfinance.core.database.converter

import androidx.room.TypeConverter
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.core.database.entity.RecurringPeriod
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Room [TypeConverter]-ы.
 *
 * Деньги хранятся как TEXT (через [BigDecimal.toPlainString]), а не REAL/Double —
 * это осознанное решение для FinTech-приложения: BigDecimal исключает ошибки
 * округления с плавающей точкой при суммировании множества транзакций.
 */
class Converters {

    // ---- BigDecimal (деньги, курсы) ----

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    // ---- Instant (метки времени с точностью до миллисекунд) ----

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    // ---- LocalDate (календарные даты без времени, ISO-8601: "2026-09-28") ----

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    // ---- Enums ----

    @TypeConverter
    fun fromAccountType(value: AccountType?): String? = value?.name

    @TypeConverter
    fun toAccountType(value: String?): AccountType? = value?.let { AccountType.valueOf(it) }

    @TypeConverter
    fun fromTransactionType(value: TransactionType?): String? = value?.name

    @TypeConverter
    fun toTransactionType(value: String?): TransactionType? =
        value?.let { TransactionType.valueOf(it) }

    @TypeConverter
    fun fromTransactionSource(value: TransactionSource?): String? = value?.name

    @TypeConverter
    fun toTransactionSource(value: String?): TransactionSource? =
        value?.let { TransactionSource.valueOf(it) }

    @TypeConverter
    fun fromRecurringPeriod(value: RecurringPeriod?): String? = value?.name

    @TypeConverter
    fun toRecurringPeriod(value: String?): RecurringPeriod? =
        value?.let { RecurringPeriod.valueOf(it) }
}
