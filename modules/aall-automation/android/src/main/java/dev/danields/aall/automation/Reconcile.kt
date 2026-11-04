package dev.danields.aall.automation

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Clock(val wall: Long, val elapsed: Long, val boot: Int, val zone: ZoneId) {
  val today: LocalDate get() = Instant.ofEpochMilli(wall).atZone(zone).toLocalDate()
}

data class Timer(val boot: Int, val elapsed: Long, val wall: Long, val duration: Long) {
  fun remaining(clock: Clock) =
    Rules.remaining(boot == clock.boot, elapsed, clock.elapsed, wall, clock.wall, duration)

  companion object {
    const val FOCUS = "focus"
    const val PAUSE = "pause"
    const val GRAY_TEST = "grayTest"

    fun start(clock: Clock, duration: Long) = Timer(
      clock.boot,
      clock.elapsed + duration,
      clock.wall + duration,
      duration
    )
  }
}

data class Override(val routine: Routine, val date: LocalDate, val until: Long)

data class Permissions(
  val accessibility: Boolean,
  val dnd: Boolean,
  val alarms: Boolean,
  val grayscale: Boolean
)

data class State(
  val config: Config = Config.EMPTY,
  val overrides: Map<RoutineKind, Override> = emptyMap(),
  val daysOff: List<String> = emptyList(),
  val timers: Map<String, Timer> = emptyMap(),
  val bedtimeDate: LocalDate? = null,
  val bedtimeUntil: Long = 0,
  val dndOwned: Boolean = false
)

data class Environment(
  val clock: Clock,
  val foreground: String,
  val alwaysAllowed: Set<String>,
  val plugged: Boolean,
  val permissions: Permissions
)

data class Decision(
  val state: State,
  val routines: List<Routine>,
  val dayOff: Boolean,
  val focusRemaining: Long,
  val pauseRemaining: Long,
  val setDnd: Boolean?,
  val setGrayscale: Boolean?,
  val blocked: Boolean,
  val nextChange: Long,
  val errors: List<String>,
  val statuses: Map<RoutineKind, RoutineStatus>
)

object Reconcile {
  fun decide(before: State, env: Environment): Decision {
    val clock = env.clock
    val now = clock.wall
    val settled = settleOverrides(reanchorTimers(before, clock), clock)
    val state =
      refreshBedtime(if (env.plugged) startBedtimeEarly(settled, clock) else settled, clock)
    val focusRemaining = remaining(state, Timer.FOCUS, clock)
    val pauseRemaining = remaining(state, Timer.PAUSE, clock)
    val dayOff = clock.today.toString() in state.daysOff
    val running = state.config.routines.mapNotNull { base ->
      val (r, anchor) = effective(state, base)
      r.takeIf {
        it.enabled &&
          r.kind.runs(
            Rules.active(r, now, clock.zone, anchor),
            focusRemaining,
            state.bedtimeUntil,
            now
          )
      }
    }
    val routines = if (Rules.suppress(dayOff, pauseRemaining)) emptyList() else running
    val dnd = routines.any { it.kind.usesDnd }
    val gray = routines.any { it.requestsGrayscale(env.foreground) }
    val grayscale =
      (gray || remaining(state, Timer.GRAY_TEST, clock) > 0) && !dayOff && pauseRemaining <= 0
    val blocked = env.foreground !in env.alwaysAllowed && routines.any { it.blocks(env.foreground) }
    val runningKinds = routines.map { it.kind }
    return Decision(
      state, routines, dayOff, focusRemaining, pauseRemaining,
      setDnd = dnd.takeIf { env.permissions.dnd && it != state.dndOwned },
      setGrayscale = grayscale.takeIf { env.permissions.grayscale },
      blocked = blocked,
      nextChange = nextChange(state, clock),
      errors = errors(routines, dnd, gray, env.permissions),
      statuses = state.config.routines.associate { base ->
        base.kind to effective(state, base).first.status(env.permissions, base.kind in runningKinds)
      }
    )
  }

  fun saveWeekly(state: State, routine: Routine) = state.copy(
    config = state.config.with(routine),
    overrides = state.overrides - routine.kind
  )

