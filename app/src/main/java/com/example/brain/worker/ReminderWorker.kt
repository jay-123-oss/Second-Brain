package com.example.brain.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.brain.MainActivity
import com.example.brain.R
import com.example.brain.data.AppDatabase
import kotlinx.coroutines.flow.first

/**
 * Background WorkManager worker implementing Spaced Repetition resurfacing.
 * Periodically searches for items captured >= 7 days ago and posts an intelligent notification.
 */
class ReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "second_brain_resurface_channel"
        const val NOTIFICATION_ID = 1001
        private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L
    }

    override suspend fun doWork(): Result {
        val database = AppDatabase.getInstance(context)
        val cutoffTimestamp = System.currentTimeMillis() - SEVEN_DAYS_MS

        val oldItems = database.savedItemDao().getOldForgottenItems(cutoffTimestamp, limit = 1).first()

        if (oldItems.isNotEmpty()) {
            val itemToResurface = oldItems.first()
            sendResurfaceNotification(itemToResurface.title, itemToResurface.notes)
        }

        return Result.success()
    }

    private fun sendResurfaceNotification(title: String, preview: String?) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Knowledge Resurfacing",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Periodically surfaces forgotten knowledge from your Second Brain."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Rediscover: $title")
            .setContentText(preview?.takeIf { it.isNotBlank() } ?: "Saved over a week ago in your Second Brain.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(preview?.takeIf { it.isNotBlank() } ?: "Saved in your Second Brain. Tap to review.")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
