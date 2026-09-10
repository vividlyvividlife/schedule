package com.schedule.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object OEMHelper {
    private const val TAG = "ScheduleApp"

    data class OEMInfo(
        val name: String,
        val manufacturer: String,
        val steps: List<String>,
        val intents: List<Intent>,
        val batteryIntent: Intent?
    )

    fun getManufacturer(): String {
        return Build.MANUFACTURER?.lowercase() ?: "unknown"
    }

    fun isAggressiveOEM(): Boolean {
        val m = getManufacturer()
        return m in listOf("xiaomi", "huawei", "honor", "samsung", "oppo", "vivo", "realme", "oneplus", "meizu", "asus")
    }

    fun getOEMInfo(): OEMInfo {
        val m = getManufacturer()
        return when (m) {
            "xiaomi" -> OEMInfo(
                name = "MIUI / HyperOS",
                manufacturer = "Xiaomi",
                steps = listOf(
                    "1. Настройки → Приложения → Управление приложениями → Расписание",
                    "2. Включить «Автозапуск»",
                    "3. Настройки → Аккумулятор → Экономия энергии → Расписание → Без ограничений",
                    "4. Разрешения → Показывать на экране блокировки"
                ),
                intents = listOf(
                    Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                    Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")),
                    Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
                ),
                batteryIntent = Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"))
            )
            "samsung" -> OEMInfo(
                name = "One UI",
                manufacturer = "Samsung",
                steps = listOf(
                    "1. Настройки → Уход за устройством → Батарея",
                    "2. Ограничения фонового использования → Расписание → Не спящее",
                    "3. Настройки → Приложения → Расписание → Батарея → Без ограничений"
                ),
                intents = listOf(
                    Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
                    Intent().setComponent(ComponentName("com.samsung.android.app.cocktailbarservice", "com.samsung.android.app.cocktailbarservice.CocktailBarServiceActivity"))
                ),
                batteryIntent = Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"))
            )
            "huawei", "honor" -> OEMInfo(
                name = if (m == "honor") "Magic UI" else "EMUI",
                manufacturer = if (m == "honor") "Honor" else "Huawei",
                steps = listOf(
                    "1. Настройки → Аккумулятор → Запуск приложений → Расписание → Вручную",
                    "2. Включить все переключатели (Автозапуск, запуск в фоне, запуск вторичным)",
                    "3. Настройки → Аккумулятор → Доп. настройки → Отключить «Оптимизацию сна»"
                ),
                intents = listOf(
                    Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                    Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
                ),
                batteryIntent = Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
            )
            "oppo", "realme" -> OEMInfo(
                name = "ColorOS",
                manufacturer = "OPPO/Realme",
                steps = listOf(
                    "1. Настройки → Приложения → Управление приложениями → Расписание",
                    "2. Автозапуск → Включить",
                    "3. Настройки → Аккумулятор → Расход батареи → Фоновая активность → Разрешить",
                    "4. Доп. настройки → Отключить «Оптимизацию сна»"
                ),
                intents = listOf(
                    Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppManagerActivity")),
                    Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.privacypermissions.repository.PermissionRepositoryActivity")),
                    Intent("oppo.intent.action.AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
                ),
                batteryIntent = Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppManagerActivity"))
            )
            "vivo" -> OEMInfo(
                name = "Funtouch OS",
                manufacturer = "Vivo",
                steps = listOf(
                    "1. Настройки → Больше настроек → Приложения → Расписание",
                    "2. Разрешения → Автозапуск → Включить",
                    "3. Настройки → Батарея → Высокое потребление энергии в фоне → Включить"
                ),
                intents = listOf(
                    Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                    Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))
                ),
                batteryIntent = Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))
            )
            "oneplus" -> OEMInfo(
                name = "OxygenOS",
                manufacturer = "OnePlus",
                steps = listOf(
                    "1. Настройки → Приложения → Расписание → Батарея",
                    "2. Не ограничивать",
                    "3. Настройки → Батарея → Оптимизация батареи → Расписание → Не оптимизировать"
                ),
                intents = listOf(
                    Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.ChainLaunchSettingActivity"))
                ),
                batteryIntent = Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.ChainLaunchSettingActivity"))
            )
            else -> OEMInfo(
                name = "Стандартный Android",
                manufacturer = Build.MANUFACTURER ?: "Unknown",
                steps = listOf(
                    "1. Настройки → Приложения → Расписание → Батарея",
                    "2. Без ограничений",
                    "3. Настройки → Аккумулятор → Оптимизация батареи → Расписание → Не оптимизировать"
                ),
                intents = listOf(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                ),
                batteryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        }
    }

    fun openOEMSettings(context: Context): Boolean {
        val info = getOEMInfo()
        for (intent in info.intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent)
                    Log.d(TAG, "OEM settings opened: ${info.manufacturer}")
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "OEM intent failed: ${intent.component}")
            }
        }
        return openBatterySettings(context)
    }

    fun openBatterySettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    fun openAppSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isIgnoringBatteryOptimization(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    fun getDiagnosticSummary(context: Context): String {
        val manufacturer = getManufacturer()
        val isIgnoring = isIgnoringBatteryOptimization(context)
        val info = getOEMInfo()

        val sb = StringBuilder()
        sb.appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Прошивка: ${info.name}")
        sb.appendLine("Оптимизация батареи: ${if (isIgnoring) "✔ отключена" else "✘ включена"}")

        if (isAggressiveOEM() && !isIgnoring) {
            sb.appendLine()
            sb.appendLine("⚠️ ${info.manufacturer} требует дополнительных настроек:")
            info.steps.forEach { sb.appendLine("  $it") }
        }

        return sb.toString()
    }
}
