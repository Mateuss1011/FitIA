package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.api.OpenFoodFactsClient
import com.example.ui.LocalFoodItem
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class TacoJsonItem(
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double = 0.0,
    val source: String = "TACO",
    val tip: String? = null
)

object FoodSearchRepository {

    private var cachedTacoList: List<LocalFoodItem>? = null

    fun getTacoFoods(context: Context?): List<LocalFoodItem> {
        cachedTacoList?.let { return it }

        if (context != null) {
            try {
                val jsonString = context.assets.open("taco_database.json").bufferedReader().use { it.readText() }
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val listType = Types.newParameterizedType(List::class.java, TacoJsonItem::class.java)
                val adapter = moshi.adapter<List<TacoJsonItem>>(listType)
                val parsed = adapter.fromJson(jsonString)
                if (!parsed.isNullOrEmpty()) {
                    val list = parsed.map {
                        LocalFoodItem(
                            name = it.name,
                            calories = it.calories,
                            protein = it.protein,
                            carbs = it.carbs,
                            fat = it.fat,
                            fiber = it.fiber,
                            source = "TACO",
                            tip = it.tip
                        )
                    }
                    cachedTacoList = list
                    return list
                }
            } catch (e: Exception) {
                Log.e("FoodSearchRepository", "Error reading taco_database.json from assets", e)
            }
        }

        val fallback = defaultTacoFoods()
        cachedTacoList = fallback
        return fallback
    }

