package dev.danields.aall.movent

import android.app.NotificationManager
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class MoventModule : Module() {
  private val context get() = requireNotNull(appContext.reactContext)

  override fun definition() = ModuleDefinition {
    Name("Movent")
    Function("isRunning") { MoventService.isEnabled(context) }
    Function("setRunning") { running: Boolean -> MoventService.set(context, running) }
    Function("notificationsEnabled") {
      context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }
    Function("show") { title: String, text: String, lines: List<String>, offline: Boolean ->
      MoventService.show(context, Notice(title, text, lines, offline))
    }
  }
}
