package dev.danields.aall.core

import dev.danields.aall.core.Foreground.SYSTEM_UI
import dev.danields.aall.core.WindowType.ACCESSIBILITY_OVERLAY
import dev.danields.aall.core.WindowType.APPLICATION
import dev.danields.aall.core.WindowType.OTHER
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundTest {
  private val aall = "dev.danields.aall"
  private val chrome = Front("chrome", "chrome")
  private val shade = Front(SYSTEM_UI, "chrome")

  private fun state(
    pkg: String,
    current: Front = chrome,
    className: String? = null,
    systemApp: Boolean = false,
    overlay: Boolean = false
  ) = Foreground.stateChanged(pkg, className, systemApp, setOf("keyboard"), aall, overlay, current)

  private fun windows(
    vararg windows: ScreenWindow,
    current: Front = chrome,
    overlay: Boolean = false
  ) = Foreground.windowsChanged(windows.toList(), aall, overlay, current)

  @Test fun decidesTheAppInFront() {
    val recents = "com.android.quickstep.RecentsActivity"
    val rows = listOf(
      Triple("an app opens", state("maps"), Front("maps", "maps")),
      Triple("the shade keeps the app below", state(SYSTEM_UI), shade),
      Triple(
        "system Recents keeps the app below",
        state("launcher", className = recents, systemApp = true),
        Front(SYSTEM_UI, "chrome")
      ),
      Triple(
        "a third-party Recents is an app",
        state("launcher", className = recents),
        Front("launcher", "launcher")
      ),
      Triple("#18 a keyboard popup is not an app", state("keyboard"), null),
      Triple(
        "#18 closing the shade brings back the app below",
        windows(ScreenWindow(APPLICATION, true, "chrome"), current = shade),
        chrome
      ),
      Triple(
        "#26 under aall's overlay, the app below opens",
        windows(
          ScreenWindow(ACCESSIBILITY_OVERLAY, true, null),
          ScreenWindow(APPLICATION, false, "maps"),
          overlay = true
        ),
        Front("maps", "maps")
      ),
      Triple("aall under its own overlay", state(aall, overlay = true), null),
      Triple(
        "aall's window under its own overlay",
        windows(
          ScreenWindow(ACCESSIBILITY_OVERLAY, true, null),
          ScreenWindow(APPLICATION, false, aall),
          overlay = true
        ),
        null
      ),
      Triple("aall without an overlay", state(aall), Front(aall, aall)),
      Triple(
        "the same app stays in front",
        windows(ScreenWindow(APPLICATION, true, "chrome")),
        null
      ),
      Triple(
        "a system window is active",
        windows(ScreenWindow(OTHER, true, null), ScreenWindow(APPLICATION, false, "maps")),
        null
      ),
      Triple("an unreadable window", windows(ScreenWindow(APPLICATION, true, null)), null)
    )
    for ((name, actual, expected) in rows) assertEquals(name, expected, actual)
  }
}