  private fun startBedtimeEarly(before: State, clock: Clock): State {
    val state = settleOverrides(before, clock)
    val base = state.config.routines.firstOrNull { it.kind == RoutineKind.BEDTIME } ?: return state
    val (r, anchor) = effective(state, base)
    if (!r.enabled) return state
    val today = clock.today
    val window = r.windows[0]
    val early = today.atTime(r.earlyStart).atZone(clock.zone).toInstant().toEpochMilli()
    val (start, end) = Rules.interval(window, today, clock.zone)
    return if (clock.wall in early until start &&
      Rules.runsOn(window, today, anchor)
    ) {
      state.copy(bedtimeUntil = end, bedtimeDate = today)
    } else {
      state
    }
  }

  private fun remaining(state: State, key: String, clock: Clock) =
    state.timers[key]?.remaining(clock) ?: 0

  private fun effective(state: State, base: Routine): Pair<Routine, LocalDate?> =
    state.overrides[base.kind]?.let { it.routine to it.date } ?: (base to null)

  private fun reanchorTimers(state: State, clock: Clock) = state.copy(
    timers = state.timers.mapValues { (_, timer) ->
      if (timer.boot == clock.boot) {
        timer
      } else {
        timer.copy(
          boot = clock.boot,
          elapsed = clock.elapsed + timer.remaining(clock)
        )
      }
    }
  )

  private fun settleOverrides(state: State, clock: Clock) = state.copy(
    overrides = state.overrides
      .mapValues { (_, o) -> o.copy(until = Rules.overrideExpiry(o.routine, o.date, clock.zone)) }
      .filterValues { clock.wall < it.until }
  )

  private fun refreshBedtime(state: State, clock: Clock): State {
    val date = state.bedtimeDate ?: return state
    val base = state.config.routines.firstOrNull { it.kind == RoutineKind.BEDTIME } ?: return state
    val (r) = effective(state, base)
    if (!r.enabled) return state
    val end = Rules.interval(r.windows[0], date, clock.zone).second
    return state.copy(bedtimeUntil = end, bedtimeDate = date.takeIf { end > clock.wall })
  }

  private fun errors(routines: List<Routine>, dnd: Boolean, gray: Boolean, p: Permissions) =
    buildList {
      if (routines.any { it.kind.appRule != AppRule.NONE } && !p.accessibility) {
        add("App blocking needs accessibility access.")
      }
      if (dnd && !p.dnd) add("DND access missing.")
      if (gray && routines.any { it.colorApps.isNotEmpty() } && !p.accessibility) {
        add("Color exceptions need accessibility access.")
      }
      if (gray && !p.grayscale) add("Grayscale needs the ADB grant.")
      if (!p.alarms) add("Exact alarm access missing; changes may be delayed.")
    }

  private fun nextChange(state: State, clock: Clock): Long {
    val now = clock.wall
    val zone = clock.zone
    val today = clock.today
    val candidates = mutableListOf(
      today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
      state.bedtimeUntil
    )
    for (key in listOf(Timer.FOCUS, Timer.PAUSE, Timer.GRAY_TEST)) {
      remaining(state, key, clock).takeIf { it > 0 }?.let { candidates.add(now + it) }
    }
    for (base in state.config.routines) {
      val (r, anchor) = effective(state, base)
      state.overrides[base.kind]?.let { candidates.add(it.until) }
      if (r.kind == RoutineKind.BEDTIME && r.enabled) {
        candidates.add(today.atTime(r.earlyStart).atZone(zone).toInstant().toEpochMilli())
      }
      for (rule in listOf(r, base).filter { it.enabled }) {
        for (w in rule.windows) {
          for (offset in -1L..8L) {
            val date = today.plusDays(offset)
            if (Rules.runsOn(
                w,
                date,
                anchor
              )
            ) {
              candidates.addAll(Rules.interval(w, date, zone).toList())
            }
          }
        }
      }
    }
    return candidates.filter { it > now }.minOrNull() ?: (now + 60000)
  }
}
