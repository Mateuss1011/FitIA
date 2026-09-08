package com.example

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object SupplementScheduler {
    const val ACTION_SUPPLEMENT_REMINDER = "com.example.ACTION_SUPPLEMENT_REMINDER"
    const val EXTRA_SUPPLEMENT_ID = "suplemento_id"
    const val EXTRA_SUPPLEMENT_NAME = "suplemento_nome"
    const val EXTRA_SUPPLEMENT_DOSE = "suplemento_dose"
    const val EXTRA_SUPPLEMENT_TIME = "suplemento_horario"

    /**
     * Schedules an exact daily recurring supplement notification strictly in the device's local timezone.
     * Extracts hour and minute directly without any UTC conversions or timezone shifts.
     */
    fun scheduleSupplementReminder(
        context: Context,
        id: String,
        nome: String,
        dose: String,
        horario: String
    ) {
        try {
            val parts = horario.split(":")
            val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 9
            val minute = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0

            // Strict local device timezone calculation
            val localTz = TimeZone.getDefault()
            val now = Calendar.getInstance(localTz)
            val target = Calendar.getInstance(localTz).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If time has already passed today in local time, schedule for tomorrow
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(context, SupplementAlarmReceiver::class.java).apply {
                action = ACTION_SUPPLEMENT_REMINDER
                putExtra(EXTRA_SUPPLEMENT_ID, id)
                putExtra(EXTRA_SUPPLEMENT_NAME, nome)
                putExtra(EXTRA_SUPPLEMENT_DOSE, dose)
                putExtra(EXTRA_SUPPLEMENT_TIME, horario)
            }

            val requestCode = id.hashCode()
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

            alarmManager?.let { am ->
                try {
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
                    Log.w("SupplementScheduler", "Exact alarm permission restricted, falling back to standard alarm", e)
                    am.set(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                }
            }

            // Redundant robust WorkManager fallback
            val initialDelay = target.timeInMillis - now.timeInMillis
            val data = workDataOf(
                EXTRA_SUPPLEMENT_ID to id,
                EXTRA_SUPPLEMENT_NAME to nome,
                EXTRA_SUPPLEMENT_DOSE to dose,
                EXTRA_SUPPLEMENT_TIME to horario
            )

            val workRequest = OneTimeWorkRequestBuilder<SupplementWorker>()
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("suplemento_tag_$id")
                .addTag("fitai_supplement_reminder")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "suplemento_work_$id",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            Log.d("SupplementScheduler", "Scheduled daily supplement reminder for '$nome' at $hour:$minute local time (target: ${target.time})")
        } catch (e: Exception) {
            Log.e("SupplementScheduler", "Error scheduling supplement reminder", e)
        }
    }

    /**
     * Cancels scheduled alarms and work for the given supplement ID.
     */
    fun cancelSupplementReminder(context: Context, id: String) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(context, SupplementAlarmReceiver::class.java).apply {
                action = ACTION_SUPPLEMENT_REMINDER
            }
            val requestCode = id.hashCode()
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()

            WorkManager.getInstance(context).cancelUniqueWork("suplemento_work_$id")
            Log.d("SupplementScheduler", "Cancelled supplement reminder for ID: $id")
        } catch (e: Exception) {
            Log.e("SupplementScheduler", "Error cancelling supplement reminder", e)
        }
    }

    /**
     * Cancels all scheduled supplement reminders across all supplements.
     */
    fun cancelAllSupplementReminders(context: Context) {
        try {
            WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag("fitai_supplement_reminder")
            Log.d("SupplementScheduler", "Cancelled all supplement background work")
        } catch (e: Exception) {
            Log.e("SupplementScheduler", "Error cancelling all supplement reminders", e)
        }
    }
}
