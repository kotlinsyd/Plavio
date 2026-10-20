package com.pulsestream.app.ui.components

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.ui.theme.PulseStreamTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class StreamCardDisplayTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun regularSourceCardDoesNotRepeatFilenameQualityOrSizeAsChips() {
        val filename = "Fixture.1080p.WEB-DL.x265.AAC.5.4GB.mkv"
        val stream = StreamItem("https://fixture.invalid/movie.mkv", title = filename, name = filename, sourceName = "Fixture provider")
        composeRule.setContent { PulseStreamTheme { StreamCard(stream, onClick = {}, compact = false) } }
        composeRule.onAllNodesWithText(filename).assertCountEquals(1)
        composeRule.onAllNodesWithText("1080P").assertCountEquals(0)
        composeRule.onAllNodesWithText("5.4 GB").assertCountEquals(0)
        composeRule.onAllNodesWithText("WEB-DL").assertCountEquals(0)
        composeRule.onNodeWithText("Fixture provider").assertExists()
    }
}
