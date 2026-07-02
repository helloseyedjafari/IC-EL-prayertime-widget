package com.seyedjafari.prayertimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PrayerCoreTest {
  private fun fx(n: String) = javaClass.classLoader!!.getResourceAsStream(n)!!.bufferedReader().readText()

  @Test fun parsesLondon() {
    val t = PrayerCore.extractToday(fx("london-2026-07.html"), 2, 780, "London")
    assertEquals("02:29", t.dawn)
    assertEquals("04:48", t.sunrise)
    assertEquals("13:05", t.noon)
    assertEquals("21:36", t.maghrib)
    assertEquals("23:55", t.midnight)
  }

  @Test fun repairsCardiffSunrise() {
    val t = PrayerCore.extractToday(fx("cardiff-2026-07.html"), 2, 360, "Cardiff")
    assertEquals("05:00", t.sunrise) // between 04:59 and 05:01
    assertNotEquals("00:00", t.sunrise)
  }

  @Test fun doesNotRepairNearMidnight() {
    val t = PrayerCore.extractToday(fx("cardiff-2026-07.html"), 2, 360, "Cardiff")
    assertEquals("00:12", t.midnight)
  }

  @Test fun nextIsNoonAt1300() {
    val t = PrayerCore.extractToday(fx("london-2026-07.html"), 2, 780, "London")
    assertEquals("Noon", t.nextName)
    assertEquals("13:05", t.nextTime)
  }

  @Test fun nextIsMidnightAt2200() {
    val t = PrayerCore.extractToday(fx("london-2026-07.html"), 2, 1320, "London")
    assertEquals("Midnight", t.nextName)
  }

  @Test fun nextWrapsToDawn() {
    val t = PrayerCore.extractToday(fx("london-2026-07.html"), 2, 1439, "London")
    assertEquals("Dawn", t.nextName)
  }
}
