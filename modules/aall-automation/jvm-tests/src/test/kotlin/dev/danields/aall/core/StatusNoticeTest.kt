package dev.danields.aall.core

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusNoticeTest {
  @Test fun focusTimerOnADisabledRoutineShowsNoCountdown() {
    // Only stored state can hold this: Engine refuses to start focus while it is off.
    val clock = Clock(1_793_617_200_000, 1000, 1, ZoneId.of("Europe/Madrid"))

    fun routine(kind: RoutineKind, enabled: Boolean, windows: List<Window>) =
      Routine(kind, kind.title, enabled, windows, emptyList(), emptyList(), LocalTime.of(20, 0))
    val allDay = Window(LocalTime.MIN, LocalTime.of(23, 59), listOf(1, 2, 3, 4, 5, 6, 7))
    val state = State(
      Config(
        50,
        listOf(
          routine(RoutineKind.GRAYSCALE, true, listOf(allDay)),
          routine(RoutineKind.FOCUS, false, emptyList())
        )
      ),
      timers = mapOf(Timer.FOCUS to Timer.start(clock, 3000000))
    )
    val all = Permissions(accessibility = true, dnd = true, alarms = true, grayscale = true)
    val d = Reconcile.decide(state, Environment(clock, "", emptySet(), false, all))
    val notice = StatusNotice.of(d, clock)!!
    assertEquals("Escala de grises", notice.title)
    assertNull(notice.countdownTo)
  }

  @Test fun severalRoutinesListWhenEachEnds() {
    val zone = ZoneId.of("Europe/Madrid")
    val clock = Clock(
      ZonedDateTime.parse("2026-11-09T23:00:00+01:00[Europe/Madrid]").toInstant().toEpochMilli(),
      1000,
      1,
      zone
    )
    val days = listOf(1, 2, 3, 4, 5, 6, 7)

    fun routine(kind: RoutineKind, start: Int, end: Int) = Routine(
      kind,
      kind.title,
      true,
      listOf(Window(LocalTime.of(start, 0), LocalTime.of(end, 0), days)),
      emptyList(),
      emptyList(),
      LocalTime.of(20, 0)
    )
    val state = State(
      Config(
        50,
        listOf(routine(RoutineKind.BEDTIME, 22, 7), routine(RoutineKind.GRAYSCALE, 21, 8))
      )
    )
    val all = Permissions(accessibility = true, dnd = true, alarms = true, grayscale = true)
    val d = Reconcile.decide(state, Environment(clock, "", emptySet(), false, all))
    val notice = StatusNotice.of(d, clock)!!
    assertEquals("2 rutinas activas", notice.title)
    assertEquals(
      "Descanso · hasta mañana las 07:00\nEscala de grises · hasta mañana las 08:00",
      notice.text
    )
  }

  @Test fun keyIgnoresDeadlineJitterButNotAMovedDeadline() {
    val notice = StatusNotice("focus", "", 1_000_000)
    assertEquals(notice.key, notice.copy(countdownTo = 1_000_005).key)
    assertNotEquals(notice.key, notice.copy(countdownTo = 1_060_000).key)
  }
}
