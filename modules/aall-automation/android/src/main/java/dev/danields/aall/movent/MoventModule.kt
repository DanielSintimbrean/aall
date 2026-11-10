package dev.danields.aall.movent

import android.app.NotificationManager
import dev.danields.aall.core.Board
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.json.JSONArray
import org.json.JSONObject

class MoventModule : Module() {
  private val context get() = requireNotNull(appContext.reactContext)

  override fun definition() = ModuleDefinition {
    Name("AallMovent")
    AsyncFunction("board") {
      val now = System.currentTimeMillis()
      JSONArray(
        Board.fetch(now).map { departure ->
          JSONObject()
            .put("line", departure.line)
            .put("destination", departure.destination)
            .put("clock", Board.clock(departure.time))
            .put("minutes", Board.minutes(departure, now))
            .put("source", Board.source(departure, now) ?: JSONObject.NULL)
            .put(
              "scheduledClock",
              if (departure.time != departure.scheduled) {
                Board.clock(departure.scheduled)
              } else {
                JSONObject.NULL
              }
            )
        }
      ).toString()
    }
    Function("isRunning") { MoventService.isEnabled(context) }
    Function("setRunning") { running: Boolean -> MoventService.set(context, running) }
    Function("notificationsEnabled") {
      context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }
  }
}
