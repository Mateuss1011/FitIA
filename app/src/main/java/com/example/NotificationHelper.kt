package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_ID = "incentivos_fitai"
    const val CHANNEL_NAME = "Motivação Diária"

    const val SUPPLEMENTS_CHANNEL_ID = "suplementos_fitai"
    const val SUPPLEMENTS_CHANNEL_NAME = "Lembrete de Suplementos 💊"

    const val MEALS_CHANNEL_ID = "refeicoes_fitai"
    const val MEALS_CHANNEL_NAME = "Lembretes de Refeições 🍽️"

    val MOTIVATIONAL_PHRASES = listOf(
        "O segredo do sucesso é a constância. Vamos treinar hoje! 🏋️‍♂️",
        "Cada gota de suor aproxima você do seu objetivo. Não desista! 🔥",
        "Não sabendo que era impossível, ele foi lá e fez. Hora do treino! 💪",
        "A sua única competição é contra quem você era ontem. Vista o tênis e vá! 👟",
        "Consistência vence talento toda vez. Mantenha o foco hoje! 🎯",
        "A dor de hoje é a força de amanhã. Faça valer a pena! ⚡",
        "Seu corpo pode aguentar quase tudo. É a sua mente que você precisa convencer! 🧠",
        "Foco, determinação e muito suor. Esse é o caminho da transformação! 🌟",
        "Não espere motivação, crie disciplina. O treino te espera! 🏆",
        "A saúde é a maior riqueza. Cuide de você hoje mesmo! 🍏"
    )

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Daily motivation channel
            val motivationChannel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notificações diárias de incentivo e foco do FitAI"
            }
            notificationManager.createNotificationChannel(motivationChannel)

            // High Priority Supplement Reminders Channel (Exact Local Time Alarms)
            val supplementChannel = NotificationChannel(
                SUPPLEMENTS_CHANNEL_ID,
                SUPPLEMENTS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lembretes pontuais e diários de horários de suplementação do FitAI"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(supplementChannel)

            // Meal Logging Reminders Channel (WorkManager Scheduled Daily)
            val mealChannel = NotificationChannel(
                MEALS_CHANNEL_ID,
                MEALS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Lembretes diários para registrar Café da manhã, Almoço e Jantar no FitAI"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(mealChannel)
        }
    }

    fun sendMealReminderNotification(
        context: Context,
        mealType: String,
        title: String,
        message: String,
        notificationId: Int
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("NAVIGATE_TO", "NUTRITION")
            putExtra("MEAL_TYPE", mealType)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val icon = R.drawable.ic_fitai_logo
        val notificationColor = androidx.core.content.ContextCompat.getColor(context, R.color.notification_color)

        val builder = NotificationCompat.Builder(context, MEALS_CHANNEL_ID)
            .setSmallIcon(icon)
            .setColor(notificationColor)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.notify(notificationId, builder.build())
            }
        } catch (e: Exception) {
            android.util.Log.e("FitAI_Error", "Notification error: ${e.message}", e)
        }
    }

    fun sendSupplementNotification(context: Context, title: String, message: String, notificationId: Int = 1001) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val icon = R.drawable.ic_fitai_logo
        val notificationColor = androidx.core.content.ContextCompat.getColor(context, R.color.notification_color)

        val builder = NotificationCompat.Builder(context, SUPPLEMENTS_CHANNEL_ID)
            .setSmallIcon(icon)
            .setColor(notificationColor)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.notify(notificationId, builder.build())
            }
        } catch (e: Exception) {
            android.util.Log.e("FitAI_Error", "Notification error: ${e.message}", e)
        }
    }

    fun sendNotification(context: Context, customMessage: String? = null) {
        // Create the notification channel in case it wasn't created yet
        createNotificationChannel(context)

        // Select message
        val message = customMessage ?: MOTIVATIONAL_PHRASES.random()

        // Create intent to open MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // FitAI official app logo icon & notification color
        val icon = R.drawable.ic_fitai_logo
        val notificationColor = androidx.core.content.ContextCompat.getColor(context, R.color.notification_color)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setColor(notificationColor)
            .setContentTitle("FitAI - Hora do Treino! 💪")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.notify(42, builder.build())
            }
        } catch (e: Exception) {
            android.util.Log.e("FitAI_Error", "Notification error: ${e.message}", e)
        }
    }

    fun sendNotification(context: Context, title: String, message: String) {
        // Create the notification channel in case it wasn't created yet
        createNotificationChannel(context)

        // Create intent to open MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val icon = R.drawable.ic_fitai_logo
        val notificationColor = androidx.core.content.ContextCompat.getColor(context, R.color.notification_color)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setColor(notificationColor)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.notify(43, builder.build())
            }
        } catch (e: Exception) {
            android.util.Log.e("FitAI_Error", "Notification error: ${e.message}", e)
        }
    }
}
