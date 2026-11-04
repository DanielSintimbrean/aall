package dev.danields.aall.automation
import org.junit.Test
import org.junit.Assert.*
import java.time.*
class RulesTest {
  private val zone=ZoneId.of("Europe/Madrid")
  private fun time(s:String)=ZonedDateTime.parse(s).toInstant().toEpochMilli()
  private fun routine()=Routine(RoutineKind.BEDTIME,"Bedtime",true,listOf(Window(LocalTime.of(22,30),LocalTime.of(7,0),listOf(1))),emptyList(),emptyList(),LocalTime.of(20,0))
  @Test fun timersUseElapsedWithinBootAndWallOnRecovery() {
    assertEquals(500L,Rules.remaining(true,1000,500,9000,999999,1000))
    assertEquals(0L,Rules.remaining(true,1000,1000,9000,0,1000))
    assertEquals(100L,Rules.remaining(false,1000,1,9000,8900,1000))
    assertEquals(1000L,Rules.remaining(false,1000,1,9000,1,1000))
  }
  @Test fun bypassAndDayOffSuppressAllOwnersUntilExpiry() {
    assertTrue(Rules.suppress(true,0));assertTrue(Rules.suppress(false,1));assertFalse(Rules.suppress(false,0))
    assertTrue(Rules.requests(RoutineKind.BEDTIME,true,0,0,100))
    assertFalse(Rules.requests(RoutineKind.FOCUS,false,0,0,100))
    assertTrue(Rules.requests(RoutineKind.BEDTIME,false,0,200,100))
    assertFalse(Rules.requests(RoutineKind.BEDTIME,false,0,200,200))
  }
  @Test fun wrongAnswersRetrySameExpectedValue() {
    assertFalse(Rules.verifies("123",456));assertFalse(Rules.verifies("456.0",456));assertTrue(Rules.verifies("456",456))
  }
  @Test fun temporaryExpiryFollowsLocalWakeAcrossZoneChanges() {
    val day=LocalDate.parse("2026-11-02")
    val madrid=Rules.overrideExpiry(routine(),day,zone)
    val utc=Rules.overrideExpiry(routine(),day,ZoneId.of("UTC"))
    assertEquals(3600000L,utc-madrid)
  }
  @Test fun colorExceptionDoesNotReleaseAnotherOwnersGrayscale() {
    assertFalse(Rules.grayRequest(RoutineKind.GRAYSCALE,true))
    assertTrue(listOf(Rules.grayRequest(RoutineKind.GRAYSCALE,true),Rules.grayRequest(RoutineKind.FOCUS,false)).any{it})
    assertFalse(Rules.grayRequest(RoutineKind.ACCESS,false))
  }
  @Test fun overnightBelongsToStartingWeekday() {
    assertTrue(Rules.active(routine(),time("2026-11-03T06:59:00+01:00[Europe/Madrid]"),zone))
    assertFalse(Rules.active(routine(),time("2026-11-03T07:00:00+01:00[Europe/Madrid]"),zone))
    assertFalse(Rules.active(routine(),time("2026-11-04T02:00:00+01:00[Europe/Madrid]"),zone))
  }
  @Test fun todayIncludesFollowingMorning() {
    assertTrue(Rules.active(routine(),time("2026-11-04T06:59:00+01:00[Europe/Madrid]"),zone,LocalDate.parse("2026-11-03")))
    assertFalse(Rules.active(routine(),time("2026-11-04T07:00:00+01:00[Europe/Madrid]"),zone,LocalDate.parse("2026-11-03")))
  }
  @Test fun bookingUsesElapsedHoursAcrossDst() {
    val start=LocalDate.parse("2026-03-30").atStartOfDay(zone).toInstant().toEpochMilli()
    assertTrue(Rules.canBook("2026-03-30",start-72*3600000,zone))
    assertFalse(Rules.canBook("2026-03-30",start-72*3600000+1,zone))
    assertFalse(Rules.canBook("2026-03-30",LocalDate.parse("2026-03-27").atStartOfDay(zone).toInstant().toEpochMilli(),zone))
  }
  @Test fun arithmeticAlwaysSatisfiesBounds() {
    repeat(10000) {
      val (expression, answer)=Rules.challenge(); val parts=expression.split(" ")
      assertEquals(5,parts.size)
      for(i in listOf(0,2,4)) assertTrue(parts[i].toInt() in 50..999)
      assertTrue(answer in -999..999)
      assertEquals(parts[0].toInt()+(if(parts[1]=="+")1 else -1)*parts[2].toInt()+(if(parts[3]=="+")1 else -1)*parts[4].toInt(),answer)
    }
  }
  private fun config(routines: String)="""{"version":1,"focusMinutes":50,"routines":[$routines]}"""
  private fun stored(id: String, windows: String="[]", extra: String="")="""{"id":"$id","title":"$id","enabled":false,"windows":$windows,"apps":["com.example.app"],"colorApps":[],"earlyStart":"20:00"$extra}"""
  private val validRoutines=listOf(stored("access"),stored("focus"),stored("grayscale"),stored("bedtime","""[{"start":"22:30","end":"07:00","days":[1,3]}]"""))
  @Test fun storedConfigSurvivesRoundTrip() {
    val parsed=Config.parse(config(validRoutines.joinToString(",")))
    assertEquals(parsed,Config.parse(parsed.toJson().toString()))
    val bedtime=parsed.routines.first{it.kind==RoutineKind.BEDTIME}
    assertEquals(listOf(1,3),bedtime.windows[0].days)
    assertEquals("22:30",bedtime.toJson().getJSONArray("windows").getJSONObject(0).getString("start"))
  }
  @Test(expected=IllegalArgumentException::class) fun configNeedsEveryKind() { Config.parse(config(validRoutines.drop(1).joinToString(","))) }
  @Test(expected=IllegalArgumentException::class) fun bedtimeNeedsOneWindow() { Config.parse(config((validRoutines.dropLast(1)+stored("bedtime")).joinToString(","))) }
  @Test(expected=IllegalArgumentException::class) fun earlyStartStartsAtTwenty() {
    Routine.parse(org.json.JSONObject(stored("focus").replace("20:00","19:59")))
  }
}
