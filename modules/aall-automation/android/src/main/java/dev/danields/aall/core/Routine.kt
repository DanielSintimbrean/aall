package dev.danields.aall.core

import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

enum class AppRule { NONE, BLOCK_LISTED, ALLOW_LISTED }

enum class RoutineStatus(val id: String) {
  DISABLED("disabled"),
  NEEDS_SETUP("needsSetup"),
  ACTIVE("active"),
  READY("ready")
}

enum class RoutineKind(
  val id: String,
  val appRule: AppRule,
  val usesDnd: Boolean,
  val usesGrayscale: Boolean,
  /** Restrictions need notice to turn off and yield to a pause or a day off. */
  val restriction: Boolean = true
) {
  ACCESS("access", AppRule.BLOCK_LISTED, usesDnd = false, usesGrayscale = false),
  FOCUS("focus", AppRule.BLOCK_LISTED, usesDnd = true, usesGrayscale = true),
  BEDTIME("bedtime", AppRule.ALLOW_LISTED, usesDnd = true, usesGrayscale = true),
  GRAYSCALE("grayscale", AppRule.NONE, usesDnd = false, usesGrayscale = true),
  NIGHT_LIGHT(
    "nightLight",
    AppRule.NONE,
    usesDnd = false,
    usesGrayscale = false,
    restriction = false
  );

  val title: String get() = when (this) {
    ACCESS -> "Acceso a aplicaciones"
    FOCUS -> "Concentración"
    BEDTIME -> "Descanso"
    GRAYSCALE -> "Escala de grises"
    NIGHT_LIGHT -> "Luz nocturna"
  }

  fun runs(scheduled: Boolean, focusRemaining: Long, earlyUntil: Long, now: Long) = when (this) {
    FOCUS -> focusRemaining > 0
    ACCESS -> !scheduled
    BEDTIME -> scheduled || earlyUntil > now
    GRAYSCALE, NIGHT_LIGHT -> scheduled
  }

  companion object {
    fun fromId(id: String) =
      requireNotNull(entries.firstOrNull { it.id == id }) { "Unknown routine $id" }
  }
}

data class Window(val start: LocalTime, val end: LocalTime, val days: List<Int>) {
  fun toJson() = JSONObject().put(
    "start",
    start.toString()
  ).put("end", end.toString()).put("days", JSONArray(days))

  companion object {
    fun parse(w: JSONObject): Window {
      require(w.getString("start").matches(Regex("[0-2][0-9]:[0-5][0-9]")))
      val days = w.getJSONArray("days").let { a -> (0 until a.length()).map(a::getInt) }
      require(days.isNotEmpty() && days.all { it in 1..7 })
      return Window(
        LocalTime.parse(w.getString("start")),
        LocalTime.parse(w.getString("end")),
        days
      )
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
  val rampMinutes: Int = 90,
  val finalIntensity: Int = 50
) {
  fun blocks(pkg: String) = when (kind.appRule) {
    AppRule.NONE -> false
    AppRule.BLOCK_LISTED -> pkg in apps
    AppRule.ALLOW_LISTED -> pkg !in apps
  }

  fun requestsGrayscale(foreground: String) = kind.usesGrayscale && foreground !in colorApps

  fun needsAccessibility() = kind.appRule != AppRule.NONE || colorApps.isNotEmpty()

  fun needsSetup(p: Permissions) = !p.alarms ||
    (needsAccessibility() && !p.accessibility) ||
    ((kind.usesGrayscale || kind == RoutineKind.NIGHT_LIGHT) && !p.grayscale) ||
    (kind.usesDnd && !p.dnd)

  fun status(p: Permissions, running: Boolean) = when {
    !enabled -> RoutineStatus.DISABLED
    needsSetup(p) -> RoutineStatus.NEEDS_SETUP
    running -> RoutineStatus.ACTIVE
    else -> RoutineStatus.READY
  }

  fun toJson() = JSONObject()
    .put("id", kind.id).put("title", title).put("enabled", enabled)
    .put("windows", JSONArray(windows.map { it.toJson() }))
    .put("apps", JSONArray(apps)).put("colorApps", JSONArray(colorApps))
    .put("earlyStart", earlyStart.toString())
    .put("rampMinutes", rampMinutes).put("finalIntensity", finalIntensity)

  companion object {
    private val packageName = Regex("[A-Za-z0-9_.]+")

    private fun packages(r: JSONObject, key: String): List<String> {
      val apps = r.getJSONArray(key)
      require(apps.length() <= 1000)
      return (0 until apps.length()).map {
        apps.getString(it).also { app -> require(app.matches(packageName)) }
      }
    }

    fun parse(r: JSONObject): Routine {
      val kind = RoutineKind.fromId(r.getString("id"))
      val earlyStart = LocalTime.parse(r.getString("earlyStart"))
      require(earlyStart >= LocalTime.of(20, 0))
      val windows = r.getJSONArray("windows").let { a ->
        (0 until a.length()).map { Window.parse(a.getJSONObject(it)) }
      }
      require(windows.size <= 20)
      if (kind == RoutineKind.BEDTIME || kind == RoutineKind.NIGHT_LIGHT) {
        require(windows.size == 1)
      }
      val rampMinutes = r.optInt("rampMinutes", 90)
      val finalIntensity = r.optInt("finalIntensity", 50)
      require(rampMinutes in 15..240 && finalIntensity in 1..100)
      return Routine(
        kind,
        kind.title,
        r.getBoolean("enabled"),
        windows,
        packages(r, "apps"),
        packages(r, "colorApps"),
        earlyStart,
        rampMinutes,
        finalIntensity
      )
    }
  }
}

data class Config(val focusMinutes: Int, val routines: List<Routine>) {
  fun toJson() = JSONObject().put("version", 1).put("focusMinutes", focusMinutes).put(
    "routines",
    JSONArray(
      routines.map {
        it.toJson()
      }
    )
  )

  fun with(routine: Routine) = copy(
    routines = routines.map {
      if (it.kind == routine.kind) {
        routine
      } else {
        it
      }
    }
  )

  fun validated() = apply {
    require(focusMinutes in 1..1440)
    require(
      routines.size == RoutineKind.entries.size &&
        routines.map { it.kind }.toSet() == RoutineKind.entries.toSet()
    )
  }

  companion object {
    val EMPTY = Config(50, emptyList())

    private val defaultNightLight = Routine(
      RoutineKind.NIGHT_LIGHT,
      RoutineKind.NIGHT_LIGHT.title,
      false,
      listOf(Window(LocalTime.of(21, 0), LocalTime.of(7, 0), listOf(1, 2, 3, 4, 5, 6, 7))),
      emptyList(),
      emptyList(),
      LocalTime.of(20, 0)
    )

    fun parse(raw: String): Config {
      val c = JSONObject(raw)
      require(c.getInt("version") == 1)
      val rs = c.getJSONArray("routines")
      val routines = (0 until rs.length()).map { Routine.parse(rs.getJSONObject(it)) }
      // Configs saved before Night Light existed lack it, and updating must keep them.
      val added = listOf(defaultNightLight).filter { new -> routines.none { it.kind == new.kind } }
      return Config(c.getInt("focusMinutes"), routines + added).validated()
    }
  }
}
