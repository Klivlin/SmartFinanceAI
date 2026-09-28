package ru.vogu.smartfinance.domain.model

import java.math.BigDecimal

data class Category(
    val id: Long,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val monthlyBudget: BigDecimal?,
    val isIncomeCategory: Boolean,
    val isSystem: Boolean,
    val sortOrder: Int
)
