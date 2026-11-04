package dev.danields.aall.automation

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

class Engine(private val context: Context) {
  private val store = Store(context)
  private val prefs get() = store.prefs
  private val alarm get() = context.getSystemService(AlarmManager::class.java)
  private val notification get() = context.getSystemService(NotificationManager::class.java)

  private fun clock() = Clock(
    System.currentTimeMillis(),
    SystemClock.elapsedRealtime(),
    Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1),
    ZoneId.systemDefault()
  )

  private fun put(key: String, value: String) {
    check(prefs.edit().putString(key, value).commit())
  }

  private fun update(change: (State) -> State) {
    val state = store.load()
    store.save(state, change(state))
    reconcile()
  }

  fun initialize(raw: String) {
    if (!store.hasConfig()) setConfig(raw)
  }

  fun setConfig(raw: String) = update { it.copy(config = Config.parse(raw)) }

  fun saveWeekly(raw: String) = update {
    Reconcile.saveWeekly(it, Routine.parse(JSONObject(raw))).also { saved ->
      saved.config.validated()
    }
  }

  fun overrideToday(id: String, raw: String) = update { state ->
    val r = Routine.parse(JSONObject(raw))
    require(r.kind.id == id && r.kind != RoutineKind.FOCUS)
    state.config.with(r).validated()
    val clock = clock()
    state.copy(
      overrides = state.overrides +
        (r.kind to Override(r, clock.today, Rules.overrideExpiry(r, clock.today, clock.zone)))
    )
  }

  fun book(date: String) = update { state ->
    val clock = clock()
    require(Rules.canBook(date, clock.wall, clock.zone)) {
      "Book at least 72 elapsed hours before midnight."
    }
    state.copy(daysOff = (state.daysOff + date).distinct())
  }

  fun cancel(date: String) = update { it.copy(daysOff = it.daysOff - date) }

  fun focus() = update { state ->
    require(
      state.config.routines.any {
        it.kind == RoutineKind.FOCUS && it.enabled
      }
    ) { "Enable Focus first." }
    state.copy(
      timers =
        state.timers + (Timer.FOCUS to Timer.start(clock(), state.config.focusMinutes * 60000L))
    )
  }

  fun testGray(enabled: Boolean) = update { state ->
    state.copy(
      timers = if (enabled) {
        state.timers + (Timer.GRAY_TEST to Timer.start(clock(), 10000))
      } else {
        state.timers - Timer.GRAY_TEST
      }
    )
  }

  fun challenge(): String {
    if (!prefs.contains("challenge")) {
      val (expression, answer) = Rules.challenge()
      put("challenge", JSONObject().put("expression", expression).put("answer", answer).toString())
    }
    return JSONObject(prefs.getString("challenge", "{}")!!).getString("expression")
  }

  fun solve(answer: String): Boolean {
    val raw = prefs.getString("challenge", null) ?: return false
    if (!Rules.verifies(answer, JSONObject(raw).getInt("answer"))) return false
    prefs.edit().remove("challenge").commit()
    update { it.copy(timers = it.timers + (Timer.PAUSE to Timer.start(clock(), 900000))) }
    return true
  }

  fun snapshot() = snapshot(Reconcile.decide(store.load(), environment()))

  fun reconcile(plugged: Boolean? = null) {
    val before = store.load()
    val decision = Reconcile.decide(before, environment(plugged))
    store.save(before, decision.state)
    val failures = mutableListOf<String>()
    decision.setDnd?.let { wanted ->
      try {
        applyDnd(wanted)
        store.save(decision.state, decision.state.copy(dndOwned = wanted))
      } catch (ex: Exception) {
        failures.add("DND: ${ex.message}")
      }
    }
    decision.setGrayscale?.let { wanted ->
      try {
        applyGrayscale(wanted)
      } catch (ex: Exception) {
        failures.add("Grayscale: ${ex.message}")
      }
    }
    AallAccessibilityService.instance?.update(decision)
    scheduleAlarm(decision.nextChange)
    put("effectFailures", JSONArray(failures).toString())
  }

  private fun environment(plugged: Boolean? = null): Environment {
    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val launcher = context.packageManager.resolveActivity(
      Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
      0
    )?.activityInfo?.packageName
    val dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    return Environment(
      clock = clock(),
      foreground = AallAccessibilityService.instance?.foreground ?: "",
      alwaysAllowed = Rules.alwaysAllowed + listOfNotNull(context.packageName, launcher, dialer),
      plugged = plugged ?: ((battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0),
      permissions = permissions()
    )
  }

  private fun permissions() = Permissions(
    accessibility = AallAccessibilityService.instance != null,
    dnd = notification.isNotificationPolicyAccessGranted,
    alarms = alarm.canScheduleExactAlarms(),
    grayscale = context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
      PackageManager.PERMISSION_GRANTED
  )

  private fun applyDnd(on: Boolean) = notification.setInterruptionFilter(
    if (on) {
      NotificationManager.INTERRUPTION_FILTER_PRIORITY
    } else {
      NotificationManager.INTERRUPTION_FILTER_ALL
    }
  )

  private fun applyGrayscale(on: Boolean) {
    val resolver = context.contentResolver
    val enabled = Settings.Secure.getInt(resolver, "accessibility_display_daltonizer_enabled", 0)
    val mode = Settings.Secure.getInt(resolver, "accessibility_display_daltonizer", -1)
    if (on && !prefs.getBoolean("grayOwned", false)) {
      prefs.edit().putInt(
        "grayBeforeEnabled",
        enabled
      ).putInt("grayBeforeMode", mode).putBoolean("grayOwned", true).commit()
      check(Settings.Secure.putInt(resolver, "accessibility_display_daltonizer", 0))
      check(Settings.Secure.putInt(resolver, "accessibility_display_daltonizer_enabled", 1))
    } else if (!on && prefs.getBoolean("grayOwned", false)) {
      if (enabled == 1 && mode == 0) {
        Settings.Secure.putInt(
          resolver,
          "accessibility_display_daltonizer",
          prefs.getInt("grayBeforeMode", -1)
        )
        Settings.Secure.putInt(
          resolver,
          "accessibility_display_daltonizer_enabled",
          prefs.getInt("grayBeforeEnabled", 0)
        )
      }
      prefs.edit().putBoolean("grayOwned", false).commit()
    }
  }

  private fun scheduleAlarm(next: Long) {
    val pending = PendingIntent.getBroadcast(
      context,
      0,
      Intent(context, AutomationReceiver::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    if (alarm.canScheduleExactAlarms()) {
      alarm.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        next,
        pending
      )
    } else {
      alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
    }
  }

  private fun snapshot(d: Decision): JSONObject {
    val now = System.currentTimeMillis()
    val p = permissions()
    val failures = JSONArray(prefs.getString("effectFailures", "[]")).let { a ->
      (0 until a.length()).map(a::getString)
    }
    return JSONObject()
      .put("config", d.state.config.toJson())
      .put("active", JSONArray(d.routines.map { it.kind.id }))
      .put("dayOff", d.dayOff)
      .put("daysOff", JSONArray(d.state.daysOff))
      .put("focusUntil", now + d.focusRemaining)
      .put("pauseUntil", now + d.pauseRemaining)
      .put("nextChange", d.nextChange)
      .put(
        "permissions",
        JSONObject().put(
          "accessibility",
          p.accessibility
        ).put("dnd", p.dnd).put("alarms", p.alarms).put("grayscale", p.grayscale)
      )
      .put(
        "overrides",
        JSONObject(
          d.state.overrides.entries.associate {
            it.key.id to it.value.routine.toJson()
          }
        )
      )
      .put("errors", JSONArray(failures + d.errors))
      .put("statuses", JSONObject(d.statuses.entries.associate { it.key.id to it.value.id }))
  }
}
