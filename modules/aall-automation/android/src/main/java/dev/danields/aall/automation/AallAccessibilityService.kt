package dev.danields.aall.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AallAccessibilityService : AccessibilityService() {
  companion object {
    var instance: AallAccessibilityService? = null
    private const val SYSTEM_UI = "com.android.systemui"
  }

  var foreground = ""
  var foregroundApp = ""
    private set
  private var overlay: android.view.View? = null
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
    Engine(this).reconcile()
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
    Engine(this).reconcile()
  }

  /** Closing the shade or the lock screen sends no event for the app below, only a window change. */
  private fun windowsChanged() {
    if (foreground != SYSTEM_UI) return
    val active = windows.firstOrNull { it.isActive } ?: return
    if (active.type != AccessibilityWindowInfo.TYPE_APPLICATION) return
    val pkg = active.root?.packageName?.toString() ?: return
    foregroundApp = pkg
    foreground = pkg
    Engine(this).reconcile()
  }

  private fun isSystemApp(pkg: String) = try {
    packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
  } catch (_: PackageManager.NameNotFoundException) {
    false
  }

  fun update(decision: Decision) {
    val wm = getSystemService(WindowManager::class.java)
    if (!decision.blocked) {
      overlay?.let { wm.removeView(it) }
      overlay = null
      return
    }
    if (overlay != null) return
    val layout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setPadding(48, 48, 48, 48)
      setBackgroundColor(Color.rgb(8, 11, 11))
    }
    layout.addView(
      TextView(this).apply {
        text =
          "aall\nEsta aplicación está bloqueada\n${decision.routines.joinToString { it.title }}"
        textSize = 24f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
      }
    )
    layout.addView(
      Button(this).apply {
        text = "Abrir aall"
        setOnClickListener {
          this@AallAccessibilityService.foregroundApp = packageName
          this@AallAccessibilityService.foreground = packageName
          Engine(this@AallAccessibilityService).reconcile()
          startActivity(
            packageManager.getLaunchIntentForPackage(
              packageName
            )!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          )
        }
      }
    )
    layout.addView(
      Button(this).apply {
        text = "Inicio"
        setOnClickListener { performGlobalAction(GLOBAL_ACTION_HOME) }
      }
    )
    overlay = layout
    wm.addView(
      layout,
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        0,
        android.graphics.PixelFormat.OPAQUE
      )
    )
  }

  override fun onInterrupt() {
    overlay?.let { getSystemService(WindowManager::class.java).removeView(it) }
    overlay = null
  }

  override fun onDestroy() {
    if (powerRegistered) unregisterReceiver(powerReceiver)
    onInterrupt()
    instance = null
    super.onDestroy()
  }
}
