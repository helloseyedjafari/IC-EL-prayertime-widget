package com.seyedjafari.prayertimes

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches + parses today's prayer times (via [PrayerCore]) and keeps a
 * per-city cache in SharedPreferences for offline / stale fallback.
 */
object PrayerRepository {
    private fun prefs(c: Context) = c.getSharedPreferences("prayer_cache", Context.MODE_PRIVATE)

    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Android-PrayerTimes/1.0")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        try {
            conn.inputStream.bufferedReader().use { return it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun cityFor(context: Context, appWidgetId: Int): String =
        prefs(context).getString("city_$appWidgetId", "London") ?: "London"

    fun setCity(context: Context, appWidgetId: Int, city: String) =
        prefs(context).edit().putString("city_$appWidgetId", city).apply()

    /** City shown in the standalone app screen (separate from per-widget cities). */
    fun appCity(context: Context): String =
        prefs(context).getString("app_city", "London") ?: "London"

    fun setAppCity(context: Context, city: String) =
        prefs(context).edit().putString("app_city", city).apply()

    fun removeWidget(context: Context, appWidgetId: Int) =
        prefs(context).edit().remove("city_$appWidgetId").apply()

    fun load(context: Context, city: String): PrayerTimes {
        val (y, m, d) = PrayerCore.nowLondon()
        return try {
            val html = fetch(PrayerCore.buildUrl(city, y, m))
            val t = PrayerCore.extractToday(html, d, PrayerCore.nowMinLondon(), city)
            writeCache(context, t)
            t
        } catch (e: Exception) {
            readCache(context, city)?.copy(stale = true) ?: throw e
        }
    }

    private fun writeCache(c: Context, t: PrayerTimes) {
        val o = JSONObject()
            .put("city", t.city).put("dawn", t.dawn).put("sunrise", t.sunrise)
            .put("noon", t.noon).put("maghrib", t.maghrib).put("midnight", t.midnight)
            .put("nextName", t.nextName).put("nextTime", t.nextTime)
        prefs(c).edit().putString("data_${t.city}", o.toString()).apply()
    }

    private fun readCache(c: Context, city: String): PrayerTimes? {
        val s = prefs(c).getString("data_$city", null) ?: return null
        return try {
            val o = JSONObject(s)
            PrayerTimes(
                o.getString("city"), o.getString("dawn"), o.getString("sunrise"),
                o.getString("noon"), o.getString("maghrib"), o.getString("midnight"),
                o.getString("nextName"), o.getString("nextTime"),
            )
        } catch (e: Exception) {
            null
        }
    }
}
