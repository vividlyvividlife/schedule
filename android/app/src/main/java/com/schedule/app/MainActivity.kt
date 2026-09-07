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
import android.webkit.WebResourceResponse
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
import androidx.webkit.WebViewAssetLoader
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val FILE_PICKER_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

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
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request?.url!!)
            }
        }

        webView.webChromeClient = WebChromeClient()
        webView.loadUrl("https://appassets.androidplatform.net/index.html")
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
            R.id.menu_reset -> { resetData(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ── Export ─────────────────────────────────────────────────────────

    private fun showExportDialog() {
        val options = arrayOf(
            "💾 Всё расписание (JSON — для резервной копии)",
            "📄 Уроки (TXT — для чтения)"
        )
        AlertDialog.Builder(this, R.style.Theme_Schedule_Dialog)
            .setTitle("Экспорт")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportFullJson()
                    1 -> webView.evaluateJavascript("exportSchool()", null)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun exportFullJson() {
        webView.evaluateJavascript(
            """(function() {
                try {
                    var local = localStorage.getItem('tg_local_data');
                    if (local) {
                        var ld = JSON.parse(local);
                        if (ld.schedule) SCHEDULE = ld.schedule;
                        if (ld.personal) PERSONAL = ld.personal;
                        if (ld.extended) EXTENDED = ld.extended;
                    }
                    var out = { schedule: SCHEDULE, personal: PERSONAL, extended: EXTENDED };
                    return JSON.stringify(out, null, 2);
                } catch(e) { return '{"error":"' + e.message + '"}'; }
            })()"""
        ) { result ->
            if (result != null && result != "null") {
                val json = result.removeSurrounding("\"")
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                saveAndShareFile("raspisanie_2A.json", json, "application/json")
                Toast.makeText(this, "JSON экспортирован!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Import ─────────────────────────────────────────────────────────

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, FILE_PICKER_REQUEST)
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
            val r = result?.removeSurrounding("\"") ?: "Ошибка"
            if (r.startsWith("ok")) {
                val what = r.removePrefix("ok:")
                Toast.makeText(this, "$what импортировано! Перезапускаю...", Toast.LENGTH_SHORT).show()
                webView.reload()
            } else {
                Toast.makeText(this, "Ошибка: $r", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Edit ───────────────────────────────────────────────────────────

    private fun showEditDialog() {
        val days = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(60, 40, 60, 20) }
        val daySpinner = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, days) }
        layout.addView(TextView(this).apply { text = "День:" }); layout.addView(daySpinner)
        val numInput = EditText(this).apply { hint = "№ урока"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        layout.addView(TextView(this).apply { text = "Урок:" }); layout.addView(numInput)
        val subjInput = EditText(this).apply { hint = "Предмет" }
        layout.addView(TextView(this).apply { text = "Предмет:" }); layout.addView(subjInput)
        val timeInput = EditText(this).apply { hint = "08:30-09:15" }
        layout.addView(TextView(this).apply { text = "Время:" }); layout.addView(timeInput)
        val roomInput = EditText(this).apply { hint = "Кабинет" }
        layout.addView(TextView(this).apply { text = "Кабинет:" }); layout.addView(roomInput)

        AlertDialog.Builder(this, R.style.Theme_Schedule_Dialog)
            .setTitle("Редактировать урок")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                val day = daySpinner.selectedItemPosition
                val num = numInput.text.toString().toIntOrNull()
                val subj = subjInput.text.toString().trim()
                val time = timeInput.text.toString().trim()
                val room = roomInput.text.toString().trim()
                if (num != null && subj.isNotEmpty()) editLesson(day, num, subj, time, room)
                else Toast.makeText(this, "Номер + предмет обязательны", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null).show()
    }

    private fun editLesson(day: Int, num: Int, subj: String, time: String, room: String) {
        val es = subj.replace("'", "\\'").replace("\\", "\\\\")
        val er = room.replace("'", "\\'").replace("\\", "\\\\")
        webView.evaluateJavascript(
            """(function() {
                try {
                    var d = JSON.parse(localStorage.getItem('tg_local_data') || '{"schedule":[]}');
                    if (!d.schedule) d.schedule = [];
                    while (d.schedule.length <= $day) d.schedule.push({lessons:[]});
                    var ls = d.schedule[$day].lessons || [];
                    var i = ls.findIndex(function(l){return l.n===$num;});
                    var item = {n:$num, subj:'$es', time:'$time'};
                    if ('$er') item.room='$er';
                    if (i>=0) ls[i]=item; else {ls.push(item); ls.sort(function(a,b){return a.n-b.n;});}
                    d.schedule[$day].lessons = ls;
                    localStorage.setItem('tg_local_data', JSON.stringify(d));
                    return 'ok';
                } catch(e) { return e.message; }
            })()"""
        ) { r ->
            val s = r?.removeSurrounding("\"") ?: ""
            if (s == "ok") { Toast.makeText(this, "Сохранено!", Toast.LENGTH_SHORT).show(); webView.reload() }
            else Toast.makeText(this, "Ошибка: $s", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Delete ─────────────────────────────────────────────────────────

    private fun showDeleteDialog() {
        val days = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(60, 40, 60, 20) }
        val daySpinner = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, days) }
        layout.addView(TextView(this).apply { text = "День:" }); layout.addView(daySpinner)
        val numInput = EditText(this).apply { hint = "№ урока"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        layout.addView(TextView(this).apply { text = "Урок:" }); layout.addView(numInput)

        AlertDialog.Builder(this, R.style.Theme_Schedule_Dialog)
            .setTitle("Удалить урок")
            .setView(layout)
            .setPositiveButton("Удалить") { _, _ ->
                val day = daySpinner.selectedItemPosition
                val num = numInput.text.toString().toIntOrNull()
                if (num != null) deleteLesson(day, num)
                else Toast.makeText(this, "Введите номер", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null).show()
    }

    private fun deleteLesson(day: Int, num: Int) {
        webView.evaluateJavascript(
            """(function() {
                try {
                    var d = JSON.parse(localStorage.getItem('tg_local_data') || '{"schedule":[]}');
                    if (!d.schedule || !d.schedule[$day]) return 'no data';
                    var ls = d.schedule[$day].lessons || [];
                    var i = ls.findIndex(function(l){return l.n===$num;});
                    if (i<0) return 'not found';
                    ls.splice(i, 1);
                    d.schedule[$day].lessons = ls;
                    localStorage.setItem('tg_local_data', JSON.stringify(d));
                    return 'ok';
                } catch(e) { return e.message; }
            })()"""
        ) { r ->
            val s = r?.removeSurrounding("\"") ?: ""
            if (s == "ok") { Toast.makeText(this, "Удалено!", Toast.LENGTH_SHORT).show(); webView.reload() }
            else Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
        }
    }

    // ── Sync ───────────────────────────────────────────────────────────

    private fun syncFromFile() {
        val f = File(filesDir, "exports/raspisanie_2A.json")
        if (!f.exists()) { Toast.makeText(this, "Сначала экспортируйте JSON", Toast.LENGTH_SHORT).show(); return }
        importJson(f.readText(Charsets.UTF_8))
    }

    private fun resetData() {
        AlertDialog.Builder(this, R.style.Theme_Schedule_Dialog)
            .setTitle("Сбросить?")
            .setMessage("Удалить все правки и вернуть оригинальное расписание?")
            .setPositiveButton("Да") { _, _ ->
                webView.evaluateJavascript("localStorage.removeItem('tg_local_data'); 'ok'") {
                    Toast.makeText(this, "Сброшено!", Toast.LENGTH_SHORT).show()
                    webView.reload()
                }
            }
            .setNegativeButton("Нет", null).show()
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

    class AndroidBridge(private val activity: MainActivity) {
        @JavascriptInterface fun exportTxt(filename: String, content: String) {
            activity.runOnUiThread { activity.saveAndShareFile(filename, content, "text/plain") }
        }
        @JavascriptInterface fun showToast(message: String) {
            activity.runOnUiThread { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show() }
        }
    }
}
