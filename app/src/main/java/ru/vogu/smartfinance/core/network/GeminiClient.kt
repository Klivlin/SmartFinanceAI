package ru.vogu.smartfinance.core.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import ru.vogu.smartfinance.BuildConfig
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.common.normalizeAsAliasKeyword
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.core.network.dto.GeminiContent
import ru.vogu.smartfinance.core.network.dto.GeminiGenerateContentRequest
import ru.vogu.smartfinance.core.network.dto.GeminiGenerationConfig
import ru.vogu.smartfinance.core.network.dto.GeminiPart
import ru.vogu.smartfinance.core.network.dto.GeminiSchema
import ru.vogu.smartfinance.core.network.dto.GeminiTransactionDto
import ru.vogu.smartfinance.core.common.DefaultCategories
import ru.vogu.smartfinance.core.security.GeminiApiKeyProvider
import ru.vogu.smartfinance.domain.model.AccountHint
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.ParsedExpense
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * Tier 3 каскада — CLAUDE.md §3.3. Единственная точка приложения, которая обращается
 * к внешнему LLM. Контекст чата НЕ передаётся (stateless) — только текущая фраза,
 * список категорий и текущая дата, что и держит промпт "ультракомпактным" (минимум токенов).
 */
class GeminiClient @Inject constructor(
    private val geminiApi: GeminiApi,
    private val apiKeyProvider: GeminiApiKeyProvider,
    private val json: Json
) {
    suspend fun parseExpenses(
        userPhraseSanitized: String,
        categories: List<Category>,
        defaultAccountName: String,
        currentDate: LocalDate = LocalDate.now()
    ): Resource<List<ParsedExpense>> {
        val apiKey = apiKeyProvider.getApiKey()
        if (apiKey.isBlank()) {
            return Resource.Error(
                "Не задан ключ Gemini API. Добавьте его в настройках приложения, " +
                    "чтобы обрабатывать сложные фразы (простые операции по-прежнему работают офлайн)."
            )
        }

        val request = buildRequest(userPhraseSanitized, categories, defaultAccountName, currentDate)

        return try {
            val response = geminiApi.generateContent(
                model = BuildConfig.GEMINI_MODEL,
                apiKey = apiKey,
                request = request
            )

            response.promptFeedback?.blockReason?.let { reason ->
                return Resource.Error("Gemini отклонил запрос по соображениям безопасности ($reason).")
            }

            val rawJson = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?: return Resource.Error("Gemini вернул пустой ответ. Попробуйте переформулировать.")

            val dtos = json.decodeFromString(ListSerializer(GeminiTransactionDto.serializer()), rawJson)
            val parsed = dtos.mapNotNull { it.toParsedExpenseOrNull(userPhraseSanitized, categories) }

            if (parsed.isEmpty()) {
                Resource.Error("Не удалось распознать ни одной операции во фразе.")
            } else {
                Resource.Success(parsed)
            }
        } catch (e: IOException) {
            Resource.Error("Нет соединения с сервером Gemini. Проверьте интернет и попробуйте снова.", e)
        } catch (e: HttpException) {
            Resource.Error(httpErrorMessage(e), e)
        } catch (e: SerializationException) {
            Resource.Error("Gemini вернул некорректный JSON, не удалось его разобрать.", e)
        }
    }

    private fun buildRequest(
        userPhrase: String,
        categories: List<Category>,
        defaultAccountName: String,
        currentDate: LocalDate
    ): GeminiGenerateContentRequest = GeminiGenerateContentRequest(
        systemInstruction = GeminiContent(parts = listOf(GeminiPart(buildSystemPrompt(categories, defaultAccountName, currentDate)))),
        contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(userPhrase)))),
        generationConfig = GeminiGenerationConfig(
            temperature = 0.1,
            responseSchema = buildResponseSchema()
        )
    )

    /**
     * Список категорий генерируется динамически из реальной БД (а не захардкожен, как в
     * иллюстративном примере CLAUDE.md §3.3) — так промпт никогда не рассинхронизируется
     * со Step 1 сидингом (см. [ru.vogu.smartfinance.core.common.DefaultCategories]).
     */
    private fun buildSystemPrompt(categories: List<Category>, defaultAccountName: String, currentDate: LocalDate): String {
        val categoriesList = categories.sortedBy { it.sortOrder }.joinToString(", ") { "${it.id}:${it.name}" }
        return """
            Ты — парсер финансовых операций для приложения личного учёта финансов. Пользователь пишет на русском.
            Верни ТОЛЬКО валидный JSON-массив объектов по заданной схеме. Никакого текста до или после массива, без markdown.
            Категории (id:название): [$categoriesList]
            Счёт по умолчанию, если в тексте явно не указан другой: "$defaultAccountName".
            Текущая дата: ${currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}.
            Правила:
            - Если во фразе несколько операций — верни несколько элементов массива, по одному на каждую.
            - Поле "d" вычисляй от текущей даты с учётом слов "вчера", "позавчера", дней недели и т.п.
            - Если счёт не упомянут явно — используй счёт по умолчанию.
            - Поле "a" — всегда положительное число (знак операции определяется полем "tp").
            - Поле "acc" — как можно ближе к тому, как счёт назван в тексте пользователя (или имя счёта по умолчанию).
        """.trimIndent()
    }

    private fun buildResponseSchema(): GeminiSchema = GeminiSchema(
        type = "ARRAY",
        items = GeminiSchema(
            type = "OBJECT",
            properties = mapOf(
                "a" to GeminiSchema(type = "NUMBER"),
                "c" to GeminiSchema(type = "INTEGER"),
                "t" to GeminiSchema(type = "STRING"),
                "d" to GeminiSchema(type = "STRING"),
                "acc" to GeminiSchema(type = "STRING"),
                "tp" to GeminiSchema(type = "STRING", enum = listOf("EXPENSE", "INCOME", "TRANSFER"))
            ),
            required = listOf("a", "c", "t", "d", "acc", "tp")
        )
    )

    /** Защитный маппинг недоверенных данных с границы сети — CLAUDE.md §9.4. */
    private fun GeminiTransactionDto.toParsedExpenseOrNull(
        rawInputText: String,
        categories: List<Category>
    ): ParsedExpense? {
        val amount = try {
            BigDecimal(a.toString())
        } catch (e: NumberFormatException) {
            return null
        }
        if (amount.signum() <= 0) return null

        val type = TransactionType.entries.firstOrNull { it.name == tp } ?: TransactionType.EXPENSE

        val categoryId = categories.firstOrNull { it.id == c.toLong() }?.id
            ?: if (type == TransactionType.INCOME) {
                DefaultCategories.INCOME_CATEGORY_ID
            } else {
                DefaultCategories.OTHER_CATEGORY_ID
            }

        val date = try {
            LocalDate.parse(d)
        } catch (e: DateTimeParseException) {
            LocalDate.now()
        }

        val title = t.trim().ifBlank { "Операция" }
        val trimmedAcc = acc.trim()

        return ParsedExpense(
            amount = amount,
            title = title,
            transactionType = type,
            source = TransactionSource.NLI,
            accountHint = trimmedAcc.takeIf { it.isNotEmpty() }
                ?.let { AccountHint(rawKeyword = it, normalizedKeyword = it.normalizeAsAliasKeyword()) },
            categoryId = categoryId,
            date = date,
            rawInputText = rawInputText
        )
    }

    private fun httpErrorMessage(e: HttpException): String = when (e.code()) {
        400 -> "Некорректный запрос к Gemini API."
        401, 403 -> "Неверный или недействительный ключ Gemini API. Проверьте его в настройках."
        429 -> "Превышен лимит запросов к Gemini API (бесплатный тир: 15 запросов в минуту). Попробуйте через минуту."
        in 500..599 -> "Сервис Gemini временно недоступен. Попробуйте позже."
        else -> "Ошибка Gemini API (код ${e.code()})."
    }
}
