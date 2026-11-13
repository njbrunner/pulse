package com.pulse.app.health.data

import androidx.health.connect.client.records.metadata.DataOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferredStepsDataOriginPolicyTest {

    @Test
    fun defaultPolicy_includesFitbit() {
        val policy = PreferredStepsDataOriginPolicy()

        val origins = policy.knownDataOrigins()

        assertEquals(listOf(DataOrigin(PreferredStepsDataOriginPolicy.FITBIT_PACKAGE)), origins)
    }

    @Test
    fun isPreferred_fitbitOrigin_returnsTrue() {
        val policy = PreferredStepsDataOriginPolicy()

        assertTrue(policy.isPreferred(DataOrigin(PreferredStepsDataOriginPolicy.FITBIT_PACKAGE)))
    }

    @Test
    fun isPreferred_phoneOrigin_returnsFalse() {
        val policy = PreferredStepsDataOriginPolicy()

        assertFalse(policy.isPreferred(DataOrigin("com.google.android.gms")))
    }

    @Test
    fun customPolicy_supportsAdditionalOrigins() {
        val customPackage = "com.garmin.connect.mobile"
        val policy = PreferredStepsDataOriginPolicy(
            knownOrigins = listOf(
                PreferredStepsDataOriginPolicy.FITBIT_PACKAGE,
                customPackage,
            ),
        )

        assertTrue(policy.isPreferred(DataOrigin(customPackage)))
        assertEquals(2, policy.knownDataOrigins().size)
    }
}
