package dev.danields.aall.automation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.io.ByteArrayOutputStream

class AallAutomationModule : Module() {
  private val context get() = requireNotNull(appContext.reactContext)

  override fun definition() = ModuleDefinition {
    Name("AallAutomation")
    OnCreate {
      context.getSystemService(android.app.LocaleManager::class.java).applicationLocales =
        android.os.LocaleList.forLanguageTags("es-ES")
    }
    Function("initialize") { raw: String -> engine(context).initialize(raw) }
    Function("getSnapshot") { engine(context).snapshot().toString() }
    Function("reconcile") { engine(context).reconcile() }
    Function("setFocusMinutes") { minutes: Int -> engine(context).setFocusMinutes(minutes) }
    Function("saveWeekly") { raw: String -> engine(context).saveWeekly(raw) }
    Function("requestTurnOff") { id: String -> engine(context).requestTurnOff(id) }
    Function("cancelTurnOff") { id: String -> engine(context).cancelTurnOff(id) }
    Function("startFocus") { engine(context).focus() }
    Function("challenge") { engine(context).challenge() }
    Function("solve") { answer: String -> engine(context).solve(answer) }
    Function("bookDay") { date: String -> engine(context).book(date) }
    Function("cancelDay") { date: String -> engine(context).cancel(date) }
    Function("overrideToday") { id: String, raw: String -> engine(context).overrideToday(id, raw) }
    Function("testGrayscale") { enabled: Boolean -> engine(context).testGray(enabled) }
    Function("installedApps") {
      context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        0
      )
        .distinctBy { it.activityInfo.packageName }.map {
          mapOf(
            "packageName" to it.activityInfo.packageName,
            "label" to it.loadLabel(context.packageManager).toString()
          )
        }.sortedBy { it["label"] }
    }
    AsyncFunction("appIcons") {
      val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
      context.packageManager.queryIntentActivities(launcher, 0).distinctBy {
        it.activityInfo.packageName
      }
        .associate {
          it.activityInfo.packageName to iconDataUri(it.loadIcon(context.packageManager))
        }
    }
    Function("openSettings") { kind: String ->
      val intent = when (kind) {
        "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        "dnd" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

        "alarms" -> Intent(
          Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
          Uri.parse("package:${context.packageName}")
        )

        "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
          .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        else -> Intent(
          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.parse("package:${context.packageName}")
        )
      }
      context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
  }
}

private fun iconDataUri(icon: Drawable): String {
  val size = 96
  val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
  icon.setBounds(0, 0, size, size)
  icon.draw(Canvas(bitmap))
  val png = ByteArrayOutputStream()
  bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)
  bitmap.recycle()
  return "data:image/png;base64," + Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP)
}
