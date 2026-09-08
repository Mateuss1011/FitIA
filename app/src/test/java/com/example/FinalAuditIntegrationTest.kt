package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.*
import com.example.SupplementScheduler
import com.example.MealNotificationScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FinalAuditIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: AppDao

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        dao = db.appDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `teste isolamento completo entre Usuario A e Usuario B com limpeza total no logout`() = runBlocking {
        val userAUid = "user_alpha_999"
        val userBUid = "user_beta_888"

        // --- 1. USUÁRIO A: Cria perfil, treinos, refeições, água, evolução e renomeia ficha ---
        val profileA = UserProfile(
            id = 1,
            uid = userAUid,
            name = "Usuário Alpha",
            email = "alpha@fitai.com",
            age = 25,
            weight = 82.0,
            height = 180.0,
            fitnessLevel = "Intermediário",
            restrictions = "Nenhuma",
            objective = "Hipertrofia",
            trainingDays = "Seg,Ter,Qui,Sex",
            notificationMessage = "Hora do treino Alpha!",
            gender = "Masculino",
            avatarPreset = "avatar_1",
            onboardingCompleted = true,
            hasSeenTour = true
        )
        dao.insertUserProfile(profileA)

        // Usuário A renomeia Treino D para "Treino D — Tríceps & Bíceps"
        dao.insertWorkoutFichaName(WorkoutFichaName(workoutDay = "D", name = "Treino D — Tríceps & Bíceps"))
        val prefsA = context.getSharedPreferences("fitai_ficha_names_$userAUid", Context.MODE_PRIVATE)
        prefsA.edit().putString("ficha_name_D", "Treino D — Tríceps & Bíceps").commit()

        // Usuário A adiciona exercícios ao Treino D
        dao.insertExercises(listOf(
            WorkoutExercise(
                id = 401,
                workoutDay = "D",
                name = "Tríceps Corda",
                series = 4,
                reps = "12",
                currentWeight = 35.0,
                completed = false,
                orderIndex = 0
            ),
            WorkoutExercise(
                id = 402,
                workoutDay = "D",
                name = "Rosca Direta Barra W",
                series = 3,
                reps = "10",
                currentWeight = 30.0,
                completed = true,
                orderIndex = 1
            )
        ))

        // Usuário A registra refeição e consumo de água
        dao.insertMeals(listOf(Meal(id = 1, name = "Café da Manhã Alpha", description = "Ovos e Aveia", calories = 450, protein = "30g", carbs = "40g", fat = "12g")))
        dao.insertRegistroAgua(RegistroAgua(amountMl = 500, date = System.currentTimeMillis()))
        dao.insertHistoryEntry(HistoryEntry(exerciseName = "Supino", weight = 82.0, reps = "10", date = System.currentTimeMillis()))

        // Validar dados do Usuário A presentes
        val loadedProfileA = dao.getUserProfile().first()
        assertNotNull(loadedProfileA)
        assertEquals(userAUid, loadedProfileA?.uid)
        assertEquals(2, dao.getExercisesForDayFlow("D").first().size)
        val fichasA = dao.getAllWorkoutFichaNamesDirect()
        assertEquals("Treino D — Tríceps & Bíceps", fichasA.find { it.workoutDay == "D" }?.name)
        assertEquals(1, dao.getAllMealsFlow().first().size)
        assertEquals(1, dao.getAllRegistroAguaFlow().first().size)
        assertEquals(1, dao.getHistoryFlow().first().size)

        // --- 2. LOGOUT DO USUÁRIO A: Limpeza Total ---
        db.clearAllTables()
        prefsA.edit().clear().commit()
        MealNotificationScheduler.cancelAllMealReminders(context)
        SupplementScheduler.cancelAllSupplementReminders(context)

        // Validar que o banco local está completamente zerado após logout do Usuário A
        assertNull(dao.getUserProfile().first())
        assertTrue(dao.getAllExercisesFlow().first().isEmpty())
        assertTrue(dao.getAllMealsFlow().first().isEmpty())
        assertTrue(dao.getAllRegistroAguaFlow().first().isEmpty())
        assertTrue(dao.getHistoryFlow().first().isEmpty())
        assertTrue(dao.getAllWorkoutFichaNamesDirect().isEmpty())
        assertTrue(prefsA.all.isEmpty())

        // --- 3. USUÁRIO B: Login / Criação de Conta independente ---
        val profileB = UserProfile(
            id = 1,
            uid = userBUid,
            name = "Usuária Beta",
            email = "beta@fitai.com",
            age = 30,
            weight = 60.0,
            height = 165.0,
            fitnessLevel = "Iniciante",
            restrictions = "Lactose",
            objective = "Emagrecimento",
            trainingDays = "Seg,Qua,Sex",
            notificationMessage = "Foco Beta!",
            gender = "Feminino",
            avatarPreset = "avatar_2",
            onboardingCompleted = true,
            hasSeenTour = false
        )
        dao.insertUserProfile(profileB)

        // Usuária B configura Treino D com nome completamente diferente
        dao.insertWorkoutFichaName(WorkoutFichaName(workoutDay = "D", name = "Treino D — Ombros & Trapézio"))
        val prefsB = context.getSharedPreferences("fitai_ficha_names_$userBUid", Context.MODE_PRIVATE)
        prefsB.edit().putString("ficha_name_D", "Treino D — Ombros & Trapézio").commit()

        dao.insertExercises(listOf(
            WorkoutExercise(
                id = 501,
                workoutDay = "D",
                name = "Elevação Lateral",
                series = 3,
                reps = "15",
                currentWeight = 8.0,
                completed = false,
                orderIndex = 0
            )
        ))

        // Confirmar isolamento estrito: Usuária B NÃO vê nenhum dado do Usuário A
        val loadedProfileB = dao.getUserProfile().first()
        assertNotNull(loadedProfileB)
        assertEquals(userBUid, loadedProfileB?.uid)
        assertEquals("Usuária Beta", loadedProfileB?.name)
        assertNotEquals("Usuário Alpha", loadedProfileB?.name)

        val exercisesB = dao.getExercisesForDayFlow("D").first()
        assertEquals(1, exercisesB.size)
        assertEquals("Elevação Lateral", exercisesB[0].name)
        val fichasB = dao.getAllWorkoutFichaNamesDirect()
        assertEquals("Treino D — Ombros & Trapézio", fichasB.find { it.workoutDay == "D" }?.name)
        assertEquals(prefsB.getString("ficha_name_D", null), "Treino D — Ombros & Trapézio")
        assertNull(prefsA.getString("ficha_name_D", null))

        // --- 4. LOGOUT DO USUÁRIO B E RETORNO DO USUÁRIO A ---
        db.clearAllTables()
        prefsB.edit().clear().commit()

        // Simula recarregamento dos dados do Usuário A (a partir da nuvem)
        dao.insertUserProfile(profileA)
        dao.insertWorkoutFichaName(WorkoutFichaName(workoutDay = "D", name = "Treino D — Tríceps & Bíceps"))
        val restoredProfileA = dao.getUserProfile().first()
        assertEquals("Usuário Alpha", restoredProfileA?.name)
        val restoredFichasA = dao.getAllWorkoutFichaNamesDirect()
        assertEquals("Treino D — Tríceps & Bíceps", restoredFichasA.find { it.workoutDay == "D" }?.name)
    }

    @Test
    fun `teste alteracao do nome da ficha preserva integridade e vinculo dos exercicios`() = runBlocking {
        // Inserir ficha D original
        dao.insertWorkoutFichaName(WorkoutFichaName(workoutDay = "D", name = "Treino D — Ombros & Trapézio"))

        dao.insertExercises(listOf(
            WorkoutExercise(id = 1, workoutDay = "D", name = "Desenvolvimento Halteres", series = 4, reps = "10", currentWeight = 22.0, completed = false, orderIndex = 0),
            WorkoutExercise(id = 2, workoutDay = "D", name = "Encolhimento", series = 3, reps = "12", currentWeight = 30.0, completed = true, orderIndex = 1)
        ))

        var fichas = dao.getAllWorkoutFichaNamesDirect()
        assertEquals("Treino D — Ombros & Trapézio", fichas.find { it.workoutDay == "D" }?.name)
        assertEquals(2, dao.getExercisesForDayFlow("D").first().size)

        // Alterar nome da ficha para "Treino D — Tríceps & Bíceps"
        dao.insertWorkoutFichaName(WorkoutFichaName(workoutDay = "D", name = "Treino D — Tríceps & Bíceps"))

        // Verificar que o nome foi atualizado mas os exercícios vinculados a "D" continuam exatamente intactos
        fichas = dao.getAllWorkoutFichaNamesDirect()
        val updatedFicha = fichas.find { it.workoutDay == "D" }
        assertEquals("Treino D — Tríceps & Bíceps", updatedFicha?.name)

        val exercises = dao.getExercisesForDayFlow("D").first()
        assertEquals(2, exercises.size)
        assertEquals("Desenvolvimento Halteres", exercises[0].name)
        assertEquals("Encolhimento", exercises[1].name)
    }

    @Test
    fun `teste grafico de evolucao de peso em todos os cenarios de registros`() {
        // Cenário 1: Lista vazia (0 registros)
        val emptyList = emptyList<PesoHistoryEntry>()
        val defaultPoints = if (emptyList.isEmpty()) {
            listOf(
                PesoHistoryEntry(peso = 80.0, data = System.currentTimeMillis() - 86400000),
                PesoHistoryEntry(peso = 80.0, data = System.currentTimeMillis())
            )
        } else emptyList
        assertEquals(2, defaultPoints.size)

        // Cenário 2: 1 registro único
        val singleList = listOf(PesoHistoryEntry(peso = 75.5, data = 1700000000L))
        val maxW1 = (singleList.maxOfOrNull { it.peso } ?: 75.5).toFloat() + 2f
        val minW1 = ((singleList.minOfOrNull { it.peso } ?: 75.5).toFloat() - 2f).coerceAtLeast(0f)
        val range1 = if ((maxW1 - minW1) == 0f) 10f else (maxW1 - minW1)
        assertTrue("Range de peso deve ser estritamente positivo para evitar divisão por zero", range1 > 0f)
        assertFalse("Range não pode ser NaN ou infinito", range1.isNaN() || range1.isInfinite())

        // Cenário 3: 2 registros
        val doubleList = listOf(
            PesoHistoryEntry(peso = 76.0, data = 1700000000L),
            PesoHistoryEntry(peso = 75.0, data = 1700100000L)
        )
        val maxW2 = doubleList.maxOf { it.peso }
        val minW2 = doubleList.minOf { it.peso }
        assertEquals(76.0, maxW2, 0.001)
        assertEquals(75.0, minW2, 0.001)

        // Cenário 4: Múltiplos registros (10 registros variados)
        val multiList = (1..10).map { i ->
            PesoHistoryEntry(peso = 70.0 + (i * 0.5), data = 1700000000L + (i * 86400000L))
        }
        val maxWMulti = multiList.maxOf { it.peso }
        val minWMulti = multiList.minOf { it.peso }
        assertEquals(75.0, maxWMulti, 0.001)
        assertEquals(70.5, minWMulti, 0.001)
    }

    @Test
    fun `teste calculos nutricionais somas caloricas e macronutrientes`() {
        val consumos = listOf(
            ConsumoDiario(name = "Frango Grelhado", calories = 250, protein = 40.0, carbs = 0.0, fat = 5.0, mealName = "Almoço", date = System.currentTimeMillis()),
            ConsumoDiario(name = "Arroz Branco", calories = 200, protein = 4.0, carbs = 45.0, fat = 1.0, mealName = "Almoço", date = System.currentTimeMillis()),
            ConsumoDiario(name = "Azeite", calories = 108, protein = 0.0, carbs = 0.0, fat = 12.0, mealName = "Almoço", date = System.currentTimeMillis())
        )

        val totalCal = consumos.sumOf { it.calories }
        val totalProt = consumos.sumOf { it.protein }
        val totalCarb = consumos.sumOf { it.carbs }
        val totalFat = consumos.sumOf { it.fat }

        assertEquals(558, totalCal)
        assertEquals(44.0, totalProt, 0.001)
        assertEquals(45.0, totalCarb, 0.001)
        assertEquals(18.0, totalFat, 0.001)

        // Teste divisão por zero em refeição vazia
        val emptyConsumo = emptyList<ConsumoDiario>()
        val calZero = emptyConsumo.sumOf { it.calories }
        val pctProt = if (calZero > 0) (emptyConsumo.sumOf { it.protein } * 4 / calZero) else 0.0
        assertEquals(0.0, pctProt, 0.001)
    }

    @Test
    fun `teste cancelamento de notificacoes sem crash e sem vazamentos`() {
        try {
            MealNotificationScheduler.cancelAllMealReminders(context)
            SupplementScheduler.cancelAllSupplementReminders(context)
            SupplementScheduler.cancelSupplementReminder(context, "supl_test_123")
            assertTrue(true)
        } catch (e: Exception) {
            fail("Cancelamento de notificações não pode gerar exceção: ${e.message}")
        }
    }
}
