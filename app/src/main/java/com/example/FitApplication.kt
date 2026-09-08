package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class FitApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Set defensive default uncaught exception handler to log fatal crash causes
        try {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                Log.e("FitAI_Error", "FATAL UNCAUGHT EXCEPTION on thread '${thread.name}': ${throwable.message}", throwable)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Error setting default uncaught exception handler: ${e.message}", e)
        }

        // Initialize Firebase defensively
        try {
            FirebaseApp.initializeApp(this)
            Log.d("FitAI_Init", "FirebaseApp initialized successfully.")
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Error initializing FirebaseApp on cold start: ${e.message}", e)
        }

        // Initialize notification channel defensively
        try {
            NotificationHelper.createNotificationChannel(this)
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Error creating NotificationChannel on app start: ${e.message}", e)
        }

        // Schedule WorkManager meal reminders defensively
        try {
            MealNotificationScheduler.scheduleDailyMealReminders(this)
        } catch (e: Throwable) {
            Log.e("FitAI_Error", "Error scheduling WorkManager meal reminders on app start: ${e.message}", e)
        }
    }
}
