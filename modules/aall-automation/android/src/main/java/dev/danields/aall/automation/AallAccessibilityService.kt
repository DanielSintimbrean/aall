package dev.danields.aall.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AallAccessibilityService : AccessibilityService() {
  companion object {
    var instance: AallAccessibilityService? = null
  }

  var foreground = ""
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
    if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
    val pkg = event.packageName?.toString() ?: return
    if (pkg == packageName && overlay != null) return
    foreground = pkg
    Engine(this).reconcile()
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
        text = "aall\nThis app is paused\n${decision.routines.joinToString { it.title }}"
        textSize = 24f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
      }
    )
    layout.addView(
      Button(this).apply {
        text = "Open aall"
        setOnClickListener {
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
        text = "Home"
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
