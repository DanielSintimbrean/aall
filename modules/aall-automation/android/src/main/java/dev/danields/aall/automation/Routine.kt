package dev.danields.aall.automation

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

enum class RoutineKind(val id: String) {
  ACCESS("access"), FOCUS("focus"), BEDTIME("bedtime"), GRAYSCALE("grayscale");
  companion object {
    fun fromId(id: String) = requireNotNull(entries.firstOrNull { it.id == id }) { "Unknown routine $id" }
  }
}

data class Window(val start: LocalTime, val end: LocalTime, val days: List<Int>) {
  fun toJson() = JSONObject().put("start", start.toString()).put("end", end.toString()).put("days", JSONArray(days))
  companion object {
    fun parse(w: JSONObject): Window {
      require(w.getString("start").matches(Regex("[0-2][0-9]:[0-5][0-9]")))
      val days = w.getJSONArray("days").let { a -> (0 until a.length()).map(a::getInt) }
      require(days.isNotEmpty() && days.all { it in 1..7 })
      return Window(LocalTime.parse(w.getString("start")), LocalTime.parse(w.getString("end")), days)
    }
  }
}

data class Routine(
  val kind: RoutineKind,
  val title: String,
  val enabled: Boolean,
  val windows: List<Window>,
  val apps: List<String>,
  val colorApps: List<String>,
  val earlyStart: LocalTime,
) {
  fun toJson() = JSONObject()
    .put("id", kind.id).put("title", title).put("enabled", enabled)
    .put("windows", JSONArray(windows.map { it.toJson() }))
    .put("apps", JSONArray(apps)).put("colorApps", JSONArray(colorApps))
    .put("earlyStart", earlyStart.toString())
  companion object {
    private val packageName = Regex("[A-Za-z0-9_.]+")
    private fun packages(r: JSONObject, key: String): List<String> {
      val apps = r.getJSONArray(key)
      require(apps.length() <= 1000)
      return (0 until apps.length()).map { apps.getString(it).also { app -> require(app.matches(packageName)) } }
    }
    fun parse(r: JSONObject): Routine {
      val kind = RoutineKind.fromId(r.getString("id"))
      val earlyStart = LocalTime.parse(r.getString("earlyStart"))
      require(earlyStart >= LocalTime.of(20, 0))
      val windows = r.getJSONArray("windows").let { a -> (0 until a.length()).map { Window.parse(a.getJSONObject(it)) } }
      require(windows.size <= 20)
      if (kind == RoutineKind.BEDTIME) require(windows.size == 1)
      return Routine(kind, r.getString("title"), r.getBoolean("enabled"), windows, packages(r, "apps"), packages(r, "colorApps"), earlyStart)
    }
  }
}

data class Config(val focusMinutes: Int, val routines: List<Routine>) {
  fun toJson() = JSONObject().put("version", 1).put("focusMinutes", focusMinutes).put("routines", JSONArray(routines.map { it.toJson() }))
  fun with(routine: Routine) = copy(routines = routines.map { if (it.kind == routine.kind) routine else it })
  fun validated() = apply {
    require(focusMinutes in 1..1440)
    require(routines.size == RoutineKind.entries.size && routines.map { it.kind }.toSet() == RoutineKind.entries.toSet())
  }
  companion object {
    val EMPTY = Config(50, emptyList())
    fun parse(raw: String): Config {
      val c = JSONObject(raw)
      require(c.getInt("version") == 1)
      val rs = c.getJSONArray("routines")
      return Config(c.getInt("focusMinutes"), (0 until rs.length()).map { Routine.parse(rs.getJSONObject(it)) }).validated()
    }
  }
}
