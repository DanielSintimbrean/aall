package dev.danields.aall.core

import org.json.JSONArray
import org.json.JSONObject

class Engine(private val device: Device, private val prefs: KeyValue) {
  private val store = Store(prefs)

  private fun clock() = device.clock()

  private fun put(key: String, value: String) {
    check(prefs.write(mapOf(key to value)))
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
    prefs.write(mapOf("challenge" to null))
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
        device.setDnd(wanted)
        store.save(decision.state, decision.state.copy(dndOwned = wanted))
      } catch (ex: Exception) {
        device.logError("Failed to change DND", ex)
        failures.add("No se ha podido cambiar No molestar.")
      }
    }
    decision.setGrayscale?.let { wanted ->
      try {
        applyGrayscale(wanted)
      } catch (ex: Exception) {
        device.logError("Failed to change grayscale", ex)
        failures.add("No se ha podido cambiar la escala de grises.")
      }
    }
    var nightLightStep: Long? = null
    if (env.permissions.grayscale) {
      try {
        nightLightStep = applyNightLight(decision.nightLight, env.clock.wall)
      } catch (ex: Exception) {
        device.logError("Failed to change Night Light", ex)
        failures.add("No se ha podido cambiar la luz nocturna.")
      }
    }
    device.showBlock(decision, env.clock)
    device.scheduleAlarm(decision.nextChange)
    device.scheduleNightLightStep(nightLightStep)
    try {
      device.showStatus(StatusNotice.of(decision, env.clock, failures))
    } catch (ex: Exception) {
      device.logError("Failed to show status notification", ex)
      failures.add("No se ha podido mostrar la notificación.")
    }
    put("effectFailures", JSONArray(failures).toString())
  }

  private fun environment(plugged: Boolean? = null): Environment {
    val foreground = device.foreground
    return Environment(
      clock = clock(),
      foreground = foreground,
      foregroundIsSystemApp = device.isSystemApp(foreground),
      foregroundApp = device.foregroundApp,
      alwaysAllowed = Rules.alwaysAllowed + device.alwaysAllowed(),
      plugged = plugged ?: device.plugged(),
      permissions = device.permissions()
    )
  }

  private fun applyGrayscale(on: Boolean) = synchronized(displayLock) {
    val enabled = device.getSecure("accessibility_display_daltonizer_enabled", 0)
    val mode = device.getSecure("accessibility_display_daltonizer", -1)
    if (on && !prefs.getBoolean("grayOwned", false)) {
      prefs.write(
        mapOf("grayBeforeEnabled" to enabled, "grayBeforeMode" to mode, "grayOwned" to true)
      )
      check(device.putSecure("accessibility_display_daltonizer", 0))
      check(device.putSecure("accessibility_display_daltonizer_enabled", 1))
    } else if (!on && prefs.getBoolean("grayOwned", false)) {
      if (enabled == 1 && mode == 0) {
        device.putSecure("accessibility_display_daltonizer", prefs.getInt("grayBeforeMode", -1))
        device.putSecure(
          "accessibility_display_daltonizer_enabled",
          prefs.getInt("grayBeforeEnabled", 0)
        )
      }
      prefs.write(mapOf("grayOwned" to false))
    }
  }

  /** Returns when the ramp next needs a change, or null when it has finished or was taken over. */
  private fun applyNightLight(ramp: NightLightRamp?, now: Long): Long? = synchronized(displayLock) {
    val raw = prefs.getString("nightLightSession", null)
    val session = raw?.let { store.parseNightLightSession(JSONObject(it)) }
    if (ramp == null && session == null) return null
    val read = NightLightSettings(
      device.getSecure("night_display_activated", 0),
      device.getSecure("night_display_color_temperature", -1),
      device.getSecure("night_display_auto_mode", -1)
    )
    val step = NightLight.advance(session, read, ramp, now, device.nightLightRange())
    // Saved before writing and removed after, so a failure can't lose the user's settings.
    val next = step.session?.let { store.nightLightSessionJson(it).toString() }
    if (next != null && next != raw) {
      check(prefs.write(mapOf("nightLightSession" to next)))
    }
    // Android reads the schedule last, so restoring it lets Android settle the final state.
    if (step.settings.temperature != read.temperature) {
      check(device.putSecure("night_display_color_temperature", step.settings.temperature))
      refreshNightLightMatrix()
    }
    if (step.settings.activated != read.activated) {
      check(device.putSecure("night_display_activated", step.settings.activated))
    }
    if (step.settings.autoMode != read.autoMode) {
      check(device.putSecure("night_display_auto_mode", step.settings.autoMode))
    }
    if (next == null) check(prefs.write(mapOf("nightLightSession" to null)))
    if (ramp == null || step.session?.yielded == true) null else NightLight.nextStep(ramp, now)
  }

  /**
   * Android's Night Light ignores a written temperature and keeps the matrix it already has, until
   * color correction is switched. With correction off, this switches it on with no correction mode
   * set, so only the matrix changes. With grayscale on, it has to leave grayscale for a moment.
   */
  private fun refreshNightLightMatrix() {
    val enabledKey = "accessibility_display_daltonizer_enabled"
    val modeKey = "accessibility_display_daltonizer"
    if (device.getSecure(enabledKey, 0) == 1) {
      check(device.putSecure(enabledKey, 0))
      check(device.putSecure(enabledKey, 1))
      return
    }
    val mode = device.getSecure(modeKey, -1)
    check(device.putSecure(modeKey, -1))
    check(device.putSecure(enabledKey, 1))
    check(device.putSecure(enabledKey, 0))
    check(device.putSecure(modeKey, mode))
  }

  private fun snapshot(d: Decision): JSONObject {
    val clock = clock()
    val p = device.permissions()
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
          .put("notifications", device.notificationsEnabled())
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
