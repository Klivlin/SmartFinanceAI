package ru.vogu.smartfinance.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Флаги первого запуска — CLAUDE.md список доработок, п.3 ("при первоначальном входе
 * зафиксировать текущий баланс"). Единственный флаг сегодня — просили ли уже пользователя
 * указать стартовый баланс основного счёта (создаётся с балансом 0 — см. `SeedDatabaseCallback`
 * в `DatabaseModule.kt`); после первого показа диалог больше не появляется, даже если
 * пользователь его пропустил "как есть".
 */
interface OnboardingRepository {
    val isInitialBalancePrompted: Flow<Boolean>
    suspend fun markInitialBalancePrompted()
}
