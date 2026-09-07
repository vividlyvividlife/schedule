package com.schedule.app

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ScheduleApp"
    }

    private lateinit var webView: WebView
    private val FILE_PICKER_REQUEST = 1001
    private var hasSchedule = false
    private var hasPersonal = false
    private var hasExtended = false
    internal var _ringtonePlayer: android.media.Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Расписание 2026–2027"

        webView = findViewById(R.id.webView)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.addJavascriptInterface(AndroidBridge(this), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page loaded: $url")
                webView.postDelayed({ queryDataState {} }, 1000)
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.contains("zayavlenie")) {
                    Log.d(TAG, "Opening zayavlenie in browser: $url")
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }
                return false
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request?.url!!)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                msg?.let { Log.d(TAG, "JS: ${it.message()} [${it.sourceId()}:${it.lineNumber()}]") }
                return true
            }
        }
        Log.d(TAG, "onCreate: loading schedule")
        webView.loadUrl("https://appassets.androidplatform.net/index.html")

        checkBatteryOptimization()
        requestNotificationPermission()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val prefs = getSharedPreferences("schedule_prefs", MODE_PRIVATE)
            if (!prefs.getBoolean("notif_permitted", false)) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2001)
                prefs.edit().putBoolean("notif_permitted", true).apply()
            }
        }
    }

    private fun checkBatteryOptimization() {
        val prefs = getSharedPreferences("schedule_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("battery_prompted", false)) return

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(packageName)

        if (!isIgnoring) {
            AlertDialog.Builder(this, R.style.Theme_Schedule_Dialog)
                .setTitle("🔋 Оптимизация батареи")
                .setMessage(
                    "Для стабильной работы расписания:\n\n" +
                    "1. Нажмите «Разрешить» — это отключит оптимизацию батареи\n" +
                    "2. В свежих приложениях нажмите иконку 🔒 рядом с приложением\n\n" +
                    "Это позволит расписанию работать в фоне и не обновляться."
                )
                .setPositiveButton("Разрешить") { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Battery settings error", e)
                    }
                }
                .setNegativeButton("Позже", null)
                .setOnDismissListener {
                    prefs.edit().putBoolean("battery_prompted", true).apply()
                }
                .show()
        } else {
            prefs.edit().putBoolean("battery_prompted", true).apply()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val deleteMenu = menu?.findItem(R.id.menu_delete)?.subMenu
        deleteMenu?.findItem(R.id.menu_delete_schedule)?.isVisible = hasSchedule
        deleteMenu?.findItem(R.id.menu_delete_personal)?.isVisible = hasPersonal
        deleteMenu?.findItem(R.id.menu_delete_extended)?.isVisible = hasExtended
        menu?.findItem(R.id.menu_reset)?.isVisible = hasSchedule || hasPersonal || hasExtended
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "Menu: ${item.title}")
        return when (item.itemId) {
            R.id.menu_export -> { showExportDialog(); true }
            R.id.menu_import -> { openFilePicker(); true }
            R.id.menu_edit_mode -> { toggleEditMode(); true }
            R.id.menu_delete_schedule -> { confirmDeleteType("schedule", "все уроки"); true }
            R.id.menu_delete_personal -> { confirmDeleteType("personal", "все занятия"); true }
            R.id.menu_delete_extended -> { confirmDeleteType("extended", "продлёнку"); true }
            R.id.menu_reset -> { resetData(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ── Export ─────────────────────────────────────────────────────────

    private fun showExportDialog() {
        val options = mutableListOf("💾 Всё расписание (JSON)")
        val handlers = mutableListOf(Runnable { exportFullJson() })
        if (hasSchedule) { options.add("📄 Только уроки (JSON)"); handlers.add(Runnable { exportPart("schedule") }) }
        if (hasPersonal) { options.add("🤸 Только занятия (JSON)"); handlers.add(Runnable { exportPart("personal") }) }
        if (hasExtended) { options.add("🎒 Только продлёнка (JSON)"); handlers.add(Runnable { exportPart("extended") }) }
        AlertDialog.Builder(this, R.style.Theme_Schedule_Dialog)
            .setTitle("Экспорт")
            .setItems(options.toTypedArray()) { _, which -> handlers[which].run() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun exportFullJson() {
        webView.evaluateJavascript(
            """(function() {
                try {
                    var d = JSON.parse(localStorage.getItem('tg_local_data') || '{}');
                    var local = d;
                    var sch = (local.schedule && local.schedule.length) ? local.schedule : SCHEDULE;
                    var pers = (local.personal && Object.keys(local.personal).length) ? local.personal : PERSONAL;
                    var ext = (local.extended && local.extended.length) ? local.extended : EXTENDED;
                    var out = { schedule: sch, personal: pers, extended: ext };
                    return JSON.stringify(out, null, 2);
                } catch(e) { return '{"error":"' + e.message + '"}'; }
            })()"""
        ) { result -> handleJsonResult(result, "raspisanie_2A.json") }
    }

    private fun exportPart(type: String) {
        val names = mapOf("schedule" to "Uroki_2A.json", "personal" to "Zanyatiya_2A.json", "extended" to "Prodlenka_2A.json")
        webView.evaluateJavascript(
            """(function() {
                try {
                    var d = JSON.parse(localStorage.getItem('tg_local_data') || '{}');
                    var data = d['$type'] || [];
                    if (Array.isArray(data) && data.length === 0) {
                        if ('$type' === 'schedule') data = SCHEDULE;
                        else if ('$type' === 'personal') data = PERSONAL;
                        else if ('$type' === 'extended') data = EXTENDED;
                    }
                    return JSON.stringify(data, null, 2);
                } catch(e) { return '{"error":"' + e.message + '"}'; }
            })()"""
        ) { result -> handleJsonResult(result, names[type] ?: "export.json") }
    }

    private fun handleJsonResult(result: String?, filename: String) {
        if (result != null && result != "null") {
            val json = result.trim('"')
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
            if (!json.contains("\"error\"")) {
                saveAndShareFile(filename, json, "application/json")
                Log.d(TAG, "Export OK: $filename (${json.length} bytes)")
                Toast.makeText(this, "JSON экспортирован!", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "Export JS error: $json")
                Toast.makeText(this, "Ошибка: $json", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Import ─────────────────────────────────────────────────────────

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(Intent.createChooser(intent, "Выберите JSON файл"), FILE_PICKER_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICKER_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val content = readUriContent(uri)
                if (content != null) {
                    importJson(content)
                }
            }
        }
    }

    private fun importJson(jsonStr: String) {
        val escaped = jsonStr.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "")
        webView.evaluateJavascript(
            """(function() {
                try {
                    var data = JSON.parse('$escaped');
                    var merged = JSON.parse(localStorage.getItem('tg_local_data') || '{"schedule":[],"personal":{},"extended":[]}');
                    var what = '';
                    if (Array.isArray(data) && data.length > 0 && data[0] && data[0].lessons) {
                        merged.schedule = data;
                        what = 'Уроки';
                    } else if (Array.isArray(data) && data.length > 0 && data[0] && data[0].time && data[0].subj && !data[0].lessons) {
                        merged.extended = data;
                        what = 'Продлёнка';
                    } else if (typeof data === 'object' && !Array.isArray(data) && data[0] && Array.isArray(data[0])) {
                        merged.personal = data;
                        what = 'Личные занятия';
                    } else if (data.schedule || data.extended || data.personal) {
                        if (data.schedule) merged.schedule = data.schedule;
                        if (data.personal) merged.personal = data.personal;
                        if (data.extended) merged.extended = data.extended;
                        what = 'Всё';
                    } else {
                        throw new Error('Не удалось определить тип данных');
                    }
                    localStorage.setItem('tg_local_data', JSON.stringify(merged));
                    return 'ok:' + what;
                } catch(e) { return 'err:' + e.message; }
            })()"""
        ) { result ->
            val r = result?.trim('"') ?: "Ошибка"
            if (r.startsWith("ok")) {
                val what = r.removePrefix("ok:")
                Log.d(TAG, "Import OK: $what")
                Toast.makeText(this, "$what импортировано! Перезапускаю...", Toast.LENGTH_SHORT).show()
                webView.reload()
            } else {
                Log.e(TAG, "Import error: $r")
                Toast.makeText(this, "Ошибка: $r", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Edit Mode ─────────────────────────────────────────────────────

    private fun toggleEditMode() {
        Log.d(TAG, "Toggle edit mode")
        webView.evaluateJavascript("toggleEditMode(); 'ok'") {
            val s = it?.trim('"') ?: ""
            Toast.makeText(this, if (s == "ok") "Режим редактирования" else "Ошибка", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Delete by Type ────────────────────────────────────────────────

    private fun confirmDeleteType(type: String, label: String) {
        AlertDialog.Builder(this, R.style.Theme_Schedule_Dialog)
            .setTitle("Удалить $label?")
            .setMessage("Вы уверены?")
            .setPositiveButton("Да") { _, _ -> deleteType(type) }
            .setNegativeButton("Нет", null).show()
    }

    private fun deleteType(type: String) {
        Log.d(TAG, "Delete type: $type")
        webView.evaluateJavascript(
            """(function() {
                try {
                    var d = JSON.parse(localStorage.getItem('tg_local_data') || '{"schedule":[],"personal":{},"extended":[]}');
                    if ('$type' === 'schedule') { d.schedule = []; }
                    else if ('$type' === 'personal') { d.personal = {}; }
                    else if ('$type' === 'extended') { d.extended = []; }
                    localStorage.setItem('tg_local_data', JSON.stringify(d));
                    return 'ok';
                } catch(e) { return e.message; }
            })()"""
        ) { r ->
            val s = r?.trim('"') ?: ""
            if (s == "ok") {
                Toast.makeText(this, "Удалено!", Toast.LENGTH_SHORT).show()
                webView.reload()
            } else Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
        }
    }

    // ── Sync ───────────────────────────────────────────────────────────

    private fun resetData() {
        AlertDialog.Builder(this, R.style.Theme_Schedule_Dialog)
            .setTitle("Сбросить?")
            .setMessage("Удалить все данные?")
            .setPositiveButton("Да") { _, _ ->
                cancelAllReminders()
                webView.evaluateJavascript("localStorage.removeItem('tg_local_data'); localStorage.removeItem('tg_reminders'); 'ok'") {
                    hasSchedule = false; hasPersonal = false; hasExtended = false
                    Toast.makeText(this, "Сброшено!", Toast.LENGTH_SHORT).show()
                    invalidateOptionsMenu()
                    webView.reload()
                }
            }
            .setNegativeButton("Нет", null).show()
    }

    // ── Data State ────────────────────────────────────────────────────

    private fun queryDataState(onDone: () -> Unit = {}) {
        webView.evaluateJavascript(
            """(function() {
                try {
                    var d = JSON.parse(localStorage.getItem('tg_local_data') || '{}');
                    var sch = d.schedule || [];
                    var hasSch = false;
                    for (var i = 0; i < sch.length; i++) {
                        if (sch[i] && sch[i].lessons && sch[i].lessons.length > 0) { hasSch = true; break; }
                    }
                    var pers = d.personal || {};
                    var hasPers = false;
                    var keys = Object.keys(pers);
                    for (var i = 0; i < keys.length; i++) {
                        if (Array.isArray(pers[keys[i]]) && pers[keys[i]].length > 0) { hasPers = true; break; }
                    }
                    var ext = d.extended || [];
                    var hasExt = ext.length > 0;
                    return (hasSch ? '1' : '0') + (hasPers ? '1' : '0') + (hasExt ? '1' : '0');
                } catch(e) { return '000'; }
            })()"""
        ) { result ->
            val s = result?.trim('"', ' ') ?: "000"
            Log.d(TAG, "queryDataState raw='$s'")
            if (s.length >= 3) {
                hasSchedule = s[0] == '1'
                hasPersonal = s[1] == '1'
                hasExtended = s[2] == '1'
            }
            Log.d(TAG, "Data state: schedule=$hasSchedule personal=$hasPersonal extended=$hasExtended")
            runOnUiThread {
                invalidateOptionsMenu()
                onDone()
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun readUriContent(uri: Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.use { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { it.readText() } }
        } catch (e: Exception) { Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show(); null }
    }

    private fun getFileName(uri: Uri): String {
        var name = "unknown"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) name = c.getString(i)
        }
        return name
    }

    private fun saveAndShareFile(filename: String, content: String, mimeType: String) {
        try {
            val dir = File(filesDir, "exports").also { it.mkdirs() }
            val file = File(dir, filename)
            FileOutputStream(file).use { it.write(content.toByteArray(Charsets.UTF_8)) }
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, filename.removeSuffix(".json").removeSuffix(".txt"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Экспорт"))
        } catch (e: Exception) { Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    override fun onBackPressed() { if (webView.canGoBack()) webView.goBack() else super.onBackPressed() }

    // ── Reminders / Notifications ────────────────────────────────────

    fun scheduleRemindersFromJson(json: String) {
        try {
            val am = getSystemService(ALARM_SERVICE) as android.app.AlarmManager

            cancelAllReminders()

            val arr = org.json.JSONArray(json)
            val dayNames = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
            val dayNamesFull = arrayOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                val type = r.getString("type")
                val dayIdx = r.getInt("dayIdx")
                val time = r.getString("time")
                val subj = r.optString("subj", "")
                val mins = r.getInt("mins")
                val key = r.getString("key")

                val typeLabel = when(type) { "school" -> "Урок"; "personal" -> "Занятие"; "extended" -> "Продлёнка"; else -> "Занятие" }

                val parts = time.split("–")
                val startParts = parts[0].split(":")
                val startHour = startParts[0].toInt()
                val startMin = startParts[1].toInt()
                val startTotalMin = startHour * 60 + startMin

                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.DAY_OF_WEEK, when(dayIdx) {
                        0 -> java.util.Calendar.MONDAY
                        1 -> java.util.Calendar.TUESDAY
                        2 -> java.util.Calendar.WEDNESDAY
                        3 -> java.util.Calendar.THURSDAY
                        4 -> java.util.Calendar.FRIDAY
                        5 -> java.util.Calendar.SATURDAY
                        6 -> java.util.Calendar.SUNDAY
                        else -> java.util.Calendar.MONDAY
                    })
                    set(java.util.Calendar.HOUR_OF_DAY, startHour)
                    set(java.util.Calendar.MINUTE, startMin - mins)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }

                if (cal.timeInMillis <= System.currentTimeMillis()) {
                    cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                }

                val notifId = key.hashCode()
                val sound = r.optString("sound", "")
                val vibro = r.optBoolean("vibro", true)
                val intent = Intent(this, NotificationReceiver::class.java).apply {
                    putExtra(NotificationReceiver.EXTRA_TITLE, "$typeLabel: $subj")
                    putExtra(NotificationReceiver.EXTRA_TEXT, "${dayNamesFull[dayIdx]} · Начало в ${parts[0]} · Через $mins мин")
                    putExtra(NotificationReceiver.EXTRA_NOTIF_ID, notifId)
                    putExtra(NotificationReceiver.EXTRA_SOUND, sound)
                    putExtra(NotificationReceiver.EXTRA_VIBRO, vibro)
                }
                val pending = PendingIntent.getBroadcast(
                    this, notifId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val repeat = r.optString("repeat", "weekly")
                if (repeat == "weekly") {
                    val interval = 7L * 24 * 60 * 60 * 1000
                    am.setRepeating(android.app.AlarmManager.RTC_WAKEUP, cal.timeInMillis, interval, pending)
                    Log.d(TAG, "Weekly alarm set: $key at ${cal.time}")
                } else {
                    am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
                    Log.d(TAG, "Once alarm set: $key at ${cal.time}")
                }
            }

            val editor = getSharedPreferences("schedule_prefs", MODE_PRIVATE).edit()
            editor.putString("reminders_json", json)
            editor.apply()

            runOnUiThread { Toast.makeText(this, "Напоминания настроены ✓", Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) {
            Log.e(TAG, "scheduleReminders error", e)
        }
    }

    private fun cancelAllReminders() {
        val am = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        val prefs = getSharedPreferences("schedule_prefs", MODE_PRIVATE)
        val json = prefs.getString("reminders_json", "[]") ?: "[]"
        try {
            val arr = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                val key = r.getString("key")
                val intent = Intent(this, NotificationReceiver::class.java)
                val pending = PendingIntent.getBroadcast(
                    this, key.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.cancel(pending)
            }
        } catch (_: Exception) {}
        prefs.edit().remove("reminders_json").apply()
    }

    class AndroidBridge(private val activity: MainActivity) {
        @JavascriptInterface fun exportTxt(filename: String, content: String) {
            activity.runOnUiThread { activity.saveAndShareFile(filename, content, "text/plain") }
        }
        @JavascriptInterface fun showToast(message: String) {
            activity.runOnUiThread { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show() }
        }
        @JavascriptInterface fun syncReminders(json: String) {
            Log.d(TAG, "syncReminders: $json")
            activity.scheduleRemindersFromJson(json)
        }
        @JavascriptInterface fun getInstalledRingtones(): String {
            val list = org.json.JSONArray()

            val bundled = arrayOf(
                Pair("🔔 Ding", "android.resource://com.schedule.app/raw/notif_ding"),
                Pair("🎵 Chime", "android.resource://com.schedule.app/raw/notif_chime"),
                Pair("🌿 Gentle", "android.resource://com.schedule.app/raw/notif_gentle"),
                Pair("⚡ Urgent", "android.resource://com.schedule.app/raw/notif_urgent"),
                Pair("🛎 Bell", "android.resource://com.schedule.app/raw/notif_bell")
            )
            for ((title, uri) in bundled) {
                val obj = org.json.JSONObject()
                obj.put("title", title)
                obj.put("uri", uri)
                list.put(obj)
            }

            val manager = android.media.RingtoneManager(activity)
            manager.setType(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val cursor = manager.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(android.media.RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = manager.getRingtoneUri(cursor.position).toString()
                val obj = org.json.JSONObject()
                obj.put("title", "📱 $title")
                obj.put("uri", uri)
                list.put(obj)
            }
            return list.toString()
        }
        @JavascriptInterface fun playRingtone(uri: String) {
            try {
                activity._ringtonePlayer?.stop()
                val r = android.media.RingtoneManager.getRingtone(activity, android.net.Uri.parse(uri))
                r?.play()
                activity._ringtonePlayer = r
            } catch (_: Exception) {}
        }
        @JavascriptInterface fun stopRingtone() {
            try { activity._ringtonePlayer?.stop(); activity._ringtonePlayer = null } catch (_: Exception) {}
        }
    }
}
