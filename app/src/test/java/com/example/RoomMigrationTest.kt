package com.example

import android.content.ContentValues
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomMigrationTest {

    private lateinit var context: Context
    private val dbName = "migration_test.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `migration de v4 para v5 cria tabela workout_ficha_names e preserva todos os dados existentes`() {
        // 1. Criar banco manual no schema v4 via SupportSQLiteOpenHelper
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Tabelas v4
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `user_profile` (
                            `id` INTEGER PRIMARY KEY NOT NULL,
                            `uid` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `email` TEXT NOT NULL,
                            `age` INTEGER,
                            `weight` REAL,
                            `height` REAL,
                            `fitnessLevel` TEXT NOT NULL,
                            `restrictions` TEXT NOT NULL,
                            `objective` TEXT NOT NULL,
                            `trainingDays` TEXT NOT NULL,
                            `notificationMessage` TEXT NOT NULL DEFAULT 'Hora de construir fibras! Seu treino de hoje está pronto.',
                            `gender` TEXT NOT NULL,
                            `avatarPreset` TEXT NOT NULL DEFAULT 'avatar_1',
                            `onboardingCompleted` INTEGER NOT NULL DEFAULT 0,
                            `hasSeenTour` INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `workout_exercises` (
                            `id` INTEGER PRIMARY KEY NOT NULL,
                            `workoutDay` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `series` INTEGER NOT NULL,
                            `reps` TEXT NOT NULL,
                            `currentWeight` REAL NOT NULL,
                            `completed` INTEGER NOT NULL,
                            `badge` TEXT,
                            `instructions` TEXT,
                            `orderIndex` INTEGER NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `meals` (
                            `id` INTEGER PRIMARY KEY NOT NULL,
                            `name` TEXT NOT NULL,
                            `description` TEXT NOT NULL,
                            `calories` INTEGER NOT NULL,
                            `protein` TEXT NOT NULL,
                            `carbs` TEXT NOT NULL,
                            `fat` TEXT NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `history` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `exerciseName` TEXT NOT NULL,
                            `weight` REAL NOT NULL,
                            `reps` TEXT NOT NULL,
                            `date` INTEGER NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `consumo_diario` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `calories` INTEGER NOT NULL,
                            `protein` REAL NOT NULL,
                            `carbs` REAL NOT NULL,
                            `fat` REAL NOT NULL,
                            `mealName` TEXT NOT NULL,
                            `date` INTEGER NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `registro_agua` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `amountMl` INTEGER NOT NULL,
                            `date` INTEGER NOT NULL
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val v4Db = helper.writableDatabase

        // 2. Inserir dados do usuário em v4
        val profileValues = ContentValues().apply {
            put("id", 1)
            put("uid", "user_123_test")
            put("name", "Carlos Silva")
            put("email", "carlos@test.com")
            put("age", 28)
            put("weight", 78.5)
            put("height", 178.0)
            put("gender", "Masculino")
            put("fitnessLevel", "Intermediário")
            put("restrictions", "Nenhuma")
            put("objective", "Hipertrofia")
            put("trainingDays", "Seg,Qua,Sex")
            put("notificationMessage", "Hora do treino!")
            put("avatarPreset", "avatar_3")
            put("onboardingCompleted", 1)
            put("hasSeenTour", 1)
        }
        v4Db.insert("user_profile", 0, profileValues)

        val exerciseValues = ContentValues().apply {
            put("id", 101)
            put("workoutDay", "A")
            put("name", "Supino Reto")
            put("series", 4)
            put("reps", "10")
            put("currentWeight", 80.0)
            put("completed", 1)
            put("orderIndex", 0)
        }
        v4Db.insert("workout_exercises", 0, exerciseValues)

        val mealValues = ContentValues().apply {
            put("id", 201)
            put("name", "Almoço")
            put("description", "Frango e Batata Doce")
            put("calories", 550)
            put("protein", "45g")
            put("carbs", "50g")
            put("fat", "10g")
        }
        v4Db.insert("meals", 0, mealValues)

        val waterValues = ContentValues().apply {
            put("amountMl", 500)
            put("date", 1700000000L)
        }
        v4Db.insert("registro_agua", 0, waterValues)

        // Fechar helper v4 antes da migração
        helper.close()

        // 3. Abrir o banco com Room na versão 6 aplicando ALL_MIGRATIONS (SEM fallback destrutivo)
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        val dao = roomDb.appDao()

        // 4. Validar que os dados existentes de v4 foram 100% preservados
        runBlocking {
            val loadedProfile = dao.getUserProfile().first()
            assertNotNull("Perfil deve existir após migração", loadedProfile)
            assertEquals("user_123_test", loadedProfile?.uid)
            assertEquals("Carlos Silva", loadedProfile?.name)
            assertEquals("carlos@test.com", loadedProfile?.email)
            assertEquals(78.5, loadedProfile?.weight ?: 0.0, 0.001)

            val exercises = dao.getAllExercisesFlow().first()
            assertEquals(1, exercises.size)
            assertEquals("Supino Reto", exercises[0].name)
            assertEquals(80.0, exercises[0].currentWeight, 0.001)
            assertNull("completedDate deve ser null para registros pre-v6", exercises[0].completedDate)

            val meals = dao.getAllMealsFlow().first()
            assertEquals(1, meals.size)
            assertEquals("Almoço", meals[0].name)
            assertEquals(550, meals[0].calories)

            val waterLogs = dao.getAllRegistroAguaFlow().first()
            assertEquals(1, waterLogs.size)
            assertEquals(500, waterLogs[0].amountMl)

            // 5. Validar que a nova tabela workout_ficha_names existe e funciona normalmente
            dao.insertWorkoutFichaName(WorkoutFichaName(workoutDay = "A", name = "Peito e Tríceps"))
            dao.insertWorkoutFichaName(WorkoutFichaName(workoutDay = "B", name = "Costas e Bíceps"))

            val fichas = dao.getAllWorkoutFichaNamesDirect()
            assertEquals(2, fichas.size)
            assertEquals("Peito e Tríceps", fichas.firstOrNull { it.workoutDay == "A" }?.name)
            assertEquals("Costas e Bíceps", fichas.firstOrNull { it.workoutDay == "B" }?.name)

            // 6. Validar que a nova coluna completedDate pode ser atualizada e salva na versao 6
            dao.updateExercise(exercises[0].copy(completedDate = "2026-09-08"))
            val updatedExercises = dao.getAllExercisesFlow().first()
            assertEquals("2026-09-08", updatedExercises[0].completedDate)
        }

        roomDb.close()
    }

    @Test
    fun `migrations step-by-step de v1 ate v6 executam sem erro e sao idempotentes`() {
        // Criar helper manual v1
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("step_by_step.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `user_profile` (
                            `id` INTEGER PRIMARY KEY NOT NULL,
                            `uid` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `email` TEXT NOT NULL,
                            `gender` TEXT NOT NULL
                        )
                    """.trimIndent())
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Executar migrations sequenciais
        MIGRATION_1_2.migrate(db)
        MIGRATION_2_3.migrate(db)
        MIGRATION_3_4.migrate(db)
        MIGRATION_4_5.migrate(db)
        MIGRATION_5_6.migrate(db)

        // Idempotência: rodar novamente não deve lançar exceção
        MIGRATION_1_2.migrate(db)
        MIGRATION_2_3.migrate(db)
        MIGRATION_3_4.migrate(db)
        MIGRATION_4_5.migrate(db)
        MIGRATION_5_6.migrate(db)

        // Inserir registro na tabela v5
        val cv = ContentValues().apply {
            put("workoutDay", "C")
            put("name", "Pernas Completo")
        }
        val insertResult = db.insert("workout_ficha_names", 0, cv)
        assertTrue("Inserção na nova tabela migrada deve ser bem sucedida", insertResult > -1)

        helper.close()
        context.deleteDatabase("step_by_step.db")
    }

    @Test
    fun `migration isolada de v5 para v6 adiciona completedDate com sucesso`() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration_5_6_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `workout_exercises` (
                            `id` INTEGER PRIMARY KEY NOT NULL,
                            `workoutDay` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `series` INTEGER NOT NULL,
                            `reps` TEXT NOT NULL,
                            `currentWeight` REAL NOT NULL,
                            `completed` INTEGER NOT NULL,
                            `badge` TEXT,
                            `instructions` TEXT,
                            `orderIndex` INTEGER NOT NULL
                        )
                    """.trimIndent())
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val v5Db = helper.writableDatabase

        val cv = ContentValues().apply {
            put("id", 10)
            put("workoutDay", "C")
            put("name", "Leg Press 45")
            put("series", 4)
            put("reps", "12")
            put("currentWeight", 160.0)
            put("completed", 1)
            put("orderIndex", 1)
        }
        v5Db.insert("workout_exercises", 0, cv)

        // Executar MIGRATION_5_6
        MIGRATION_5_6.migrate(v5Db)

        val cursor = v5Db.query("SELECT id, name, completedDate FROM workout_exercises WHERE id = 10")
        assertTrue(cursor.moveToFirst())
        assertEquals(10, cursor.getInt(0))
        assertEquals("Leg Press 45", cursor.getString(1))
        assertNull(cursor.getString(2)) // completedDate is null initially
        cursor.close()

        helper.close()
        context.deleteDatabase("migration_5_6_test.db")
    }
}
