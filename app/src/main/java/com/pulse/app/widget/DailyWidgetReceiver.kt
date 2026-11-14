package com.pulse.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * [AppWidgetProvider] that backs the daily Pulse widget.
 */
class DailyWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = DailyWidget()
}
