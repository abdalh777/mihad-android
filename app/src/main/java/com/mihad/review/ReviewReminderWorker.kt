package com.mihad.review

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class ReviewReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val channel = "review_reminders"
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel, "تذكيرات المراجعة", NotificationManager.IMPORTANCE_DEFAULT))
        val title = inputData.getString("title") ?: "درس"
        val notification = NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("وقت مراجعة قصيرة")
            .setContentText("حان وقت مراجعة: $title")
            .setAutoCancel(true).build()
        NotificationManagerCompat.from(applicationContext).notify(title.hashCode(), notification)
        return Result.success()
    }
}
