package com.pulsestream.app.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.pulsestream.app.data.db.DownloadItem
import com.pulsestream.app.data.download.DownloadManager
import com.pulsestream.app.ui.components.WifiOnlyDownloadsControl
import com.pulsestream.app.ui.theme.PulseStreamTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DownloadsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val manager = mockk<DownloadManager>(relaxed = true)
    private var companionMocked = false

    private fun showScreen() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val item = DownloadItem(
            id = "offline-1",
            url = "https://example.invalid/movie.mp4",
            title = "Saved movie",
            fileName = "movie.mp4",
            filePath = "/tmp/movie.mp4",
            totalBytes = 1024,
            status = "COMPLETED",
            createdAt = 1L
        )
        mockkObject(DownloadManager.Companion)
        companionMocked = true
        every { DownloadManager.get(any()) } returns manager
        every { manager.observeAll() } returns flowOf(listOf(item))
        every { manager.activeDownloads } returns MutableStateFlow(emptyMap())
        every { manager.isWifiOnlyDownloads() } returns false
        every { manager.isAdaptiveDownloadUrl(any()) } returns false
        every { manager.formatBytes(any()) } returns "1 KB"
        coEvery { manager.recoverInterruptedDownloads() } just Runs
        coEvery { manager.storageUsedBytes() } returns 1024L
        every { manager.setWifiOnlyDownloads(any()) } just Runs

        composeRule.setContent { PulseStreamTheme { DownloadsScreen() } }
    }

    @After
    fun cleanup() {
        if (companionMocked) unmockkObject(DownloadManager.Companion)
    }

    @Test
    fun wifiSettingIsAboveDownloadListAndTogglePersistsWithoutChangingActions() {
        showScreen()

        val settingLabel = composeRule.onNodeWithText("Wi-Fi only").fetchSemanticsNode().boundsInRoot
        val listLabel = composeRule.onNodeWithText("Storage used: 1 KB").fetchSemanticsNode().boundsInRoot
        assertTrue("Wi-Fi policy must be in the header before the downloads list", settingLabel.bottom < listLabel.top)
        composeRule.onAllNodesWithText("Wi-Fi only").assertCountEquals(1)
        composeRule.onNodeWithText("Saved movie").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Delete").assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithContentDescription("Wi-Fi only downloads")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithContentDescription("Wi-Fi only downloads").assertIsDisplayed()
        verify(exactly = 1) { manager.setWifiOnlyDownloads(true) }
    }

    @Test
    @Config(qualifiers = "w360dp-h200dp-mdpi")
    fun wifiControlFits360dpLandscapeHeightAtLargeTextScale() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                Box(Modifier.requiredSize(360.dp, 200.dp)) {
                    WifiOnlyDownloadsControl(checked = true, onCheckedChange = {})
                }
            }
        }

        val title = composeRule.onNodeWithText("Wi-Fi only").fetchSemanticsNode().boundsInRoot
        val switch = composeRule.onNodeWithContentDescription("Wi-Fi only downloads")
        switch.assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Download only when connected to Wi-Fi").assertIsDisplayed()
        assertTrue("Header text must stay inside the 360dp layout", title.left >= 0f && title.right <= 360f)
    }
}
