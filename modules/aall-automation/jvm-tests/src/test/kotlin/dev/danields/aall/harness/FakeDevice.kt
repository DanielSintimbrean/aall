package dev.danields.aall.harness

import dev.danields.aall.core.BlockNotice
import dev.danields.aall.core.Clock
import dev.danields.aall.core.Decision
import dev.danields.aall.core.Device
import dev.danields.aall.core.KeyValue
import dev.danields.aall.core.Permissions
import dev.danields.aall.core.StatusNotice

class FakeDevice(var clock: Clock) : Device {
  var permissions = Permissions(accessibility = true, dnd = true, alarms = true, grayscale = true)
  var plugged = false
  override var foreground = ""
  override var foregroundApp = ""
  val systemApps = mutableSetOf<String>()

  val secure = mutableMapOf<String, Int>()
  val failWrites = mutableSetOf<String>()
  var failDnd = false

  val dndWrites = mutableListOf<Boolean>()
  val dnd get() = dndWrites.lastOrNull() ?: false

  var alarm: Long? = null
  var nightLightStep: Long? = null

  /** What the block overlay shows, or null when no overlay is up. */
  var block: BlockNotice? = null
  var notice: StatusNotice? = null

  override fun clock() = clock

  override fun permissions() = permissions

  override fun plugged() = plugged

  override fun alwaysAllowed() = setOf(AALL, LAUNCHER, DIALER)

  override fun isSystemApp(pkg: String) = pkg in systemApps

  override fun getSecure(key: String, fallback: Int) = secure[key] ?: fallback

  override fun putSecure(key: String, value: Int): Boolean {
    if (key in failWrites) return false
    secure[key] = value
    return true
  }

  override fun setDnd(on: Boolean) {
    check(!failDnd) { "DND refused" }
    dndWrites.add(on)
  }

  override fun nightLightRange() = 2596..4082

  override fun scheduleAlarm(at: Long) {
    alarm = at
  }

  override fun scheduleNightLightStep(at: Long?) {
    nightLightStep = at
  }

  override fun showBlock(decision: Decision, clock: Clock) {
    if (!permissions.accessibility) return
    block = if (decision.blocked) BlockNotice.of(decision, foreground, clock) else null
  }

  override fun showStatus(notice: StatusNotice?) {
    this.notice = notice
  }

  override fun notificationsEnabled() = true

  override fun logError(message: String, error: Exception) = Unit

  companion object {
    const val AALL = "dev.danields.aall"
    const val LAUNCHER = "launcher"
    const val DIALER = "dialer"
  }
}

class MemoryStore : KeyValue {
  val values = mutableMapOf<String, Any>()

  override fun contains(key: String) = key in values

  override fun getString(key: String, fallback: String?) = values[key] as String? ?: fallback

  override fun getLong(key: String, fallback: Long) = values[key] as Long? ?: fallback

  override fun getInt(key: String, fallback: Int) = values[key] as Int? ?: fallback

  override fun getBoolean(key: String, fallback: Boolean) = values[key] as Boolean? ?: fallback

  override fun write(changes: Map<String, Any?>): Boolean {
    for ((key, value) in changes) {
      if (value == null) values.remove(key) else values[key] = value
    }
    return true
  }
}
