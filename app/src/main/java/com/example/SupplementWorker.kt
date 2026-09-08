package com.example

import android.content.Context
import androidx.work.*

class SupplementWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val id = inputData.getString(SupplementScheduler.EXTRA_SUPPLEMENT_ID) ?: ""
        val nome = inputData.getString(SupplementScheduler.EXTRA_SUPPLEMENT_NAME) ?: "Suplemento"
        val dose = inputData.getString(SupplementScheduler.EXTRA_SUPPLEMENT_DOSE) ?: ""
        val horario = inputData.getString(SupplementScheduler.EXTRA_SUPPLEMENT_TIME) ?: "09:00"

        val title = "FitAI - Lembrete de Suplemento 💊"
        val message = if (dose.isNotEmpty()) {
            "Está na hora de tomar seu suplemento: $nome ($dose)!"
        } else {
            "Está na hora de tomar seu suplemento: $nome!"
        }

        val notifId = if (id.isNotEmpty()) id.hashCode() else 1001
        NotificationHelper.sendSupplementNotification(
            context = context,
            title = title,
            message = message,
            notificationId = notifId
        )

        // Reschedule for next day using exact local time scheduler
        if (id.isNotEmpty()) {
            SupplementScheduler.scheduleSupplementReminder(
                context = context,
                id = id,
                nome = nome,
                dose = dose,
                horario = horario
            )
        }

        return Result.success()
    }

    companion object {
        fun scheduleSupplementWork(context: Context, id: String, nome: String, dose: String, horario: String) {
            SupplementScheduler.scheduleSupplementReminder(context, id, nome, dose, horario)
        }

        fun cancelSupplementWork(context: Context, id: String) {
            SupplementScheduler.cancelSupplementReminder(context, id)
        }
    }
}
