package com.pulsestream.app.data

import com.pulsestream.app.data.db.AppDatabase
import com.pulsestream.app.data.db.AddonDao
import com.pulsestream.app.data.db.PlaybackDao
import com.pulsestream.app.data.db.WatchlistDao
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28], application = android.app.Application::class)
class BackupValidationTest {
    private fun repository(): StreamRepository {
        val db = mockk<AppDatabase>(relaxed = true)
        val addonDao = mockk<AddonDao> { every { observeAll() } returns emptyFlow() }
        val playbackDao = mockk<PlaybackDao> { every { observeRecent() } returns emptyFlow() }
        val watchlistDao = mockk<WatchlistDao> { every { observeAll() } returns emptyFlow() }
        every { db.addonDao() } returns addonDao
        every { db.playbackDao() } returns playbackDao
        every { db.watchlistDao() } returns watchlistDao
        return StreamRepository(db, mockk(relaxed = true), mockk(relaxed = true))
    }

    @Test
    fun `version 2 empty sections are a valid explicit replacement target`() {
        val repo = repository()
        val empty = repo.emptyBackupJson().getOrThrow()
        val preview = repo.previewBackupJson(empty).getOrThrow()
        assertEquals(2, preview.version)
        assertEquals(0, preview.customListCount)
        assertEquals(0, preview.customListItemCount)
    }

    @Test
    fun `wrong-typed section is rejected instead of becoming empty`() {
        val json = """{"format":"plavio-backup","version":2,"settings":{},"watchlist":"bad-data","customLists":[],"customListItems":[]}"""
        assertFalse(repository().previewBackupJson(json).isSuccess)
    }

    @Test
    fun `custom-list items cannot refer to missing list or duplicate identity`() {
        val dangling = """{"format":"plavio-backup","version":2,"settings":{},"customLists":[],"customListItems":[{"listId":"missing","itemKey":"x"}]}"""
        assertFalse(repository().previewBackupJson(dangling).isSuccess)

        val duplicate = """{"format":"plavio-backup","version":2,"settings":{},"customLists":[{"id":"list","name":"List","createdAt":0}],"customListItems":[{"listId":"list","itemKey":"x"},{"listId":"list","itemKey":"x"}]}"""
        assertFalse(repository().previewBackupJson(duplicate).isSuccess)
    }

    @Test
    fun `unsupported backup version is rejected`() {
        val json = """{"format":"plavio-backup","version":3,"settings":{},"customLists":[],"customListItems":[]}"""
        assertTrue(repository().previewBackupJson(json).isFailure)
    }
}
