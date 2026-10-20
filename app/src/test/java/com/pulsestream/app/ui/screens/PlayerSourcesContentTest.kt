package com.pulsestream.app.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.ui.screens.player.PlayerSourcesContent
import com.pulsestream.app.ui.theme.PulseStreamTheme
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class PlayerSourcesContentTest {
    @get:Rule val composeRule = createComposeRule()
    private val streams = (1..8).map { index -> StreamItem("https://fixture.invalid/$index.mkv",
        title = "Fixture.Release.$index.1080p.WEB-DL.5.4GB.mkv", sourceName = "Fixture addon") }

    @Test fun landscapeSourceListScrollsToLastSourceAndKeepsPlayAndDownloadSeparate() {
        var picked: StreamItem? = null
        var downloaded: StreamItem? = null
        var closed = false
        val settings = mockk<SettingsStore>(relaxed = true)
        every { settings.streamsPerSource } returns 1
        every { settings.qualityOrder } returns emptyList()
        composeRule.setContent {
            PulseStreamTheme {
                Box(Modifier.requiredSize(640.dp, 300.dp)) {
                    PlayerSourcesContent("Fixture title", streams, streams.first().url, false,
                        onPick = { picked = it }, onDownload = { downloaded = it }, onRetry = {}, onClose = { closed = true }, settings = settings)
                }
            }
        }
        composeRule.onNodeWithText("Sources · Fixture title").assertExists()
        composeRule.onNodeWithText("8 sources").assertExists()
        composeRule.onNodeWithText("Playing").assertExists()
        composeRule.onNodeWithTag("player-source-list").performScrollToNode(hasText(streams.last().title!!))
        composeRule.onNodeWithText(streams.last().title!!).assertExists().performClick()
        assertEquals(streams.last(), picked)
        assertEquals(null, downloaded)
        composeRule.onAllNodesWithContentDescription("Download").onLast().performClick()
        assertEquals(streams.last(), downloaded)
        composeRule.onNodeWithTag("player-source-list").performScrollToNode(hasText("Sources · Fixture title"))
        composeRule.onNodeWithContentDescription("Close sources").performClick()
        assertEquals(true, closed)
    }

    @Test fun emptyPlayerSourcesHasRetryWithoutAnyArtworkRegion() {
        var retries = 0
        composeRule.setContent { PulseStreamTheme {
            Box(Modifier.requiredSize(640.dp, 300.dp)) {
                PlayerSourcesContent("Fixture title", emptyList(), "", false,
                    onPick = {}, onDownload = {}, onRetry = { retries++ }, onClose = {})
            }
        } }
        composeRule.onNodeWithText("No sources loaded for this title.").assertExists()
        composeRule.onNodeWithText("Load sources").performClick()
        assertEquals(1, retries)
    }
}
