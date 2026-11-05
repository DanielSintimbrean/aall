package dev.danields.aall.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle

class StatusNotifier(private val context: Context, private val prefs: SharedPreferences) {
  private val manager get() = context.getSystemService(NotificationManager::class.java)

  fun enabled() = manager.areNotificationsEnabled() &&
    manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

  /** Reconcile runs on every app switch, so this only posts when the notice differs from what is showing. */
  fun show(notice: StatusNotice?) {
    val showing = manager.activeNotifications.firstOrNull { it.id == ID }
    if (notice == null) {
      if (showing != null) manager.cancel(ID)
      prefs.edit().remove(DISMISSED_KEY).apply()
      return
    }
    if (showing?.notification?.extras?.getString(KEY) == notice.key ||
      prefs.getString(DISMISSED_KEY, null) == notice.key ||
      !enabled()
    ) {
      return
    }
    // Recreated on each post so the channel gains the DND bypass once DND access is granted.
    manager.createNotificationChannel(channel())
    manager.notify(ID, build(notice))
    prefs.edit().remove(DISMISSED_KEY).apply()
  }

  /** Android only honors the bypass for apps with DND access. Focus and Bedtime need that access to turn DND on, and without the bypass aall's own DND rule hides this notification. */
  private fun channel() =
    NotificationChannel(CHANNEL, "Estado", NotificationManager.IMPORTANCE_LOW).apply {
      description = "Rutinas activas y cuándo terminan."
      setBypassDnd(true)
      setShowBadge(false)
    }

  private fun build(notice: StatusNotice): Notification {
    val open = PendingIntent.getActivity(
      context,
      0,
      context.packageManager.getLaunchIntentForPackage(context.packageName),
      PendingIntent.FLAG_IMMUTABLE
    )
    val dismissed = Intent(
      context,
      AutomationReceiver::class.java
    ).setAction(ACTION_DISMISSED).putExtra(KEY, notice.key)
    val builder = Notification.Builder(context, CHANNEL)
      .setSmallIcon(R.drawable.aall_status)
      .setColor(Color.parseColor("#5eead4"))
      .setContentTitle(notice.title)
      .setCategory(Notification.CATEGORY_STATUS)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setLocalOnly(true)
      .setShowWhen(false)
      .setContentIntent(open)
      .setDeleteIntent(
        PendingIntent.getBroadcast(
          context,
          0,
          dismissed,
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
      )
      .addExtras(Bundle().apply { putString(KEY, notice.key) })
    if (notice.text.isNotEmpty()) {
      builder.setContentText(
        notice.text
      ).setStyle(Notification.BigTextStyle().bigText(notice.text))
    }
    notice.countdownTo?.let { deadline ->
      builder.setWhen(
        deadline
      ).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
    }
    return builder.build()
  }

  companion object {
    const val ACTION_DISMISSED = "dev.danields.aall.STATUS_DISMISSED"
    const val KEY = "dev.danields.aall.STATUS_KEY"
    private const val DISMISSED_KEY = "statusDismissed"
    private const val CHANNEL = "status"
    private const val ID = 1

    /** Android 14 lets people swipe away ongoing notifications, so a dismissed notice stays gone until the status changes. */
    fun dismissed(prefs: SharedPreferences, key: String?) {
      prefs.edit().putString(DISMISSED_KEY, key).apply()
    }
  }
}
