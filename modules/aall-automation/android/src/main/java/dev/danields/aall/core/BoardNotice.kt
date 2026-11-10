package dev.danields.aall.core

data class BoardNotice(
  val title: String,
  val text: String,
  val lines: List<String>,
  val offline: Boolean
) {
  companion object {
    private const val MAX_LINES = 5

    /** [departures] is null until the first fetch has finished. */
    fun of(departures: List<Departure>?, offline: Boolean, now: Long): BoardNotice {
      val upcoming = departures?.let { Board.upcoming(it, now) }
      val next = upcoming?.firstOrNull() ?: return BoardNotice(
        title = "Movent",
        text = when {
          upcoming != null && !offline -> "Sin buses en las próximas 2 horas"
          offline -> "Sin conexión"
          else -> "Buscando buses…"
        },
        lines = emptyList(),
        offline = false
      )
      val minutes = Board.minutes(next, now)
      return BoardNotice(
        title = "${next.line} · ${Board.clock(next.time)} · " +
          if (minutes == 0L) "ahora" else "en $minutes min",
        text = listOfNotNull(next.destination, Board.source(next, now)).joinToString(" · "),
        lines = upcoming.drop(1).take(MAX_LINES).map { departure ->
          listOfNotNull(
            "${Board.clock(departure.time)} ${departure.line}",
            departure.destination,
            Board.source(departure, now)
          ).joinToString(" · ")
        },
        offline = offline
      )
    }
  }
}
