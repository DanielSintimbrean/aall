package dev.danields.aall.automation

import android.content.Context
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

class Store(context: Context) {
  val prefs = context.getSharedPreferences("automation", Context.MODE_PRIVATE)!!

  fun hasConfig() = prefs.contains("config")

  fun load() = State(
    config = prefs.getString("config", null)?.let(Config::parse) ?: Config.EMPTY,
    overrides = RoutineKind.entries.mapNotNull { kind ->
      prefs.getString(overrideKey(kind), null)?.let {
        kind to
          parseOverride(JSONObject(it))
      }
    }.toMap(),
    daysOff = JSONArray(prefs.getString("daysOff", "[]")).let { a ->
      (0 until a.length()).map(a::getString)
    },
    timers = timerKeys.mapNotNull { key ->
      prefs.getString(key, null)?.let {
        key to
          parseTimer(JSONObject(it))
      }
    }.toMap(),
    bedtimeDate = prefs.getString("bedtimeDate", null)?.let(LocalDate::parse),
    bedtimeUntil = prefs.getLong("bedtimeUntil", 0),
    dndOwned = prefs.getBoolean("dndOwned", false)
  )

  fun save(before: State, after: State) {
    if (before == after) return
    val edit = prefs.edit()
    if (before.config != after.config) edit.putString("config", after.config.toJson().toString())
    for (kind in RoutineKind.entries) {
      if (before.overrides[kind] != after.overrides[kind]) {
        val o = after.overrides[kind]
        if (o ==
          null
        ) {
          edit.remove(overrideKey(kind))
        } else {
          edit.putString(
            overrideKey(kind),
            JSONObject().put(
              "routine",
              o.routine.toJson()
            ).put("date", o.date.toString()).put("until", o.until).toString()
          )
        }
      }
    }
    if (before.daysOff !=
      after.daysOff
    ) {
      edit.putString("daysOff", JSONArray(after.daysOff).toString())
    }
    for (key in timerKeys) {
      if (before.timers[key] != after.timers[key]) {
        val t = after.timers[key]
        if (t ==
          null
        ) {
          edit.remove(key)
        } else {
          edit.putString(
            key,
            JSONObject().put(
              "boot",
              t.boot
            ).put("elapsed", t.elapsed).put("wall", t.wall).put("duration", t.duration).toString()
          )
        }
      }
    }
    if (before.bedtimeDate !=
      after.bedtimeDate
    ) {
      edit.putString("bedtimeDate", after.bedtimeDate?.toString())
    }
    if (before.bedtimeUntil != after.bedtimeUntil) edit.putLong("bedtimeUntil", after.bedtimeUntil)
    if (before.dndOwned != after.dndOwned) edit.putBoolean("dndOwned", after.dndOwned)
    check(edit.commit())
  }

  private val timerKeys = listOf(Timer.FOCUS, Timer.PAUSE, Timer.GRAY_TEST)

  private fun overrideKey(kind: RoutineKind) = "today.${kind.id}"

  private fun parseOverride(o: JSONObject) = Override(
    Routine.parse(o.getJSONObject("routine")),
    LocalDate.parse(o.getString("date")),
    o.getLong("until")
  )

  private fun parseTimer(t: JSONObject) =
    Timer(t.getInt("boot"), t.getLong("elapsed"), t.getLong("wall"), t.getLong("duration"))
}
