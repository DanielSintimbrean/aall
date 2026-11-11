package dev.danields.aall.core

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the status notification says for a decision. Null when nothing is in effect. */
data class StatusNotice(val title: String, val text: String, val countdownTo: Long? = null) {
  /** Identifies what is shown. Deadlines are rounded because each reconcile recomputes them with a few milliseconds of jitter. */
  val key get() = "$title|$text|${countdownTo?.let { (it + 2500) / 5000 }}"

  companion object {
    private val locale = Locale.forLanguageTag("es-ES")
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val weekday = DateTimeFormatter.ofPattern("EEEE", locale)
    private const val HORIZON = 8L * 24 * 3600000
    private val ANY = Permissions(accessibility = true, dnd = true, alarms = true, grayscale = true)

    fun of(d: Decision, clock: Clock, failures: List<String> = emptyList()): StatusNotice? {
      if (d.dayOff) {
        return StatusNotice(
          "Día libre",
          "Todas las restricciones están suspendidas hasta medianoche."
        )
      }
      if (d.pauseRemaining > 0) {
        val until = clock.wall + d.pauseRemaining
        return StatusNotice("En pausa", "Las restricciones se reanudarán al terminar.", until)
      }
      if (d.routines.isEmpty()) return null
      val focusFirst = d.routines.sortedBy { it.kind != RoutineKind.FOCUS }
      val focusRunning = focusFirst[0].kind == RoutineKind.FOCUS
      val ends = ends(d, clock)
      val single = focusFirst.size == 1
      val lines = focusFirst.map { r ->
        val end = ends[r.kind]?.let { "hasta ${until(it, clock)}" }
        when {
          single -> end?.replaceFirstChar { it.uppercase() } ?: ""
          end == null -> r.title
          else -> "${r.title} · $end"
        }
      }.filter { it.isNotEmpty() }
      val problems = failures + d.errors
      val names = if (single) focusFirst[0].title else "${focusFirst.size} rutinas activas"
      val title = if (problems.isEmpty()) names else "$names · requiere configuración"
      return StatusNotice(
        title = title,
        text = (problems + lines).joinToString("\n"),
        countdownTo = (clock.wall + d.focusRemaining).takeIf { focusRunning }
      )
    }

    /** When each running routine stops, replaying the engine at each upcoming change so overrides, turn-offs and back-to-back windows count. Missing when it runs past the horizon. */
    fun ends(d: Decision, clock: Clock): Map<RoutineKind, Long> {
      val now = clock.wall
      val open = d.routines.map { it.kind }.toMutableSet()
      val ends = mutableMapOf<RoutineKind, Long>()
      var at = d.nextChange
      while (open.isNotEmpty() && at <= now + HORIZON) {
        val later = clock.copy(wall = at, elapsed = clock.elapsed + (at - now))
        val next = Reconcile.decide(d.state, Environment(later, "", emptySet(), false, ANY))
        val running = next.routines.map { it.kind }.toSet()
        open.filter { it !in running }.forEach {
          ends[it] = at
          open.remove(it)
        }
        at = next.nextChange
      }
      return ends
    }

    private fun until(ms: Long, clock: Clock): String {
      val local = Instant.ofEpochMilli(ms).atZone(clock.zone)
      val day = when (local.toLocalDate()) {
        clock.today -> ""
        clock.today.plusDays(1) -> "mañana "
        else -> "el " + local.format(weekday) + " "
      }
      return "${day}las ${local.format(time)}"
    }
  }
}
