package ru.vogu.smartfinance.domain.usecase

import kotlinx.coroutines.flow.first
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Выгрузка всей истории операций в CSV — CLAUDE.md §2.7 ("Экспорт/Импорт"), §5.2 ("экспорт данных"). */
class ExportTransactionsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(): String {
        val transactions = transactionRepository.getAllForExport()
        val categoriesById = categoryRepository.observeAll().first().associateBy { it.id }

        val header = "Дата,Тип,Название,Категория,Счёт,Сумма,Валюта,Источник"
        val rows = transactions.joinToString(separator = "\n") { tx ->
            val date = DATE_FORMATTER.format(tx.timestamp.atZone(ZoneId.systemDefault()))
            val categoryName = tx.categoryId?.let { categoriesById[it]?.name } ?: ""
            listOf(
                date,
                tx.transactionType.name,
                tx.title.csvEscaped(),
                categoryName.csvEscaped(),
                tx.accountId.toString(),
                tx.amount.toPlainString(),
                tx.originalCurrency,
                tx.source.name
            ).joinToString(",")
        }

        return if (rows.isEmpty()) header else "$header\n$rows"
    }

    private fun String.csvEscaped(): String =
        if (contains(',') || contains('"') || contains('\n')) {
            "\"${replace("\"", "\"\"")}\""
        } else {
            this
        }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
