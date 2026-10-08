package ru.vogu.smartfinance.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Локальная (на этом устройстве, не в Room) отметка "очистить чат" — CLAUDE.md §5.1.
 * Кнопка "Очистить чат" в [ru.vogu.smartfinance.presentation.chat.ChatScreen] НЕ удаляет
 * транзакции (они остаются в БД и видны в "Категориях"/"Аналитике"/"Счетах") — она лишь
 * скрывает из ленты чата всё, что было создано до момента очистки, аналогично "Clear chat"
 * в мессенджерах. Хранится в DataStore, поэтому переживает перезапуск приложения.
 */
interface ChatHistoryRepository {
    val clearedAt: Flow<Instant?>
    suspend fun clearChat()
}
