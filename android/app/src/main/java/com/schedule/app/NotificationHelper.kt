package com.schedule.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log

object NotificationHelper {
    private const val TAG = "ScheduleApp"
    const val CHANNEL_ID = "schedule_reminders"

    private val DAY_NAMES = arrayOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

    fun reminderTitle(type: String): String = when (type) {
        "school" -> "Урок"
        "personal" -> "Занятие"
        "extended" -> "Продлёнка"
        else -> type
    }

    fun reminderText(type: String, dayIdx: Int, time: String, subj: String, mins: Int, whenType: String): String {
        val action = if (whenType == "end") {
            "Заканчивается через $mins ${minutesWord(mins, accusative = true)}"
        } else {
            "До начала $mins ${minutesWord(mins, accusative = false)}"
        }
        return listOf(action, DAY_NAMES.getOrNull(dayIdx), time, subj)
            .filter { !it.isNullOrBlank() }
            .joinToString(" · ")
    }

    private fun minutesWord(n: Int, accusative: Boolean): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod100 in 11..14 -> "минут"
            mod10 == 1 -> if (accusative) "минуту" else "минута"
            mod10 in 2..4 -> "минуты"
            else -> "минут"
        }
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Напоминания расписания",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Оповещения о начале и конце уроков"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // Since API 26 channel settings override builder.setSound/setVibrate,
    // so each (sound, vibro) pair gets its own channel.
    fun channelFor(context: Context, soundUri: String, vibro: Boolean): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CHANNEL_ID
        val id = CHANNEL_ID + "_" + (soundUri.ifEmpty { "default" }).hashCode() + if (vibro) "_v" else "_nv"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(id) != null) return id

        val sound: Uri = if (soundUri.isNotEmpty()) {
            try { Uri.parse(soundUri) } catch (_: Exception) { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) }
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
        val channel = NotificationChannel(id, "Напоминания расписания", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Оповещения о начале и конце уроков"
            setSound(sound, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            if (vibro) {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            } else {
                enableVibration(false)
            }
        }
        manager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created: $id")
        return id
    }

    // Highest priority scheduling (survives Doze and most OEM killers).
    // Falls back to a 10-minute window if exact alarms are not permitted.
    fun scheduleExact(context: Context, triggerAtMillis: Long, operation: PendingIntent, showIntent: PendingIntent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), operation)
            } else {
                am.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, 10 * 60 * 1000L, operation)
                Log.w(TAG, "Exact alarms denied — using setWindow fallback")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "setAlarmClock denied — using setWindow fallback", e)
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, 10 * 60 * 1000L, operation)
        }
    }
}
