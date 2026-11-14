package com.pulse.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.app.R
import com.pulse.app.health.data.cache.RoomHealthDataCache

/**
 * Pulse's primary daily home-screen widget.
 *
 * The widget renders from locally cached data and does not make live API
 * requests. It displays sleep duration, Pulse Sleep Score, Pulse Recovery
 * Score, steps with step-goal progress, resting heart rate, and water intake
 * with water-goal progress.
 */
class DailyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val loader = DailyWidgetStateLoader(RoomHealthDataCache(context))
        val state = loader.load()

        provideContent {
            GlanceTheme {
                DailyWidgetContent(state)
            }
        }
    }
}

@Composable
internal fun DailyWidgetContent(
    state: DailyWidgetState,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val unavailablePlaceholder = context.getString(R.string.widget_unavailable)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(16.dp),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Text(
                text = context.getString(R.string.app_name),
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.primary,
                ),
            )

            Spacer(modifier = GlanceModifier.height(12.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.Vertical.Top,
            ) {
                Column(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                ) {
                    MetricItem(
                        label = context.getString(R.string.widget_label_sleep),
                        value = DailyWidgetFormatters.formatDuration(state.sleepDuration)
                            ?: unavailablePlaceholder,
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    MetricItem(
                        label = context.getString(R.string.widget_label_pulse_sleep),
                        value = DailyWidgetFormatters.formatScore(state.pulseSleepScore)
                            ?: unavailablePlaceholder,
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    MetricItem(
                        label = context.getString(R.string.widget_label_pulse_recovery),
                        value = DailyWidgetFormatters.formatScore(state.pulseRecoveryScore)
                            ?: unavailablePlaceholder,
                    )
                }

                Column(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                ) {
                    GoalMetricItem(
                        label = context.getString(R.string.widget_label_steps),
                        value = DailyWidgetFormatters.formatSteps(state.steps)
                            ?: unavailablePlaceholder,
                        progress = state.stepsProgress,
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    MetricItem(
                        label = context.getString(R.string.widget_label_resting_heart_rate),
                        value = DailyWidgetFormatters.formatRestingHeartRate(state.restingHeartRate)
                            ?: unavailablePlaceholder,
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    GoalMetricItem(
                        label = context.getString(R.string.widget_label_water),
                        value = DailyWidgetFormatters.formatWaterLiters(state.waterLiters)
                            ?: unavailablePlaceholder,
                        progress = state.waterProgress,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onBackground,
            ),
        )
        Text(
            text = value,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onBackground,
            ),
        )
    }
}

@Composable
private fun GoalMetricItem(
    label: String,
    value: String,
    progress: Float?,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onBackground,
            ),
        )
        Text(
            text = value,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onBackground,
            ),
        )
        progress?.let {
            Spacer(modifier = GlanceModifier.height(4.dp))
            LinearProgressIndicator(
                progress = it,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
        }
    }
}
