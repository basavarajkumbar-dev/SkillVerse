package com.skillverse.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.skillverse.app.MainActivity
import com.skillverse.app.R
import com.skillverse.app.SkillVerseApplication
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Schedules the optional daily reminder with WorkManager.
 *
 * - One unique work name with REPLACE policy, so there is never more than one pending reminder.
 * - WorkManager persists work across reboots and app updates on its own.
 * - Delivery is NOT exact: Android may delay deferrable work (Doze, battery saver, OEM limits).
 *   The settings screen explains this to the user.
 */
class ReminderScheduler(private val context: Context) {
    fun schedule(hour: Int, minute: Int) {
        val delay = delayUntil(LocalDateTime.now(), ZoneId.systemDefault(), hour, minute)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "skillverse_daily_reminder"

        /** Time until the next local occurrence of hour:minute (tomorrow if it has already passed today). */
        fun delayUntil(now: LocalDateTime, zone: ZoneId, hour: Int, minute: Int): Duration {
            var next = now.toLocalDate().atTime(LocalTime.of(hour, minute))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val d = Duration.between(now.atZone(zone), next.atZone(zone))
            return if (d.isNegative) Duration.ofMinutes(1) else d
        }
    }
}

object ReminderNotifications {
    const val CHANNEL_ID = "daily_reminder"
    private const val NOTIFICATION_ID = 1001

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notificationsEnabled(context: Context): Boolean =
        hasPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.reminder_channel_description) }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, remaining: Int) {
        if (!notificationsEnabled(context)) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = if (remaining > 0) {
            "$remaining mission${if (remaining == 1) "" else "s"} waiting. About 10–20 minutes keeps your streak alive."
        } else {
            "Your daily missions are ready. About 10–20 minutes keeps your streak alive."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_skillverse)
            .setContentTitle("Mission Control is calling")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }
}

class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? SkillVerseApplication ?: return Result.success()
        val container = app.container
        val settings = container.settingsRepository.settings.first()
        if (!settings.reminderEnabled) return Result.success()
        val remaining = try {
            val today = container.timeProvider.today()
            container.progressRepository.observeMissions(today).first().count { it.completedAtEpochMs == null }
        } catch (e: Exception) {
            -1
        }
        // Skip the nudge only when today's missions exist and are all done.
        if (remaining != 0) ReminderNotifications.show(applicationContext, remaining.coerceAtLeast(0))
        ReminderScheduler(applicationContext).schedule(settings.reminderHour, settings.reminderMinute)
        return Result.success()
    }
}
