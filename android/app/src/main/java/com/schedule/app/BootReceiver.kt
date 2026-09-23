package com.schedule.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.LOCKED_BOOT_COMPLETED" &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Log.d("ScheduleApp", "Restore trigger (${intent.action}) — restoring reminders")

        try {
            val prefs = context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
            // Re-offer the OEM autostart prompt after every app update.
            if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                prefs.edit().remove("autostart_prompted").apply()
            }
            val json = prefs.getString("reminders_json", null)
            if (json.isNullOrEmpty()) {
                Log.d("ScheduleApp", "No saved reminders")
                return
            }

            val arr = org.json.JSONArray(json)

            for (i in 0 until arr.length()) {
                try {
                    val r = arr.getJSONObject(i)
                    val type = r.getString("type")
                    val dayIdx = r.getInt("dayIdx")
                    val time = r.getString("time")
                    val subj = r.optString("subj", "")
                    val mins = r.getInt("mins")
                    val key = r.getString("key")
                    val whenType = r.optString("when", "start")
                    val sound = r.optString("sound", "")
                    val vibro = r.optBoolean("vibro", true)
                    val repeat = r.optString("repeat", "weekly")

                    val triggerAt = NotificationHelper.nextTriggerMillis(dayIdx, time, whenType, mins)
                        ?: throw IllegalArgumentException("bad time '$time'")

                    val notifId = key.hashCode()
                    val alarmIntent = Intent(context, NotificationReceiver::class.java).apply {
                        putExtra(NotificationReceiver.EXTRA_TITLE, NotificationHelper.reminderTitle(type))
                        putExtra(NotificationReceiver.EXTRA_TEXT, NotificationHelper.reminderText(type, dayIdx, time, subj, mins, whenType))
                        putExtra(NotificationReceiver.EXTRA_NOTIF_ID, notifId)
                        putExtra(NotificationReceiver.EXTRA_SOUND, sound)
                        putExtra(NotificationReceiver.EXTRA_VIBRO, vibro)
                        putExtra(NotificationReceiver.EXTRA_REPEAT, repeat)
                        putExtra(NotificationReceiver.EXTRA_TYPE, type)
                        putExtra(NotificationReceiver.EXTRA_DAY_IDX, dayIdx)
                        putExtra(NotificationReceiver.EXTRA_ITEM_IDX, i)
                        putExtra(NotificationReceiver.EXTRA_TIME, time)
                        putExtra(NotificationReceiver.EXTRA_SUBJ, subj)
                        putExtra(NotificationReceiver.EXTRA_MINS, mins)
                        putExtra(NotificationReceiver.EXTRA_WHEN, whenType)
                        putExtra(NotificationReceiver.EXTRA_KEY, key)
                    }
                    val pending = android.app.PendingIntent.getBroadcast(
                        context, notifId, alarmIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )

                    val showIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val showPending = android.app.PendingIntent.getActivity(
                        context, notifId, showIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    NotificationHelper.scheduleExact(context, triggerAt, pending, showPending)
                    Log.d("ScheduleApp", "Boot alarm restored: $key at ${java.util.Date(triggerAt)}")
                } catch (e: Exception) {
                    Log.e("ScheduleApp", "BootReceiver: skipping reminder[$i]: ${e.message}")
                }
            }
            Log.d("ScheduleApp", "Restored ${arr.length()} reminders after boot")
        } catch (e: Exception) {
            Log.e("ScheduleApp", "BootReceiver error", e)
        }
    }
}
