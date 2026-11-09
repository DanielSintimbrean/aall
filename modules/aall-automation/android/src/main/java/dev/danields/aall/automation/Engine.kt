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
  private val status get() = StatusNotifier(context, prefs)
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
    store.save(state, change(Reconcile.finishTurnOffs(state, clock())))
    reconcile()
  }

  fun initialize(raw: String) {
    if (!store.hasConfig()) update { it.copy(config = Config.parse(raw)) }
  }

  fun setFocusMinutes(minutes: Int) = update {
    it.copy(config = it.config.copy(focusMinutes = minutes).validated())
  }

  fun saveWeekly(raw: String) = update {
    Reconcile.saveWeekly(it, Routine.parse(JSONObject(raw))).also { saved ->
      saved.config.validated()
    }
  }

  fun overrideToday(id: String, raw: String) = update { state ->
    val r = Routine.parse(JSONObject(raw))
    require(r.kind.id == id && r.kind != RoutineKind.FOCUS)
    Reconcile.requireNoInstantTurnOff(state, r)
    state.config.with(r).validated()
    val clock = clock()
    state.copy(
      overrides = state.overrides +
        (r.kind to Override(r, clock.today, Rules.overrideExpiry(r, clock.today, clock.zone)))
    )
  }

  fun requestTurnOff(id: String) = update {
    Reconcile.requestTurnOff(it, RoutineKind.fromId(id), clock())
  }

  fun cancelTurnOff(id: String) = update {
    Reconcile.cancelTurnOff(it, RoutineKind.fromId(id))
  }

  fun book(date: String) = update { state ->
    val clock = clock()
    require(Rules.canBook(date, clock.wall, clock.zone)) {
      "Reserva con al menos 72 horas de antelación a medianoche."
    }
    state.copy(daysOff = (state.daysOff + date).distinct())
  }

  fun cancel(date: String) = update { it.copy(daysOff = it.daysOff - date) }

  fun focus() = update { state ->
    require(
      state.config.routines.any {
        it.kind == RoutineKind.FOCUS && it.enabled
      }
    ) { "Activa primero Concentración." }
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
    val env = environment(plugged)
    val decision = Reconcile.decide(before, env)
    store.save(before, decision.state)
    val failures = mutableListOf<String>()
    decision.setDnd?.let { wanted ->
      try {
        applyDnd(wanted)
        store.save(decision.state, decision.state.copy(dndOwned = wanted))
      } catch (ex: Exception) {
        android.util.Log.e("AallAutomation", "Failed to change DND", ex)
        failures.add("No se ha podido cambiar No molestar.")
      }
    }
    decision.setGrayscale?.let { wanted ->
      try {
        applyGrayscale(wanted)
      } catch (ex: Exception) {
        android.util.Log.e("AallAutomation", "Failed to change grayscale", ex)
        failures.add("No se ha podido cambiar la escala de grises.")
      }
    }
    var nightLightStep: Long? = null
    if (env.permissions.grayscale) {
      try {
        nightLightStep = applyNightLight(decision.nightLight, env.clock.wall)
      } catch (ex: Exception) {
        android.util.Log.e("AallAutomation", "Failed to change Night Light", ex)
        failures.add("No se ha podido cambiar la luz nocturna.")
      }
    }
    AallAccessibilityService.instance?.update(decision, env.clock)
    scheduleAlarm(decision.nextChange)
    scheduleNightLightStep(nightLightStep)
    try {
      status.show(StatusNotice.of(decision, env.clock, failures))
    } catch (ex: Exception) {
      android.util.Log.e("AallAutomation", "Failed to show status notification", ex)
      failures.add("No se ha podido mostrar la notificación.")
    }
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
      foregroundApp = AallAccessibilityService.instance?.foregroundApp ?: "",
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

  private fun applyGrayscale(on: Boolean) = synchronized(displayLock) {
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

  /** Returns when the ramp next needs a change, or null when it has finished or was taken over. */
  private fun applyNightLight(ramp: NightLightRamp?, now: Long): Long? = synchronized(displayLock) {
    val raw = prefs.getString("nightLightSession", null)
    val session = raw?.let { store.parseNightLightSession(JSONObject(it)) }
    if (ramp == null && session == null) return null
    val resolver = context.contentResolver
    val read = NightLightSettings(
      Settings.Secure.getInt(resolver, "night_display_activated", 0),
      Settings.Secure.getInt(resolver, "night_display_color_temperature", -1),
      Settings.Secure.getInt(resolver, "night_display_auto_mode", -1)
    )
    val step = NightLight.advance(session, read, ramp, now, nightLightRange())
    // Saved before writing and removed after, so a failure can't lose the user's settings.
    val next = step.session?.let { store.nightLightSessionJson(it).toString() }
    if (next != null && next != raw) {
      check(prefs.edit().putString("nightLightSession", next).commit())
    }
    // Android reads the schedule last, so restoring it lets Android settle the final state.
    if (step.settings.temperature != read.temperature) {
      check(
        Settings.Secure.putInt(
          resolver,
          "night_display_color_temperature",
          step.settings.temperature
        )
      )
      refreshNightLightMatrix()
    }
    if (step.settings.activated != read.activated) {
      check(Settings.Secure.putInt(resolver, "night_display_activated", step.settings.activated))
    }
    if (step.settings.autoMode != read.autoMode) {
      check(Settings.Secure.putInt(resolver, "night_display_auto_mode", step.settings.autoMode))
    }
    if (next == null) check(prefs.edit().remove("nightLightSession").commit())
    if (ramp == null || step.session?.yielded == true) null else NightLight.nextStep(ramp, now)
  }

  /**
   * Android's Night Light ignores a written temperature and keeps the matrix it already has, until
   * color correction is switched. With correction off, this switches it on with no correction mode
   * set, so only the matrix changes. With grayscale on, it has to leave grayscale for a moment.
   */
  private fun refreshNightLightMatrix() {
    val resolver = context.contentResolver
    val enabledKey = "accessibility_display_daltonizer_enabled"
    val modeKey = "accessibility_display_daltonizer"
    if (Settings.Secure.getInt(resolver, enabledKey, 0) == 1) {
      check(Settings.Secure.putInt(resolver, enabledKey, 0))
      check(Settings.Secure.putInt(resolver, enabledKey, 1))
      return
    }
    val mode = Settings.Secure.getInt(resolver, modeKey, -1)
    check(Settings.Secure.putInt(resolver, modeKey, -1))
    check(Settings.Secure.putInt(resolver, enabledKey, 1))
    check(Settings.Secure.putInt(resolver, enabledKey, 0))
    check(Settings.Secure.putInt(resolver, modeKey, mode))
  }

  private fun nightLightRange(): IntRange {
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

  /** Doesn't wake the phone, because nobody sees the screen while it sleeps. The step fires on wake. */
  private fun scheduleNightLightStep(next: Long?) {
    val pending = PendingIntent.getBroadcast(
      context,
      1,
      Intent(context, AutomationReceiver::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    if (next == null) {
      alarm.cancel(pending)
    } else if (alarm.canScheduleExactAlarms()) {
      alarm.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pending)
    } else {
      alarm.setAndAllowWhileIdle(AlarmManager.RTC, next, pending)
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
    val clock = clock()
    val p = permissions()
    val failures = JSONArray(prefs.getString("effectFailures", "[]")).let { a ->
      (0 until a.length()).map(a::getString)
    }
    return JSONObject()
      .put("config", d.state.config.toJson())
      .put("active", JSONArray(d.routines.map { it.kind.id }))
      .put("dayOff", d.dayOff)
      .put("daysOff", JSONArray(d.state.daysOff))
      .put("earliestDayOff", Rules.earliestDayOff(clock.wall, clock.zone).toString())
      .put("focusUntil", clock.wall + d.focusRemaining)
      .put(
        "turningOff",
        JSONObject(
          RoutineKind.entries.mapNotNull { kind ->
            d.state.timers[Timer.turnOff(kind)]?.let { kind.id to clock.wall + it.remaining(clock) }
          }.toMap()
        )
      )
      .put("pauseUntil", clock.wall + d.pauseRemaining)
      .put("nextChange", d.nextChange)
      .put(
        "permissions",
        JSONObject().put(
          "accessibility",
          p.accessibility
        ).put("dnd", p.dnd).put("alarms", p.alarms).put("grayscale", p.grayscale)
          .put("notifications", status.enabled())
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

private val displayLock = Any()
