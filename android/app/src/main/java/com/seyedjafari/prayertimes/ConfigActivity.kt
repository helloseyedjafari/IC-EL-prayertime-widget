package com.seyedjafari.prayertimes

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout

/** Shown when the widget is dropped on the home screen: pick a city. */
class ConfigActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.config_activity)
        val list = findViewById<LinearLayout>(R.id.city_list)
        for (city in PrayerCore.CITIES) {
            val b = Button(this).apply {
                text = city
                isAllCaps = false
                setTextColor(Color.parseColor("#e8eef5"))
                setBackgroundColor(Color.parseColor("#1b263b"))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                lp.topMargin = 8
                layoutParams = lp
                setOnClickListener { choose(city) }
            }
            list.addView(b)
        }
    }

    private fun choose(city: String) {
        PrayerRepository.setCity(this, appWidgetId, city)
        RefreshScheduler.schedule(this)
        PrayerWidgetProvider.requestUpdate(this, intArrayOf(appWidgetId))
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}
