package ru.vogu.smartfinance.domain.usecase

import ru.vogu.smartfinance.core.common.DefaultCategories
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.FiscalReceiptData
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
        return persistLump(receipt)
    }

    /** OCR-путь — CLAUDE.md §4.2. Несколько позиций → одна сплит-транзакция (CLAUDE.md §2.3). */
    suspend fun fromOcrLines(rawLines: List<String>): Resource<Transaction> {
        return when (val result = receiptRepository.parseOcrLines(rawLines)) {
            is Resource.Error -> Resource.Error(result.message, result.cause)
            Resource.Loading -> Resource.Loading
            is Resource.Success -> persistItems(result.data, timestamp = Instant.now())
        }
    }

    /**
     * Комбинированный путь сканера — CLAUDE.md §4: QR даёт только итоговую сумму чека без
     * разбивки (см. [fromFiscalQr]), а в самом чеке при этом печатаются и позиции, и QR рядом.
     * Поэтому при обнаружении QR сканер ([ru.vogu.smartfinance.presentation.scanner.ScannerScreen])
     * сразу же фотографирует тот же кадр и прогоняет его через OCR — так пользователь получает
     * разбивку по позициям (Продукты/Кафе/... вместо одной обезличенной "Покупка по чеку"),
     * не нажимая отдельно кнопку "Сфотографировать чек". Позиции — приоритетный результат;
     * запасной вариант — если OCR/Gemini не смогли прочитать ни одной позиции (плохое
     * освещение, нет сети и т.п.), тогда пользователь всё равно получает операцию на сумму
     * чека, просто без разбивки, как раньше. Время операции — всегда момент сканирования
     * ([Instant.now]), а не время из самого QR: лента чата (CLAUDE.md §5.1) показывает только
     * последние N операций по времени, и старый физический чек с датой в прошлом иначе рисковал
     * бы просто не попасть в это окно, выглядя так, будто сканирование ничего не дало.
     */
    suspend fun fromQrAndOcr(qrRawValue: String, ocrLines: List<String>): Resource<Transaction> {
        val fiscalData = receiptRepository.parseFiscalQr(qrRawValue)
        val ocrResult = if (ocrLines.isNotEmpty()) receiptRepository.parseOcrLines(ocrLines) else null

        if (ocrResult is Resource.Success && ocrResult.data.isNotEmpty()) {
            val reconciled = reconcileWithFiscalTotal(ocrResult.data, fiscalData)
            if (reconciled != null) {
                return persistItems(reconciled, timestamp = Instant.now())
            }
            // Позиции насчитали БОЛЬШЕ, чем реальная сумма чека в QR (задвоение строк OCR) —
            // разбивке доверять нельзя, откатываемся на надёжный QR-итог ниже.
        }

        if (fiscalData != null) {
            return persistLump(fiscalData.copy(timestamp = Instant.now()))
        }

        return Resource.Error(
            "Не удалось распознать чек. Убедитесь, что QR-код и позиции хорошо видны, и попробуйте снова."
        )
    }

    /**
     * OCR/Gemini иногда не дочитывают часть позиций чека (смазанное фото, кириллица без
     * устойчивой поддержки у используемой модели распознавания текста — см. doc-комментарий
     * [ru.vogu.smartfinance.presentation.scanner.ScannerScreen]) — тогда сумма найденных
     * позиций оказывается МЕНЬШЕ реальной суммы чека. QR же даёт точную сумму всегда (это
     * фискальные данные кассы, а не распознавание изображения) — без сверки с ним операция
     * сохранялась бы на заниженную сумму, расходясь с реальным чеком и балансом счёта.
     *
     * При нехватке — недостающая разница добавляется отдельной позицией без категории, чтобы
     * итог операции ВСЕГДА совпадал с реальным чеком. При избытке (OCR задвоила строку — сумма
     * позиций больше, чем в QR) доверять разбивке нельзя вовсе — возвращает `null`, и вызывающая
     * сторона откатывается на обычную операцию по одной сумме из QR.
     */
    private fun reconcileWithFiscalTotal(
        items: List<ParsedReceiptItem>,
        fiscalData: FiscalReceiptData?
    ): List<ParsedReceiptItem>? {
        if (fiscalData == null) return items

        val itemsTotal = items.fold(BigDecimal.ZERO) { sum, item -> sum + item.amount }
        val diff = fiscalData.amount - itemsTotal

        return when {
            diff.abs() < BigDecimal("0.01") -> items
            diff.signum() > 0 -> items + ParsedReceiptItem(name = "Остальные позиции", amount = diff, categoryId = null)
            else -> null
        }
    }

    private suspend fun persistLump(receipt: FiscalReceiptData): Resource<Transaction> {
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

    private suspend fun persistItems(items: List<ParsedReceiptItem>, timestamp: Instant): Resource<Transaction> {
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
            timestamp = timestamp,
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
