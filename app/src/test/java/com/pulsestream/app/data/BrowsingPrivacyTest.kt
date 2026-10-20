package com.pulsestream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowsingPrivacyTest {
    @Test
    fun `spoiler rule hides only when enabled and episode has not been revealed`() {
        assertFalse(BrowsingPrivacy.shouldHideEpisodeDetails(hidingEnabled = false, episodeRevealed = false))
        assertFalse(BrowsingPrivacy.shouldHideEpisodeDetails(hidingEnabled = false, episodeRevealed = true))
        assertTrue(BrowsingPrivacy.shouldHideEpisodeDetails(hidingEnabled = true, episodeRevealed = false))
        assertFalse(BrowsingPrivacy.shouldHideEpisodeDetails(hidingEnabled = true, episodeRevealed = true))
    }

    @Test
    fun `show preference keys are deterministic and distinguish delimiter collisions`() {
        assertEquals(BrowsingPrivacy.showPreferenceKey("addon|series|id"), BrowsingPrivacy.showPreferenceKey("addon|series|id"))
        assertNotEquals(BrowsingPrivacy.showPreferenceKey("a|b"), BrowsingPrivacy.showPreferenceKey("a"))
        assertNotEquals(BrowsingPrivacy.showPreferenceKey("a|b"), BrowsingPrivacy.showPreferenceKey("a|b|"))
    }
}
