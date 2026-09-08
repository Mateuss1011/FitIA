package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SupplementAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("SupplementAlarmReceiver", "Received broadcast: $action")

        if (action == SupplementScheduler.ACTION_SUPPLEMENT_REMINDER) {
            val id = intent.getStringExtra(SupplementScheduler.EXTRA_SUPPLEMENT_ID) ?: ""
            val nome = intent.getStringExtra(SupplementScheduler.EXTRA_SUPPLEMENT_NAME) ?: "Suplemento"
            val dose = intent.getStringExtra(SupplementScheduler.EXTRA_SUPPLEMENT_DOSE) ?: ""
            val horario = intent.getStringExtra(SupplementScheduler.EXTRA_SUPPLEMENT_TIME) ?: "09:00"

            val title = "FitAI - Lembrete de Suplemento 💊"
            val message = if (dose.isNotBlank()) {
                "Está na hora de tomar seu suplemento: $nome ($dose)!"
            } else {
                "Está na hora de tomar seu suplemento: $nome!"
            }

            val notificationId = if (id.isNotEmpty()) id.hashCode() else 1001
            NotificationHelper.sendSupplementNotification(
                context = context,
                title = title,
                message = message,
                notificationId = notificationId
            )

            // Re-schedule for the next day at the exact same local hour/minute (repeats: true)
            if (id.isNotEmpty()) {
                SupplementScheduler.scheduleSupplementReminder(
                    context = context,
                    id = id,
                    nome = nome,
                    dose = dose,
                    horario = horario
                )
            }
        }
    }
}
