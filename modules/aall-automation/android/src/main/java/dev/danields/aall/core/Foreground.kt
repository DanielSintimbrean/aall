package dev.danields.aall.core

/** The window in front, and the app underneath it that color exceptions follow. */
data class Front(val foreground: String, val foregroundApp: String)

enum class WindowType { APPLICATION, ACCESSIBILITY_OVERLAY, OTHER }

/** An interactive window; [pkg] is null when its content can't be read. */
data class ScreenWindow(val type: WindowType, val active: Boolean, val pkg: String?)

/** Decides which app is in front from accessibility events. Null means the event changes nothing. */
object Foreground {
  const val SYSTEM_UI = "com.android.systemui"
  private const val RECENTS = "com.android.quickstep.RecentsActivity"

  fun stateChanged(
    pkg: String,
    className: String?,
    systemApp: Boolean,
    keyboards: Set<String>,
    self: String,
    overlay: Boolean,
    current: Front
  ): Front? {
    // Keyboard popups, such as accents and emoji, are windows of their own.
    if (pkg in keyboards) return null
    if (pkg == self && overlay) return null
    // System UI and Recents leave the underlying app in front for color exceptions.
    val systemUi = pkg == SYSTEM_UI || (className == RECENTS && systemApp)
    return if (systemUi) Front(SYSTEM_UI, current.foregroundApp) else Front(pkg, pkg)
  }

  /** Closing the shade sends no state change for the app below, and aall's overlay can keep focus. */
  fun windowsChanged(
    windows: List<ScreenWindow>,
    self: String,
    overlay: Boolean,
    current: Front
  ): Front? {
    val active = windows.firstOrNull { it.active }
    val app = when (active?.type) {
      WindowType.APPLICATION -> active
      WindowType.ACCESSIBILITY_OVERLAY -> windows.firstOrNull { it.type == WindowType.APPLICATION }
      else -> null
    }
    val pkg = app?.pkg ?: return null
    if (pkg == current.foreground || (pkg == self && overlay)) return null
    return Front(pkg, pkg)
  }
}
