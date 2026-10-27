package com.pulse.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionsRationaleActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<PermissionsRationaleActivity>()

    @Test
    fun rationaleActivity_displaysHealthDataExplanation() {
        composeTestRule.onNodeWithText("Why Pulse needs Health Connect access").assertExists()
        composeTestRule.onNodeWithText("Sleep duration and sleep sessions").assertExists()
        composeTestRule.onNodeWithText("Steps").assertExists()
        composeTestRule.onNodeWithText("Resting heart rate").assertExists()
        composeTestRule.onNodeWithText("Heart rate variability (HRV)").assertExists()
        composeTestRule.onNodeWithText("Hydration").assertExists()
        composeTestRule.onNodeWithText("Done").assertExists()
    }
}
