package ru.vogu.smartfinance.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTO для Google Generative Language API (generateContent), REST-вызов напрямую через
// Retrofit/OkHttp — CLAUDE.md §6 "Сеть & AI". Structured Outputs (responseSchema) гарантируют,
// что модель вернёт строго валидный JSON без markdown-обёртки (§3.3).

@Serializable
data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double = 0.1,
    @SerialName("responseMimeType") val responseMimeType: String = "application/json",
    @SerialName("responseSchema") val responseSchema: GeminiSchema
)

/**
 * Урезанное подмножество OpenAPI Schema, которое принимает Gemini `responseSchema`.
 * Значения [type] — строго из набора Gemini: "OBJECT", "ARRAY", "STRING", "NUMBER",
 * "INTEGER", "BOOLEAN".
 */
@Serializable
data class GeminiSchema(
    val type: String,
    val items: GeminiSchema? = null,
    val properties: Map<String, GeminiSchema>? = null,
    val required: List<String>? = null,
    val enum: List<String>? = null
)

@Serializable
data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate>? = null,
    val promptFeedback: GeminiPromptFeedback? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

@Serializable
data class GeminiPromptFeedback(
    val blockReason: String? = null
)

/**
 * Ультракомпактная схема одной операции, которую модель кладёт в JSON-массив — CLAUDE.md §3.3:
 * `{"a":number,"c":category_id,"t":"title","d":"YYYY-MM-DD","acc":"account","tp":"EXPENSE|INCOME|TRANSFER"}`
 *
 * `a` — Double, а не BigDecimal: это сырые данные границы сети (JSON-число из ответа модели),
 * конвертация в BigDecimal происходит сразу при маппинге в [ru.vogu.smartfinance.domain.model.ParsedExpense]
 * через строковое представление, во избежание ошибок двоичного округления Double.
 */
@Serializable
data class GeminiTransactionDto(
    val a: Double,
    val c: Int,
    val t: String,
    val d: String,
    val acc: String,
    val tp: String
)

/**
 * Одна позиция товарного чека, распознаваемая Gemini из предотфильтрованных OCR-строк —
 * CLAUDE.md §4.2. `n` — название позиции, `a` — сумма позиции, `c` — id категории.
 */
@Serializable
data class GeminiReceiptItemDto(
    val n: String,
    val a: Double,
    val c: Int
)

/**
 * Ответ "к какой категории отнести это название" — CLAUDE.md §3.2. Используется, когда
 * Tier 1 успешно распознал сумму/название локально, но ни персональный кэш алиасов, ни
 * встроенный офлайн-словарь не знают такое слово (в том числе — когда у пользователя есть
 * СВОЯ категория вроде "Подписки", про которую заранее ничего не могло быть захардкожено).
 */
@Serializable
data class GeminiCategoryClassificationDto(
    val c: Long
)
