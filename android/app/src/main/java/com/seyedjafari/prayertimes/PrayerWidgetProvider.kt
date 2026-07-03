package com.seyedjafari.prayertimes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
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
            // Guaranteed content box (dp): the size the widget is at least this big
            // in either orientation, so sizing to it never clips after a rotate.
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150)

            val data = runCatching { PrayerRepository.load(context, city) }.getOrNull()
            val views = buildViews(context, data, minWidth, minHeight)
            views.setOnClickPendingIntent(R.id.card, cityPickerIntent(context, id))
            mgr.updateAppWidget(id, views)
        }

        /** Inflate + size + fill the widget RemoteViews for a given content box (dp). */
        private fun buildViews(
            context: Context, data: PrayerTimes?, minWidthDp: Int, minHeightDp: Int,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_large)
            applySizing(views, minWidthDp, minHeightDp)
            if (data != null) {
                bind(views, data)
            } else {
                views.setTextViewText(R.id.city, context.getString(R.string.unavailable))
            }
            return views
        }

        private val GLYPH_IDS = intArrayOf(
            R.id.glyph_hdr, R.id.glyph_dawn, R.id.glyph_sunrise,
            R.id.glyph_noon, R.id.glyph_maghrib, R.id.glyph_midnight,
        )
        private val NAME_IDS = intArrayOf(
            R.id.name_dawn, R.id.name_sunrise, R.id.name_noon,
            R.id.name_maghrib, R.id.name_midnight,
        )
        private val TIME_IDS = intArrayOf(
            R.id.dawn_time, R.id.sunrise_time, R.id.noon_time,
            R.id.maghrib_time, R.id.midnight_time,
        )

        /**
         * Scale every font to the widget's current size so text grows with the
         * widget (instead of leaving dead margin) and always fits (instead of
         * clipping when small). Sizes are in DIP, not SP, so the fit math holds
         * regardless of the user's system font-scale setting. Called on every
         * render, including onAppWidgetOptionsChanged (i.e. on resize).
         */
        private fun applySizing(v: RemoteViews, minWidthDp: Int, minHeightDp: Int) {
            val usableW = (minWidthDp - 20).coerceAtLeast(50)
            val usableH = (minHeightDp - 16).coerceAtLeast(50)

            // Drop the glyph column when too narrow to fit "Midnight" + time beside it.
            val showGlyph = usableW >= 155
            val glyphColW = if (showGlyph) 30 else 0
            // Drop the "Next" label when too short — the highlighted row already marks
            // it, so the height goes to making the five times bigger instead.
            val showNext = usableH >= 118

            // Vertical fit: header (~1.55) + 5 weighted rows + optional Next (~1.15),
            // each unit ≈ font * 1.28 line height (includeFontPadding is off).
            val vUnits = 1.55f + 5f + if (showNext) 1.15f else 0f
            val fromHeight = (usableH - 4f) / vUnits / 1.28f
            // Horizontal fit: longest row is "Midnight" (name) + "12:34" (bold time).
            // Denominator = name chars·width + time chars·width + a slack term that
            // leaves a small name↔time gap, so text shrinks to fit rather than clipping.
            val textBudget = (usableW - glyphColW - 6).coerceAtLeast(30)
            val fromWidth = textBudget / 7.4f

            val base = minOf(fromHeight, fromWidth).coerceIn(9f, 26f)
            val nameSize = base * 0.95f
            val citySize = base * 0.95f
            val glyphSize = base * 0.95f
            val nextSize = (base * 0.82f).coerceAtLeast(9f)

            for (gid in GLYPH_IDS) {
                v.setViewVisibility(gid, if (showGlyph) View.VISIBLE else View.GONE)
                v.setTextViewTextSize(gid, TypedValue.COMPLEX_UNIT_DIP, glyphSize)
            }
            for (nid in NAME_IDS) v.setTextViewTextSize(nid, TypedValue.COMPLEX_UNIT_DIP, nameSize)
            for (tid in TIME_IDS) v.setTextViewTextSize(tid, TypedValue.COMPLEX_UNIT_DIP, base)
            v.setTextViewTextSize(R.id.city, TypedValue.COMPLEX_UNIT_DIP, citySize)
            v.setViewVisibility(R.id.next_label, if (showNext) View.VISIBLE else View.GONE)
            v.setTextViewTextSize(R.id.next_label, TypedValue.COMPLEX_UNIT_DIP, nextSize)
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
