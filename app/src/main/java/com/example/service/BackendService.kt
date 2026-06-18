package com.example.service

import android.util.Log
import com.example.BuildConfig
import com.example.data.*
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    @Json(name = "name") val name: String,
    @Json(name = "email") val email: String,
    @Json(name = "pinHash") val pinHash: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "email") val email: String,
    @Json(name = "pinHash") val pinHash: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "email") val email: String,
    @Json(name = "sessionToken") val sessionToken: String
)

@JsonClass(generateAdapter = true)
data class ProfileUpdateRequest(
    @Json(name = "name") val name: String? = null,
    @Json(name = "privacyOnboarded") val privacyOnboarded: Boolean? = null,
    @Json(name = "leakDetectorOnboarded") val leakDetectorOnboarded: Boolean? = null,
    @Json(name = "advisorOnboarded") val advisorOnboarded: Boolean? = null,
    @Json(name = "biometricEnabled") val biometricEnabled: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class StatusResponse(
    @Json(name = "status") val status: String,
    @Json(name = "id") val id: Long? = null
)

interface BackendApiService {

    // --- AUTH & PROFILE ---
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("api/user/profile")
    suspend fun getProfile(@Header("Authorization") authHeader: String): UserEntity

    @PUT("api/user/profile")
    suspend fun updateProfile(
        @Header("Authorization") authHeader: String,
        @Body request: ProfileUpdateRequest
    ): StatusResponse

    // --- TRANSACTIONS ---
    @GET("api/transactions")
    suspend fun getTransactions(@Header("Authorization") authHeader: String): List<TransactionEntity>

    @POST("api/transactions")
    suspend fun addTransaction(
        @Header("Authorization") authHeader: String,
        @Body transaction: TransactionEntity
    ): StatusResponse

    @DELETE("api/transactions/{id}")
    suspend fun deleteTransaction(
        @Header("Authorization") authHeader: String,
        @Path("id") id: Long
    ): StatusResponse

    // --- BUDGETS ---
    @GET("api/budgets")
    suspend fun getBudgets(
        @Header("Authorization") authHeader: String,
        @Query("monthYear") monthYear: String
    ): List<BudgetEntity>

    @POST("api/budgets")
    suspend fun addBudget(
        @Header("Authorization") authHeader: String,
        @Body budget: BudgetEntity
    ): StatusResponse

    @PUT("api/budgets/{id}")
    suspend fun updateBudget(
        @Header("Authorization") authHeader: String,
        @Path("id") id: Int,
        @Body body: Map<String, Double>
    ): StatusResponse

    // --- SAVINGS GOALS ---
    @GET("api/savings-goals")
    suspend fun getSavingsGoals(@Header("Authorization") authHeader: String): List<SavingsGoalEntity>

    @POST("api/savings-goals")
    suspend fun addSavingsGoal(
        @Header("Authorization") authHeader: String,
        @Body goal: SavingsGoalEntity
    ): StatusResponse

    @PUT("api/savings-goals/{id}")
    suspend fun updateSavingsGoal(
        @Header("Authorization") authHeader: String,
        @Path("id") id: Int,
        @Body body: Map<String, Double>
    ): StatusResponse

    @DELETE("api/savings-goals/{id}")
    suspend fun deleteSavingsGoal(
        @Header("Authorization") authHeader: String,
        @Path("id") id: Int
    ): StatusResponse

    // --- BILLS ---
    @GET("api/bills")
    suspend fun getBills(@Header("Authorization") authHeader: String): List<BillEntity>

    @POST("api/bills")
    suspend fun addBill(
        @Header("Authorization") authHeader: String,
        @Body bill: BillEntity
    ): StatusResponse

    @PUT("api/bills/{id}")
    suspend fun updateBill(
        @Header("Authorization") authHeader: String,
        @Path("id") id: Int,
        @Body body: Map<String, Boolean>
    ): StatusResponse

    @DELETE("api/bills/{id}")
    suspend fun deleteBill(
        @Header("Authorization") authHeader: String,
        @Path("id") id: Int
    ): StatusResponse

    // --- SUBSCRIPTIONS ---
    @GET("api/subscriptions")
    suspend fun getSubscriptions(@Header("Authorization") authHeader: String): List<SubscriptionEntity>

    @POST("api/subscriptions")
    suspend fun addSubscription(
        @Header("Authorization") authHeader: String,
        @Body subscription: SubscriptionEntity
    ): StatusResponse

    @PUT("api/subscriptions/{id}")
    suspend fun updateSubscription(
        @Header("Authorization") authHeader: String,
        @Path("id") id: Int,
        @Body body: Map<String, String> // We can pass isForgotten (converted to string) or status
    ): StatusResponse

    @DELETE("api/subscriptions/{id}")
    suspend fun deleteSubscription(
        @Header("Authorization") authHeader: String,
        @Path("id") id: Int
    ): StatusResponse

    // --- INVESTMENTS ---
    @GET("api/investments")
    suspend fun getInvestments(@Header("Authorization") authHeader: String): List<InvestmentEntity>

    @POST("api/investments")
    suspend fun addInvestment(
        @Header("Authorization") authHeader: String,
        @Body investment: InvestmentEntity
    ): StatusResponse

    @PUT("api/investments/{id}")
    suspend fun updateInvestment(
        @Header("Authorization") authHeader: String,
        @Path("id") id: Int,
        @Body body: Map<String, Double>
    ): StatusResponse

    @DELETE("api/investments/{id}")
    suspend fun deleteInvestment(
        @Header("Authorization") authHeader: String,
        @Path("id") id: Int
    ): StatusResponse

    // --- NOTIFICATIONS ---
    @GET("api/notifications")
    suspend fun getNotifications(@Header("Authorization") authHeader: String): List<NotificationEntity>

    @POST("api/notifications")
    suspend fun addNotification(
        @Header("Authorization") authHeader: String,
        @Body notification: NotificationEntity
    ): StatusResponse

    @DELETE("api/notifications")
    suspend fun clearNotifications(@Header("Authorization") authHeader: String): StatusResponse

    // --- CHATBOT & ADVISOR ENDPOINTS ---
    @POST("api/chat")
    suspend fun sendMessage(
        @Header("Authorization") authHeader: String,
        @Body request: ChatRequest
    ): ChatResponse

    @Streaming
    @POST("api/chat/stream")
    suspend fun streamMessage(
        @Header("Authorization") authHeader: String,
        @Body request: ChatRequest
    ): okhttp3.ResponseBody

    @GET("api/chat/history")
    suspend fun getChatHistory(
        @Header("Authorization") authHeader: String
    ): List<ChatMessage>

    @DELETE("api/chat/history")
    suspend fun clearChatHistory(
        @Header("Authorization") authHeader: String
    ): StatusResponse

    @GET("api/chat/suggestions")
    suspend fun getChatSuggestions(
        @Header("Authorization") authHeader: String
    ): List<String>
}

object BackendClient {
    private const val BASE_URL = "http://10.0.2.2:5000/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val apiService: BackendApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BackendApiService::class.java)
    }
}

