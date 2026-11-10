package dev.danields.aall.automation

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import dev.danields.aall.core.Clock
import dev.danields.aall.core.Decision
import dev.danields.aall.core.Device
import dev.danields.aall.core.Engine
import dev.danields.aall.core.KeyValue
import dev.danields.aall.core.Permissions
import dev.danields.aall.core.StatusNotice
import java.time.ZoneId

fun engine(context: Context) = Engine(AndroidDevice(context), PrefsKeyValue(prefs(context)))

fun prefs(context: Context): SharedPreferences =
  context.getSharedPreferences("automation", Context.MODE_PRIVATE)

class PrefsKeyValue(private val prefs: SharedPreferences) : KeyValue {
  override fun contains(key: String) = prefs.contains(key)

  override fun getString(key: String, fallback: String?) = prefs.getString(key, fallback)

  override fun getLong(key: String, fallback: Long) = prefs.getLong(key, fallback)

  override fun getInt(key: String, fallback: Int) = prefs.getInt(key, fallback)

  override fun getBoolean(key: String, fallback: Boolean) = prefs.getBoolean(key, fallback)

  override fun write(changes: Map<String, Any?>): Boolean {
    val edit = prefs.edit()
    for ((key, value) in changes) {
      when (value) {
        null -> edit.remove(key)
        is String -> edit.putString(key, value)
        is Long -> edit.putLong(key, value)
        is Int -> edit.putInt(key, value)
        is Boolean -> edit.putBoolean(key, value)
        else -> error("Unsupported value for $key")
      }
    }
    return edit.commit()
  }
}

class AndroidDevice(private val context: Context) : Device {
  private val alarm get() = context.getSystemService(AlarmManager::class.java)
  private val notification get() = context.getSystemService(NotificationManager::class.java)
  private val status get() = StatusNotifier(context, prefs(context))
  private val resolver get() = context.contentResolver

  override fun clock() = Clock(
    System.currentTimeMillis(),
    SystemClock.elapsedRealtime(),
    Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1),
    ZoneId.systemDefault()
  )

  override fun permissions() = Permissions(
    accessibility = AallAccessibilityService.instance != null,
    dnd = notification.isNotificationPolicyAccessGranted,
    alarms = alarm.canScheduleExactAlarms(),
    grayscale = context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
      PackageManager.PERMISSION_GRANTED
  )

  override fun plugged(): Boolean {
    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
  }

  override fun alwaysAllowed(): Set<String> {
    val launcher = context.packageManager.resolveActivity(
      Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
      0
    )?.activityInfo?.packageName
    val dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    return setOfNotNull(context.packageName, launcher, dialer)
  }

  override val foreground get() = AallAccessibilityService.instance?.foreground ?: ""
  override val foregroundApp get() = AallAccessibilityService.instance?.foregroundApp ?: ""

  override fun isSystemApp(pkg: String) = try {
    val flags = context.packageManager.getApplicationInfo(pkg, 0).flags
    flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
  } catch (_: PackageManager.NameNotFoundException) {
    false
  }

  override fun getSecure(key: String, fallback: Int) =
    Settings.Secure.getInt(resolver, key, fallback)

  override fun putSecure(key: String, value: Int) = Settings.Secure.putInt(resolver, key, value)

  override fun setDnd(on: Boolean) = notification.setInterruptionFilter(
    if (on) {
      NotificationManager.INTERRUPTION_FILTER_PRIORITY
    } else {
      NotificationManager.INTERRUPTION_FILTER_ALL
    }
  )

  override fun nightLightRange(): IntRange {
    val resources = android.content.res.Resources.getSystem()

    fun config(name: String, fallback: Int): Int {
      val id = resources.getIdentifier(name, "integer", "android")
      return if (id != 0) resources.getInteger(id) else fallback
    }
    return config(
      "config_nightDisplayColorTemperatureMin",
      2596
    )..config("config_nightDisplayColorTemperatureMax", 4082)
  }

  override fun scheduleAlarm(at: Long) {
    val pending = pending(0)
    if (alarm.canScheduleExactAlarms()) {
      alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    } else {
      alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }
  }

  /** Doesn't wake the phone, because nobody sees the screen while it sleeps. The step fires on wake. */
  override fun scheduleNightLightStep(at: Long?) {
    val pending = pending(1)
    if (at == null) {
      alarm.cancel(pending)
    } else if (alarm.canScheduleExactAlarms()) {
      alarm.setExactAndAllowWhileIdle(AlarmManager.RTC, at, pending)
    } else {
      alarm.setAndAllowWhileIdle(AlarmManager.RTC, at, pending)
    }
  }

  private fun pending(code: Int) = PendingIntent.getBroadcast(
    context,
    code,
    Intent(context, AutomationReceiver::class.java),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
  )

  override fun showBlock(decision: Decision, clock: Clock) {
    AallAccessibilityService.instance?.update(decision, clock)
  }

  override fun showStatus(notice: StatusNotice?) = status.show(notice)

  override fun notificationsEnabled() = status.enabled()

  override fun logError(message: String, error: Exception) {
    android.util.Log.e("AallAutomation", message, error)
  }
}
