package com.pulse.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pulse.app.health.permission.HealthPermissionState
import com.pulse.app.ui.theme.PulseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun permissionScreenDeniedState_isDisplayed() {
        composeTestRule.setContent {
            PulseTheme {
                PermissionScreen(
                    state = HealthPermissionState.Denied,
                    onRequestPermission = {},
                    onRefresh = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Grant permissions").assertExists()
    }
}
