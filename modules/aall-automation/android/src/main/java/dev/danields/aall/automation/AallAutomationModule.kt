package dev.danields.aall.automation

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import android.content.Intent
import android.provider.Settings
import android.net.Uri

class AallAutomationModule : Module() {
  private val context get() = requireNotNull(appContext.reactContext)
  override fun definition() = ModuleDefinition {
    Name("AallAutomation")
    Function("initialize") { raw: String -> Engine(context).initialize(raw) }
    Function("getSnapshot") { Engine(context).snapshot().toString() }
    Function("reconcile") { Engine(context).reconcile() }
    Function("setConfig") { raw: String -> Engine(context).setConfig(raw) }
    Function("startFocus") { Engine(context).focus() }
    Function("challenge") { Engine(context).challenge() }
    Function("solve") { answer: String -> Engine(context).solve(answer) }
    Function("bookDay") { date: String -> Engine(context).book(date) }
    Function("cancelDay") { date: String -> Engine(context).cancel(date) }
    Function("overrideToday") { id: String, raw: String -> Engine(context).overrideToday(id,raw) }
    Function("testGrayscale") { enabled: Boolean -> Engine(context).testGray(enabled) }
    Function("installedApps") {
      context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
        .distinctBy{it.activityInfo.packageName}.map{mapOf("packageName" to it.activityInfo.packageName,"label" to it.loadLabel(context.packageManager).toString())}.sortedBy{it["label"]}
    }
    Function("openSettings") { kind: String ->
      val intent=when(kind) {
        "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        "dnd" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        "alarms" -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}"))
        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))
      }
      context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
  }
}
