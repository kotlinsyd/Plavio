package com.pulsestream.app.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.theme.PulseStreamTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TorBoxSettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()
    private val settings = mockk<SettingsStore>(relaxed = true)
    private var storedKey: String? = null

    private fun showScreen(initialKey: String? = null) {
        storedKey = initialKey
        every { settings.torboxApiKey } answers { storedKey }
        every { settings.torboxApiKey = any() } answers {
            storedKey = firstArg<String?>()?.trim()?.takeIf { it.isNotBlank() }
        }
        val repository = mockk<StreamRepository>(relaxed = true)
        every { repository.settings } returns settings
        composeRule.setContent { PulseStreamTheme { IntegrationsScreen(repository) } }
    }

    @Test fun torboxEditorIsEnabledAndSavesTrimmedKeyWithoutRemoteValidation() {
        showScreen()
        composeRule.onNodeWithText("Add your API key for cached streams").assertExists()
        composeRule.onNodeWithText("TorBox").performClick()
        composeRule.onNodeWithText("TorBox API Key").assertExists()
        composeRule.onNodeWithText("API key").assertIsEnabled()
            .performTextReplacement("  fake-torbox-key-for-ui-test  ")
        composeRule.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals("fake-torbox-key-for-ui-test", storedKey)
        verify(exactly = 1) { settings.torboxApiKey = "fake-torbox-key-for-ui-test" }
        composeRule.onNodeWithText("TorBox API Key").assertDoesNotExist()
        composeRule.onNodeWithText("API key saved").assertExists()
        // Saving locally must not claim the credential has been validated remotely.
        composeRule.onNodeWithText("Saved").assertExists()
    }

    @Test fun savedKeyCanBeReopenedShownHiddenAndReplaced() {
        showScreen("fake-old-key")
        composeRule.onNodeWithText("TorBox").performClick()
        composeRule.onNodeWithContentDescription("Show key").assertIsEnabled().performClick()
        composeRule.onNodeWithText("fake-old-key").assertExists()
        composeRule.onNodeWithContentDescription("Hide key").performClick()
        composeRule.onNodeWithContentDescription("Show key").assertExists()
        composeRule.onNodeWithText("API key").performTextReplacement("fake-new-key")
        composeRule.onNodeWithText("Save").performClick()
        assertEquals("fake-new-key", storedKey)
        composeRule.onNodeWithText("TorBox").performClick()
        composeRule.onNodeWithContentDescription("Show key").assertExists().performClick()
        composeRule.onNodeWithText("fake-new-key").assertExists()
    }

    @Test fun cancellingDoesNotPersistDraftOrClearExistingKey() {
        showScreen("fake-existing-key")
        composeRule.onNodeWithText("TorBox").performClick()
        composeRule.onNodeWithText("API key").performTextReplacement("fake-unsaved-key")
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals("fake-existing-key", storedKey)
        verify(exactly = 0) { settings.torboxApiKey = any() }
        composeRule.onNodeWithText("TorBox").performClick()
        composeRule.onNodeWithContentDescription("Show key").performClick()
        composeRule.onNodeWithText("fake-existing-key").assertExists()
    }

    @Test fun clearingAndSavingRemovesKeyAndUpdatesStatus() {
        showScreen("fake-existing-key")
        composeRule.onNodeWithText("TorBox").performClick()
        composeRule.onNodeWithText("API key").performTextReplacement("   ")
        composeRule.onNodeWithText("Save").performClick()
        verify(exactly = 1) { settings.torboxApiKey = "" }
        assertNull(storedKey)
        composeRule.onNodeWithText("Add your API key for cached streams").assertExists()
        composeRule.onNodeWithText("API key saved").assertDoesNotExist()
    }

    @Test fun encryptedStorageFailureKeepsDialogOpenAndDoesNotClaimSuccess() {
        showScreen()
        every { settings.torboxApiKey = any() } throws IllegalStateException("fixture storage error")
        composeRule.onNodeWithText("TorBox").performClick()
        composeRule.onNodeWithText("API key").performTextReplacement("fake-unsaved-key")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Could not save the API key securely. Please try again.").assertExists()
        composeRule.onNodeWithText("TorBox API Key").assertExists()
        composeRule.onNodeWithText("fixture storage error").assertDoesNotExist()
        assertNull(storedKey)
    }

    @Test fun enablingTorboxDoesNotEnableRealDebrid() {
        showScreen()
        composeRule.onNodeWithText("Real-Debrid").performClick()
        composeRule.onNodeWithText("API key").assertIsNotEnabled()
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        composeRule.onNodeWithText("Currently under testing — coming soon.").assertExists()
    }
}
