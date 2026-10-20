package com.pulsestream.app.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SourceDiagnosticsTest {
    @Test fun redactsProviderAndUntrustedOutcome() {
        SourceDiagnostics.clear()
        SourceDiagnostics.record("https://user:SECRET@example.com/movie/private-title?token=SECRET", "SECRET error", -50)
        val text = SourceDiagnostics.export()
        assertFalse(text.contains("SECRET"))
        assertFalse(text.contains("private-title"))
        assertFalse(text.contains("example.com"))
        assertEquals("failed", SourceDiagnostics.entries.value.single().outcome)
        assertEquals(0L, SourceDiagnostics.entries.value.single().durationMs)
    }
    @Test fun boundedAndClearable() {
        SourceDiagnostics.clear()
        repeat(100) { SourceDiagnostics.record("provider-$it", "succeeded", 99) }
        assertEquals(80, SourceDiagnostics.entries.value.size)
        SourceDiagnostics.clear()
        assertTrue(SourceDiagnostics.entries.value.isEmpty())
    }
}
