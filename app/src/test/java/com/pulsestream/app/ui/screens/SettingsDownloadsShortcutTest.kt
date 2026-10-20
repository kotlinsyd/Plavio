package com.pulsestream.app.ui.screens

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.theme.PulseStreamTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SettingsDownloadsShortcutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun settingsHeaderHasNoRedundantDownloadsShortcut() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val repository = mockk<StreamRepository>(relaxed = true)
        every { repository.settings } returns SettingsStore(context)
        every { repository.addons } returns flowOf(emptyList())
        composeRule.setContent { PulseStreamTheme { SettingsScreen(repository = repository) } }
        composeRule.onNodeWithText("Settings").assertExists()
        composeRule.onNodeWithContentDescription("Downloads").assertDoesNotExist()
        composeRule.onNodeWithText("Backup & Restore").assertDoesNotExist()
        composeRule.onNodeWithText("Account").assertExists()
    }
}
