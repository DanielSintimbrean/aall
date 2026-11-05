package dev.danields.aall.movent

import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

data class Departure(
  val line: String,
  val destination: String,
  val scheduled: Long,
  val realtime: Long?,
  val located: Boolean
) {
  val time get() = realtime ?: scheduled
}

/** Departures from Cascada de les Moles (stop 126), read from the gateway behind Mou-te's web app. */
object Board {
  val zone: ZoneId = ZoneId.of("Europe/Andorra")
  const val WINDOW_MINUTES = 120
  private const val MINUTE = 60_000L
  private const val UNCONFIRMED_WITHIN = 30 * MINUTE
  private const val GATE = "https://feda.hafas.cloud/gate"
  private const val STOP = "A=1@O=Cascada de les Moles - 126@X=1615568@Y=42572769@U=80@L=948@"
  private val date = DateTimeFormatter.ofPattern("yyyyMMdd")
  private val time = DateTimeFormatter.ofPattern("HHmmss")
  private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

  fun fetch(now: Long): List<Departure> {
    val connection = URL(GATE).openConnection() as HttpURLConnection
    try {
      connection.requestMethod = "POST"
      connection.connectTimeout = 15_000
      connection.readTimeout = 15_000
      connection.doOutput = true
      connection.setRequestProperty("Content-Type", "application/json")
      connection.outputStream.use { it.write(request(now).toByteArray()) }
      return parse(connection.inputStream.bufferedReader().use { it.readText() }, now)
    } finally {
      connection.disconnect()
    }
  }

  fun request(now: Long): String {
    val moment = Instant.ofEpochMilli(now).atZone(zone)
    val board = JSONObject()
      .put("dur", WINDOW_MINUTES)
      .put("stbLoc", JSONObject().put("type", "S").put("lid", STOP).put("extId", "948"))
      .put("type", "DEP")
      .put("date", moment.format(date))
      .put("time", moment.format(time))
      .put("sort", "RT")
      .put("maxJny", 40)
      .put("getJnySameTime", true)
    return JSONObject()
      .put("id", "aall")
      .put("ver", "1.85")
      .put("lang", "es")
      .put(
        "client",
        JSONObject()
          .put("id", "HAFAS")
          .put("type", "WEB")
          .put("name", "FEDA_webapp_prod")
          .put("l", "vs_webapp")
      )
      .put("auth", JSONObject().put("type", "AID").put("aid", "nextgen"))
      .put(
        "svcReqL",
        JSONArray().put(JSONObject().put("meth", "StationBoard").put("req", board))
      )
      .toString()
  }

  fun parse(raw: String, now: Long): List<Departure> {
    val result = JSONObject(raw).getJSONArray("svcResL").getJSONObject(0)
    val response = result.optJSONObject("res")
      ?: error("Mou-te error: ${result.optString("err", "no response")}")
    val products = response.optJSONObject("common")?.optJSONArray("prodL")
    val journeys = response.optJSONArray("jnyL") ?: return emptyList()
    return (0 until journeys.length()).mapNotNull { index ->
      val journey = journeys.getJSONObject(index)
      val stop = journey.getJSONObject("stbStop")
      if (journey.optBoolean("isCncl") || stop.optBoolean("dCncl")) return@mapNotNull null
      val day = LocalDate.parse(journey.getString("date"), date)
      val product = products?.optJSONObject(stop.optInt("dProdX", journey.optInt("prodX", -1)))
      Departure(
        line = product?.optJSONObject("prodCtx")?.optString("line").orEmpty()
          .ifEmpty { product?.optString("nameS").orEmpty() }
          .ifEmpty { "Bus" },
        destination = journey.optString("dirTxt"),
        scheduled = millis(day, stop.getString("dTimeS")),
        realtime = stop.optString("dTimeR").ifEmpty { null }?.let { millis(day, it) },
        located = !journey.isNull("pos")
      )
    }.let { upcoming(it, now) }
  }

  /** Also drops buses that left since the last fetch, so a failed refresh can still show what is ahead. */
  fun upcoming(departures: List<Departure>, now: Long) = departures
    .filter { it.time in now - MINUTE + 1..now + WINDOW_MINUTES * MINUTE }
    .sortedBy { it.time }

  fun source(departure: Departure, now: Long) = when {
    departure.realtime != null -> "GPS"
    departure.located -> "Localizado"
    departure.scheduled - now < UNCONFIRMED_WITHIN -> "Sin confirmar"
    else -> null
  }

  fun minutes(departure: Departure, now: Long) = ((departure.time - now) / MINUTE).coerceAtLeast(0)

  fun clock(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(clockFormat)

  /** Times are HHmmss, with two leading digits for a day offset when the journey crosses midnight. */
  private fun millis(day: LocalDate, value: String): Long {
    val offset = if (value.length == 8) value.take(2).toLong() else 0
    val clock = value.takeLast(6)
    return day.plusDays(offset).atStartOfDay(zone)
      .plusHours(clock.take(2).toLong())
      .plusMinutes(clock.substring(2, 4).toLong())
      .plusSeconds(clock.substring(4).toLong())
      .toInstant()
      .toEpochMilli()
  }
}