    suspend fun searchOpenFoodFacts(query: String): List<LocalFoodItem> = withContext(Dispatchers.IO) {
        if (query.isBlank() || query.length < 2) return@withContext emptyList()
        try {
            val response = OpenFoodFactsClient.service.searchProducts(terms = query)
            val products = response.products ?: return@withContext emptyList()

            return@withContext products.mapNotNull { p ->
                val nameRaw = p.productNamePt?.takeIf { it.isNotBlank() }
                    ?: p.productName?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val brandStr = p.brands?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                val fullName = "$nameRaw$brandStr"

                val nut = p.nutriments ?: return@mapNotNull null

                val cals = (nut.energyKcal100g ?: nut.energyKcal ?: ((nut.energy100g ?: 0.0) / 4.184)).toInt()
                val prot = nut.proteins100g ?: 0.0
                val carb = nut.carbohydrates100g ?: 0.0
                val fat = nut.fat100g ?: 0.0
                val fiber = nut.fiber100g ?: 0.0

                if (cals == 0 && prot == 0.0 && carb == 0.0 && fat == 0.0) {
                    null
                } else {
                    LocalFoodItem(
                        name = fullName,
                        calories = cals,
                        protein = prot,
                        carbs = carb,
                        fat = fat,
                        fiber = fiber,
                        source = "OPEN_FOOD_FACTS",
                        tip = "Dados nutricionais por 100g (API Open Food Facts)"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("FoodSearchRepository", "Error searching Open Food Facts", e)
            emptyList()
        }
    }

    suspend fun getProductByBarcode(barcode: String): LocalFoodItem? = withContext(Dispatchers.IO) {
        if (barcode.isBlank()) return@withContext null
        try {
            val response = OpenFoodFactsClient.service.getProductByBarcode(barcode.trim())
            val p = response.product ?: return@withContext null
            val nameRaw = p.productNamePt?.takeIf { it.isNotBlank() }
                ?: p.productName?.takeIf { it.isNotBlank() }
                ?: "Produto com Código $barcode"
            val brandStr = p.brands?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
            val fullName = "$nameRaw$brandStr"
            val nut = p.nutriments

            val cals = (nut?.energyKcal100g ?: nut?.energyKcal ?: ((nut?.energy100g ?: 0.0) / 4.184)).toInt()
            val prot = nut?.proteins100g ?: 0.0
            val carb = nut?.carbohydrates100g ?: 0.0
            val fat = nut?.fat100g ?: 0.0
            val fiber = nut?.fiber100g ?: 0.0

            LocalFoodItem(
                name = fullName,
                calories = cals,
                protein = prot,
                carbs = carb,
                fat = fat,
                fiber = fiber,
                source = "OPEN_FOOD_FACTS",
                tip = "Cód. Barras: $barcode"
            )
        } catch (e: Exception) {
            Log.e("FoodSearchRepository", "Error getting product by barcode", e)
            null
        }
    }

    private fun defaultTacoFoods(): List<LocalFoodItem> {
        return listOf(
            LocalFoodItem("Arroz Branco Cozido", 128, 2.5, 28.1, 0.2, 1.6, "TACO", "Combinação clássica com feijão."),
            LocalFoodItem("Arroz Integral Cozido", 124, 2.6, 25.8, 1.0, 2.7, "TACO", "Rico em fibras e baixo índice glicêmico."),
            LocalFoodItem("Feijão Carioca Cozido", 76, 4.8, 13.6, 0.5, 8.5, "TACO", "Excelente fonte de fibras e ferro."),
            LocalFoodItem("Feijão Preto Cozido", 77, 4.5, 14.0, 0.5, 8.4, "TACO", "Rico em antioxidantes."),
            LocalFoodItem("Peito de Frango Grelhado", 165, 31.0, 0.0, 3.6, 0.0, "TACO", "Proteína magra para hipertrofia."),
            LocalFoodItem("Patinho Moído Cozido", 219, 35.9, 0.0, 7.3, 0.0, "TACO", "Carne bovina magra e nutritiva."),
            LocalFoodItem("Alcatra Grelhada", 185, 28.0, 0.0, 7.5, 0.0, "TACO", "Rica em ferro e zinco."),
            LocalFoodItem("Ovo Cozido Inteiro", 146, 13.3, 0.6, 9.5, 0.0, "TACO", "Proteína de referência com gema rica."),
            LocalFoodItem("Ovo Frito", 240, 15.6, 0.6, 18.6, 0.0, "TACO", "Preparo rápido."),
            LocalFoodItem("Clara de Ovo Cozida", 54, 11.3, 0.7, 0.2, 0.0, "TACO", "Proteína pura."),
            LocalFoodItem("Tapioca Pronta (Massa)", 240, 0.0, 60.0, 0.0, 0.5, "TACO", "Carboidrato simples sem glúten."),
            LocalFoodItem("Batata Doce Cozida", 86, 1.6, 20.1, 0.1, 2.2, "TACO", "Baixo índice glicêmico."),
            LocalFoodItem("Batata Inglesa Cozida", 52, 1.2, 11.9, 0.1, 1.3, "TACO", "Baixa densidade calórica."),
            LocalFoodItem("Mandioca Cozida", 125, 0.6, 30.1, 0.3, 1.6, "TACO", "Energia duradoura."),
            LocalFoodItem("Banana Prata", 89, 1.3, 22.8, 0.3, 2.0, "TACO", "Rica em potássio."),
            LocalFoodItem("Banana Nanica", 92, 1.4, 23.8, 0.3, 1.9, "TACO", "Doce e ideal pré-treino."),
            LocalFoodItem("Maçã Gala com Casca", 52, 0.3, 13.8, 0.2, 2.4, "TACO", "Rica em pectina."),
            LocalFoodItem("Mamão Formosa", 45, 0.8, 11.6, 0.1, 1.8, "TACO", "Contém papaína digestiva."),
            LocalFoodItem("Laranja Pera", 46, 1.0, 11.5, 0.1, 1.8, "TACO", "Rica em vitamina C."),
            LocalFoodItem("Morango Cru", 30, 0.9, 6.8, 0.3, 1.7, "TACO", "Baixa caloria por volume."),
            LocalFoodItem("Abacaxi Cru", 48, 0.9, 12.3, 0.1, 1.0, "TACO", "Possui bromelina."),
            LocalFoodItem("Abacate Cru", 96, 1.2, 6.0, 8.4, 6.3, "TACO", "Gordura monoinsaturada e fibra."),
            LocalFoodItem("Cenoura Crua", 34, 1.3, 7.7, 0.2, 3.2, "TACO", "Rica em betacaroteno."),
            LocalFoodItem("Brócolis Cozido", 25, 2.1, 4.4, 0.5, 3.4, "TACO", "Rico em fibras e vegetais."),
            LocalFoodItem("Azeite de Oliva Extra Virgem", 884, 0.0, 0.0, 100.0, 0.0, "TACO", "Gordura cardioprotetora."),
            LocalFoodItem("Castanha-do-Pará", 643, 14.5, 15.1, 63.5, 7.9, "TACO", "Rica em selênio."),
            LocalFoodItem("Pasta de Amendoim", 588, 26.0, 20.0, 50.0, 6.0, "TACO", "Concentrado de gorduras e proteínas."),
            LocalFoodItem("Leite Integral", 60, 3.2, 4.6, 3.2, 0.0, "TACO", "Rico em cálcio e proteínas."),
            LocalFoodItem("Leite Desnatado", 35, 3.4, 4.9, 0.1, 0.0, "TACO", "Cálcio e proteína sem gordura."),
            LocalFoodItem("Iogurte Natural Integral", 66, 4.1, 4.5, 3.6, 0.0, "TACO", "Rico em probióticos."),
            LocalFoodItem("Queijo Cottage", 98, 11.1, 3.4, 4.3, 0.0, "TACO", "Queijo magro rico em caseína."),
            LocalFoodItem("Pão Francês", 300, 8.0, 58.6, 3.1, 2.3, "TACO", "Tradição do café (1 unid ~50g)."),
            LocalFoodItem("Pão de Forma Integral", 253, 9.4, 49.9, 3.7, 6.9, "TACO", "Rico em fibras."),
            LocalFoodItem("Aveia em Flocos", 394, 13.9, 66.6, 8.5, 9.1, "TACO", "Contém betaglucanas."),
            LocalFoodItem("Filé de Tilápia Grelhado", 128, 26.0, 0.0, 2.7, 0.0, "TACO", "Peixe magro de fácil digestão."),
            LocalFoodItem("Salmão Grelhado", 211, 24.2, 0.0, 12.1, 0.0, "TACO", "Fonte de Ômega-3."),
            LocalFoodItem("Whey Protein Concentrado 80%", 400, 80.0, 10.0, 5.0, 0.0, "TACO", "Rápida absorção proteica."),
            LocalFoodItem("Whey Protein Isolado 90%", 366, 90.0, 3.3, 0.0, 0.0, "TACO", "Ultra puro sem lactose."),
            LocalFoodItem("Creatina Monohidratada", 0, 0.0, 0.0, 0.0, 0.0, "TACO", "Força muscular e ATP.")
        )
    }
}
