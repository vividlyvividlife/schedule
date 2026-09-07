package com.schedule.app

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pendingImportContent: String? = null

    private val FILE_PICKER_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.addJavascriptInterface(AndroidBridge(this), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.webChromeClient = WebChromeClient()
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_export -> { showExportDialog(); true }
            R.id.menu_import -> { openFilePicker(); true }
            R.id.menu_edit -> { showEditDialog(); true }
            R.id.menu_delete -> { showDeleteDialog(); true }
            R.id.menu_sync -> { syncFromFile(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ── Export dialog ──────────────────────────────────────────────────

    private fun showExportDialog() {
        val options = arrayOf(
            "📄 Уроки (TXT)",
            "🤸 Личные занятия (TXT)",
            "🎒 Продлёнка (TXT)",
            "💾 Всё расписание (JSON)"
        )
        AlertDialog.Builder(this, R.style.Theme_Schedule_Dialog)
            .setTitle("Экспорт расписания")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> webView.evaluateJavascript("exportSchool()", null)
                    1 -> webView.evaluateJavascript("exportNikolPersonal()", null)
                    2 -> webView.evaluateJavascript("exportExtended()", null)
                    3 -> exportToJson()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun exportToJson() {
        webView.evaluateJavascript("""
            (function() {
                try {
                    var data = {
                        schedule: typeof SCHEDULE !== 'undefined' ? SCHEDULE : [],
                        personal: typeof PERSONAL !== 'undefined' ? PERSONAL : {},
                        extended: typeof EXTENDED !== 'undefined' ? EXTENDED : []
                    };
                    return JSON.stringify(data, null, 2);
                } catch(e) {
                    return null;
                }
            })()
        """) { result ->
            if (result != null && result != "null") {
                val json = result.removeSurrounding("\"")
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                saveAndShareFile("raspisanie_2A.json", json, "application/json")
            }
        }
    }

    // ── Import ─────────────────────────────────────────────────────────

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/json", "text/*"))
        }
        startActivityForResult(intent, FILE_PICKER_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICKER_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val content = readUriContent(uri)
                if (content != null) {
                    val fileName = getFileName(uri)
                    when {
                        fileName.endsWith(".json", true) -> importJson(content)
                        fileName.endsWith(".txt", true) -> importTxt(content)
                        else -> {
                            // Try JSON first, then TXT
                            if (content.trimStart().startsWith("{")) {
                                importJson(content)
                            } else {
                                importTxt(content)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun importJson(jsonStr: String) {
        webView.evaluateJavascript("""
            (function() {
                try {
                    var data = JSON.parse('${jsonStr.replace("'", "\\'")}');
                    if (!data.schedule) throw new Error('Неверный формат');
                    localStorage.setItem('tg_local_data', JSON.stringify(data));
                    return 'ok';
                } catch(e) {
                    return e.message;
                }
            })()
        """) { result ->
            val msg = result?.removeSurrounding("\"") ?: "Ошибка"
            if (msg == "ok") {
                Toast.makeText(this, "Импорт завершён! Обновите страницу.", Toast.LENGTH_LONG).show()
                webView.reload()
            } else {
                Toast.makeText(this, "Ошибка: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun importTxt(txtContent: String) {
        val escaped = txtContent
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
        webView.evaluateJavascript("""
            (function() {
                try {
                    var txt = '$escaped';
                    var lines = txt.split('\\n');
                    var result = { schedule: [], personal: {}, extended: [] };
                    var currentDay = -1;
                    var dayMap = { 'Понедельник':0, 'Вторник':1, 'Среда':2, 'Четверг':3, 'Пятница':4, 'Суббота':5 };
                    var isExtended = txt.includes('Продлёнка') || txt.includes('продлёнк');
                    var isPersonal = txt.includes('Личные занятия') || txt.includes('личные');
                    
                    for (var i = 0; i < lines.length; i++) {
                        var line = lines[i].trim();
                        // Detect day
                        for (var dayName in dayMap) {
                            if (line.indexOf(dayName) !== -1) {
                                currentDay = dayMap[dayName];
                                break;
                            }
                        }
                        if (currentDay === -1) continue;
                        
                        // Parse lesson line: "  1. 08:30-09:15 — Математика · каб.101"
                        var m = line.match(/(?:\\d+\\.\\s*)?(\\d{1,2}:\\d{2})\\s*[-–]\\s*(\\d{1,2}:\\d{2})\\s*[—-]\\s*(.+?)(?:\\s*·\\s*(.+))?$/);
                        if (m) {
                            var item = { time: m[1] + '-' + m[2], subj: m[3].trim() };
                            if (m[4]) item.room = m[4].trim();
                            if (isExtended) {
                                result.extended.push(item);
                            } else if (isPersonal) {
                                if (!result.personal[currentDay]) result.personal[currentDay] = [];
                                result.personal[currentDay].push(item);
                            } else {
                                item.n = result.schedule.filter(function(d){return d.lessons}).reduce(function(a,d){return a+d.lessons.length},0) + 1;
                                if (!result.schedule[currentDay]) result.schedule[currentDay] = { lessons: [] };
                                result.schedule[currentDay].lessons.push(item);
                            }
                        }
                    }
                    
                    // Fill empty days
                    for (var d = 0; d < 7; d++) {
                        if (!result.schedule[d]) result.schedule[d] = { lessons: [] };
                    }
                    
                    localStorage.setItem('tg_local_data', JSON.stringify(result));
                    return 'ok:' + result.schedule.reduce(function(a,d){return a+(d.lessons?d.lessons.length:0)},0) + ' уроков';
                } catch(e) {
                    return 'err:' + e.message;
                }
            })()
        """) { result ->
            val r = result?.removeSurrounding("\"") ?: "Ошибка"
            if (r.startsWith("ok")) {
                Toast.makeText(this, "Импорт: $r. Обновите страницу.", Toast.LENGTH_LONG).show()
                webView.reload()
            } else {
                Toast.makeText(this, "Ошибка парсинга TXT", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Edit dialog ────────────────────────────────────────────────────

    private fun showEditDialog() {
        val days = arrayOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }

        val daySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, days)
        }
        layout.addView(TextView(this).apply { text = "День:" })
        layout.addView(daySpinner)

        val numInput = EditText(this).apply {
            hint = "Номер урока (1-11)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(TextView(this).apply { text = "Урок:" })
        layout.addView(numInput)

        val subjInput = EditText(this).apply { hint = "Предмет" }
        layout.addView(TextView(this).apply { text = "Предмет:" })
        layout.addView(subjInput)

        val timeInput = EditText(this).apply { hint = "Время (08:30-09:15)" }
        layout.addView(TextView(this).apply { text = "Время:" })
        layout.addView(timeInput)

        val roomInput = EditText(this).apply { hint = "Кабинет" }
        layout.addView(TextView(this).apply { text = "Кабинет:" })
        layout.addView(roomInput)

        AlertDialog.Builder(this, R.style.Theme_Schedule_Dialog)
            .setTitle("Редактировать урок")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                val day = daySpinner.selectedItemPosition
                val num = numInput.text.toString().toIntOrNull()
                val subj = subjInput.text.toString().trim()
                val time = timeInput.text.toString().trim()
                val room = roomInput.text.toString().trim()
                if (num != null && subj.isNotEmpty()) {
                    editLesson(day, num, subj, time, room)
                } else {
                    Toast.makeText(this, "Заполните номер и предмет", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun editLesson(day: Int, num: Int, subj: String, time: String, room: String) {
        val escaped = subj.replace("'", "\\'").replace("\\", "\\\\")
        val escRoom = room.replace("'", "\\'").replace("\\", "\\\\")
        webView.evaluateJavascript("""
            (function() {
                try {
                    var data = JSON.parse(localStorage.getItem('tg_local_data') || '{}');
                    if (!data.schedule) data.schedule = [];
                    while (data.schedule.length <= $day) data.schedule.push({ lessons: [] });
                    var lessons = data.schedule[$day].lessons || [];
                    var idx = lessons.findIndex(function(l) { return l.n === $num; });
                    var item = { n: $num, subj: '$escaped', time: '$time' };
                    if ('$escRoom') item.room = '$escRoom';
                    if (idx >= 0) {
                        lessons[idx] = item;
                    } else {
                        lessons.push(item);
                        lessons.sort(function(a,b) { return a.n - b.n; });
                    }
                    data.schedule[$day].lessons = lessons;
                    localStorage.setItem('tg_local_data', JSON.stringify(data));
                    return 'ok';
                } catch(e) { return e.message; }
            })()
        """) { result ->
            val r = result?.removeSurrounding("\"") ?: "Ошибка"
            if (r == "ok") {
                Toast.makeText(this, "Урок обновлён!", Toast.LENGTH_SHORT).show()
                webView.reload()
            } else {
                Toast.makeText(this, "Ошибка: $r", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Delete dialog ──────────────────────────────────────────────────

    private fun showDeleteDialog() {
        val days = arrayOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }

        val daySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, days)
        }
        layout.addView(TextView(this).apply { text = "День:" })
        layout.addView(daySpinner)

        val numInput = EditText(this).apply {
            hint = "Номер урока для удаления"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(TextView(this).apply { text = "Урок №:" })
        layout.addView(numInput)

        AlertDialog.Builder(this, R.style.Theme_Schedule_Dialog)
            .setTitle("Удалить урок")
            .setView(layout)
            .setPositiveButton("Удалить") { _, _ ->
                val day = daySpinner.selectedItemPosition
                val num = numInput.text.toString().toIntOrNull()
                if (num != null) {
                    deleteLesson(day, num)
                } else {
                    Toast.makeText(this, "Введите номер урока", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteLesson(day: Int, num: Int) {
        webView.evaluateJavascript("""
            (function() {
                try {
                    var data = JSON.parse(localStorage.getItem('tg_local_data') || '{}');
                    if (!data.schedule || !data.schedule[$day]) return 'Нет данных';
                    var lessons = data.schedule[$day].lessons || [];
                    var idx = lessons.findIndex(function(l) { return l.n === $num; });
                    if (idx < 0) return 'Урок №$num не найден';
                    lessons.splice(idx, 1);
                    data.schedule[$day].lessons = lessons;
                    localStorage.setItem('tg_local_data', JSON.stringify(data));
                    return 'ok';
                } catch(e) { return e.message; }
            })()
        """) { result ->
            val r = result?.removeSurrounding("\"") ?: "Ошибка"
            if (r == "ok") {
                Toast.makeText(this, "Урок удалён!", Toast.LENGTH_SHORT).show()
                webView.reload()
            } else {
                Toast.makeText(this, r, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Sync from file ─────────────────────────────────────────────────

    private fun syncFromFile() {
        val localFile = File(filesDir, "exports/raspisanie_2A.json")
        if (!localFile.exists()) {
            Toast.makeText(this, "Нет локальной копии. Сначала экспортируйте.", Toast.LENGTH_SHORT).show()
            return
        }
        val content = localFile.readText(Charsets.UTF_8)
        importJson(content)
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun readUriContent(uri: Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { it.readText() }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка чтения файла: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "unknown.txt"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIdx >= 0) {
                name = cursor.getString(nameIdx)
            }
        }
        return name
    }

    private fun saveAndShareFile(filename: String, content: String, mimeType: String) {
        try {
            val dir = File(filesDir, "exports")
            dir.mkdirs()
            val file = File(dir, filename)
            FileOutputStream(file).use { it.write(content.toByteArray(Charsets.UTF_8)) }

            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, filename.removeSuffix(".txt").removeSuffix(".json"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Экспорт расписания"))
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // ── Bridge ─────────────────────────────────────────────────────────

    class AndroidBridge(private val activity: MainActivity) {

        @JavascriptInterface
        fun exportTxt(filename: String, content: String) {
            activity.runOnUiThread {
                activity.saveAndShareFile(filename, content, "text/plain")
            }
        }

        @JavascriptInterface
        fun showToast(message: String) {
            activity.runOnUiThread {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
