package com.example.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Entities ---

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val age: Int? = null,
    val weight: Double? = null,
    val height: Double? = null,
    val fitnessLevel: String = "Iniciante", // e.g. "Avançado", "Tempo de Academia"
    val restrictions: String = "Nenhuma", // e.g. "Nenhuma", "Lesão no ombro"
    val objective: String = "Hipertrofia",    // e.g. "Hipertrofia", "Emagrecimento"
    val trainingDays: String = "Seg,Qua,Sex", // Comma-separated active days (e.g. "Seg,Qua,Sex")
    val notificationMessage: String = "Hora de construir fibras! Seu treino de hoje está pronto.",
    val gender: String = "Masculino",
    val avatarPreset: String = "avatar_1",
    val onboardingCompleted: Boolean = false,
    val hasSeenTour: Boolean = false
) {
    val ageSafe: Int get() = age ?: 0
    val weightSafe: Double get() = weight ?: 0.0
    val heightSafe: Double get() = height ?: 0.0
}

@Entity(tableName = "workout_exercises")
data class WorkoutExercise(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val workoutDay: String, // e.g. "A", "B", "C" or custom name
    val name: String,
    val series: Int,
    val reps: String, // e.g. "8-10" or "12"
    val currentWeight: Double, // in kg
    val completed: Boolean = false,
    val completedDate: String? = null, // e.g. "2026-09-08" session date
    val badge: String? = null, // e.g. "+12% ↗", "EXPLOSIVO"
    val instructions: String? = null,
    val orderIndex: Int = 0
) {
    fun isCompletedOn(dateStr: String): Boolean = completed && completedDate == dateStr
}

@Entity(tableName = "meals")
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String, // e.g. "Café da Manhã"
    val description: String,
    val calories: Int,
    val protein: String = "",
    val carbs: String = "",
    val fat: String = ""
)

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val exerciseName: String,
    val weight: Double,
    val reps: String = "",
    val date: Long = System.currentTimeMillis()
)

data class PesoHistoryEntry(
    val id: String = "",
    val peso: Double,
    val data: Long = System.currentTimeMillis()
)

@Entity(tableName = "consumo_diario")
data class ConsumoDiario(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val mealName: String = "Geral",
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "registro_agua")
data class RegistroAgua(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amountMl: Int,
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "workout_ficha_names")
data class WorkoutFichaName(
    @PrimaryKey val workoutDay: String,
    val name: String
)

data class Suplemento(
    val id: String = "",
    val nome: String = "",
    val dose: String = "",
    val horario: String = "09:00",
    val ativo: Boolean = true
)

data class RegistroDiario(
    val dateStr: String = "",
    val aguaMl: Int = 0,
    val caloriasConsumidas: Int = 0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val suplementosTomados: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

// --- DAO ---

@Dao
interface AppDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile): Long

    @Query("SELECT * FROM workout_exercises ORDER BY orderIndex ASC, id ASC")
    fun getAllExercisesFlow(): Flow<List<WorkoutExercise>>

    @Query("SELECT * FROM workout_exercises WHERE workoutDay = :day ORDER BY orderIndex ASC, id ASC")
    fun getExercisesForDayFlow(day: String): Flow<List<WorkoutExercise>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<WorkoutExercise>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: WorkoutExercise)

    @Update
    suspend fun updateExercise(exercise: WorkoutExercise)

    @Delete
    suspend fun deleteExercise(exercise: WorkoutExercise)

    @Query("DELETE FROM workout_exercises WHERE workoutDay = :day")
    suspend fun deleteExercisesForDay(day: String)

    @Query("DELETE FROM workout_exercises")
    suspend fun clearAllExercises()

    @Query("SELECT * FROM meals")
    fun getAllMealsFlow(): Flow<List<Meal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeals(meals: List<Meal>)

    @Update
    suspend fun updateMeal(meal: Meal)

    @Query("DELETE FROM meals")
    suspend fun clearAllMeals()

    @Query("DELETE FROM user_profile")
    suspend fun clearUserProfile()

    @Query("DELETE FROM history")
    suspend fun clearAllHistory()

    @Query("DELETE FROM consumo_diario")
    suspend fun clearAllConsumoDiario()

    @Query("DELETE FROM registro_agua")
    suspend fun clearAllRegistroAgua()

    @Query("SELECT * FROM history ORDER BY date DESC")
    fun getHistoryFlow(): Flow<List<HistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEntry(entry: HistoryEntry)

    @Query("SELECT * FROM consumo_diario ORDER BY date DESC")
    fun getAllConsumoDiarioFlow(): Flow<List<ConsumoDiario>>

    @Query("SELECT * FROM consumo_diario ORDER BY date DESC")
    suspend fun getAllConsumoDiarioDirect(): List<ConsumoDiario>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsumoDiario(consumo: ConsumoDiario)

    @Query("DELETE FROM consumo_diario WHERE id = :id")
    suspend fun deleteConsumoDiario(id: Int)

    @Query("SELECT * FROM registro_agua ORDER BY date DESC")
    fun getAllRegistroAguaFlow(): Flow<List<RegistroAgua>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegistroAgua(agua: RegistroAgua)

    @Query("SELECT * FROM workout_ficha_names")
    fun getAllWorkoutFichaNamesFlow(): Flow<List<WorkoutFichaName>>

    @Query("SELECT * FROM workout_ficha_names")
    suspend fun getAllWorkoutFichaNamesDirect(): List<WorkoutFichaName>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutFichaName(fichaName: WorkoutFichaName)

    @Query("DELETE FROM workout_ficha_names WHERE workoutDay = :day")
    suspend fun deleteWorkoutFichaName(day: String)

    @Query("DELETE FROM workout_ficha_names")
    suspend fun clearAllWorkoutFichaNames()
}

