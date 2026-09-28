package ru.vogu.smartfinance.data.repository

import ru.vogu.smartfinance.core.common.NetworkMonitor
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.database.dao.CategoryDao
import ru.vogu.smartfinance.core.network.GeminiClient
import ru.vogu.smartfinance.core.security.PiiFilter
import ru.vogu.smartfinance.data.mapper.toDomain
import ru.vogu.smartfinance.data.parser.FiscalQrParser
import ru.vogu.smartfinance.data.parser.ReceiptOcrParser
import ru.vogu.smartfinance.domain.model.FiscalReceiptData
import ru.vogu.smartfinance.domain.model.ParsedReceiptItem
import ru.vogu.smartfinance.domain.repository.ReceiptRepository
import javax.inject.Inject

/**
 * Реализация обоих путей сканера — CLAUDE.md §4. QR — полностью локальный, без обращения
 * к сети (как и требует §4.1: "без вызова внешних платных API"). OCR — локальная
 * предфильтрация строк ([ReceiptOcrParser]) + PII-очистка + Tier 3 (Gemini), с той же
 * offline-обработкой краевого случая "нет сети", что и у [NliExpenseRepositoryImpl] для
 * чата (CLAUDE.md §9.4).
 */
class ReceiptRepositoryImpl @Inject constructor(
    private val fiscalQrParser: FiscalQrParser,
    private val receiptOcrParser: ReceiptOcrParser,
    private val piiFilter: PiiFilter,
    private val categoryDao: CategoryDao,
    private val geminiClient: GeminiClient,
    private val networkMonitor: NetworkMonitor
) : ReceiptRepository {

    override fun parseFiscalQr(rawQrValue: String): FiscalReceiptData? =
        fiscalQrParser.parse(rawQrValue)

    override suspend fun parseOcrLines(rawLines: List<String>): Resource<List<ParsedReceiptItem>> {
        val candidateLines = receiptOcrParser.extractCandidateLines(rawLines)
        if (candidateLines.isEmpty()) {
            return Resource.Error(
                "Не удалось найти ни одной позиции с ценой на фото. Сфотографируйте чек " +
                    "ровно и целиком, без бликов, или отсканируйте QR-код в верхней/нижней части чека."
            )
        }

        if (!networkMonitor.isOnline()) {
            return Resource.Error(
                "Распознавание позиций чека по фото требует подключения к интернету. " +
                    "Попробуйте отсканировать QR-код на чеке — это работает офлайн."
            )
        }

        val sanitizedLines = candidateLines.map { piiFilter.sanitize(it).sanitizedText }
        val categories = categoryDao.getAllOnce().map { it.toDomain() }
        return geminiClient.parseReceiptItems(sanitizedLines, categories)
    }
}
