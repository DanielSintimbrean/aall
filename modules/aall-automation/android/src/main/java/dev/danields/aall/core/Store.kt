package dev.danields.aall.core

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/** Typed like SharedPreferences, because the phone already holds values of these types. */
interface KeyValue {
  fun contains(key: String): Boolean

  fun getString(key: String, fallback: String?): String?

  fun getLong(key: String, fallback: Long): Long

  fun getInt(key: String, fallback: Int): Int

  fun getBoolean(key: String, fallback: Boolean): Boolean

  /** Writes String, Long, Int or Boolean values at once, and removes keys mapped to null. */
  fun write(changes: Map<String, Any?>): Boolean
}

class Store(private val prefs: KeyValue) {
  fun hasConfig() = prefs.contains("config")

  fun load() = State(
    config = prefs.getString("config", null)?.let(Config::parse) ?: Config.EMPTY,
    overrides = RoutineKind.entries.mapNotNull { kind ->
      prefs.getString(overrideKey(kind), null)?.let {
        kind to parseOverride(JSONObject(it))
      }
    }.toMap(),
    daysOff = JSONArray(prefs.getString("daysOff", "[]")).let { a ->
      (0 until a.length()).map(a::getString)
    },
    timers = timerKeys.mapNotNull { key ->
      prefs.getString(key, null)?.let {
        key to parseTimer(JSONObject(it))
      }
    }.toMap(),
    bedtimeDate = prefs.getString("bedtimeDate", null)?.let(LocalDate::parse),
    bedtimeUntil = prefs.getLong("bedtimeUntil", 0),
    dndOwned = prefs.getBoolean("dndOwned", false)
  )

  fun save(before: State, after: State) {
    if (before == after) return
    val edit = mutableMapOf<String, Any?>()
    if (before.config != after.config) edit["config"] = after.config.toJson().toString()
    for (kind in RoutineKind.entries) {
      if (before.overrides[kind] != after.overrides[kind]) {
        val o = after.overrides[kind]
        if (o == null) {
          edit[overrideKey(kind)] = null
        } else {
          edit[overrideKey(kind)] =
            JSONObject().put(
              "routine",
              o.routine.toJson()
            ).put("date", o.date.toString()).put("until", o.until).toString()
        }
      }
    }
    if (before.daysOff != after.daysOff) {
      edit["daysOff"] = JSONArray(after.daysOff).toString()
    }
    for (key in timerKeys) {
      if (before.timers[key] != after.timers[key]) {
        val t = after.timers[key]
        if (t == null) {
          edit[key] = null
        } else {
          edit[key] =
            JSONObject().put(
              "boot",
              t.boot
            ).put("elapsed", t.elapsed).put("wall", t.wall).put("duration", t.duration).toString()
        }
      }
    }
    if (before.bedtimeDate != after.bedtimeDate) {
      edit["bedtimeDate"] = after.bedtimeDate?.toString()
    }
    if (before.bedtimeUntil != after.bedtimeUntil) edit["bedtimeUntil"] = after.bedtimeUntil
    if (before.dndOwned != after.dndOwned) edit["dndOwned"] = after.dndOwned
    check(prefs.write(edit))
  }

  fun nightLightSessionJson(s: NightLightSession) = JSONObject()
    .put("startedAt", s.startedAt)
    .put("before", nightLightJson(s.before))
    .put("startTemperature", s.startTemperature)
    .put("wrote", nightLightJson(s.wrote))
    .put("yielded", s.yielded)

  fun parseNightLightSession(o: JSONObject) = NightLightSession(
    o.optLong("startedAt"),
    parseNightLight(o.getJSONObject("before")),
    o.getInt("startTemperature"),
    parseNightLight(o.getJSONObject("wrote")),
    o.getBoolean("yielded")
  )

  private fun nightLightJson(s: NightLightSettings) = JSONObject()
    .put("activated", s.activated).put("temperature", s.temperature).put("autoMode", s.autoMode)

  private fun parseNightLight(o: JSONObject) =
    NightLightSettings(o.getInt("activated"), o.getInt("temperature"), o.getInt("autoMode"))

  private val timerKeys =
    listOf(Timer.FOCUS, Timer.PAUSE, Timer.GRAY_TEST) + RoutineKind.entries.map(Timer::turnOff)

  private fun overrideKey(kind: RoutineKind) = "today.${kind.id}"

  private fun parseOverride(o: JSONObject) = Override(
    Routine.parse(o.getJSONObject("routine")),
    LocalDate.parse(o.getString("date")),
    o.getLong("until")
  )

  private fun parseTimer(t: JSONObject) =
    Timer(t.getInt("boot"), t.getLong("elapsed"), t.getLong("wall"), t.getLong("duration"))
}