// --- Database Migrations ---
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
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
    }
}

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `registro_agua` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `amountMl` INTEGER NOT NULL,
                `date` INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `avatarPreset` TEXT NOT NULL DEFAULT 'avatar_1'")
        } catch (ignored: Exception) {}
        try {
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `onboardingCompleted` INTEGER NOT NULL DEFAULT 0")
        } catch (ignored: Exception) {}
        try {
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `hasSeenTour` INTEGER NOT NULL DEFAULT 0")
        } catch (ignored: Exception) {}
        try {
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `notificationMessage` TEXT NOT NULL DEFAULT 'Hora de construir fibras! Seu treino de hoje está pronto.'")
        } catch (ignored: Exception) {}
    }
}

val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `workout_ficha_names` (`workoutDay` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`workoutDay`))")
    }
}

val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE `workout_exercises` ADD COLUMN `completedDate` TEXT DEFAULT NULL")
        } catch (ignored: Exception) {}
    }
}

val MIGRATION_1_6 = object : androidx.room.migration.Migration(1, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_1_2.migrate(db)
        MIGRATION_2_3.migrate(db)
        MIGRATION_3_4.migrate(db)
        MIGRATION_4_5.migrate(db)
        MIGRATION_5_6.migrate(db)
    }
}

val MIGRATION_2_6 = object : androidx.room.migration.Migration(2, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_2_3.migrate(db)
        MIGRATION_3_4.migrate(db)
        MIGRATION_4_5.migrate(db)
        MIGRATION_5_6.migrate(db)
    }
}

val MIGRATION_3_6 = object : androidx.room.migration.Migration(3, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_3_4.migrate(db)
        MIGRATION_4_5.migrate(db)
        MIGRATION_5_6.migrate(db)
    }
}

val MIGRATION_4_6 = object : androidx.room.migration.Migration(4, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_4_5.migrate(db)
        MIGRATION_5_6.migrate(db)
    }
}

val MIGRATION_1_5 = object : androidx.room.migration.Migration(1, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_1_2.migrate(db)
        MIGRATION_2_3.migrate(db)
        MIGRATION_3_4.migrate(db)
        MIGRATION_4_5.migrate(db)
    }
}

val MIGRATION_2_5 = object : androidx.room.migration.Migration(2, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_2_3.migrate(db)
        MIGRATION_3_4.migrate(db)
        MIGRATION_4_5.migrate(db)
    }
}

val MIGRATION_3_5 = object : androidx.room.migration.Migration(3, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_3_4.migrate(db)
        MIGRATION_4_5.migrate(db)
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_1_5,
    MIGRATION_2_5,
    MIGRATION_3_5,
    MIGRATION_1_6,
    MIGRATION_2_6,
    MIGRATION_3_6,
    MIGRATION_4_6
)

// --- Database Class ---

@Database(
    entities = [UserProfile::class, WorkoutExercise::class, Meal::class, HistoryEntry::class, ConsumoDiario::class, RegistroAgua::class, WorkoutFichaName::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: try {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "fitai_database"
                    )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    INSTANCE = instance
                    instance
                } catch (e: Throwable) {
                    android.util.Log.e("FitAI_Error", "Failed to build standard Room database. Creating fallback in-memory database: ${e.message}", e)
                    val fallback = Room.inMemoryDatabaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java
                    )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    INSTANCE = fallback
                    fallback
                }
            }
        }
    }
}
