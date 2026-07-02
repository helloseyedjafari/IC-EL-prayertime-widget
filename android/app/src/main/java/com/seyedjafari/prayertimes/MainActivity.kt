package com.seyedjafari.prayertimes

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Launcher screen: shows today's prayer-times table for a chosen city, with the
 * "add widget to Home screen" controls pinned to the bottom.
 */
class MainActivity : Activity() {

    private val londonSample = PrayerTimes(
        city = "London", dawn = "02:29", sunrise = "04:48", noon = "13:05",
        maghrib = "21:36", midnight = "23:55", nextName = "Maghrib", nextTime = "21:36",
    )

    private val rowIds = mapOf(
        "Dawn" to R.id.row_dawn, "Sunrise" to R.id.row_sunrise, "Noon" to R.id.row_noon,
        "Maghrib" to R.id.row_maghrib, "Midnight" to R.id.row_midnight,
    )
    private val timeIds = listOf(
        R.id.dawn_time, R.id.sunrise_time, R.id.noon_time, R.id.maghrib_time, R.id.midnight_time,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val city = PrayerRepository.appCity(this)
        if (city == "London") renderTable(londonSample) else renderLoading(city)
        loadCity(city)

        findViewById<LinearLayout>(R.id.city_selector).setOnClickListener { showCityPicker() }

        val addButton = findViewById<Button>(R.id.add_button)
        val awm = AppWidgetManager.getInstance(this)
        if (awm.isRequestPinAppWidgetSupported) {
            addButton.setOnClickListener {
                awm.requestPinAppWidget(
                    ComponentName(this, PrayerWidgetProvider::class.java), null, null,
                )
            }
        } else {
            addButton.setOnClickListener { }
            addButton.text = getString(R.string.add_widget_manual)
            addButton.alpha = 0.5f
            findViewById<TextView>(R.id.pin_hint).visibility = View.GONE
        }
    }

    private fun showCityPicker() {
        val cities = PrayerCore.CITIES.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Choose a city")
            .setItems(cities) { _, which ->
                val city = cities[which]
                PrayerRepository.setAppCity(this, city)
                renderLoading(city)
                loadCity(city)
            }
            .show()
    }

    private fun loadCity(city: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val data = runCatching { PrayerRepository.load(applicationContext, city) }.getOrNull()
            if (data != null) withContext(Dispatchers.Main) { renderTable(data) }
        }
    }

    private fun renderLoading(city: String) {
        findViewById<TextView>(R.id.city).text = city.uppercase()
        for (id in timeIds) findViewById<TextView>(id).text = "··:··"
        findViewById<TextView>(R.id.next_label).text = ""
        findViewById<TextView>(R.id.stale).visibility = View.GONE
        for (rid in rowIds.values) findViewById<View>(rid).setBackgroundResource(0)
    }

    private fun renderTable(d: PrayerTimes) {
        findViewById<TextView>(R.id.city).text = d.city.uppercase()
        findViewById<TextView>(R.id.dawn_time).text = d.dawn
        findViewById<TextView>(R.id.sunrise_time).text = d.sunrise
        findViewById<TextView>(R.id.noon_time).text = d.noon
        findViewById<TextView>(R.id.maghrib_time).text = d.maghrib
        findViewById<TextView>(R.id.midnight_time).text = d.midnight
        findViewById<TextView>(R.id.next_label).text = "Next · ${d.nextName} ${d.nextTime}"
        findViewById<TextView>(R.id.stale).visibility = if (d.stale) View.VISIBLE else View.GONE
        for ((name, rid) in rowIds) {
            findViewById<View>(rid)
                .setBackgroundResource(if (name == d.nextName) R.drawable.row_highlight else 0)
        }
    }
}
