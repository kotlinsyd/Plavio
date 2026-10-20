package com.pulsestream.app.data.update

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class MobileReleaseManifestTest {
    private fun release(version: String = "0.11.3", urlVersion: String = version) = JSONObject()
        .put("version", version).put("apkUrl", "https://gitlab.com/api/v4/projects/87396069/packages/generic/plavio/$urlVersion/Plavio-v$urlVersion.apk")

    @Test fun acceptsPublishedMobileStable() {
        assertEquals("0.11.3", MobileReleaseManifest.parse(release()).version)
    }
    @Test fun rejectsTvAndPrereleaseTags() {
        listOf("0.11.3-tv", "0.11.3-beta", "latest", "1.2").forEach {
            assertTrue(runCatching { MobileReleaseManifest.parse(release(it)) }.isFailure)
        }
    }
    @Test fun rejectsMismatchedVersionAndWrongOrigins() {
        assertTrue(runCatching { MobileReleaseManifest.parse(release("0.11.4", "0.11.3")) }.isFailure)
        listOf("http://gitlab.com/test.apk", "https://gitlab.com.evil.example/test.apk",
            "https://user:pass@gitlab.com/test.apk", "https://gitlab.com/test.html").forEach {
            assertTrue(runCatching { MobileReleaseManifest.parse(release().put("apkUrl", it)) }.isFailure)
        }
    }
    @Test fun rejectsOtherChannelsAndPlatforms() {
        assertTrue(runCatching { MobileReleaseManifest.parse(release().put("platform", "tv")) }.isFailure)
        assertTrue(runCatching { MobileReleaseManifest.parse(release().put("channel", "beta")) }.isFailure)
    }
}
