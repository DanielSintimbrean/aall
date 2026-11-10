package dev.danields.aall.core

/** Everything Engine reads from and does to Android. */
interface Device {
  fun clock(): Clock

  fun permissions(): Permissions

  fun plugged(): Boolean

  /** aall itself, the home app and the default dialer. */
  fun alwaysAllowed(): Set<String>

  /** The window in front, which is System UI while the shade is down. */
  val foreground: String

  /** The app underneath System UI, which color exceptions follow. */
  val foregroundApp: String

  fun isSystemApp(pkg: String): Boolean

  fun getSecure(key: String, fallback: Int): Int

  fun putSecure(key: String, value: Int): Boolean

  fun setDnd(on: Boolean)

  fun nightLightRange(): IntRange

  fun scheduleAlarm(at: Long)

  fun scheduleNightLightStep(at: Long?)

  fun showBlock(decision: Decision, clock: Clock)

  fun showStatus(notice: StatusNotice?)

  fun notificationsEnabled(): Boolean

  fun logError(message: String, error: Exception)
}
