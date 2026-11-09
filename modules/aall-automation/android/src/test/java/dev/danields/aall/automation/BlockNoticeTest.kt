package dev.danields.aall.automation

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockNoticeTest {
  private val zone = ZoneId.of("Europe/Madrid")
  private val allDays = listOf(1, 2, 3, 4, 5, 6, 7)
  private val allow = Permissions(accessibility = true, dnd = true, alarms = true, grayscale = true)

  private fun clock(local: String) = Clock(
    ZonedDateTime.parse("$local+01:00[Europe/Madrid]").toInstant().toEpochMilli(),
    1000,
    1,
    zone
  )

  private fun routine(kind: RoutineKind, windows: List<Window>, apps: List<String>) =
    Routine(kind, kind.title, true, windows, apps, emptyList(), LocalTime.of(20, 0))

  private val access = routine(
    RoutineKind.ACCESS,
    listOf(
      Window(LocalTime.of(12, 30), LocalTime.of(13, 15), allDays),
      Window(LocalTime.of(19, 0), LocalTime.of(20, 0), allDays)
    ),
    listOf("social")
  )
  private val bedtime = routine(
    RoutineKind.BEDTIME,
    listOf(Window(LocalTime.of(22, 30), LocalTime.of(7, 0), allDays)),
    listOf("clock")
  )

  private fun notice(state: State, clock: Clock, app: String = "social"): BlockNotice {
    val d = Reconcile.decide(state, Environment(clock, app, emptySet(), false, allow))
    return BlockNotice.of(d, app, clock)
  }

  @Test fun outsideAllowedHoursSaysWhenTheNextWindowOpens() {
    val n = notice(State(Config(50, listOf(access))), clock("2026-11-09T15:00:00"))
    assertEquals("Fuera de horario", n.title)
    assertEquals("Disponible a las 19:00.", n.text)
    assertEquals(listOf(RoutineKind.ACCESS.title), n.routines)
  }

  @Test fun afterTheLastWindowTheAppOpensTomorrow() {
    val n = notice(State(Config(50, listOf(access))), clock("2026-11-09T20:30:00"))
    assertEquals("Disponible mañana a las 12:30.", n.text)
  }

  @Test fun theRoutineHoldingTheAppLongestLeads() {
    val n = notice(State(Config(50, listOf(access, bedtime))), clock("2026-11-09T23:00:00"))
    assertEquals("Fuera de horario", n.title)
    assertEquals("Disponible mañana a las 12:30.", n.text)
    assertEquals(2, n.routines.size)
  }

  @Test fun bedtimeAloneEndsAtWakeUp() {
    val n = notice(State(Config(50, listOf(bedtime))), clock("2026-11-09T23:00:00"), "other")
    assertEquals("Es hora de descansar", n.title)
    assertEquals("Disponible mañana a las 07:00.", n.text)
  }

  @Test fun focusSaysWhenItEnds() {
    val c = clock("2026-11-09T10:00:00")
    val focus = routine(RoutineKind.FOCUS, emptyList(), listOf("social"))
    val state = State(
      Config(50, listOf(focus)),
      timers = mapOf(Timer.FOCUS to Timer.start(c, 50 * 60000L))
    )
    val n = notice(state, c)
    assertEquals("Estás concentrándote", n.title)
    assertEquals("La concentración termina a las 10:50.", n.text)
  }

  @Test fun noWindowsMeansNoTime() {
    val n = notice(
      State(Config(50, listOf(routine(RoutineKind.ACCESS, emptyList(), listOf("social"))))),
      clock("2026-11-09T10:00:00")
    )
    assertNull(n.until)
  }
}
