package ru.vogu.smartfinance.data.parser

import ru.vogu.smartfinance.domain.model.ParsedExpense
import javax.inject.Inject

/**
 * Разбивает ОДНО сообщение чата на НЕСКОЛЬКО операций — CLAUDE.md §3.1: "такси 450, кофе 200"
 * должно стать двумя отдельными Action Card, а не одной операцией "такси 450, кофе 200" с
 * "суммой" 450. Работает поверх Tier 1 (0 токенов): делит текст по явным разделителям
 * (запятая, точка с запятой, перенос строки, союз "и") и пытается разобрать КАЖДЫЙ фрагмент
 * regex-парсером по отдельности.
 *
 * Намеренно "всё или ничего": если хотя бы один фрагмент не разбирается как отдельная
 * операция (например, название содержит "и" как обычное слово — "хлеб и молоко 300"),
 * разбиение целиком отменяется и исходный текст уходит на Tier 3 (LLM), которая справляется
 * с такими фразами по контексту. Ложное разбиение было бы хуже отсутствия разбиения.
 */
class MultiExpenseSplitter @Inject constructor(
    private val regexLocalParser: RegexLocalParser
) {
    fun trySplit(rawInputText: String): List<ParsedExpense>? {
        val segments = SPLIT_REGEX.split(rawInputText)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (segments.size < 2) return null

        val parsed = segments.map { regexLocalParser.tryParse(it) }
        if (parsed.any { it == null }) return null

        return parsed.filterNotNull()
    }

    private companion object {
        /**
         * Разделители операций внутри одного сообщения: `,` `;` перенос строки или отдельное
         * слово "и". Границы слова у "и" сделаны через lookaround по пробелу, а не `\b` —
         * стандартный `\b` в Java/ICU regex завязан на ASCII-класс `\w` и не считает кириллицу
         * "буквой" (см. аналогичный фикс в RegexLocalParser.ACCOUNT_KEYWORD_PATTERNS), из-за
         * чего на Android-устройстве повёл бы себя непредсказуемо.
         */
        val SPLIT_REGEX = Regex("""[,;\n]+|(?<=\s)и(?=\s)""", RegexOption.IGNORE_CASE)
    }
}
