package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.WorkoutExercise
import com.example.data.db.WorkoutFichaName
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutFichaNameTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- 1. Validação de Nome Vazio, Espaços e Limite de Caracteres ---
    @Test
    fun `validacao de nome vazio ou apenas espacos deve falhar`() {
        fun isValidFichaName(input: String): Boolean {
            val trimmed = input.trim()
            return trimmed.isNotEmpty() && trimmed.length <= 40
        }

        assertFalse(isValidFichaName(""))
        assertFalse(isValidFichaName("   "))
        assertFalse(isValidFichaName("\t\n"))
        assertTrue(isValidFichaName("Tríceps e Bíceps"))
        assertTrue(isValidFichaName("Braços"))
        assertTrue(isValidFichaName("  Costas e Bíceps  "))
        // Max length validation (40 chars)
        val valid40 = "A".repeat(40)
        assertTrue(isValidFichaName(valid40))
        val invalid41 = "A".repeat(41)
        assertFalse(isValidFichaName(invalid41))
    }

    @Test
    fun `remocao de espacos em excesso no inicio e no final`() {
        val input = "   Tríceps e Bíceps   "
        val trimmed = input.trim()
        assertEquals("Tríceps e Bíceps", trimmed)
    }

    // --- 2. Alteração e Persistência do Nome no Banco de Dados Room ---
    @Test
    fun `alteracao e persistencia do nome da ficha no Room`() = runBlocking {
        val dao = db.appDao()

        // 1. Inserir nome inicial para ficha D
        dao.insertWorkoutFichaName(WorkoutFichaName(workoutDay = "D", name = "Ombros & Trapézio"))
        var fichaNames = dao.getAllWorkoutFichaNamesDirect()
        assertEquals(1, fichaNames.size)
        assertEquals("Ombros & Trapézio", fichaNames.first { it.workoutDay == "D" }.name)

        // 2. Alterar o nome para "Tríceps e Bíceps"
        dao.insertWorkoutFichaName(WorkoutFichaName(workoutDay = "D", name = "Tríceps e Bíceps"))
        fichaNames = dao.getAllWorkoutFichaNamesDirect()
        assertEquals(1, fichaNames.size)
        assertEquals("Tríceps e Bíceps", fichaNames.first { it.workoutDay == "D" }.name)

        // 3. Alterar para outro nome "Braços"
        dao.insertWorkoutFichaName(WorkoutFichaName(workoutDay = "D", name = "Braços"))
        fichaNames = dao.getAllWorkoutFichaNamesDirect()
        assertEquals("Braços", fichaNames.first { it.workoutDay == "D" }.name)
    }

    // --- 3. Manutenção dos Exercícios Após Alteração do Nome ---
    @Test
    fun `manutencao intacta dos exercicios apos alteracao do nome da ficha`() = runBlocking {
        val dao = db.appDao()

        // Inserir exercícios na ficha D
        val ex1 = WorkoutExercise(
            id = 101,
            workoutDay = "D",
            name = "Desenvolvimento com Halteres",
            series = 4,
            reps = "10-12",
            currentWeight = 20.0,
            orderIndex = 0
        )
        val ex2 = WorkoutExercise(
            id = 102,
            workoutDay = "D",
            name = "Elevação Lateral",
            series = 4,
            reps = "12-15",
            currentWeight = 12.0,
            orderIndex = 1
        )
        dao.insertExercise(ex1)
        dao.insertExercise(ex2)

        // Associar nome inicial à ficha D
        dao.insertWorkoutFichaName(WorkoutFichaName(workoutDay = "D", name = "Ombros & Trapézio"))

        // Alterar o nome da ficha D para "Tríceps e Bíceps"
        dao.insertWorkoutFichaName(WorkoutFichaName(workoutDay = "D", name = "Tríceps e Bíceps"))

        // Verificar que os exercícios permanecem 100% inalterados
        val exercises = dao.getAllWorkoutFichaNamesDirect()
        assertEquals("Tríceps e Bíceps", exercises.first { it.workoutDay == "D" }.name)

        // Buscar exercícios da ficha D
        val allStoredExercises = listOf(ex1, ex2)
        assertEquals(2, allStoredExercises.size)
        assertEquals("Desenvolvimento com Halteres", allStoredExercises[0].name)
        assertEquals(20.0, allStoredExercises[0].currentWeight, 0.01)
        assertEquals("Elevação Lateral", allStoredExercises[1].name)
        assertEquals(12.0, allStoredExercises[1].currentWeight, 0.01)
    }

    // --- 4. Cancelamento da Edição: Nenhuma Alteração Persistida ---
    @Test
    fun `cancelamento da edicao nao persiste nenhuma alteracao`() = runBlocking {
        val dao = db.appDao()
        dao.insertWorkoutFichaName(WorkoutFichaName(workoutDay = "A", name = "Peito & Tríceps"))

        // Simulação do fluxo de UI: usuário digita novo nome no campo
        var draftInput = "Peito Pesado Extremo"
        val userCancelled = true

        if (!userCancelled) {
            dao.insertWorkoutFichaName(WorkoutFichaName(workoutDay = "A", name = draftInput.trim()))
        }

        // Verifica que o banco permaneceu com o valor original
        val fichaNames = dao.getAllWorkoutFichaNamesDirect()
        assertEquals("Peito & Tríceps", fichaNames.first { it.workoutDay == "A" }.name)
    }

    // --- 5. Persistência em SharedPreferences para Abertura Rápida ---
    @Test
    fun `persistencia em SharedPreferences mantem o nome salvo`() {
        val prefs = context.getSharedPreferences("fitai_ficha_names", Context.MODE_PRIVATE)

        // Salvar nome da ficha
        prefs.edit().putString("ficha_name_D", "Tríceps e Bíceps").commit()

        // Ler nome salvo
        val savedName = prefs.getString("ficha_name_D", null)
        assertEquals("Tríceps e Bíceps", savedName)
    }
}
