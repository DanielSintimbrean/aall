package dev.danields.aall.automation

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

    fun of(d: Decision, app: String, clock: Clock): BlockNotice {
      val now = clock.wall
      val blockers = d.routines.filter { it.blocks(app) }
      val ends = blockers.filter { it.kind != RoutineKind.ACCESS }.associateWith { r ->
        when (r.kind) {
          RoutineKind.FOCUS -> now + d.focusRemaining

          RoutineKind.BEDTIME -> maxOf(
            d.state.bedtimeUntil,
            Rules.activeInterval(r, now, clock.zone, d.state.overrides[r.kind]?.date)?.second ?: 0
          ).takeIf { it > now }

          else -> null
        }
      }.toMutableMap<Routine, Long?>()
      // Allowed hours only count once the other routines have let the app go.
      blockers.firstOrNull { it.kind == RoutineKind.ACCESS }?.let { r ->
        val anchor = d.state.overrides[r.kind]?.date
        val others = ends.values.filterNotNull().maxOrNull() ?: now
        ends[r] = if (others > now && Rules.active(r, others, clock.zone, anchor)) {
          others
        } else {
          Rules.nextStart(r, others, clock.zone, anchor)
        }
      }
      // The routine that holds the app longest decides the headline.
      val main = blockers.maxByOrNull { ends[it] ?: Long.MAX_VALUE }
      val until = if (ends.values.any {
          it == null
        }
      ) {
        null
      } else {
        ends.values.filterNotNull().maxOrNull()
      }
      val title = when (main?.kind) {
        RoutineKind.ACCESS -> "Fuera de horario"
        RoutineKind.FOCUS -> "Estás concentrándote"
        RoutineKind.BEDTIME -> "Es hora de descansar"
        else -> "Aplicación bloqueada"
      }
      val text = when {
        until == null -> "Esta aplicación no está disponible ahora."

        main?.kind == RoutineKind.FOCUS && blockers.size == 1 ->
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
