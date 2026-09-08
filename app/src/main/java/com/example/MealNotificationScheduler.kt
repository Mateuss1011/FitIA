package com.example

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object MealNotificationScheduler {

    const val ACTION_MEAL_REMINDER = "com.example.ACTION_MEAL_REMINDER"
    const val EXTRA_TARGET_HOUR = "target_hour"
    const val EXTRA_TARGET_MINUTE = "target_minute"
    const val EXTRA_WORK_NAME = "work_name"

    // Unique Work Names and Tags for each meal
    const val WORK_NAME_BREAKFAST = "notification_breakfast"
    const val WORK_NAME_LUNCH = "notification_lunch"
    const val WORK_NAME_DINNER = "notification_dinner"

    const val TAG_BREAKFAST = "notification_breakfast"
    const val TAG_LUNCH = "notification_lunch"
    const val TAG_DINNER = "notification_dinner"

    // Legacy work names to clean up previous schedules
    private const val LEGACY_WORK_NAME_BREAKFAST = "fitai_meal_reminder_breakfast"
    private const val LEGACY_WORK_NAME_LUNCH = "fitai_meal_reminder_lunch"
    private const val LEGACY_WORK_NAME_DINNER = "fitai_meal_reminder_dinner"

    // Default daily schedule times (24-hour format)
    const val DEFAULT_BREAKFAST_HOUR = 8
    const val DEFAULT_BREAKFAST_MINUTE = 0 // 08:00

    const val DEFAULT_LUNCH_HOUR = 12
    const val DEFAULT_LUNCH_MINUTE = 30 // 12:30

    const val DEFAULT_DINNER_HOUR = 20
    const val DEFAULT_DINNER_MINUTE = 0 // 20:00

    /**
     * Schedules daily notifications using AlarmManager (exact local device timezone)
     * with WorkManager as a redundant fallback for breakfast, lunch, and dinner.
     */
    fun scheduleDailyMealReminders(context: Context) {
        try {
            // Cancel legacy work names if any were previously registered
            cancelLegacyMealReminders(context)

            // 1. Breakfast (08:00)
            scheduleMealReminder(
                context = context,
                workName = WORK_NAME_BREAKFAST,
                mealType = MealReminderWorker.MEAL_BREAKFAST,
                mealName = "Café da manhã",
                title = "Hora do Café da Manhã! ☕",
                message = "Comece o dia batendo suas metas de energia e proteínas. Registre seu café da manhã agora!",
                targetHour = DEFAULT_BREAKFAST_HOUR,
                targetMinute = DEFAULT_BREAKFAST_MINUTE,
                notificationId = 2001
            )

            // 2. Lunch (12:30)
            scheduleMealReminder(
                context = context,
                workName = WORK_NAME_LUNCH,
                mealType = MealReminderWorker.MEAL_LUNCH,
                mealName = "Almoço",
                title = "Hora do Almoço! 🍽️",
                message = "Mantenha a consistência nos seus macros. Registre seu almoço no diário FitAI!",
                targetHour = DEFAULT_LUNCH_HOUR,
                targetMinute = DEFAULT_LUNCH_MINUTE,
                notificationId = 2002
            )

            // 3. Dinner (20:00)
            scheduleMealReminder(
                context = context,
                workName = WORK_NAME_DINNER,
                mealType = MealReminderWorker.MEAL_DINNER,
                mealName = "Jantar",
                title = "Hora do Jantar! 🥗",
                message = "Feche o dia com chave de ouro atingindo sua meta diária. Registre seu jantar!",
                targetHour = DEFAULT_DINNER_HOUR,
                targetMinute = DEFAULT_DINNER_MINUTE,
                notificationId = 2003
            )

            Log.d("MealNotificationScheduler", "All daily meal reminders successfully scheduled in local timezone")
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Failed to schedule daily meal reminders: ${e.message}", e)
        }
    }

    /**
     * Schedules a meal reminder at the exact targetHour:targetMinute in the device's local timezone.
     * Cancels any previously set alarms for this meal to avoid duplication.
     */
    fun scheduleMealReminder(
        context: Context,
        workName: String,
        mealType: String,
        mealName: String,
        title: String,
        message: String,
        targetHour: Int,
        targetMinute: Int,
        notificationId: Int
    ) {
        try {
            val localTz = TimeZone.getDefault()
            val now = Calendar.getInstance(localTz)
            val target = Calendar.getInstance(localTz).apply {
                timeInMillis = now.timeInMillis
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, targetMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If target time has already passed today, schedule for tomorrow
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            // 1. AlarmManager exact scheduling
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(context, MealAlarmReceiver::class.java).apply {
                action = ACTION_MEAL_REMINDER
                putExtra(MealReminderWorker.KEY_MEAL_TYPE, mealType)
                putExtra(MealReminderWorker.KEY_MEAL_NAME, mealName)
                putExtra(MealReminderWorker.KEY_TITLE, title)
                putExtra(MealReminderWorker.KEY_MESSAGE, message)
                putExtra(MealReminderWorker.KEY_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_TARGET_HOUR, targetHour)
                putExtra(EXTRA_TARGET_MINUTE, targetMinute)
                putExtra(EXTRA_WORK_NAME, workName)
            }

            val requestCode = notificationId
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

            alarmManager?.let { am ->
                try {
                    // Cancel existing alarm first to prevent duplicates
                    am.cancel(pendingIntent)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (am.canScheduleExactAlarms()) {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                        } else {
                            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                    } else {
                        am.setExact(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                    }
                } catch (e: SecurityException) {
                    Log.w("MealNotificationScheduler", "Exact alarm restricted, falling back to standard alarm", e)
                    am.set(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                }
            }

            // 2. Redundant WorkManager fallback
            val initialDelay = target.timeInMillis - now.timeInMillis
            val inputData = Data.Builder()
                .putString(MealReminderWorker.KEY_MEAL_TYPE, mealType)
                .putString(MealReminderWorker.KEY_MEAL_NAME, mealName)
                .putString(MealReminderWorker.KEY_TITLE, title)
                .putString(MealReminderWorker.KEY_MESSAGE, message)
                .putInt(MealReminderWorker.KEY_NOTIFICATION_ID, notificationId)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<MealReminderWorker>()
                .setInputData(inputData)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag("fitai_meal_reminder")
                .addTag(workName)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            Log.d(
                "MealNotificationScheduler",
                "Scheduled $workName for %02d:%02d local time (target: %s)".format(
                    targetHour,
                    targetMinute,
                    target.time.toString()
                )
            )
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Error scheduling meal reminder for $workName: ${e.message}", e)
        }
    }

    /**
     * Cancels any previously scheduled legacy periodic work names.
     */
    private fun cancelLegacyMealReminders(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.cancelUniqueWork(LEGACY_WORK_NAME_BREAKFAST)
            workManager.cancelUniqueWork(LEGACY_WORK_NAME_LUNCH)
            workManager.cancelUniqueWork(LEGACY_WORK_NAME_DINNER)
        } catch (e: Throwable) {
            Log.w("MealNotificationScheduler", "Could not cancel legacy meal reminders: ${e.message}")
        }
    }

    /**
     * Cancels all scheduled meal reminder alarms and background work tasks.
     */
    fun cancelAllMealReminders(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            listOf(2001, 2002, 2003).forEach { reqCode ->
                val intent = Intent(context, MealAlarmReceiver::class.java).apply {
                    action = ACTION_MEAL_REMINDER
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                val pendingIntent = PendingIntent.getBroadcast(context, reqCode, intent, flags)
                alarmManager?.cancel(pendingIntent)
                pendingIntent.cancel()
            }

            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.cancelUniqueWork(WORK_NAME_BREAKFAST)
            workManager.cancelUniqueWork(WORK_NAME_LUNCH)
            workManager.cancelUniqueWork(WORK_NAME_DINNER)
            workManager.cancelUniqueWork(LEGACY_WORK_NAME_BREAKFAST)
            workManager.cancelUniqueWork(LEGACY_WORK_NAME_LUNCH)
            workManager.cancelUniqueWork(LEGACY_WORK_NAME_DINNER)
            workManager.cancelAllWorkByTag("fitai_meal_reminder")
            Log.d("MealNotificationScheduler", "Cancelled all daily meal reminders")
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Error cancelling meal reminders: ${e.message}", e)
        }
    }
}
