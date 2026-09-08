package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class MealAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("MealAlarmReceiver", "Received broadcast: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Log.d("MealAlarmReceiver", "System time/timezone changed or device rebooted. Rescheduling meal reminders.")
                MealNotificationScheduler.scheduleDailyMealReminders(context)
            }
            MealNotificationScheduler.ACTION_MEAL_REMINDER -> {
                val pendingResult = goAsync()
                val mealType = intent.getStringExtra(MealReminderWorker.KEY_MEAL_TYPE) ?: MealReminderWorker.MEAL_BREAKFAST
                val mealName = intent.getStringExtra(MealReminderWorker.KEY_MEAL_NAME) ?: "Refeição"
                val title = intent.getStringExtra(MealReminderWorker.KEY_TITLE) ?: "Hora da Refeição! 🍽️"
                val message = intent.getStringExtra(MealReminderWorker.KEY_MESSAGE) ?: "Registre sua refeição no diário FitAI!"
                val notificationId = intent.getIntExtra(MealReminderWorker.KEY_NOTIFICATION_ID, 2000)
                val targetHour = intent.getIntExtra(MealNotificationScheduler.EXTRA_TARGET_HOUR, 8)
                val targetMinute = intent.getIntExtra(MealNotificationScheduler.EXTRA_TARGET_MINUTE, 0)
                val workName = intent.getStringExtra(MealNotificationScheduler.EXTRA_WORK_NAME) ?: "notification_meal"

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Check if user already logged this meal today
                        var alreadyLogged = false
                        try {
                            val db = AppDatabase.getDatabase(context)
                            val allConsumos = db.appDao().getAllConsumoDiarioDirect()
                            val todayCal = Calendar.getInstance()
                            alreadyLogged = allConsumos.any { item ->
                                val recordCal = Calendar.getInstance().apply { timeInMillis = item.date }
                                recordCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                                        recordCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR) &&
                                        item.mealName.equals(mealName, ignoreCase = true)
                            }
                        } catch (e: Exception) {
                            Log.w("MealAlarmReceiver", "Could not check meal logs in database: ${e.message}")
                        }

                        if (!alreadyLogged) {
                            NotificationHelper.sendMealReminderNotification(
                                context = context,
                                mealType = mealType,
                                title = title,
                                message = message,
                                notificationId = notificationId
                            )
                            Log.d("MealAlarmReceiver", "Sent notification for $mealName (ID: $notificationId)")
                        } else {
                            Log.d("MealAlarmReceiver", "Meal $mealName already logged today, skipping notification")
                        }

                        // Re-schedule for the next day at the exact same local hour and minute
                        MealNotificationScheduler.scheduleMealReminder(
                            context = context,
                            workName = workName,
                            mealType = mealType,
                            mealName = mealName,
                            title = title,
                            message = message,
                            targetHour = targetHour,
                            targetMinute = targetMinute,
                            notificationId = notificationId
                        )
                    } catch (e: Exception) {
                        Log.e("MealAlarmReceiver", "Error processing meal alarm", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
