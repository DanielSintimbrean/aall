package dev.danields.aall.automation

import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the status notification says for a decision. Null when nothing is in effect. */
data class StatusNotice(val title: String, val text: String, val countdownTo: Long? = null) {
  /** Identifies what is shown. Deadlines are rounded because each reconcile recomputes them with a few milliseconds of jitter. */
  val key get() = "$title|$text|${countdownTo?.let { (it + 2500) / 5000 }}"

  companion object {
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-ES"))

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
      val problems = (failures + d.errors).joinToString(" ")
      val names = focusFirst.joinToString { it.title }
      return StatusNotice(
        title = if (problems.isEmpty()) names else "$names · requiere configuración",
        text = problems.ifEmpty { nextChange(d.nextChange, clock) },
        countdownTo = (clock.wall + d.focusRemaining).takeIf { focusRunning }
      )
    }

    private fun nextChange(at: Long, clock: Clock): String {
      val local = Instant.ofEpochMilli(at).atZone(clock.zone)
      // Reconcile always wakes at midnight to check for a day off, so midnight says nothing about routines. It also means the next change is never later than today.
      return if (local.toLocalTime() ==
        LocalTime.MIDNIGHT
      ) {
        ""
      } else {
        "Próximo cambio a las " + local.format(time)
      }
    }
  }
}
