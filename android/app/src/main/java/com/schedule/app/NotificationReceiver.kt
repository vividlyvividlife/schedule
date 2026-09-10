package com.schedule.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class NotificationReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "schedule_reminders"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_NOTIF_ID = "notif_id"
        const val EXTRA_SOUND = "sound"
        const val EXTRA_VIBRO = "vibro"
        const val EXTRA_REPEAT = "repeat"
        const val EXTRA_TYPE = "type"
        const val EXTRA_DAY_IDX = "dayIdx"
        const val EXTRA_ITEM_IDX = "itemIdx"
        const val EXTRA_TIME = "time"
        const val EXTRA_SUBJ = "subj"
        const val EXTRA_MINS = "mins"
        const val EXTRA_WHEN = "when"
        const val EXTRA_KEY = "key"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Расписание"
        val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, System.currentTimeMillis().toInt())
        val soundUri = intent.getStringExtra(EXTRA_SOUND) ?: ""
        val vibro = intent.getBooleanExtra(EXTRA_VIBRO, true)
        val repeat = intent.getStringExtra(EXTRA_REPEAT) ?: "weekly"
        val type = intent.getStringExtra(EXTRA_TYPE) ?: ""
        val dayIdx = intent.getIntExtra(EXTRA_DAY_IDX, 0)
        val itemIdx = intent.getIntExtra(EXTRA_ITEM_IDX, 0)
        val time = intent.getStringExtra(EXTRA_TIME) ?: ""
        val subj = intent.getStringExtra(EXTRA_SUBJ) ?: ""
        val mins = intent.getIntExtra(EXTRA_MINS, 5)
        val whenType = intent.getStringExtra(EXTRA_WHEN) ?: "start"
        val key = intent.getStringExtra(EXTRA_KEY) ?: ""

        Log.d(TAG, "NotificationReceiver.onReceive: title=$title, notifId=$notifId, repeat=$repeat, key=$key")

        val inputData = Data.Builder()
            .putString(NotificationWorker.KEY_TITLE, title)
            .putString(NotificationWorker.KEY_TEXT, text)
            .putInt(NotificationWorker.KEY_NOTIF_ID, notifId)
            .putString(NotificationWorker.KEY_SOUND, soundUri)
            .putBoolean(NotificationWorker.KEY_VIBRO, vibro)
            .putString(NotificationWorker.KEY_REPEAT, repeat)
            .putString(NotificationWorker.KEY_TYPE, type)
            .putInt(NotificationWorker.KEY_DAY_IDX, dayIdx)
            .putInt(NotificationWorker.KEY_ITEM_IDX, itemIdx)
            .putString(NotificationWorker.KEY_TIME, time)
            .putString(NotificationWorker.KEY_SUBJ, subj)
            .putInt(NotificationWorker.KEY_MINS, mins)
            .putString(NotificationWorker.KEY_WHEN, whenType)
            .putString(NotificationWorker.KEY_KEY, key)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInputData(inputData)
            .setInitialDelay(0, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d(TAG, "NotificationReceiver: delegated to WorkManager, notifId=$notifId")
    }

    private val TAG = "ScheduleApp"
}
