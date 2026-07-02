package com.seyedjafari.prayertimes

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Launcher screen. Gives the app a Home-screen/app-drawer icon and a one-tap
 * button that asks the launcher to pin the widget. Falls back to written
 * instructions on launchers that don't support pinning.
 */
class MainActivity : Activity() {

    private val sample = PrayerTimes(
        city = "London", dawn = "02:29", sunrise = "04:48", noon = "13:05",
        maghrib = "21:36", midnight = "23:55", nextName = "Maghrib", nextTime = "21:36",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Live preview of the actual widget layout
        val container = findViewById<FrameLayout>(R.id.preview_container)
        val preview = layoutInflater.inflate(R.layout.widget_large, container, false)
        container.addView(preview)
        renderPreview(preview, sample)
        loadRealPreview(preview)

        // One-tap "add widget" via the launcher's pin flow
        val addButton = findViewById<Button>(R.id.add_button)
        val awm = AppWidgetManager.getInstance(this)
        if (awm.isRequestPinAppWidgetSupported) {
            addButton.setOnClickListener {
                awm.requestPinAppWidget(
                    ComponentName(this, PrayerWidgetProvider::class.java), null, null,
                )
            }
        } else {
            // Launcher can't pin programmatically — steer the user to do it manually
            addButton.setOnClickListener { }
            addButton.text = getString(R.string.add_widget_manual)
            addButton.alpha = 0.5f
            findViewById<TextView>(R.id.pin_hint).visibility = View.GONE
        }
    }

    private fun loadRealPreview(preview: View) {
        CoroutineScope(Dispatchers.IO).launch {
            val data = runCatching { PrayerRepository.load(applicationContext, "London") }.getOrNull()
            if (data != null) withContext(Dispatchers.Main) { renderPreview(preview, data) }
        }
    }

    private fun renderPreview(v: View, d: PrayerTimes) {
        v.findViewById<TextView>(R.id.city).text = d.city.uppercase()
        v.findViewById<TextView>(R.id.dawn_time).text = d.dawn
        v.findViewById<TextView>(R.id.sunrise_time).text = d.sunrise
        v.findViewById<TextView>(R.id.noon_time).text = d.noon
        v.findViewById<TextView>(R.id.maghrib_time).text = d.maghrib
        v.findViewById<TextView>(R.id.midnight_time).text = d.midnight
        v.findViewById<TextView>(R.id.next_label).text = "Next · ${d.nextName} ${d.nextTime}"

        val rows = mapOf(
            "Dawn" to R.id.row_dawn, "Sunrise" to R.id.row_sunrise, "Noon" to R.id.row_noon,
            "Maghrib" to R.id.row_maghrib, "Midnight" to R.id.row_midnight,
        )
        for ((name, rid) in rows) {
            v.findViewById<View>(rid)
                .setBackgroundResource(if (name == d.nextName) R.drawable.row_highlight else 0)
        }
    }
}
