package com.schedule.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

class NotificationWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "ScheduleApp"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
        const val KEY_NOTIF_ID = "notif_id"
        const val KEY_SOUND = "sound"
        const val KEY_VIBRO = "vibro"
        const val KEY_REPEAT = "repeat"
        const val KEY_TYPE = "type"
        const val KEY_DAY_IDX = "dayIdx"
        const val KEY_ITEM_IDX = "itemIdx"
        const val KEY_TIME = "time"
        const val KEY_SUBJ = "subj"
        const val KEY_MINS = "mins"
        const val KEY_WHEN = "when"
        const val KEY_KEY = "key"
    }

    override fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "Расписание"
        val text = inputData.getString(KEY_TEXT) ?: ""
        val notifId = inputData.getInt(KEY_NOTIF_ID, System.currentTimeMillis().toInt())
        val soundUri = inputData.getString(KEY_SOUND) ?: ""
        val vibro = inputData.getBoolean(KEY_VIBRO, true)
        val repeat = inputData.getString(KEY_REPEAT) ?: "weekly"
        val key = inputData.getString(KEY_KEY) ?: ""
        val type = inputData.getString(KEY_TYPE) ?: ""
        val dayIdx = inputData.getInt(KEY_DAY_IDX, 0)
        val itemIdx = inputData.getInt(KEY_ITEM_IDX, 0)
        val time = inputData.getString(KEY_TIME) ?: ""
        val subj = inputData.getString(KEY_SUBJ) ?: ""
        val mins = inputData.getInt(KEY_MINS, 5)
        val whenType = inputData.getString(KEY_WHEN) ?: "start"

        Log.d(TAG, "NotificationWorker.doWork: title=$title, notifId=$notifId, repeat=$repeat, key=$key")

        showNotification(title, text, notifId, soundUri, vibro)

        if (repeat == "weekly") {
            rescheduleWeekly(type, dayIdx, itemIdx, time, subj, mins, whenType, key, soundUri, vibro)
        }

        return Result.success()
    }

    private fun showNotification(title: String, text: String, notifId: Int, soundUri: String, vibro: Boolean) {
        val channelId = NotificationHelper.channelFor(context, soundUri, vibro)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notifId, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId, builder.build())
        Log.d(TAG, "NotificationWorker: NOTIFICATION SHOWN notifId=$notifId channel=$channelId")
    }

    private fun rescheduleWeekly(
        type: String, dayIdx: Int, itemIdx: Int, time: String, subj: String,
        mins: Int, whenType: String, key: String, sound: String, vibro: Boolean
    ) {
        val parts = time.split(Regex("[–\\-]"))
        val refParts = if (whenType == "end") parts[1].split(":") else parts[0].split(":")
        val refHour = refParts[0].toInt()
        val refMin = refParts[1].toInt()

        val targetDow = when(dayIdx) {
            0 -> Calendar.MONDAY
            1 -> Calendar.TUESDAY
            2 -> Calendar.WEDNESDAY
            3 -> Calendar.THURSDAY
            4 -> Calendar.FRIDAY
            5 -> Calendar.SATURDAY
            6 -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }

        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 7)
            set(Calendar.HOUR_OF_DAY, refHour)
            set(Calendar.MINUTE, refMin - mins)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val curDow = get(Calendar.DAY_OF_WEEK)
            var diff = targetDow - curDow
            if (diff < 0) diff += 7
            add(Calendar.DAY_OF_MONTH, diff)
        }

        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.WEEK_OF_YEAR, 1)
        }

        val notifId = key.hashCode()
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(NotificationReceiver.EXTRA_TITLE, NotificationHelper.reminderTitle(type))
            putExtra(NotificationReceiver.EXTRA_TEXT, NotificationHelper.reminderText(type, dayIdx, time, subj, mins, whenType))
            putExtra(NotificationReceiver.EXTRA_NOTIF_ID, notifId)
            putExtra(NotificationReceiver.EXTRA_SOUND, sound)
            putExtra(NotificationReceiver.EXTRA_VIBRO, vibro)
            putExtra(NotificationReceiver.EXTRA_REPEAT, "weekly")
            putExtra(NotificationReceiver.EXTRA_TYPE, type)
            putExtra(NotificationReceiver.EXTRA_DAY_IDX, dayIdx)
            putExtra(NotificationReceiver.EXTRA_ITEM_IDX, itemIdx)
            putExtra(NotificationReceiver.EXTRA_TIME, time)
            putExtra(NotificationReceiver.EXTRA_SUBJ, subj)
            putExtra(NotificationReceiver.EXTRA_MINS, mins)
            putExtra(NotificationReceiver.EXTRA_WHEN, whenType)
            putExtra(NotificationReceiver.EXTRA_KEY, key)
        }
        val pending = PendingIntent.getBroadcast(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPending = PendingIntent.getActivity(
            context, notifId, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationHelper.scheduleExact(context, cal.timeInMillis, pending, showPending)
        Log.d(TAG, "NotificationWorker: weekly rescheduled $key → ${cal.time}")
    }
}
