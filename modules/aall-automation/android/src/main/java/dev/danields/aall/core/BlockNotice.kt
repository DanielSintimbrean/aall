package dev.danields.aall.core

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the blocking screen says about the app in front. */
data class BlockNotice(
  val title: String,
  val text: String,
  /** When every routine blocking the app has let it go, or null when that is unknown. */
  val until: Long?,
  val routines: List<String>
) {
  companion object {
    private val locale = Locale.forLanguageTag("es-ES")
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val weekday = DateTimeFormatter.ofPattern("EEEE", locale)

    private const val HORIZON = 8L * 24 * 3600000
    private val ANY = Permissions(accessibility = true, dnd = true, alarms = true, grayscale = true)

    fun of(d: Decision, app: String, clock: Clock): BlockNotice {
      val now = clock.wall
      val blockers = d.routines.filter { it.blocks(app) }
      // Replay the engine at each upcoming change, so overrides, days off, turn-offs and routines
      // that start later count the same way they will when the time comes.
      var until: Long? = null
      var last = blockers.map { it.kind }
      var at = d.nextChange
      while (at <= now + HORIZON) {
        val later = clock.copy(wall = at, elapsed = clock.elapsed + (at - now))
        val next = Reconcile.decide(d.state, Environment(later, app, emptySet(), false, ANY))
        if (!next.blocked) {
          until = at
          break
        }
        last = next.routines.filter { it.blocks(app) }.map { it.kind }
        at = next.nextChange
      }
      // The routine still holding the app at the end decides the headline.
      val main = blockers.firstOrNull { it.kind in last } ?: blockers.firstOrNull()
      val title = when (main?.kind) {
        RoutineKind.ACCESS -> "Fuera de horario"
        RoutineKind.FOCUS -> "Estás concentrándote"
        RoutineKind.BEDTIME -> "Es hora de descansar"
        else -> "Aplicación bloqueada"
      }
      val text = when {
        until == null -> "Esta aplicación no está disponible ahora."

        main?.kind == RoutineKind.FOCUS && blockers.size == 1 && last == listOf(main.kind) ->
          "La concentración termina a las ${at(until, clock)}."

        else -> "Disponible ${on(until, clock)}."
      }
      return BlockNotice(title, text, until, blockers.map { it.title })
    }

    private fun at(ms: Long, clock: Clock) =
      Instant.ofEpochMilli(ms).atZone(clock.zone).format(time)

    private fun on(ms: Long, clock: Clock): String {
      val date = Instant.ofEpochMilli(ms).atZone(clock.zone).toLocalDate()
      val day = when (date) {
        clock.today -> ""
        clock.today.plusDays(1) -> "mañana "
        else -> "el " + date.format(weekday) + " "
      }
      return "${day}a las ${at(ms, clock)}"
    }
  }
}
