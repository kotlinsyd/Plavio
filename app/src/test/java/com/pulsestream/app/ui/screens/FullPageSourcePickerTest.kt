package com.pulsestream.app.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.MetaDetail
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.ui.theme.PulseStreamTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class FullPageSourcePickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val meta = MetaDetail(
        id = "tt-fixture-001",
        type = "movie",
        name = "Fixture Movie",
        poster = null,
        releaseInfo = "2024",
        imdbRating = "8.1",
        genres = listOf("Drama", "Mystery")
    )

    private val streams = listOf(
        stream("plaviohd-1080", "PlavioHD 1080p WEB-DL", "PlavioHD"),
        stream("plaviohd-720", "PlavioHD 720p WEBRip", "PlavioHD"),
        stream("tmaddon-1080", "TMAddon 1080p BluRay", "TMAddon"),
        stream("tmaddon-4k", "TMAddon 4K WEB-DL", "TMAddon"),
        stream("staddon-720", "StAddon 720p WEBRip", "StAddon"),
        stream("staddon-4k", "StAddon 4K BluRay", "StAddon")
    )

    private fun stream(id: String, title: String, addon: String) = StreamItem(
        url = "https://$addon.example.invalid/$id.mkv",
        name = title,
        title = title,
        sourceName = addon,
        sourceId = addon.lowercase()
    )

    private fun showPicker(
        pickerStreams: List<StreamItem> = streams,
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
        onPick: (StreamItem) -> Unit = {},
        onDownload: ((StreamItem) -> Unit)? = null
    ) {
        composeRule.setContent {
            PulseStreamTheme {
                // Small viewport exercises the real scrollable picker layout.
                Box(Modifier.requiredSize(320.dp, 480.dp)) {
                    FullPageSourcePicker(
                        meta = meta,
                        streams = pickerStreams,
                        loading = false,
                        errorMessage = null,
                        onBack = onBack,
                        onRetry = onRetry,
                        onPick = onPick,
                        onDownload = onDownload
                    )
                }
            }
        }
    }

    @Test
    fun `initial metadata all addon tabs and quality filters are shown`() {
        showPicker()

        composeRule.onNodeWithText("Choose stream").assertExists()
        composeRule.onNodeWithText("Fixture Movie").assertExists()
        composeRule.onNodeWithText("2024 · Movie").assertExists()
        composeRule.onNodeWithText("★ 8.1").assertExists()
        composeRule.onNodeWithText("Drama · Mystery").assertExists()
        composeRule.onNode(hasText("All") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertExists()
        composeRule.onNode(hasText("PlavioHD") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertExists()
        composeRule.onNode(hasText("TMAddon") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performScrollTo().assertExists()
        composeRule.onNode(hasText("StAddon") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performScrollTo().assertExists()
        composeRule.onNode(hasText("1080P") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertExists()
        composeRule.onNode(hasText("720P") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertExists()
        composeRule.onNode(hasText("4K") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertExists()
        composeRule.onNodeWithText("6 results").assertExists()
    }

    @Test
    fun `compact rows show filename metadata once rather than repeating it as chips`() {
        val filename = "Film.2160p.WEB-DL.x265.DTS-HD.5.4GB.mkv"
        val fixture = stream("metadata-duplicate", filename, "PlavioHD").copy(
            title = filename,
            name = filename
        )
        showPicker(pickerStreams = listOf(fixture))

        composeRule.onNodeWithText(filename).assertExists()
        composeRule.onAllNodesWithText("2160P", substring = false).assertCountEquals(0)
        composeRule.onAllNodesWithText("5.4 GB", substring = false).assertCountEquals(0)
        composeRule.onAllNodesWithText("PlavioHD", substring = false).assertCountEquals(2)
    }

    @Test
    fun `quality filter spans addons addon intersection and Any resets quality`() {
        showPicker()

        composeRule.onNode(hasText("1080P") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        composeRule.onNodeWithText("2 results").assertExists()
        composeRule.onNodeWithTag("stream-picker-list").performScrollToNode(hasText("PlavioHD 1080p WEB-DL"))
        composeRule.onNodeWithText("PlavioHD 1080p WEB-DL").assertExists()
        composeRule.onNodeWithTag("stream-picker-list").performScrollToNode(hasText("TMAddon 1080p BluRay"))
        composeRule.onNodeWithText("TMAddon 1080p BluRay").assertExists()
        composeRule.onNodeWithText("PlavioHD 720p WEBRip").assertDoesNotExist()

        composeRule.onNodeWithTag("stream-picker-list").performScrollToIndex(1)
        composeRule.onNode(hasText("TMAddon") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performScrollTo().performClick()
        composeRule.onNodeWithText("1 results").assertExists()
        composeRule.onNodeWithText("TMAddon 1080p BluRay").assertExists()
        composeRule.onNodeWithText("PlavioHD 1080p WEB-DL").assertDoesNotExist()

        // Any clears the quality constraint while preserving the TMAddon tab.
        composeRule.onNodeWithTag("stream-picker-list").performScrollToIndex(1)
        composeRule.onNode(hasText("Any") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        composeRule.onNodeWithText("2 results").assertExists()
        composeRule.onNodeWithTag("stream-picker-list").performScrollToNode(hasText("TMAddon 1080p BluRay"))
        composeRule.onNodeWithText("TMAddon 1080p BluRay").assertExists()
        composeRule.onNodeWithTag("stream-picker-list").performScrollToNode(hasText("TMAddon 4K WEB-DL"))
        composeRule.onNodeWithText("TMAddon 4K WEB-DL").assertExists()
    }

    @Test
    fun `clear filters recovers from an addon and quality combination with no matches`() {
        showPicker()

        composeRule.onNodeWithTag("stream-picker-list").performScrollToIndex(1)
        composeRule.onNode(hasText("TMAddon") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performScrollTo().performClick()
        composeRule.onNode(hasText("720P") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        composeRule.onNodeWithText("No streams match this add-on and quality.", substring = true).assertExists()
        composeRule.onNodeWithText("Clear filters").performClick()

        composeRule.onNodeWithText("6 results").assertExists()
        composeRule.onNodeWithTag("stream-picker-list").performScrollToNode(hasText("PlavioHD 1080p WEB-DL"))
        composeRule.onNodeWithText("PlavioHD 1080p WEB-DL").assertExists()
        composeRule.onNodeWithTag("stream-picker-list").performScrollToNode(hasText("TMAddon 4K WEB-DL"))
        composeRule.onNodeWithText("TMAddon 4K WEB-DL").assertExists()
    }

    @Test
    fun `download callback is separate from explicit play callback`() {
        var picked: StreamItem? = null
        var downloaded: StreamItem? = null
        showPicker(
            onPick = { picked = it },
            onDownload = { downloaded = it }
        )

        composeRule.onNodeWithTag("stream-picker-list").performScrollToNode(hasText("PlavioHD 1080p WEB-DL"))
        composeRule.onNodeWithText("PlavioHD 1080p WEB-DL")
        composeRule.onAllNodesWithContentDescription("Download")[0].performClick()
        assertEquals("https://PlavioHD.example.invalid/plaviohd-1080.mkv", downloaded?.url)
        assertEquals("Download must not trigger playback selection", null, picked)

        composeRule.onAllNodesWithContentDescription("Play stream")[0].performClick()
        assertEquals("https://PlavioHD.example.invalid/plaviohd-1080.mkv", picked?.url)
        assertTrue("The play action should leave the download callback unchanged", downloaded === streams[0])
    }

    @Test
    fun `unresolved torrent cannot play or download before cached debrid resolution`() {
        val pending = stream("pending", "Fixture torrent 1080p", "Torrentio").copy(url = "infohash:fixture", infoHash = "fixture")
        showPicker(pickerStreams = listOf(pending), onDownload = {})
        composeRule.onNodeWithText("Torrent source · cached debrid link required").assertExists()
        composeRule.onNodeWithContentDescription("Play stream").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Download").assertIsNotEnabled()
    }

    @Test
    fun `back and close controls invoke the dismiss callback`() {
        var dismissals = 0
        showPicker(onBack = { dismissals++ })

        composeRule.onNodeWithContentDescription("Back").assertExists().performClick()
        composeRule.onNodeWithContentDescription("Close stream picker").assertExists().performClick()
        assertEquals(2, dismissals)
    }
}
