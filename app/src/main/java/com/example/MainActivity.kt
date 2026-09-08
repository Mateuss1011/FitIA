package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.repository.AppThemeMode
import com.example.ui.FitApp
import com.example.ui.FitViewModel
import com.example.ui.theme.MyApplicationTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val viewModel: FitViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Error enabling edge to edge: ${e.message}", e)
        }

        // Create the notification channel defensively
        try {
            NotificationHelper.createNotificationChannel(this)
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Error creating NotificationChannel in MainActivity: ${e.message}", e)
        }

        // Schedule periodic notifications defensively
        try {
            schedulePeriodicNotifications()
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Error scheduling periodic motivation notifications: ${e.message}", e)
        }

        try {
            scheduleHydrationNotifications()
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Error scheduling hydration notifications: ${e.message}", e)
        }

        // Schedule daily meal reminders (Breakfast, Lunch, Dinner) via WorkManager
        try {
            MealNotificationScheduler.scheduleDailyMealReminders(this)
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Error scheduling daily meal reminders: ${e.message}", e)
        }

        try {
            setContent {
                val themeMode by viewModel.themeModeState.collectAsStateWithLifecycle(initialValue = AppThemeMode.DARK)
                MyApplicationTheme(themeMode = themeMode) {
                    FitApp(viewModel = viewModel)
                }
            }
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Fatal error calling setContent in MainActivity: ${e.message}", e)
        }
    }

    private fun schedulePeriodicNotifications() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "motivacao_diaria_work",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Failed to enqueue motivacao_diaria_work: ${e.message}", e)
        }
    }

    private fun scheduleHydrationNotifications() {
        try {
            val currentDate = java.util.Calendar.getInstance()
            val dueDate = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 16)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
            }
            if (dueDate.before(currentDate)) {
                dueDate.add(java.util.Calendar.HOUR_OF_DAY, 24)
            }
            val initialDelay = dueDate.timeInMillis - currentDate.timeInMillis
            val workRequest = PeriodicWorkRequestBuilder<HydrationWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "hidratacao_inteligente_work",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Failed to enqueue hidratacao_inteligente_work: ${e.message}", e)
        }
    }
}
