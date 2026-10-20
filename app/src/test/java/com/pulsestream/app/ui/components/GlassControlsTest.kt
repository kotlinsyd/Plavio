package com.pulsestream.app.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.pulsestream.app.ui.theme.PulseStreamTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class GlassControlsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun sharedButtonsRetainAccessibleTargetsAndExistingEnabledBehavior() {
        val enabled = mutableStateOf(true)
        var clicks = 0
        composeRule.setContent { PulseStreamTheme { Column {
            GradientButton("Primary", { clicks++ }, enabled = enabled.value)
            GlassButton({ clicks++ }, enabled = enabled.value) { Text("Secondary") }
        } } }
        composeRule.onNodeWithText("Primary").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("Secondary").assertHeightIsAtLeast(52.dp).performClick()
        assertEquals(2, clicks)
        composeRule.runOnIdle { enabled.value = false }
        composeRule.onNodeWithText("Primary").assertIsNotEnabled()
        composeRule.onNodeWithText("Secondary").assertIsNotEnabled()
        assertEquals(2, clicks)
    }
}
