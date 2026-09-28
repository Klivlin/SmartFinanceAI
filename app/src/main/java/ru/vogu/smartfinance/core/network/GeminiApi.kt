package ru.vogu.smartfinance.core.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import ru.vogu.smartfinance.core.network.dto.GeminiGenerateContentRequest
import ru.vogu.smartfinance.core.network.dto.GeminiGenerateContentResponse

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): GeminiGenerateContentResponse
}
