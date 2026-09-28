package ru.vogu.smartfinance.domain.usecase

import ru.vogu.smartfinance.core.common.DefaultCategories
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.ParsedReceiptItem
import ru.vogu.smartfinance.domain.model.SplitItem
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.ReceiptRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

/**
 * Точка входа для экрана сканера (Шаг 5) — CLAUDE.md §4. Как и [ProcessNliInputUseCase] для
 * чата, транзакция создаётся сразу (оптимистично): после сохранения сканер закрывается и
 * пользователь возвращается в чат, где новая операция уже появляется как обычная Action Card
 * (лента чата реактивно строится из [TransactionRepository.observeRecent] — см.
 * ChatViewModel, Шаг 4) — те же кнопки "Подтвердить"/"Изменить"/"Отменить" работают и для
 * операций, пришедших со сканера, без дублирования UI-логики.
 */
class ParseReceiptUseCase @Inject constructor(
    private val receiptRepository: ReceiptRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) {

    /**
     * QR-путь — CLAUDE.md §4.1. Счёт-получатель определяется как счёт по умолчанию (в самом
     * QR его нет); категория — "Другое", т.к. фискальный QR даёт только итоговую сумму чека,
     * без разбивки по позициям (для разбивки — см. [fromOcrLines]).
     */
    suspend fun fromFiscalQr(rawQrValue: String): Resource<Transaction> {
        val receipt = receiptRepository.parseFiscalQr(rawQrValue)
            ?: return Resource.Error(
                "Не удалось распознать QR-код. Убедитесь, что это кассовый QR ФНС " +
                    "(обычно внизу чека), и попробуйте снова."
            )

        val account = accountRepository.findBestMatch(hint = null)
        val transaction = transactionRepository.createTransaction(
            accountId = account.id,
            categoryId = DefaultCategories.OTHER_CATEGORY_ID,
            amount = receipt.amount,
            transactionType = TransactionType.EXPENSE,
            title = "Покупка по чеку",
            timestamp = receipt.timestamp,
            source = TransactionSource.QR,
            rawInputText = null
        )
        return Resource.Success(transaction)
    }

    /** OCR-путь — CLAUDE.md §4.2. Несколько позиций → одна сплит-транзакция (CLAUDE.md §2.3). */
    suspend fun fromOcrLines(rawLines: List<String>): Resource<Transaction> {
        return when (val result = receiptRepository.parseOcrLines(rawLines)) {
            is Resource.Error -> Resource.Error(result.message, result.cause)
            Resource.Loading -> Resource.Loading
            is Resource.Success -> persist(result.data)
        }
    }

    private suspend fun persist(items: List<ParsedReceiptItem>): Resource<Transaction> {
        if (items.isEmpty()) {
            return Resource.Error("Не удалось распознать ни одной позиции на чеке.")
        }

        val total = items.fold(BigDecimal.ZERO) { sum, item -> sum + item.amount }
        if (total.signum() <= 0) {
            return Resource.Error("Сумма распознанного чека должна быть больше нуля.")
        }

        val account = accountRepository.findBestMatch(hint = null)
        val isSplit = items.size > 1

        val transaction = transactionRepository.createTransaction(
            accountId = account.id,
            categoryId = if (isSplit) null else items.first().categoryId,
            amount = total,
            transactionType = TransactionType.EXPENSE,
            title = if (isSplit) "Чек (${itemsCountLabel(items.size)})" else items.first().name,
            timestamp = Instant.now(),
            source = TransactionSource.OCR,
            rawInputText = null,
            splitItems = if (isSplit) {
                items.map { SplitItem(id = 0L, transactionId = 0L, categoryId = it.categoryId, name = it.name, amount = it.amount) }
            } else {
                emptyList()
            }
        )
        return Resource.Success(transaction)
    }

    /** Русское согласование числительного с "позиция" (1 позиция / 2 позиции / 5 позиций). */
    private fun itemsCountLabel(count: Int): String {
        val mod100 = count % 100
        val mod10 = count % 10
        val word = when {
            mod100 in 11..14 -> "позиций"
            mod10 == 1 -> "позиция"
            mod10 in 2..4 -> "позиции"
            else -> "позиций"
        }
        return "$count $word"
    }
}
