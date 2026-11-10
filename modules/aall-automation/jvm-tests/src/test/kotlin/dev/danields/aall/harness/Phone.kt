package dev.danields.aall.harness

import dev.danields.aall.core.Clock
import dev.danields.aall.core.Config
import dev.danields.aall.core.Engine
import dev.danields.aall.core.Routine
import dev.danields.aall.core.RoutineKind
import dev.danields.aall.core.Window
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONObject

val zone: ZoneId = ZoneId.of("Europe/Madrid")
private val allDays = listOf(1, 2, 3, 4, 5, 6, 7)

/** Epoch millis of a local date and time, such as "2026-11-02T23:00". */
fun at(local: String): Long = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

private fun window(hours: Pair<String, String>, days: List<Int> = allDays) =
  Window(LocalTime.parse(hours.first), LocalTime.parse(hours.second), days)

private fun routine(
  kind: RoutineKind,
  windows: List<Window>,
  apps: List<String> = emptyList(),
  colorApps: List<String> = emptyList(),
  enabled: Boolean = true
) = Routine(kind, kind.title, enabled, windows, apps, colorApps, LocalTime.of(20, 0))

fun bedtime(
  hours: Pair<String, String> = "22:30" to "07:00",
  apps: List<String> = emptyList(),
  colorApps: List<String> = emptyList(),
  days: List<Int> = allDays,
  enabled: Boolean = true
) = routine(RoutineKind.BEDTIME, listOf(window(hours, days)), apps, colorApps, enabled)

fun focus(apps: List<String> = emptyList(), enabled: Boolean = true) =
  routine(RoutineKind.FOCUS, emptyList(), apps, enabled = enabled)

/** Apps stay blocked outside the given hours. */
fun access(
  vararg hours: Pair<String, String>,
  apps: List<String> = emptyList(),
  enabled: Boolean = true
) = routine(RoutineKind.ACCESS, hours.map { window(it) }, apps, enabled = enabled)

fun grayscale(
  vararg hours: Pair<String, String>,
  apps: List<String> = emptyList(),
  colorApps: List<String> = emptyList(),
  enabled: Boolean = true
) = routine(RoutineKind.GRAYSCALE, hours.map { window(it) }, apps, colorApps, enabled)

fun nightLight(hours: Pair<String, String> = "21:00" to "07:00", enabled: Boolean = true) =
  routine(RoutineKind.NIGHT_LIGHT, listOf(window(hours)), enabled = enabled)

/**
 * A phone running aall from [start], set up with [routines]; every other routine is off. Time moves
 * only through [advanceTo], which fires the alarms Engine scheduled, so a missed boundary shows.
 */
class Phone(start: String, vararg routines: Routine, setup: FakeDevice.() -> Unit = {}) {
  val device = FakeDevice(Clock(at(start), 1000, 1, zone)).apply(setup)
  val store = MemoryStore()
  val engine = Engine(device, store)

  init {
    val config = Config(
      50,
      RoutineKind.entries.map { kind ->
        routines.firstOrNull { it.kind == kind }
          ?: routine(kind, listOf(window("22:30" to "07:00")), enabled = false)
      }
    )
    engine.initialize(config.toJson().toString())
  }

  val now get() = device.clock.wall
  val dnd get() = device.dnd
  val grayscale get() = device.secure["accessibility_display_daltonizer_enabled"] == 1 &&
    device.secure["accessibility_display_daltonizer"] == 0
  val blocked get() = device.block != null
  val notice get() = device.notice
  val alarm get() = device.alarm

  fun secure(key: String) = device.secure[key]

  fun snapshot() = engine.snapshot()

  fun active() = snapshot().getJSONArray("active").let { a ->
    (0 until a.length()).map(a::getString)
  }

  fun status(kind: RoutineKind) = snapshot().getJSONObject("statuses").getString(kind.id)

  fun errors() = snapshot().getJSONArray("errors").let { a ->
    (0 until a.length()).map(a::getString)
  }

  fun weekly(kind: RoutineKind) = snapshot().getJSONObject("config").getJSONArray("routines")
    .let { a ->
      (0 until a.length()).map(a::getJSONObject)
    }.single { it.getString("id") == kind.id }

  fun overrides() = snapshot().getJSONObject("overrides").keySet()

  fun turningOff(kind: RoutineKind) =
    snapshot().getJSONObject("turningOff").optLong(kind.id).takeIf { it != 0L }

  fun open(pkg: String) {
    device.foreground = pkg
    device.foregroundApp = pkg
    engine.reconcile()
  }

  /** System UI comes to the front and the app stays underneath. */
  fun pullDownShade() {
    device.foreground = "com.android.systemui"
    engine.reconcile()
  }

  fun plug(on: Boolean) {
    device.plugged = on
    engine.reconcile(on)
  }

  /** Alarms don't survive a reboot, and the boot broadcast reconciles. */
  fun reboot(after: Long = 0) {
    val c = device.clock
    device.clock = Clock(c.wall + after, 50, c.boot + 1, c.zone)
    device.alarm = null
    device.nightLightStep = null
    device.foreground = ""
    device.foregroundApp = ""
    engine.reconcile()
  }

  /** The user changes the wall clock; the time-set broadcast reconciles. */
  fun setClock(local: String) {
    device.clock = device.clock.copy(wall = at(local))
    engine.reconcile()
  }

  /** Accepts "HH:mm[:ss.SSS]" for the next such time, or a full local date and time. */
  fun advanceTo(target: String) {
    val to = if ('T' in target) {
      at(target)
    } else {
      val today = device.clock.today.atTime(LocalTime.parse(target)).atZone(zone)
      val t = today.toInstant().toEpochMilli()
      if (t >= now) t else today.plusDays(1).toInstant().toEpochMilli()
    }
    require(to >= now) { "Time only moves forward" }
    repeat(100_000) {
      val due = listOfNotNull(device.alarm, device.nightLightStep).filter { it <= to }.minOrNull()
      if (due == null) {
        moveTo(to)
        return
      }
      if (device.alarm == due) device.alarm = null
      if (device.nightLightStep == due) device.nightLightStep = null
      moveTo(maxOf(due, now))
      engine.reconcile()
    }
    error("Alarms never settled before $target")
  }

  private fun moveTo(wall: Long) {
    val c = device.clock
    device.clock = c.copy(wall = wall, elapsed = c.elapsed + (wall - c.wall))
  }

  fun book(date: String) = engine.book(date)

  fun startFocus() = engine.focus()

  fun setFocusMinutes(minutes: Int) = engine.setFocusMinutes(minutes)

  fun testGrayscale(on: Boolean) = engine.testGray(on)

  fun saveWeekly(routine: Routine) = engine.saveWeekly(routine.toJson().toString())

  fun overrideToday(routine: Routine) =
    engine.overrideToday(routine.kind.id, routine.toJson().toString())

  fun requestTurnOff(kind: RoutineKind) = engine.requestTurnOff(kind.id)

  fun cancelTurnOff(kind: RoutineKind) = engine.cancelTurnOff(kind.id)

  /** Solves the arithmetic challenge, which pauses restrictions for 15 minutes. */
  fun solvePause() {
    val (a, s, b, t, c) = engine.challenge().split(" ")
    val answer = a.toInt() + sign(s) * b.toInt() + sign(t) * c.toInt()
    check(engine.solve(answer.toString()))
  }

  private fun sign(op: String) = if (op == "+") 1 else -1

  /** Writes a raw stored value, as an older build would have left it. */
  fun stored(key: String, value: JSONObject) = store.write(mapOf(key to value.toString()))
}
