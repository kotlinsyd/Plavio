package com.pulsestream.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbClientTest {
    @Test fun `empty cast is not incorrectly considered fully enriched`() {
        assertFalse(TmdbClient.isFullyEnrichedCast(emptyList()))
    }

    @Test fun `cast missing photo or id remains eligible for enrichment`() {
        assertFalse(TmdbClient.isFullyEnrichedCast(listOf(CastMember(id = 1, name = "A", photo = null))))
        assertFalse(TmdbClient.isFullyEnrichedCast(listOf(CastMember(id = 0, name = "A", photo = "photo"))))
        assertTrue(TmdbClient.isFullyEnrichedCast(listOf(CastMember(id = 1, name = "A", photo = "photo"))))
    }
}
