package com.example.data.api

import com.squareup.moshi.Json
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

data class OffSearchResponse(
    @param:Json(name = "products") val products: List<OffProduct>? = null
)

data class OffProductResponse(
    @param:Json(name = "status") val status: Int? = null,
    @param:Json(name = "product") val product: OffProduct? = null
)

data class OffProduct(
    @param:Json(name = "product_name") val productName: String? = null,
    @param:Json(name = "product_name_pt") val productNamePt: String? = null,
    @param:Json(name = "brands") val brands: String? = null,
    @param:Json(name = "code") val code: String? = null,
    @param:Json(name = "nutriments") val nutriments: OffNutriments? = null
)

data class OffNutriments(
    @param:Json(name = "energy-kcal_100g") val energyKcal100g: Double? = null,
    @param:Json(name = "energy-kcal") val energyKcal: Double? = null,
    @param:Json(name = "energy_100g") val energy100g: Double? = null,
    @param:Json(name = "proteins_100g") val proteins100g: Double? = null,
    @param:Json(name = "carbohydrates_100g") val carbohydrates100g: Double? = null,
    @param:Json(name = "fat_100g") val fat100g: Double? = null,
    @param:Json(name = "fiber_100g") val fiber100g: Double? = null
)

interface OpenFoodFactsApiService {
    @GET("cgi/search.pl")
    suspend fun searchProducts(
        @Query("search_terms") terms: String,
        @Query("search_simple") searchSimple: Int = 1,
        @Query("action") action: String = "process",
        @Query("json") json: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("lc") language: String = "pt"
    ): OffSearchResponse

    @GET("api/v0/product/{barcode}.json")
    suspend fun getProductByBarcode(
        @Path("barcode") barcode: String
    ): OffProductResponse
}

object OpenFoodFactsClient {
    private const val BASE_URL = "https://world.openfoodfacts.org/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val service: OpenFoodFactsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenFoodFactsApiService::class.java)
    }
}
