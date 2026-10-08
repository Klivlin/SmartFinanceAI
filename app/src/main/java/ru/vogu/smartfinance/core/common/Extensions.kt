package ru.vogu.smartfinance.core.common

import ru.vogu.smartfinance.domain.model.CryptoAsset
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Денежное округление до 2 знаков (копейки/центы), банковское округление (HALF_EVEN) — стандарт для финансов. */
fun BigDecimal.toMoneyScale(): BigDecimal = setScale(2, RoundingMode.HALF_EVEN)

/**
 * Форматирует сумму в формате "1 234,56 ₽" (локаль ru-RU) для отображения в UI. Для крипто-
 * тикеров (CLAUDE.md §2.1) — не ISO 4217, [java.util.Currency.getInstance] выбросил бы
 * исключение — форматируем вручную с точностью до 8 знаков (без хвостовых нулей, минимум 2).
 */
fun BigDecimal.formatAsCurrency(currencyCode: String, locale: Locale = Locale("ru", "RU")): String {
    if (CryptoAsset.fromCode(currencyCode) != null) {
        val format = NumberFormat.getNumberInstance(locale)
        format.maximumFractionDigits = 8
        format.minimumFractionDigits = 2
        return "${format.format(this)} ${currencyCode.uppercase()}"
    }
    val format = NumberFormat.getCurrencyInstance(locale)
    format.currency = java.util.Currency.getInstance(currencyCode)
    format.maximumFractionDigits = 2
    format.minimumFractionDigits = 2
    return format.format(this)
}

/** true, если сумма отрицательна или равна нулю — используется для валидации ввода (CLAUDE.md §9.4). */
fun BigDecimal.isNonPositive(): Boolean = this.signum() <= 0

fun Instant.toLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    this.atZone(zoneId).toLocalDate()

/** Начало календарного месяца, которому принадлежит [Instant], как [Instant] (00:00:00 первого числа, локальная зона). */
fun Instant.startOfMonth(zoneId: ZoneId = ZoneId.systemDefault()): Instant =
    this.atZone(zoneId).toLocalDate().withDayOfMonth(1).atStartOfDay(zoneId).toInstant()

/** Начало следующего календарного месяца — верхняя (эксклюзивная) граница для запросов "за текущий месяц". */
fun Instant.startOfNextMonth(zoneId: ZoneId = ZoneId.systemDefault()): Instant =
    this.atZone(zoneId).toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(zoneId).toInstant()

/** Начало текущих суток — для виджета "расходы за сегодня" (CLAUDE.md §5.1). */
fun Instant.startOfDay(zoneId: ZoneId = ZoneId.systemDefault()): Instant =
    this.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant()

fun Instant.startOfNextDay(zoneId: ZoneId = ZoneId.systemDefault()): Instant =
    this.atZone(zoneId).toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()

/** Нормализация ключевого слова для Tier 2 семантического кэша — CLAUDE.md §3.2. */
fun String.normalizeAsAliasKeyword(): String = this.trim().lowercase(Locale("ru", "RU"))

/** Разбивает уже нормализованную строку на отдельные слова — для поэлементного сопоставления категорий. */
fun String.tokenizeWords(): List<String> = split(Regex("[^a-zа-яё0-9]+")).filter { it.isNotBlank() }

/** Общая "основа" слова (первые [length] символов) — грубая, но достаточная замена стеммингу для
 * сопоставления русских словоформ ("подписка"/"подписки") без словаря окончаний. */
fun String.stem(length: Int = 5): String = if (this.length <= length) this else this.substring(0, length)
