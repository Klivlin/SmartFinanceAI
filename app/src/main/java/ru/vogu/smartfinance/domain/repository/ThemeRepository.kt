package ru.vogu.smartfinance.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Настройка светлой/тёмной темы приложения — CLAUDE.md §5.2 ("Экран настроек").
 *
 * `null` означает "следовать системной теме" (значение по умолчанию до первого явного выбора
 * пользователем); `true`/`false` — явно закреплённый пользователем выбор, который переживает
 * перезапуск приложения (хранится в DataStore, не в памяти).
 */
interface ThemeRepository {
    val isDarkTheme: Flow<Boolean?>
    suspend fun setDarkTheme(isDark: Boolean?)
}
