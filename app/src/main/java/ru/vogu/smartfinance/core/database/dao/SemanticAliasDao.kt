package ru.vogu.smartfinance.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.vogu.smartfinance.core.database.entity.SemanticAliasEntity

/**
 * DAO для Tier 2 семантического кэша — CLAUDE.md §3.2.
 * Ключ [SemanticAliasEntity.keyword] уникален (см. индекс в Entities.kt), поэтому
 * [upsert] безопасно перезаписывает существующую связь "ключевое слово → категория".
 */
@Dao
interface SemanticAliasDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alias: SemanticAliasEntity): Long

    @Delete
    suspend fun delete(alias: SemanticAliasEntity)

    /** Точка входа Tier 2: ищем нормализованный ключ (regex/LLM уже привёл его к lowercase/trim). */
    @Query("SELECT * FROM semantic_aliases WHERE keyword = :normalizedKeyword LIMIT 1")
    suspend fun findByKeyword(normalizedKeyword: String): SemanticAliasEntity?

    @Query("UPDATE semantic_aliases SET usage_count = usage_count + 1 WHERE id = :id")
    suspend fun incrementUsage(id: Long)

    @Query("SELECT * FROM semantic_aliases ORDER BY usage_count DESC")
    fun observeAll(): Flow<List<SemanticAliasEntity>>

    @Query("DELETE FROM semantic_aliases WHERE keyword = :normalizedKeyword")
    suspend fun deleteByKeyword(normalizedKeyword: String)
}
