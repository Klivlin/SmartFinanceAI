package ru.vogu.smartfinance.domain.repository

interface SemanticAliasRepository {
    /** Возвращает id категории, если ключевое слово уже встречалось ранее (Tier 2 — CLAUDE.md §3.2). */
    suspend fun resolveCategoryId(normalizedKeyword: String): Long?

    suspend fun upsert(normalizedKeyword: String, categoryId: Long, defaultAccountId: Long? = null)
    suspend fun delete(normalizedKeyword: String)
}
