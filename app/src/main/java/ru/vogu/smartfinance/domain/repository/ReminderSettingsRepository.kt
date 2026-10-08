package ru.vogu.smartfinance.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Включено ли ежедневное напоминание "внести расходы" — CLAUDE.md §2.4/§5.2. По умолчанию
 * включено (см. [ru.vogu.smartfinance.presentation.MainActivity], где ранее оно планировалось
 * безусловно на каждый запуск) — настройка позволяет пользователю его выключить насовсем.
 */
interface ReminderSettingsRepository {
    val isReminderEnabled: Flow<Boolean>
    suspend fun setReminderEnabled(enabled: Boolean)
}
