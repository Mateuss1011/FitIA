package com.example

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.db.AppDatabase
import java.util.Calendar

/**
 * WorkManager Worker responsible for triggering scheduled daily reminders
 * prompting the user to log breakfast, lunch, and dinner.
 */
class MealReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_MEAL_TYPE = "meal_type"
        const val KEY_MEAL_NAME = "meal_name"
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_NOTIFICATION_ID = "notification_id"

        const val MEAL_BREAKFAST = "BREAKFAST"
        const val MEAL_LUNCH = "LUNCH"
        const val MEAL_DINNER = "DINNER"
    }

    override suspend fun doWork(): Result {
        return try {
            val mealType = inputData.getString(KEY_MEAL_TYPE) ?: MEAL_BREAKFAST
            val mealName = inputData.getString(KEY_MEAL_NAME) ?: when (mealType) {
                MEAL_BREAKFAST -> "Café da manhã"
                MEAL_LUNCH -> "Almoço"
                MEAL_DINNER -> "Jantar"
                else -> "Refeição"
            }
            val title = inputData.getString(KEY_TITLE) ?: when (mealType) {
                MEAL_BREAKFAST -> "Hora do Café da Manhã! ☕"
                MEAL_LUNCH -> "Hora do Almoço! 🍽️"
                MEAL_DINNER -> "Hora do Jantar! 🥗"
                else -> "Hora da Refeição! 🍎"
            }
            val defaultMessage = when (mealType) {
                MEAL_BREAKFAST -> "Comece o dia batendo suas metas de energia e proteínas. Registre seu café da manhã agora!"
                MEAL_LUNCH -> "Mantenha a consistência nos seus macros. Registre seu almoço no diário FitAI!"
                MEAL_DINNER -> "Feche o dia com chave de ouro atingindo sua meta diária. Registre seu jantar!"
                else -> "Registre sua refeição no diário FitAI para manter seus macros sob controle."
            }
            val message = inputData.getString(KEY_MESSAGE) ?: defaultMessage
            val notificationId = inputData.getInt(
                KEY_NOTIFICATION_ID,
                when (mealType) {
                    MEAL_BREAKFAST -> 2001
                    MEAL_LUNCH -> 2002
                    MEAL_DINNER -> 2003
                    else -> 2000
                }
            )

            // Verify if user has already logged this meal today
            try {
                val db = AppDatabase.getDatabase(context)
                val allConsumos = db.appDao().getAllConsumoDiarioDirect()
                val todayLogged = allConsumos.any { item ->
                    isToday(item.date) && item.mealName.equals(mealName, ignoreCase = true)
                }
                if (todayLogged) {
                    Log.d("MealReminderWorker", "Meal $mealName is already logged for today. Skipping reminder.")
                    return Result.success()
                }
            } catch (e: Exception) {
                Log.w("MealReminderWorker", "Could not check database for logged meals: ${e.message}")
            }

            // Trigger notification
            NotificationHelper.sendMealReminderNotification(
                context = context,
                mealType = mealType,
                title = title,
                message = message,
                notificationId = notificationId
            )
            Log.d("MealReminderWorker", "Sent daily reminder for $mealName (ID: $notificationId)")
            Result.success()
        } catch (e: Exception) {
            Log.e("MealReminderWorker", "Error executing meal reminder worker", e)
            Result.failure()
        }
    }

    private fun isToday(timestamp: Long): Boolean {
        if (timestamp == 0L) return false
        val recordCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val todayCal = Calendar.getInstance()
        return recordCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                recordCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    }
}
