package com.example

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Tasks
import java.util.Calendar

class HydrationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        try {
            val auth = FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid

            if (uid == null) {
                Log.d("HydrationWorker", "No user logged in, skipping check")
                return Result.success()
            }

            val firestore = FirebaseFirestore.getInstance()

            // Fetch User Profile from /usuarios/{uid}/biometria/perfil
            val profileTask = firestore.collection("usuarios").document(uid)
                .collection("biometria").document("perfil")
                .get()
            val profileDoc = Tasks.await(profileTask)

            val name = profileDoc.getString("name") ?: "Mateus"
            val weight = profileDoc.getDouble("weight") ?: 80.0
            val targetWaterMl = (weight * 35).toInt()

            // Fetch today's water consumption from /usuarios/{uid}/registro_agua
            val waterTask = firestore.collection("usuarios").document(uid)
                .collection("registro_agua")
                .get()
            val waterDocs = Tasks.await(waterTask)

            var totalWaterToday = 0
            for (doc in waterDocs.documents) {
                val amount = doc.getLong("amountMl")?.toInt() ?: 0
                val date = doc.getLong("date") ?: 0L
                if (isToday(date)) {
                    totalWaterToday += amount
                }
            }

            Log.d("HydrationWorker", "Checked hydration: Today=$totalWaterToday, Goal=$targetWaterMl")

            val fiftyPercentGoal = targetWaterMl * 0.5
            if (totalWaterToday < fiftyPercentGoal) {
                // Send personalized notification
                val title = "Cadê a sua água? 💧"
                val message = "$name, você bebeu menos da metade da sua meta de água hoje. Hidrate-se agora para garantir a hipertrofia!"
                NotificationHelper.sendNotification(context, title, message)
                Log.d("HydrationWorker", "Sent warning hydration notification")
            } else {
                Log.d("HydrationWorker", "Water target met (>50%), no notification sent")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e("HydrationWorker", "Error running hydration worker", e)
            return Result.failure()
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
