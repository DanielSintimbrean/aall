package dev.danields.aall.core

/** Android's color correction: whether it is on, and its mode. -1 means a mode has no stored value. */
data class ColorCorrection(val enabled: Int, val mode: Int)

/** The settings to write, if any, and what aall will restore later, or null when it owns nothing. */
data class GrayscaleStep(val owned: ColorCorrection?, val write: ColorCorrection?)

object Grayscale {
  val GRAY = ColorCorrection(1, 0)

  /** Restores only while the settings are still gray, so a user's own change during it stays. */
  fun advance(owned: ColorCorrection?, read: ColorCorrection, on: Boolean) = when {
    on -> GrayscaleStep(owned ?: read, GRAY.takeIf { owned == null })
    owned == null -> GrayscaleStep(null, null)
    else -> GrayscaleStep(null, owned.takeIf { read == GRAY })
  }
}