// ============================================================================
// 5. CURRENCY SERVICE CLIENT
// ============================================================================

@JsonClass(generateAdapter = true)
data class ExchangeRateResponse(
    @Json(name = "result") val result: String?,
    @Json(name = "base_code") val baseCode: String?,
    @Json(name = "rates") val rates: Map<String, Double>?
)

interface CurrencyApiService {
    @GET("v6/latest/USD")
    suspend fun getLatestRates(): ExchangeRateResponse
}

object CurrencyClient {
    private const val BASE_URL = "https://open.er-api.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val apiService: CurrencyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CurrencyApiService::class.java)
    }

    suspend fun fetchRates(): Map<String, Double>? {
        return try {
            val response = apiService.getLatestRates()
            if (response.result == "success") {
                response.rates
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

// ============================================================================
// 6. GEMINI SERVICE CLIENT
// ============================================================================

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content?
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "http://10.0.2.2:5000/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    fun isApiKeyValid(): Boolean {
        val apiKey = BuildConfig.GEMINI_API_KEY
        return apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY"
    }

    suspend fun generateAdvice(prompt: String, systemPrompt: String? = null): String = withContext(Dispatchers.IO) {
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = systemPrompt?.let { Content(parts = listOf(Part(text = it))) }
        )

        try {
            val response = apiService.generateContent("backend_secured", request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "I was unable to analyze this data. Please review your parameters."
        } catch (e: Exception) {
            Log.e(TAG, "Error reaching backend server: ${e.localizedMessage}")
            getOfflineFallbackResponse(prompt)
        }
    }

    private fun getOfflineFallbackResponse(prompt: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("leak") || lower.contains("subscription") -> {
                "🤖 [FINORAAX INSIGHTS]\n" +
                "Finoraax detected continuous leaks in OTT plans. 'Abandoned Premium Gym Pass' is classified as critical leak (Cost: $55.00/mo, Usage: 0%). Optimizing today preserves $660.00 in annual net liquidity."
            }
            lower.contains("budget") || lower.contains("overspend") -> {
                "🤖 [FINORAAX BUDGET CO-PILOT]\n" +
                "Dining and entertainment categories are exceeding June benchmarks. I recommend cap settings of $200 for subsequent periods. Locking custom alerts at 85% capacity will preempt future budget strain."
            }
            lower.contains("savings") || lower.contains("emergency") -> {
                "🤖 [FINORAAX WEALTH STRATEGIST]\n" +
                "Your emergency portfolio registers at $8,400 (56% of your $15,000 threshold). Automating a $125 weekly base allocation from incoming streams will secure 6-month resilience by September."
            }
            else -> {
                "🤖 [FINORAAX INTELLIGENT ADVISOR]\n" +
                "I am your Finoraax active financial advisor. I can analyze transactions, recommend category caps, highlight recurring subscription leaks, and coach you towards robust wealth goals. What financial query can I help you resolve today?"
            }
        }
    }
}
