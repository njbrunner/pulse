package com.pulse.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pulse.app.health.permission.HealthPermissionState
import com.pulse.app.ui.theme.PulseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun deniedState_showsGrantPermissionsButton() {
        composeTestRule.setContent {
            PulseTheme {
                PermissionScreen(
                    state = HealthPermissionState.Denied,
                    onRequestPermission = {},
                    onRefresh = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Grant permissions").assertIsDisplayed()
    }

    @Test
    fun partiallyGrantedState_showsGrantPermissionsButton() {
        composeTestRule.setContent {
            PulseTheme {
                PermissionScreen(
                    state = HealthPermissionState.PartiallyGranted(
                        granted = setOf(com.pulse.app.health.permission.PulseHealthPermission.STEPS),
                        denied = setOf(com.pulse.app.health.permission.PulseHealthPermission.SLEEP),
                    ),
                    onRequestPermission = {},
                    onRefresh = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Grant permissions").assertIsDisplayed()
    }

    @Test
    fun unavailableState_hidesGrantPermissionsButton() {
        composeTestRule.setContent {
            PulseTheme {
                PermissionScreen(
                    state = HealthPermissionState.Unavailable,
                    onRequestPermission = {},
                    onRefresh = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Grant permissions").assertDoesNotExist()
    }

    @Test
    fun grantedState_hidesGrantPermissionsButton() {
        composeTestRule.setContent {
            PulseTheme {
                PermissionScreen(
                    state = HealthPermissionState.Granted,
                    onRequestPermission = {},
                    onRefresh = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Grant permissions").assertDoesNotExist()
    }
}
