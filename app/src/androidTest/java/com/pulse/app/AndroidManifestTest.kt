package com.pulse.app

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidManifestTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun requiredHealthPermissions_areDeclaredInManifest() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val declaredPermissions = packageInfo.requestedPermissions?.toSet() ?: emptySet()

        val expectedPermissions = setOf(
            "android.permission.health.READ_SLEEP",
            "android.permission.health.READ_STEPS",
            "android.permission.health.READ_RESTING_HEART_RATE",
            "android.permission.health.READ_HEART_RATE_VARIABILITY",
            "android.permission.health.READ_HYDRATION",
        )

        expectedPermissions.forEach { permission ->
            assertTrue(
                "Expected permission $permission to be declared in AndroidManifest.xml",
                declaredPermissions.contains(permission),
            )
        }
    }
}
