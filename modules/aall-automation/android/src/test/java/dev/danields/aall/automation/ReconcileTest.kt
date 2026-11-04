package dev.danields.aall.automation

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReconcileTest {
  private val zone = ZoneId.of("Europe/Madrid")
  private val allDays = listOf(1, 2, 3, 4, 5, 6, 7)
  private val allow = Permissions(accessibility = true, dnd = true, alarms = true, grayscale = true)
  private fun at(local: String) = ZonedDateTime.parse("${local}+01:00[Europe/Madrid]").toInstant().toEpochMilli()
  private fun clock(local: String, boot: Int = 1, elapsed: Long = 1000) = Clock(at(local), elapsed, boot, zone)
  private fun time(hour: Int, minute: Int = 0) = LocalTime.of(hour, minute)
  private fun routine(kind: RoutineKind, windows: List<Window> = emptyList(), apps: List<String> = emptyList(), colorApps: List<String> = emptyList(), enabled: Boolean = true) =
    Routine(kind, kind.id, enabled, windows, apps, colorApps, time(20))
  private val bedtime = routine(RoutineKind.BEDTIME, listOf(Window(time(22, 30), time(7), allDays)), apps = listOf("clock"), colorApps = listOf("maps"))
  private fun state(vararg routines: Routine, change: State.() -> State = { this }) = State(Config(50, routines.toList())).change()
  private fun decide(state: State, clock: Clock, foreground: String = "", plugged: Boolean = false, permissions: Permissions = allow, allowed: Set<String> = emptySet()) =
    Reconcile.decide(state, Environment(clock, foreground, allowed, plugged, permissions))
  private fun kinds(d: Decision) = d.routines.map { it.kind }

  @Test fun chargingStartsBedtimeEarlyAndUnpluggingKeepsItUntilWake() {
    val started = decide(state(bedtime), clock("2026-11-02T21:00:00"), plugged = true)
    assertEquals(listOf(RoutineKind.BEDTIME), kinds(started))
    assertEquals(LocalDate.parse("2026-11-02"), started.state.bedtimeDate)
    val unplugged = decide(started.state, clock("2026-11-02T21:30:00"))
    assertEquals(listOf(RoutineKind.BEDTIME), kinds(unplugged))
    val awake = decide(unplugged.state, clock("2026-11-03T07:00:00"))
    assertEquals(emptyList<RoutineKind>(), kinds(awake))
    assertNull(awake.state.bedtimeDate)
  }
  @Test fun chargingBeforeEarlyStartDoesNotStartBedtime() {
    val d = decide(state(bedtime), clock("2026-11-02T19:59:00"), plugged = true)
    assertEquals(emptyList<RoutineKind>(), kinds(d))
    assertNull(d.state.bedtimeDate)
  }
  @Test fun chargingOnAnUnscheduledWeekdayDoesNotStartBedtime() {
    val tuesdayOnly = bedtime.copy(windows = listOf(Window(time(22, 30), time(7), listOf(2))))
    assertNull(decide(state(tuesdayOnly), clock("2026-11-02T21:00:00"), plugged = true).state.bedtimeDate)
  }
  @Test fun disabledBedtimeIgnoresCharging() {
    assertNull(decide(state(bedtime.copy(enabled = false)), clock("2026-11-02T21:00:00"), plugged = true).state.bedtimeDate)
  }
  @Test fun dayOffSuppressesBedtimeCarriedOverFromTheEvening() {
    val d = decide(state(bedtime) { copy(daysOff = listOf("2026-11-03"), dndOwned = true) }, clock("2026-11-03T02:00:00"))
    assertTrue(d.dayOff)
    assertEquals(emptyList<RoutineKind>(), kinds(d))
    assertEquals(false, d.setDnd)
    assertEquals(false, d.setGrayscale)
  }
  @Test fun restrictionsResumeWhenTheDayOffEnds() {
    val d = decide(state(bedtime) { copy(daysOff = listOf("2026-11-02")) }, clock("2026-11-03T00:00:00"))
    assertFalse(d.dayOff)
    assertEquals(listOf(RoutineKind.BEDTIME), kinds(d))
  }
  @Test fun pauseSuppressesRoutinesUntilItExpires() {
    val now = clock("2026-11-02T23:00:00")
    val paused = state(bedtime) { copy(timers = mapOf(Timer.PAUSE to Timer.start(now, 900000))) }
    val during = decide(paused, now)
    assertEquals(emptyList<RoutineKind>(), kinds(during))
    assertEquals(900000L, during.pauseRemaining)
    val after = decide(paused, Clock(now.wall + 900000, now.elapsed + 900000, 1, zone))
    assertEquals(listOf(RoutineKind.BEDTIME), kinds(after))
  }
  @Test fun timersRecoverWallDeadlineAfterReboot() {
    val started = clock("2026-11-02T10:00:00", boot = 1, elapsed = 500000)
    val focus = state(routine(RoutineKind.FOCUS)) { copy(timers = mapOf(Timer.FOCUS to Timer.start(started, 3000000))) }
    val rebooted = Clock(started.wall + 1000000, 20, 2, zone)
    val d = decide(focus, rebooted)
    assertEquals(2000000L, d.focusRemaining)
    assertEquals(Timer(2, 2000020, started.wall + 3000000, 3000000), d.state.timers[Timer.FOCUS])
  }
  @Test fun dndOnlyChangesWhenOwnershipDiffers() {
    val now = clock("2026-11-02T23:00:00")
    assertEquals(true, decide(state(bedtime), now).setDnd)
    assertNull(decide(state(bedtime) { copy(dndOwned = true) }, now).setDnd)
    assertNull(decide(state(routine(RoutineKind.GRAYSCALE)), now).setDnd)
  }
  @Test fun endingFocusKeepsDndOwnedForBedtime() {
    val now = clock("2026-11-02T23:00:00")
    val d = decide(state(bedtime, routine(RoutineKind.FOCUS)) { copy(dndOwned = true) }, now)
    assertEquals(0L, d.focusRemaining)
    assertNull(d.setDnd)
  }
  @Test fun missingPermissionsSkipEffectsAndReportErrors() {
    val d = decide(state(bedtime), clock("2026-11-02T23:00:00"), permissions = Permissions(false, false, false, false))
    assertNull(d.setDnd)
    assertNull(d.setGrayscale)
    assertEquals(
      listOf("App blocking needs accessibility access.", "DND access missing.", "Color exceptions need accessibility access.", "Grayscale needs the ADB grant.", "Exact alarm access missing; changes may be delayed."),
      d.errors,
    )
  }
  @Test fun colorExceptionOnlyReleasesItsOwnGrayscaleRequest() {
    val now = clock("2026-11-02T23:00:00")
    val grayscale = routine(RoutineKind.GRAYSCALE, listOf(Window(time(21), time(7), allDays)))
    assertEquals(false, decide(state(bedtime), now, foreground = "maps").setGrayscale)
    assertEquals(true, decide(state(bedtime, grayscale), now, foreground = "maps").setGrayscale)
  }
  @Test fun grayscaleTestRunsWithoutRoutinesButNotDuringAPause() {
    val now = clock("2026-11-02T12:00:00")
    val testing = State().copy(timers = mapOf(Timer.GRAY_TEST to Timer.start(now, 10000)))
    assertEquals(true, decide(testing, now).setGrayscale)
    assertEquals(false, decide(testing.copy(timers = testing.timers + (Timer.PAUSE to Timer.start(now, 900000))), now).setGrayscale)
  }
  @Test fun blockingFollowsEachRoutineAppRule() {
    val night = clock("2026-11-02T23:00:00")
    assertFalse(decide(state(bedtime), night, foreground = "clock").blocked)
    assertTrue(decide(state(bedtime), night, foreground = "game").blocked)
    assertFalse(decide(state(bedtime), night, foreground = "launcher", allowed = setOf("launcher")).blocked)
    val access = routine(RoutineKind.ACCESS, listOf(Window(time(12, 30), time(13, 15), allDays)), apps = listOf("social"))
    assertTrue(decide(state(access), clock("2026-11-02T12:00:00"), foreground = "social").blocked)
    assertFalse(decide(state(access), clock("2026-11-02T12:45:00"), foreground = "social").blocked)
    assertFalse(decide(state(access), clock("2026-11-02T12:00:00"), foreground = "other").blocked)
    assertFalse(decide(state(routine(RoutineKind.GRAYSCALE, listOf(Window(time(9), time(18), allDays)), apps = listOf("social"))), clock("2026-11-02T12:00:00"), foreground = "social").blocked)
  }
  @Test fun overrideAppliesUntilExpiryThenWeeklyResumes() {
    val date = LocalDate.parse("2026-11-02")
    val weekly = bedtime.copy(enabled = false)
    val tonight = Override(bedtime, date, 0)
    val during = decide(state(weekly) { copy(overrides = mapOf(RoutineKind.BEDTIME to tonight)) }, clock("2026-11-03T06:59:00"))
    assertEquals(listOf(RoutineKind.BEDTIME), kinds(during))
    assertEquals(at("2026-11-03T07:00:00"), during.state.overrides[RoutineKind.BEDTIME]?.until)
    val after = decide(during.state, clock("2026-11-03T07:00:00"))
    assertEquals(emptyList<RoutineKind>(), kinds(after))
    assertTrue(after.state.overrides.isEmpty())
  }
  @Test fun nextChangeIsTheEarliestUpcomingBoundary() {
    val now = clock("2026-11-02T12:00:00")
    assertEquals(at("2026-11-03T00:00:00"), decide(State(), now).nextChange)
    val access = routine(RoutineKind.ACCESS, listOf(Window(time(12, 30), time(13, 15), allDays)))
    assertEquals(at("2026-11-02T12:30:00"), decide(state(access), now).nextChange)
    val timed = state(access) { copy(timers = mapOf(Timer.FOCUS to Timer.start(now, 60000))) }
    assertEquals(now.wall + 60000, decide(timed, now).nextChange)
    assertEquals(at("2026-11-02T20:00:00"), decide(state(bedtime), now).nextChange)
  }
}
