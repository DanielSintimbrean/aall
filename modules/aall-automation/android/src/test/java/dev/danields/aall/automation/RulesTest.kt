package dev.danields.aall.automation
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.time.*
class RulesTest {
  private val zone=ZoneId.of("Europe/Madrid")
  private fun time(s:String)=ZonedDateTime.parse(s).toInstant().toEpochMilli()
  private fun routine()=JSONObject("""{"enabled":true,"windows":[{"start":"22:30","end":"07:00","days":[1]}]}""")
  @Test fun timersUseElapsedWithinBootAndWallOnRecovery() {
    assertEquals(500L,Rules.remaining(true,1000,500,9000,999999,1000))
    assertEquals(0L,Rules.remaining(true,1000,1000,9000,0,1000))
    assertEquals(100L,Rules.remaining(false,1000,1,9000,8900,1000))
    assertEquals(1000L,Rules.remaining(false,1000,1,9000,1,1000))
  }
  @Test fun bypassAndDayOffSuppressAllOwnersUntilExpiry() {
    assertTrue(Rules.suppress(true,0));assertTrue(Rules.suppress(false,1));assertFalse(Rules.suppress(false,0))
    assertTrue(Rules.requests("bedtime",true,0,0,100))
    assertFalse(Rules.requests("focus",false,0,0,100))
    assertTrue(Rules.requests("bedtime",false,0,200,100))
    assertFalse(Rules.requests("bedtime",false,0,200,200))
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
    assertFalse(Rules.grayRequest("grayscale",true))
    assertTrue(listOf(Rules.grayRequest("grayscale",true),Rules.grayRequest("focus",false)).any{it})
    assertFalse(Rules.grayRequest("access",false))
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
}
