package ru.vogu.smartfinance.data.repository

import ru.vogu.smartfinance.core.database.dao.SemanticAliasDao
import ru.vogu.smartfinance.core.database.entity.SemanticAliasEntity
import ru.vogu.smartfinance.domain.repository.SemanticAliasRepository
import javax.inject.Inject

class SemanticAliasRepositoryImpl @Inject constructor(
    private val semanticAliasDao: SemanticAliasDao
) : SemanticAliasRepository {

    override suspend fun resolveCategoryId(normalizedKeyword: String): Long? {
        val alias = semanticAliasDao.findByKeyword(normalizedKeyword) ?: return null
        semanticAliasDao.incrementUsage(alias.id)
        return alias.categoryId
    }

    override suspend fun upsert(normalizedKeyword: String, categoryId: Long, defaultAccountId: Long?) {
        semanticAliasDao.upsert(
            SemanticAliasEntity(
                keyword = normalizedKeyword,
                categoryId = categoryId,
                defaultAccountId = defaultAccountId
            )
        )
    }

    override suspend fun delete(normalizedKeyword: String) = semanticAliasDao.deleteByKeyword(normalizedKeyword)
}
