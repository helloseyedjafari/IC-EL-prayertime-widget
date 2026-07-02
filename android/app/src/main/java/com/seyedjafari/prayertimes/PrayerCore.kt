package com.seyedjafari.prayertimes

import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Kotlin port of shared/prayer-core.js — kept in sync with
 * shared/parsing-contract.md. Pure JVM (java.time only), no Android deps,
 * so it is unit-testable on the JVM.
 */
data class PrayerTimes(
  val city: String,
  val dawn: String,
  val sunrise: String,
  val noon: String,
  val maghrib: String,
  val midnight: String,
  val nextName: String,
  val nextTime: String,
  val stale: Boolean = false,
)

object PrayerCore {
  val CITIES = listOf("London", "Cardiff", "Glasgow", "Manchester", "Newcastle")
  const val SOURCE_BASE = "https://ic-el.uk/wp-content/icel/praying_timetable/prayer_times_en.php"
  val LONDON: ZoneId = ZoneId.of("Europe/London")

  private data class Row(
    val day: Int, val dawn: String, val sunrise: String, val noon: String,
    val maghrib: String, val midnight: String, val isToday: Boolean,
  )

  fun buildUrl(city: String, y: Int, m: Int): String = "$SOURCE_BASE?year=$y&city=$city&month=$m"

  private val rowRe = Regex("""<tr ID="(OddRows|EvenRows|TodayRow)">([\s\S]*?)</tr>""")
  private val cellRe = Regex("""<td[^>]*>([\s\S]*?)</td>""")
  private val tagRe = Regex("""<[^>]*>""")
  private val hhmm = Regex("""^\d{2}:\d{2}$""")

  private fun parseRows(html: String): List<Row> = rowRe.findAll(html).mapNotNull { mr ->
    val cells = cellRe.findAll(mr.groupValues[2])
      .map { tagRe.replace(it.groupValues[1], "").trim() }.toList()
    if (cells.size >= 7) Row(
      cells[1].toIntOrNull() ?: -1, cells[2], cells[3], cells[4], cells[5], cells[6],
      mr.groupValues[1] == "TodayRow",
    ) else null
  }.toList()

  private fun toMin(s: String): Int { val p = s.split(":"); return p[0].toInt() * 60 + p[1].toInt() }
  private fun fromMin(x: Int): String { val h = (x / 60) % 24; val m = x % 60; return "%02d:%02d".format(h, m) }
  private fun valid(s: String): Boolean = hhmm.matches(s) && s != "00:00"

  private fun repair(rows: List<Row>, idx: Int, get: (Row) -> String): String {
    val v = get(rows[idx]); if (valid(v)) return v
    var prev: String? = null; var next: String? = null
    for (i in idx - 1 downTo 0) if (valid(get(rows[i]))) { prev = get(rows[i]); break }
    for (i in idx + 1 until rows.size) if (valid(get(rows[i]))) { next = get(rows[i]); break }
    return when {
      prev != null && next != null -> fromMin(Math.round((toMin(prev) + toMin(next)) / 2.0).toInt())
      prev != null -> prev
      next != null -> next
      else -> v
    }
  }

  private fun computeNext(t: Map<String, String>, nowMin: Int): Pair<String, String> {
    val order = listOf(
      "Dawn" to "dawn", "Sunrise" to "sunrise", "Noon" to "noon",
      "Maghrib" to "maghrib", "Midnight" to "midnight",
    )
    val ev = order.map { (name, key) ->
      var mm = toMin(t[key]!!); if (key == "midnight" && mm < 180) mm += 1440
      Triple(name, t[key]!!, mm)
    }
    val up = ev.filter { it.third > nowMin }.sortedBy { it.third }
    val n = if (up.isNotEmpty()) up[0] else ev[0]
    return n.first to n.second
  }

  fun extractToday(html: String, todayDay: Int, nowMin: Int, city: String): PrayerTimes {
    val rows = parseRows(html)
    require(rows.isNotEmpty()) { "no rows" }
    var idx = rows.indexOfFirst { it.isToday }
    if (idx < 0) idx = rows.indexOfFirst { it.day == todayDay }
    require(idx >= 0) { "today not found" }
    val row = rows[idx]
    val times = mapOf(
      "dawn" to repair(rows, idx) { it.dawn },
      "sunrise" to repair(rows, idx) { it.sunrise },
      "noon" to repair(rows, idx) { it.noon },
      "maghrib" to row.maghrib,
      "midnight" to row.midnight,
    )
    val (nn, nt) = computeNext(times, nowMin)
    return PrayerTimes(
      city, times["dawn"]!!, times["sunrise"]!!, times["noon"]!!,
      times["maghrib"]!!, times["midnight"]!!, nn, nt,
    )
  }

  /** year, month, day in Europe/London. */
  fun nowLondon(): Triple<Int, Int, Int> {
    val z = ZonedDateTime.now(LONDON)
    return Triple(z.year, z.monthValue, z.dayOfMonth)
  }

  /** minutes-of-day in Europe/London. */
  fun nowMinLondon(): Int {
    val z = ZonedDateTime.now(LONDON)
    return z.hour * 60 + z.minute
  }
}
