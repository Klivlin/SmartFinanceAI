package ru.vogu.smartfinance.domain.model

import ru.vogu.smartfinance.core.database.entity.AccountType
import java.math.BigDecimal
import java.time.Instant

/** Domain-модель счёта — не зависит от Room, используется в presentation/usecase слоях. */
data class Account(
    val id: Long,
    val name: String,
    val type: AccountType,
    val balance: BigDecimal,
    val currency: String,
    val isCredit: Boolean,
    val creditLimit: BigDecimal?,
    val gracePeriodDays: Int?,
    val statementDay: Int?,
    val interestRatePercent: BigDecimal? = null,
    val isArchived: Boolean,
    val isPrimary: Boolean = false,
    val sortOrder: Int,
    val createdAt: Instant
) {
    /** Доступный лимит с учётом текущей задолженности — для карточки счёта (Шаг 6). */
    val availableCredit: BigDecimal?
        get() = creditLimit?.let { it + balance } // balance у кредитки отрицательный при долге
}
