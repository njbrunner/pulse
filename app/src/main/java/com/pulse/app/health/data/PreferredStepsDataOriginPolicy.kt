package com.pulse.app.health.data

import androidx.health.connect.client.records.metadata.DataOrigin

/**
 * Policy that identifies known preferred wearable Steps data origins.
 *
 * Pulse's health metrics are wearable-centric. For the MVP the only supported
 * preferred wearable origin is Fitbit. The list is injectable so additional
 * wearables can be added later without rewriting Steps retrieval logic.
 *
 * The synthetic Android phone step source is intentionally not included in the
 * known origins list.
 */
class PreferredStepsDataOriginPolicy(
    private val knownOrigins: List<String> = listOf(FITBIT_PACKAGE),
) {

    /**
     * Returns a [DataOrigin] for each configured preferred wearable origin.
     */
    fun knownDataOrigins(): List<DataOrigin> = knownOrigins.map { DataOrigin(it) }

    /**
     * Returns `true` if [dataOrigin] matches one of the configured preferred
     * wearable origins.
     */
    fun isPreferred(dataOrigin: DataOrigin): Boolean = dataOrigin.packageName in knownOrigins

    companion object {
        /**
         * Fitbit's Health Connect package name.
         */
        const val FITBIT_PACKAGE = "com.fitbit.FitbitMobile"
    }
}
