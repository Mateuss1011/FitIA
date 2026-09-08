package com.example.data.api

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// --- Gemini / AI Domain Request Models ---

data class InlineData(
    val mimeType: String,
    val data: String
)

data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

data class Content(
    val parts: List<Part>
)

data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null
)

// --- AI Response Models ---

data class Candidate(
    val content: Content
)

data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

// --- Domain Models for parsed FitAI plans ---

data class WorkoutPlanExerciseJson(
    val workoutDay: String, // e.g. "A", "B", "C"
    val name: String,
    val series: Int,
    val reps: String,
    val badge: String? = null,
    val instructions: String? = null
)

data class MealJson(
    val name: String,
    val description: String,
    val calories: Int,
    val protein: String = "",
    val carbs: String = "",
    val fat: String = ""
)

data class FitAiGenerationResultJson(
    val workoutPlan: List<WorkoutPlanExerciseJson>,
    val meals: List<MealJson>,
    val notificationMessage: String
)

// --- Single Meal Alternative JSON ---

data class MealAlternativeJson(
    val description: String,
    val calories: Int,
    val protein: String = "",
    val carbs: String = "",
    val fat: String = ""
)

// --- AI Search Food Item JSON ---

data class FoodItemJson(
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val tip: String? = null
)

// --- AI Multimodal Plate Scanner JSON ---

data class FoodComponentJson(
    val name: String,
    val quantityGrams: Double,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double = 0.0
)

data class PlateAnalysisResultJson(
    val dishName: String,
    val totalCalories: Int,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double,
    val totalFiber: Double = 0.0,
    val items: List<FoodComponentJson>
)

// --- Secure Backend Service Interface ---

interface GeminiApiService {
    suspend fun generateContent(
        request: GenerateContentRequest
    ): GenerateContentResponse
}

/**
 * Implementação segura que se conecta ao backend (Firebase Cloud Functions).
 * A autenticação do usuário logado é enviada pelo Firebase SDK de forma criptografada.
 * NENHUMA chave da API Gemini é conhecida ou armazenada pelo cliente Android.
 */
class FirebaseAiBackendService(
    private val moshi: Moshi
) : GeminiApiService {

    private val functions: FirebaseFunctions? by lazy {
        try {
            FirebaseFunctions.getInstance("us-central1")
        } catch (e: Throwable) {
            Log.w("FitAI_AI", "FirebaseFunctions indisponível no cliente: ${e.message}")
            null
        }
    }

    override suspend fun generateContent(request: GenerateContentRequest): GenerateContentResponse = withContext(Dispatchers.IO) {
        val f = functions ?: throw IllegalStateException("Firebase Functions não inicializado no dispositivo.")

        val reqAdapter = moshi.adapter(GenerateContentRequest::class.java)
        val jsonStr = reqAdapter.toJson(request)
        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val mapAdapter = moshi.adapter<Map<String, Any>>(mapType)
        val requestMap = mapAdapter.fromJson(jsonStr) ?: emptyMap()

        val callable = f.getHttpsCallable("fitAiGenerateContent")
        val task = callable.call(requestMap)
        val httpsResult = Tasks.await(task, 45, TimeUnit.SECONDS)
        val data = httpsResult.data ?: throw IllegalStateException("Resposta vazia do backend de IA.")

        val respAdapter = moshi.adapter(GenerateContentResponse::class.java)
        val respJsonStr = if (data is Map<*, *>) {
            moshi.adapter(Map::class.java).toJson(data)
        } else {
            data.toString()
        }
        respAdapter.fromJson(respJsonStr) ?: throw IllegalStateException("Falha ao analisar a resposta recebida do backend.")
    }
}

object RetrofitClient {
    val moshiParser: Moshi by lazy {
        try {
            Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Erro ao inicializar parser Moshi: ${e.message}", e)
            Moshi.Builder().build()
        }
    }

    val service: GeminiApiService by lazy {
        FirebaseAiBackendService(moshiParser)
    }
}
