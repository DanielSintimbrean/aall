package dev.danields.aall.stories

import dev.danields.aall.harness.FakeDevice
import dev.danields.aall.harness.Phone
import dev.danields.aall.harness.access
import dev.danields.aall.harness.bedtime
import dev.danields.aall.harness.grayscale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingTest {
  @Test fun bedtimeBlocksUnlistedAppsButNotTheListOrEssentials() {
    val phone = Phone("2026-11-02T23:00", bedtime(apps = listOf("clock")))
    val essentials = listOf(
      "com.android.settings", "com.android.settings.intelligence",
      "com.android.providers.downloads", "com.android.providers.downloads.ui",
      "com.android.documentsui", "com.google.android.documentsui",
      "com.google.android.apps.nbu.files", "com.amaze.filemanager",
      "com.android.packageinstaller", "com.android.certinstaller"
    )
    for (pkg in essentials + listOf("clock", FakeDevice.LAUNCHER, FakeDevice.DIALER)) {
      phone.open(pkg)
      assertFalse(pkg, phone.blocked)
    }
    phone.open("game")
    assertTrue(phone.blocked)
    assertEquals("Es hora de descansar", phone.device.block?.title)
  }

  @Test fun systemAppsStayOpenDuringBedtimeButExplicitBlocksStillApply() {
    val phone = Phone("2026-11-02T23:00", bedtime()) { systemApps.add("system.app") }
    phone.open("system.app")
    assertFalse(phone.blocked)
    assertTrue(phone.grayscale)
    phone.open("game")
    assertTrue(phone.blocked)
    val listed = Phone("2026-11-02T23:00", bedtime(), access(apps = listOf("system.app"))) {
      systemApps.add("system.app")
    }
    listed.open("system.app")
    assertTrue(listed.blocked)
  }

  @Test fun theShadeKeepsTheAppColorExceptionButReleasesTheBlockingScreen() {
    val phone = Phone("2026-11-02T23:00", bedtime(colorApps = listOf("maps")))
    phone.open("maps")
    assertTrue(phone.blocked)
    assertFalse(phone.grayscale)
    phone.pullDownShade()
    assertFalse(phone.blocked)
    assertFalse(phone.grayscale)
    phone.open("blocked")
    assertTrue(phone.blocked)
    assertTrue(phone.grayscale)
    phone.pullDownShade()
    assertFalse(phone.blocked)
    assertTrue(phone.grayscale)
  }

  @Test fun bedtimeBlocksTheAppLeftInFrontAfterTheShadeCloses() {
    val phone = Phone("2026-11-02T22:00", bedtime())
    phone.open("chrome")
    phone.pullDownShade()
    phone.closeShade()
    phone.advanceTo("22:30")
    assertEquals("chrome", phone.device.foreground)
    assertEquals("Es hora de descansar", phone.device.block?.title)
  }

  @Test fun aColorExceptionOnlyReleasesItsOwnRoutinesGrayscale() {
    val phone = Phone(
      "2026-11-02T23:00",
      bedtime(colorApps = listOf("maps")),
      grayscale("21:00" to "07:00")
    )
    phone.open("maps")
    assertTrue(phone.grayscale)
  }

  @Test fun accessBlocksListedAppsOutsideItsHours() {
    val phone = Phone("2026-11-02T12:00", access("12:30" to "13:15", apps = listOf("social")))
    phone.open("other")
    assertFalse(phone.blocked)
    phone.open("social")
    assertTrue(phone.blocked)
    assertEquals("Fuera de horario", phone.device.block?.title)
    phone.advanceTo("12:30")
    assertFalse(phone.blocked)
    phone.advanceTo("13:15")
    assertTrue(phone.blocked)
  }

  @Test fun grayscaleNeverBlocksItsApps() {
    val phone = Phone("2026-11-02T12:00", grayscale("09:00" to "18:00", apps = listOf("social")))
    phone.open("social")
    assertFalse(phone.blocked)
  }
}
