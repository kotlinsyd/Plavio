package com.pulsestream.app.data.notifications

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeTrackerTest {

    private lateinit var tracker: EpisodeTracker

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Clear prefs between tests for hermetic runs
        context.getSharedPreferences("pulse_episode_tracker", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        tracker = EpisodeTracker(context)
    }

    @Test
    fun `key generates stable storage key`() {
        assertEquals("ep_addon1|tt123", EpisodeTracker.key("addon1", "tt123"))
    }

    @Test
    fun `lastKnown returns null for never-tracked show`() {
        assertNull(tracker.lastKnown("addon1", "tt999"))
    }

    @Test
    fun `update then lastKnown returns season and episode`() {
        tracker.update("addon1", "tt123", 2, 5)

        val result = tracker.lastKnown("addon1", "tt123")

        assertNotNull(result)
        assertEquals(2, result!!.first)
        assertEquals(5, result.second)
    }

    @Test
    fun `update overwrites previous value`() {
        tracker.update("addon1", "tt123", 1, 3)
        tracker.update("addon1", "tt123", 2, 1)

        val result = tracker.lastKnown("addon1", "tt123")

        assertEquals(2 to 1, result)
    }

    @Test
    fun `different shows are tracked independently`() {
        tracker.update("addon1", "tt111", 1, 1)
        tracker.update("addon1", "tt222", 3, 7)

        assertEquals(1 to 1, tracker.lastKnown("addon1", "tt111"))
        assertEquals(3 to 7, tracker.lastKnown("addon1", "tt222"))
    }

    @Test
    fun `different addons with same metaId are tracked independently`() {
        tracker.update("addonA", "tt123", 1, 1)
        tracker.update("addonB", "tt123", 2, 2)

        assertEquals(1 to 1, tracker.lastKnown("addonA", "tt123"))
        assertEquals(2 to 2, tracker.lastKnown("addonB", "tt123"))
    }

    @Test
    fun `prune removes shows not in keep set`() {
        tracker.update("addon1", "tt111", 1, 1)
        tracker.update("addon1", "tt222", 2, 2)
        tracker.update("addon1", "tt333", 3, 3)

        val keepKey = EpisodeTracker.key("addon1", "tt111")
        tracker.prune(setOf(keepKey))

        assertNotNull(tracker.lastKnown("addon1", "tt111"))
        assertNull(tracker.lastKnown("addon1", "tt222"))
        assertNull(tracker.lastKnown("addon1", "tt333"))
    }

    @Test
    fun `prune with empty keep set removes everything`() {
        tracker.update("addon1", "tt111", 1, 1)
        tracker.prune(emptySet())
        assertNull(tracker.lastKnown("addon1", "tt111"))
    }

    @Test
    fun `prune does nothing when nothing is stale`() {
        tracker.update("addon1", "tt111", 1, 1)
        val keepKey = EpisodeTracker.key("addon1", "tt111")
        // Should not throw
        tracker.prune(setOf(keepKey))
        assertNotNull(tracker.lastKnown("addon1", "tt111"))
    }
}
