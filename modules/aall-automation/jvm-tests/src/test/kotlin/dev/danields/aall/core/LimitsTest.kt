package dev.danields.aall.core

import java.io.File
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** The editor reads its limits from src/limits.json; native must accept exactly the same. */
class LimitsTest {
  private val limits = JSONObject(File(System.getProperty("aall.limits")).readText())

  private fun window() =
    JSONObject().put("start", "09:00").put("end", "10:00").put("days", JSONArray(listOf(1)))

  private fun routine(kind: RoutineKind) = JSONObject()
    .put("id", kind.id).put("enabled", false)
    .put("windows", JSONArray(listOf(window())))
    .put("apps", JSONArray()).put("colorApps", JSONArray())
    .put("earlyStart", "20:00").put("rampMinutes", 90).put("finalIntensity", 50)

  private fun config(focusMinutes: Int) = JSONObject().put("version", 1)
    .put("focusMinutes", focusMinutes)
    .put("routines", JSONArray(RoutineKind.entries.map(::routine)))

  private fun accepts(block: () -> Unit) = runCatching(block).isSuccess

  private fun parses(kind: RoutineKind, edit: JSONObject.() -> Unit) =
    accepts { Routine.parse(routine(kind).apply(edit)) }

  private fun checkRange(name: String, accepts: (Int) -> Boolean) {
    val range = limits.getJSONObject(name)
    val min = range.getInt("min")
    val max = range.getInt("max")
    assertEquals(
      "$name accepts [${min - 1}, $min, $max, ${max + 1}]",
      listOf(false, true, true, false),
      listOf(min - 1, min, max, max + 1).map(accepts)
    )
  }

  @Test fun focusMinutes() = checkRange("focusMinutes") { m ->
    accepts { Config.parse(config(m).toString()) }
  }

  @Test fun nightLightRamp() {
    checkRange("rampMinutes") { v -> parses(RoutineKind.NIGHT_LIGHT) { put("rampMinutes", v) } }
    checkRange("finalIntensity") { v ->
      parses(RoutineKind.NIGHT_LIGHT) { put("finalIntensity", v) }
    }
  }

  @Test fun earliestChargingStart() {
    val earliest = LocalTime.parse(limits.getString("earliestChargingStart"))
    val before = earliest.minusMinutes(1).toString()
    assertEquals(
      listOf(false, true),
      listOf(before, earliest.toString()).map { t ->
        parses(RoutineKind.BEDTIME) { put("earlyStart", t) }
      }
    )
  }

  @Test fun windowsPerRoutine() {
    val max = limits.getInt("maxWindows")
    val one = limits.getJSONArray("oneWindowRoutines").let { a ->
      (0 until a.length()).map(a::getString)
    }
    for (kind in RoutineKind.entries) {
      fun with(n: Int) = parses(kind) { put("windows", JSONArray(List(n) { window() })) }
      val (counts, expected) = if (kind.id in one) {
        listOf(0, 1, 2) to listOf(false, true, false)
      } else {
        listOf(0, max, max + 1) to listOf(true, true, false)
      }
      assertEquals("${kind.id} windows $counts", expected, counts.map(::with))
    }
  }

  @Test fun appsPerList() {
    val max = limits.getInt("maxAppsPerList")
    for (list in listOf("apps", "colorApps")) {
      fun with(n: Int) = parses(RoutineKind.BEDTIME) { put(list, JSONArray(List(n) { "a.b$it" })) }
      assertEquals(list, listOf(true, false), listOf(max, max + 1).map(::with))
    }
  }
}
