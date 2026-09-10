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
        private const val GROUP_CUSTOM_DELETE = 9001
    }

    private lateinit var webView: WebView
    private val FILE_PICKER_REQUEST = 1001
    private var hasSchedule = false
    private var hasPersonal = false
    private var hasExtended = false
    private var editModeActive = false
    private var customKeys: List<String> = emptyList()
    internal var _ringtonePlayer: android.media.Ringtone? = null
    internal var pickerCallback: ((android.net.Uri?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Расписание"

        webView = findViewById(R.id.webView)
        webView.clearCache(true)

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

        val swipeRefresh = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        swipeRefresh.setColorSchemeColors(0xFF6200EE.toInt())
        swipeRefresh.setOnRefreshListener {
            webView.clearCache(true)
            webView.loadUrl("https://appassets.androidplatform.net/index.html?t=${System.currentTimeMillis()}")
            swipeRefresh.isRefreshing = false
        }

        webView.loadUrl("https://appassets.androidplatform.net/index.html")

        val batteryPrompted = checkBatteryOptimization()
        createNotificationChannel()
        requestNotificationPermission()
        requestExactAlarmPermission()
        if (!batteryPrompted) logPermissionDiagnostics()
    }

    private fun createNotificationChannel() {
        NotificationHelper.createChannel(this)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
        if (this::webView.isInitialized) queryDataState {}
    }

    override fun onPause() {
        super.onPause()
        webView.postDelayed({
            webView.onPause()
            webView.pauseTimers()
        }, 500)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "POST_NOTIFICATIONS already granted: $granted")
            if (granted) return

            val prefs = getSharedPreferences("schedule_prefs", MODE_PRIVATE)
            val askCount = prefs.getInt("notif_ask_count", 0)

            if (askCount < 3) {
                prefs.edit().putInt("notif_ask_count", askCount + 1).apply()
                Log.d(TAG, "Requesting POST_NOTIFICATIONS (attempt ${askCount + 1})")
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2001)
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS denied $askCount times — showing settings dialog")
                runOnUiThread {
                    AlertDialog.Builder(this, R.style.Theme_Schedule_Dialog)
                        .setTitle("🔔 Разрешение на уведомления")
                        .setMessage(
                            "Разрешение на уведомления отклонено.\n\n" +
                            "Без него напоминания не будут приходить.\n\n" +
                            "Включите вручную: Настройки → Приложения → Расписание → Уведомления"
                        )
                        .setPositiveButton("Настройки") { _, _ -> openAppSettings() }
                        .setNegativeButton("Позже", null)
                        .show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2001) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            val prefs = getSharedPreferences("schedule_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("notif_permitted", granted).apply()
            if (granted) prefs.edit().putInt("notif_ask_count", 0).apply()
            Log.d(TAG, "Notification permission result: granted=$granted")
            if (!granted && Build.VERSION.SDK_INT >= 33) {
                Toast.makeText(this, "Без разрешения уведомления не будут приходить", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun logPermissionDiagnostics(force: Boolean = false) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager

        val notifEnabled = if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
        val notifChannelOk = nm.getNotificationChannel(NotificationHelper.CHANNEL_ID)?.let {
            it.importance != android.app.NotificationManager.IMPORTANCE_NONE
        } ?: false

        val am = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        val canExact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val batteryIgnored = pm.isIgnoringBatteryOptimizations(packageName)

        val oemInfo = OEMHelper.getOEMInfo()
        Log.w(TAG, "═══════════ PERMISSION DIAGNOSTICS ═══════════")
        Log.w(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}")
        Log.w(TAG, "OEM: ${oemInfo.name} (${oemInfo.manufacturer})")
        Log.w(TAG, "POST_NOTIFICATIONS granted: $notifEnabled")
        Log.w(TAG, "Notification channel ok: $notifChannelOk")
        Log.w(TAG, "canScheduleExactAlarms: $canExact")
        Log.w(TAG, "Battery optimization ignored: $batteryIgnored")
        Log.w(TAG, "═══════════════════════════════════════════════")

        val issues = mutableListOf<String>()
        if (!notifEnabled) issues.add("нет разрешения на уведомления")
        if (!notifChannelOk) issues.add("канал уведомлений отключён")
        if (!canExact) issues.add("запрещены точные будильники")
        if (!batteryIgnored) issues.add("оптимизация батареи включена")
        if (OEMHelper.isAggressiveOEM()) {
            issues.add("${oemInfo.manufacturer}: требуется автозапуск и «Без ограничений» батареи")
        }

        val prefs = getSharedPreferences("schedule_prefs", MODE_PRIVATE)

        if (issues.isEmpty()) {
            if (!force) return
            val extra = if (OEMHelper.isAggressiveOEM())
                "\n\nНапоминание для ${oemInfo.manufacturer}: если напоминания пропадают, проверьте автозапуск и экономию батареи."
            else ""
            runOnUiThread {
                AlertDialog.Builder(this, R.style.Theme_Schedule_Dialog)
                    .setTitle("✅ Уведомления настроены")
                    .setMessage(
                        "Устройство: ${Build.MANUFACTURER} ${Build.MODEL} (${oemInfo.name})\n\n" +
                        "✔ Разрешение на уведомления\n" +
                        "✔ Канал уведомлений\n" +
                        "✔ Точные будильники\n" +
                        "✔ Оптимизация батареи отключена" + extra
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
            return
        }

        if (!force && prefs.getBoolean("oem_diag_dismissed", false)) return

        val stepsText = oemInfo.steps.joinToString("\n")
        val msg = "Проблемы (${issues.size}): ${issues.joinToString(", ")}.\n\n" +
            "Как исправить:\n$stepsText"
        runOnUiThread {
            AlertDialog.Builder(this, R.style.Theme_Schedule_Dialog)
                .setTitle("⚠️ ${oemInfo.manufacturer}: уведомления могут не работать")
                .setMessage(msg + "\n\nОткрыть настройки?")
                .setPositiveButton("Настройки") { _, _ -> OEMHelper.openOEMSettings(this) }
                .setNeutralButton("Не напоминать") { _, _ ->
                    prefs.edit().putBoolean("oem_diag_dismissed", true).apply()
                }
                .setNegativeButton("Позже", null)
                .show()
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open app settings", e)
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            if (!am.canScheduleExactAlarms()) {
                val prefs = getSharedPreferences("schedule_prefs", MODE_PRIVATE)
                if (prefs.getBoolean("exact_alarm_prompted", false)) return
                AlertDialog.Builder(this, R.style.Theme_Schedule_Dialog)
                    .setTitle("⏰ Точные будильники")
                    .setMessage(
                        "Для точных напоминаний нужно разрешить приложению ставить точные будильники.\n\n" +
                        "Нажмите «Разрешить» и включите опцию в настройках."
                    )
                    .setPositiveButton("Разрешить") { _, _ ->
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            intent.data = android.net.Uri.parse("package:$packageName")
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Exact alarm settings error", e)
                        }
                    }
                    .setNegativeButton("Позже", null)
                    .setOnDismissListener {
                        prefs.edit().putBoolean("exact_alarm_prompted", true).apply()
                    }
                    .show()
            }
        }
    }

    private fun checkBatteryOptimization(): Boolean {
        val prefs = getSharedPreferences("schedule_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("battery_prompted", false)) return false

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
            return true
        } else {
            prefs.edit().putBoolean("battery_prompted", true).apply()
            return false
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
        deleteMenu?.removeGroup(GROUP_CUSTOM_DELETE)
        for (key in customKeys) {
            val item = deleteMenu?.add(GROUP_CUSTOM_DELETE, Menu.NONE, Menu.NONE, "❌ $key")
            item?.setOnMenuItemClickListener { confirmDeleteType(key, key); true }
        }
        menu?.findItem(R.id.menu_delete)?.isVisible = hasSchedule || hasPersonal || hasExtended || customKeys.isNotEmpty()
        menu?.findItem(R.id.menu_reset)?.isVisible = true

        val editItem = menu?.findItem(R.id.menu_edit_mode)
        editItem?.isChecked = editModeActive
        if (editModeActive) {
            val s = android.text.SpannableString("✏️ Редактирование")
            s.setSpan(android.text.style.ForegroundColorSpan(0xFF4CAF50.toInt()), 0, s.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            editItem?.title = s
        } else {
            editItem?.title = "✏️ Редактирование"
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "Menu: ${item.title}")
        return when (item.itemId) {
            R.id.menu_export -> { showExportDialog(); true }
            R.id.menu_import -> { openFilePicker(); true }
            R.id.menu_edit_mode -> { toggleEditMode(); true }
            R.id.menu_notifications -> { logPermissionDiagnostics(force = true); true }
            R.id.menu_delete_schedule -> { confirmDeleteType("schedule", "все уроки"); true }
            R.id.menu_delete_personal -> { confirmDeleteType("personal", "все занятия"); true }
            R.id.menu_delete_extended -> { confirmDeleteType("extended", "продлёнку"); true }
            R.id.menu_reset -> { resetData(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ── Export ─────────────────────────────────────────────────────────

    private fun showExportDialog() {
        webView.evaluateJavascript(
            "(function(){ try { return JSON.stringify(Object.keys((JSON.parse(localStorage.getItem('tg_local_data')||'{}').custom)||{})); } catch(e) { return '[]'; } })()"
        ) { result ->
            val customKeys = try {
                val s = org.json.JSONTokener(result ?: "\"[]\"").nextValue().toString()
                val arr = org.json.JSONArray(s)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (e: Exception) { emptyList<String>() }
            showExportOptions(customKeys)
        }
    }

    private fun showExportOptions(customKeys: List<String>) {
        val options = mutableListOf("💾 Всё расписание (JSON)")
        val types = mutableListOf("full")
        if (hasSchedule) { options.add("📄 Только уроки (JSON)"); types.add("schedule") }
        if (hasPersonal) { options.add("🤸 Только занятия (JSON)"); types.add("personal") }
        if (hasExtended) { options.add("🎒 Только продлёнку (JSON)"); types.add("extended") }
        for (k in customKeys) { options.add("⭐ $k (JSON)"); types.add(k) }
        AlertDialog.Builder(this, R.style.Theme_Schedule_Dialog)
            .setTitle("Экспорт")
            .setItems(options.toTypedArray()) { _, which ->
                val t = types[which]
                if (t == "full") exportFullJson() else exportPart(t)
            }
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
                    if (local.custom && Object.keys(local.custom).length) out.custom = local.custom;
                    return JSON.stringify(out, null, 2);
                } catch(e) { return '{"error":"' + e.message + '"}'; }
            })()"""
        ) { result -> handleJsonResult(result, "raspisanie_2A.json") }
    }

    private fun exportPart(type: String) {
        val names = mapOf("schedule" to "Uroki_2A.json", "personal" to "Zanyatiya_2A.json", "extended" to "Prodlenka_2A.json")
        val jsType = type.replace("'", "\\'")
        webView.evaluateJavascript(
            """(function() {
                try {
                    var d = JSON.parse(localStorage.getItem('tg_local_data') || '{}');
                    var data;
                    if ('$jsType' === 'schedule') { data = d['schedule']; if (!Array.isArray(data) || !data.length) data = SCHEDULE; }
                    else if ('$jsType' === 'personal') { data = d['personal']; if (!data || !Object.keys(data).length) data = PERSONAL; }
                    else if ('$jsType' === 'extended') { data = d['extended']; if (!Array.isArray(data) || !data.length) data = EXTENDED; }
                    else data = (d.custom && d.custom['$jsType']) ? { __type: '$jsType', days: d.custom['$jsType'] } : [];
                    return JSON.stringify(data, null, 2);
                } catch(e) { return '{"error":"' + e.message + '"}'; }
            })()"""
        ) { result -> handleJsonResult(result, names[type] ?: "$type.json") }
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

    @Deprecated("Deprecated in Java")
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
        if (requestCode == 9999 && resultCode == RESULT_OK) {
            val uri = data?.getParcelableExtra<android.net.Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            pickerCallback?.invoke(uri)
        }
        if (requestCode == 9999) pickerCallback = null
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
                    } else if (typeof data === 'object' && !Array.isArray(data) && data.custom) {
                        merged.custom = merged.custom || {};
                        for (var k in data.custom) merged.custom[k] = data.custom[k];
                        what = 'Доп. расписания';
                    } else if (typeof data === 'object' && !Array.isArray(data) && data.__type && data.days) {
                        merged.custom = merged.custom || {};
                        merged.custom[data.__type] = data.days;
                        what = data.__type;
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
        webView.evaluateJavascript("toggleEditMode(); editMode ? '1' : '0'") {
            val s = it?.trim('"') ?: ""
            editModeActive = s == "1"
            runOnUiThread { invalidateOptionsMenu() }
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
        val jsType = type.replace("'", "\\'")
        webView.evaluateJavascript(
            """(function() {
                try {
                    var d = JSON.parse(localStorage.getItem('tg_local_data') || 'null') ||
                        { schedule: JSON.parse(JSON.stringify(SCHEDULE)), personal: JSON.parse(JSON.stringify(PERSONAL)), extended: JSON.parse(JSON.stringify(EXTENDED)), custom: JSON.parse(JSON.stringify(typeof CUSTOM !== 'undefined' ? CUSTOM : {})) };
                    if ('$jsType' === 'schedule') { d.schedule = []; }
                    else if ('$jsType' === 'personal') { d.personal = {}; }
                    else if ('$jsType' === 'extended') { d.extended = []; }
                    else { if (d.custom) delete d.custom['$jsType']; localStorage.removeItem('custom_$jsType'); }
                    localStorage.setItem('tg_local_data', JSON.stringify(d));
                    return 'ok';
                } catch(e) { return e.message; }
            })()"""
        ) { r ->
            val s = r?.trim('"') ?: ""
            if (s == "ok") {
                Toast.makeText(this, "Удалено!", Toast.LENGTH_SHORT).show()
                queryDataState {}
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
                webView.evaluateJavascript("try { localStorage.clear(); sessionStorage.clear(); 'ok' } catch(e) { 'err:' + e.message }") {
                    // Documented native wipe (androidx.webkit 1.14+): legacy WebStorage.deleteAllData()
                    // is a no-op on Android 12+, WebStorageCompat is the supported replacement.
                    try {
                        androidx.webkit.WebStorageCompat.deleteBrowsingData(
                            android.webkit.WebStorage.getInstance(),
                            Runnable { runOnUiThread { finishReset() } }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "WebStorage wipe error", e)
                        finishReset()
                    }
                }
            }
            .setNegativeButton("Нет", null).show()
    }

    private fun finishReset() {
        runOnUiThread {
            webView.clearCache(true)
            hasSchedule = false; hasPersonal = false; hasExtended = false; customKeys = emptyList()
            Toast.makeText(this, "Сброшено!", Toast.LENGTH_SHORT).show()
            invalidateOptionsMenu()
            webView.reload()
        }
    }

    // ── Data State ────────────────────────────────────────────────────

    private fun queryDataState(onDone: () -> Unit = {}) {
        webView.evaluateJavascript(
            """(function() {
                try {
                    var hasSch = false, hasPers = false, hasExt = false, keys = [];
                    if (typeof SCHEDULE !== 'undefined' && SCHEDULE) hasSch = SCHEDULE.some(function(d){ return d && d.lessons && d.lessons.length > 0; });
                    if (typeof PERSONAL !== 'undefined' && PERSONAL) hasPers = Object.keys(PERSONAL).some(function(k){ return Array.isArray(PERSONAL[k]) && PERSONAL[k].length > 0; });
                    if (typeof EXTENDED !== 'undefined' && EXTENDED) hasExt = EXTENDED.length > 0;
                    if (typeof CUSTOM !== 'undefined' && CUSTOM) {
                        keys = Object.keys(CUSTOM).filter(function(k){
                            return CUSTOM[k] && Object.keys(CUSTOM[k]).length > 0;
                        });
                    }
                    var editOn = localStorage.getItem('tg_edit_mode') === 'true';
                    return JSON.stringify({ f: (hasSch ? '1' : '0') + (hasPers ? '1' : '0') + (hasExt ? '1' : '0') + (editOn ? '1' : '0'), k: keys });
                } catch(e) { return '{"f":"0000","k":[]}'; }
            })()"""
        ) { result ->
            try {
                val v = org.json.JSONTokener(result ?: "").nextValue()
                val o = when (v) {
                    is org.json.JSONObject -> v
                    is String -> org.json.JSONTokener(v).nextValue() as? org.json.JSONObject
                    else -> null
                }
                val f = o?.optString("f", "0000") ?: "0000"
                if (f.length >= 4) {
                    hasSchedule = f[0] == '1'
                    hasPersonal = f[1] == '1'
                    hasExtended = f[2] == '1'
                    editModeActive = f[3] == '1'
                }
                val keys = mutableListOf<String>()
                val ka = o?.optJSONArray("k")
                if (ka != null) for (i in 0 until ka.length()) keys.add(ka.getString(i))
                customKeys = keys
                Log.d(TAG, "Data state: schedule=$hasSchedule personal=$hasPersonal extended=$hasExtended edit=$editModeActive custom=$keys")
            } catch (e: Exception) {
                Log.e(TAG, "queryDataState parse error", e)
            }
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
        Log.d(TAG, "scheduleRemindersFromJson called, json length=${json.length}")
        try {
            cancelAllReminders()

            val arr = org.json.JSONArray(json)
            Log.d(TAG, "Parsed ${arr.length()} reminders from JSON")

            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                val type = r.getString("type")
                val dayIdx = r.getInt("dayIdx")
                val time = r.getString("time")
                val subj = r.optString("subj", "")
                val mins = r.getInt("mins")
                val key = r.getString("key")

                val whenType = r.optString("when", "start")

                val parts = time.split(Regex("[–\\-]"))
                val refParts = if (whenType == "end") parts[1].split(":") else parts[0].split(":")
                val refHour = refParts[0].toInt()
                val refMin = refParts[1].toInt()

                val targetDow = when(dayIdx) {
                    0 -> java.util.Calendar.MONDAY
                    1 -> java.util.Calendar.TUESDAY
                    2 -> java.util.Calendar.WEDNESDAY
                    3 -> java.util.Calendar.THURSDAY
                    4 -> java.util.Calendar.FRIDAY
                    5 -> java.util.Calendar.SATURDAY
                    6 -> java.util.Calendar.SUNDAY
                    else -> java.util.Calendar.MONDAY
                }
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, refHour)
                    set(java.util.Calendar.MINUTE, refMin - mins)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                    val curDow = get(java.util.Calendar.DAY_OF_WEEK)
                    var diff = targetDow - curDow
                    if (diff < 0) diff += 7
                    add(java.util.Calendar.DAY_OF_MONTH, diff)
                }

                if (cal.timeInMillis <= System.currentTimeMillis()) {
                    cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                }

                val notifId = key.hashCode()
                val sound = r.optString("sound", "")
                val vibro = r.optBoolean("vibro", true)
                val repeat = r.optString("repeat", "weekly")
                val intent = Intent(this, NotificationReceiver::class.java).apply {
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
                val pending = PendingIntent.getBroadcast(
                    this, notifId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                Log.d(TAG, "Reminder[$i]: key=$key type=$type when=$whenType time=$time mins=$mins dayIdx=$dayIdx notifId=$notifId")
                Log.d(TAG, "  → Alarm time: ${cal.time} (in ${cal.timeInMillis - System.currentTimeMillis()}ms)")

                val showIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val showPending = PendingIntent.getActivity(
                    this, notifId, showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                NotificationHelper.scheduleExact(this, cal.timeInMillis, pending, showPending)
                Log.d(TAG, "  → Exact alarm SET ($repeat)")
            }

            val editor = getSharedPreferences("schedule_prefs", MODE_PRIVATE).edit()
            editor.putString("reminders_json", json)
            editor.apply()

            Log.d(TAG, "All ${arr.length()} reminders scheduled successfully")
            runOnUiThread { Toast.makeText(this, "Напоминания настроены ✓", Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) {
            Log.e(TAG, "scheduleRemindersFromJson error", e)
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
        @JavascriptInterface fun showConfirm(message: String): Boolean {
            val latch = java.util.concurrent.CountDownLatch(1)
            var result = false
            activity.runOnUiThread {
                AlertDialog.Builder(activity, R.style.Theme_Schedule_Dialog)
                    .setTitle(message)
                    .setPositiveButton("Да") { _, _ -> result = true; latch.countDown() }
                    .setNegativeButton("Нет") { _, _ -> result = false; latch.countDown() }
                    .setOnCancelListener { result = false; latch.countDown() }
                    .show()
            }
            latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
            return result
        }
        @JavascriptInterface fun syncReminders(json: String) {
            Log.d(TAG, "syncReminders called from JS, json length=${json.length}")
            try {
                val arr = org.json.JSONArray(json)
                Log.d(TAG, "syncReminders: ${arr.length()} reminders to schedule")
                activity.scheduleRemindersFromJson(json)
            } catch (e: Exception) {
                Log.e(TAG, "syncReminders parse error", e)
            }
        }
        @JavascriptInterface fun getNotificationStatus(): String {
            val nm = activity.getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            val notifOk = if (Build.VERSION.SDK_INT >= 33) {
                activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
            val channelOk = nm.getNotificationChannel(NotificationHelper.CHANNEL_ID) != null
            val am = activity.getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            val exactOk = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
            val pm = activity.getSystemService(POWER_SERVICE) as android.os.PowerManager
            val batteryOk = pm.isIgnoringBatteryOptimizations(activity.packageName)
            val status = "notif=$notifOk channel=$channelOk exact=$exactOk battery=$batteryOk"
            Log.d(TAG, "getNotificationStatus: $status")
            return status
        }
        @JavascriptInterface fun openRingtonePicker() {
            activity.runOnUiThread {
                val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_NOTIFICATION)
                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Выбери мелодию")
                }
                activity.pickerCallback = { uri ->
                    if (uri != null) {
                        val jsUri = uri.toString()
                        activity.runOnUiThread {
                            activity.webView.evaluateJavascript("window._ringtonePicked && window._ringtonePicked('$jsUri')") {}
                        }
                    }
                }
                activity.startActivityForResult(intent, 9999)
            }
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
