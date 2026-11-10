package dev.danields.aall.movent

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BoardTest {
  private val now = at(12, 50)

  private fun at(hour: Int, minute: Int, day: Int = 5) =
    ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, Board.zone).toInstant().toEpochMilli()

  private fun journey(
    scheduled: String,
    realtime: String? = null,
    position: Boolean = false,
    cancelled: Boolean = false,
    product: Int = 0
  ) = """{"date":"20261105","dirTxt":"Andorra la Vella","prodX":$product,"isCncl":$cancelled,
    "pos":${if (position) """{"x":1,"y":2}""" else "null"},
    "stbStop":{"dProdX":$product,"dTimeS":"$scheduled"${realtime?.let {
    ""","dTimeR":"$it""""
  } ?: ""}}}"""

  private fun response(vararg journeys: String) =
    """{"svcResL":[{"err":"OK","res":{"common":{"prodL":[
      {"nameS":"L3","prodCtx":{"line":"L3"}},{"nameS":"L4"}]},
      "jnyL":[${journeys.joinToString(",")}]}}]}"""

  private fun departure(scheduled: Long, realtime: Long? = null, located: Boolean = false) =
    Departure("L3", "Andorra la Vella", scheduled, realtime, located)

  @Test fun realtimeWinsAndOrdersTheBoard() {
    val board = Board.parse(
      response(journey("130000"), journey("131300", realtime = "125500", product = 1)),
      now
    )
    assertEquals(listOf("L4", "L3"), board.map { it.line })
    assertEquals(at(12, 55), board[0].time)
    assertEquals(at(13, 13), board[0].scheduled)
  }

  @Test fun dropsCancelledPastAndBeyondTheWindow() {
    val board = Board.parse(
      response(
        journey("125500", cancelled = true),
        journey("124000"),
        journey("131300", realtime = "124500"),
        journey("145100"),
        journey("145000")
      ),
      now
    )
    assertEquals(listOf(at(14, 50)), board.map { it.time })
  }

  @Test fun dayOffsetCrossesMidnight() {
    val board = Board.parse(response(journey("01003000")), at(23, 50, day = 5))
    assertEquals(at(0, 30, day = 6), board.single().time)
  }

  @Test fun clockTimesSurviveDaylightSavingChange() {
    val raw = response(journey("131300")).replace("20261105", "20261025")
    val before = ZonedDateTime.of(2026, 10, 25, 13, 0, 0, 0, Board.zone).toInstant().toEpochMilli()
    val expected = ZonedDateTime.of(
      2026,
      10,
      25,
      13,
      13,
      0,
      0,
      Board.zone
    ).toInstant().toEpochMilli()
    assertEquals(expected, Board.parse(raw, before).single().time)
  }

  @Test fun failedResponseThrows() {
    assertThrows(IllegalStateException::class.java) {
      Board.parse("""{"svcResL":[{"err":"H890"}]}""", now)
    }
  }

  @Test fun requestAsksForLocalTimeAtTheStop() {
    val request = Board.request(now)
    assertEquals(true, request.contains("\"date\":\"20261105\""))
    assertEquals(true, request.contains("\"time\":\"125000\""))
    assertEquals(true, request.contains("L=948@"))
  }

  @Test fun sourceSaysHowFarToTrustTheTime() {
    assertEquals("GPS", Board.source(departure(at(14, 0), realtime = at(14, 1)), now))
    assertEquals("Localizado", Board.source(departure(at(14, 0), located = true), now))
    assertEquals("Sin confirmar", Board.source(departure(at(13, 19)), now))
    assertNull(Board.source(departure(at(13, 20)), now))
  }

  @Test fun noticeShowsNextBusAndTheRest() {
    val notice = BoardNotice.of(
      listOf(departure(at(13, 0), realtime = at(12, 58)), departure(at(14, 0))),
      offline = false,
      now
    )
    assertEquals("L3 · 12:58 · en 8 min", notice.title)
    assertEquals("Andorra la Vella · GPS", notice.text)
    assertEquals(listOf("14:00 L3 · Andorra la Vella"), notice.lines)
  }

  @Test fun noticeKeepsLastBoardWhenOffline() {
    val notice = BoardNotice.of(listOf(departure(at(13, 0))), offline = true, now)
    assertEquals(true, notice.offline)
    assertEquals("L3 · 13:00 · en 10 min", notice.title)
  }

  @Test fun noticeExplainsAnEmptyBoard() {
    assertEquals("Buscando buses…", BoardNotice.of(null, false, now).text)
    assertEquals("Sin conexión", BoardNotice.of(null, true, now).text)
    assertEquals("Sin conexión", BoardNotice.of(emptyList(), true, now).text)
    assertEquals("Sin buses en las próximas 2 horas", BoardNotice.of(emptyList(), false, now).text)
  }
}
