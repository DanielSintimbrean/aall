package dev.danields.aall.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import dev.danields.aall.core.BlockNotice
import dev.danields.aall.core.Clock
import dev.danields.aall.core.Decision

class AallAccessibilityService : AccessibilityService() {
  companion object {
    var instance: AallAccessibilityService? = null
    private const val SYSTEM_UI = "com.android.systemui"
  }

  var foreground = ""
  var foregroundApp = ""
    private set
  private var overlay: android.view.View? = null
  private var overlayKey: String? = null
  private var powerRegistered = false
  private val powerReceiver = AutomationReceiver()

  override fun onServiceConnected() {
    instance = this
    registerReceiver(
      powerReceiver,
      IntentFilter().apply {
        addAction(Intent.ACTION_POWER_CONNECTED)
        addAction(Intent.ACTION_POWER_DISCONNECTED)
      },
      Context.RECEIVER_NOT_EXPORTED
    )
    powerRegistered = true
    engine(this).reconcile()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    when (event?.eventType) {
      AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> windowStateChanged(event)
      AccessibilityEvent.TYPE_WINDOWS_CHANGED -> windowsChanged()
    }
  }

  private fun windowStateChanged(event: AccessibilityEvent) {
    val pkg = event.packageName?.toString() ?: return
    // Keyboard popups, such as accents and emoji, are windows of their own.
    if (getSystemService(InputMethodManager::class.java).enabledInputMethodList.any {
        it.packageName == pkg
      }
    ) {
      return
    }
    if (pkg == packageName && overlay != null) return
    // System UI and Recents leave the underlying app in front for color exceptions.
    val systemUi = pkg == SYSTEM_UI ||
      (event.className == "com.android.quickstep.RecentsActivity" && isSystemApp(pkg))
    if (!systemUi) foregroundApp = pkg
    foreground = if (systemUi) SYSTEM_UI else pkg
    engine(this).reconcile()
  }

  /** An overlay can keep focus while the application underneath changes. */
  private fun windowsChanged() {
    val current = windows
    val active = current.firstOrNull { it.isActive }
    val app = when (active?.type) {
      AccessibilityWindowInfo.TYPE_APPLICATION -> active

      AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ->
        current.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }

      else -> null
    } ?: return
    val pkg = app.root?.packageName?.toString() ?: return
    if (pkg == foreground || (pkg == packageName && overlay != null)) return
    foregroundApp = pkg
    foreground = pkg
    engine(this).reconcile()
  }

  private fun isSystemApp(pkg: String) = try {
    packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
  } catch (_: PackageManager.NameNotFoundException) {
    false
  }

  fun update(decision: Decision, clock: Clock) {
    val wm = getSystemService(WindowManager::class.java)
    if (!decision.blocked) {
      overlay?.let { wm.removeView(it) }
      overlay = null
      overlayKey = null
      return
    }
    val app = foreground
    val notice = BlockNotice.of(decision, app, clock)
    // The text, not the deadline, which jitters by milliseconds between reconciles.
    val key = "$app|${notice.title}|${notice.text}|${notice.routines}"
    if (overlay != null && key == overlayKey) return
    overlayKey = key
    val view = BlockScreen(this, app, notice, onHome = {
      performGlobalAction(GLOBAL_ACTION_HOME)
    }, onOpen = {
      foregroundApp = packageName
      foreground = packageName
      engine(this).reconcile()
      startActivity(
        packageManager.getLaunchIntentForPackage(packageName)!!
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    }).build()
    overlay?.let { wm.removeView(it) }
    overlay = view
    wm.addView(
      view,
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.OPAQUE
      ).apply {
        layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
      }
    )
  }

  override fun onInterrupt() {
    overlay?.let { getSystemService(WindowManager::class.java).removeView(it) }
    overlay = null
    overlayKey = null
  }

  override fun onDestroy() {
    if (powerRegistered) unregisterReceiver(powerReceiver)
    onInterrupt()
    instance = null
    super.onDestroy()
  }
}
