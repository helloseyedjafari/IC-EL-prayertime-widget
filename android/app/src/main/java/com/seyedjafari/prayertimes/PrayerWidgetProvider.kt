package com.seyedjafari.prayertimes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrayerWidgetProvider : AppWidgetProvider() {

    // Broadcast-driven updates (initial add via requestUpdate, tap-to-refresh, host refresh).
    // goAsync() keeps the receiver's process alive until the network fetch completes.
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (id in ids) renderBlocking(appContext, mgr, id)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle,
    ) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                renderBlocking(appContext, mgr, id)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) = RefreshScheduler.schedule(context)

    override fun onDisabled(context: Context) = RefreshScheduler.cancel(context)

    override fun onDeleted(context: Context, ids: IntArray) {
        for (id in ids) PrayerRepository.removeWidget(context, id)
    }

    companion object {
        /** Fetch (blocking) + render one widget. Must be called off the main thread. */
        fun renderBlocking(context: Context, mgr: AppWidgetManager, id: Int) {
            val city = PrayerRepository.cityFor(context, id)
            val options = mgr.getAppWidgetOptions(id)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220)
            // 2x2 / 2x3 / 3x2 (~110-170dp) use the compact layout; 3x3+ uses the roomy one.
            val layout = if (minWidth < 175 || minHeight < 175) R.layout.widget_small else R.layout.widget_large

            val data = runCatching { PrayerRepository.load(context, city) }.getOrNull()
            val views = RemoteViews(context.packageName, layout)
            if (data != null) {
                bind(views, data)
            } else {
                views.setTextViewText(R.id.city, context.getString(R.string.unavailable))
            }
            views.setOnClickPendingIntent(R.id.card, cityPickerIntent(context, id))
            mgr.updateAppWidget(id, views)
        }

        /** Re-render every placed widget (used by the periodic worker). Blocking. */
        fun updateAllBlocking(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, PrayerWidgetProvider::class.java))
            for (id in ids) renderBlocking(context, mgr, id)
        }

        /** Ask the framework to update these widgets via the normal broadcast path. */
        fun requestUpdate(context: Context, ids: IntArray) {
            context.sendBroadcast(
                Intent(context, PrayerWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
        }

        private fun bind(v: RemoteViews, d: PrayerTimes) {
            v.setTextViewText(R.id.city, d.city.uppercase())
            v.setTextViewText(R.id.dawn_time, d.dawn)
            v.setTextViewText(R.id.sunrise_time, d.sunrise)
            v.setTextViewText(R.id.noon_time, d.noon)
            v.setTextViewText(R.id.maghrib_time, d.maghrib)
            v.setTextViewText(R.id.midnight_time, d.midnight)
            v.setTextViewText(R.id.next_label, "Next · ${d.nextName} ${d.nextTime}")
            v.setViewVisibility(R.id.stale, if (d.stale) View.VISIBLE else View.GONE)

            val rows = listOf(
                "Dawn" to R.id.row_dawn, "Sunrise" to R.id.row_sunrise, "Noon" to R.id.row_noon,
                "Maghrib" to R.id.row_maghrib, "Midnight" to R.id.row_midnight,
            )
            for ((name, rid) in rows) {
                v.setInt(rid, "setBackgroundResource", if (name == d.nextName) R.drawable.row_highlight else 0)
            }
        }

        // Tapping the widget opens the city picker for that widget instance.
        private fun cityPickerIntent(context: Context, id: Int): PendingIntent {
            val intent = Intent(context, ConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                // distinct data per id so PendingIntents don't collapse into one
                data = Uri.parse("prayertimes://widget/$id")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
