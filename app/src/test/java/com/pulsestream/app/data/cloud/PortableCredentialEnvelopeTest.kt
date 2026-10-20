package com.pulsestream.app.data.cloud

import com.pulsestream.app.data.PortableAddonUrlConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PortableCredentialEnvelopeTest {
    private val account = "user-123"
    private val profile = "profile-main"
    private val passphrase = "correct horse battery staple".toCharArray()
    private val plaintext = "{\"format\":\"plavio-integration-credentials\",\"credentials\":{\"tmdb_api_key\":\"secret-value-123\"}}"

    @Test fun `envelope round trips and does not expose secrets`() {
        val envelope = PortableCredentialEnvelope.encrypt(plaintext, passphrase, account, profile)
        assertTrue(envelope.startsWith("PLAVIO-CREDENTIALS:1\n"))
        assertFalse(envelope.contains("secret-value-123"))
        assertEquals(plaintext, PortableCredentialEnvelope.decrypt(envelope, passphrase, account, profile))
    }

    @Test fun `wrong passphrase account or profile is rejected`() {
        val envelope = PortableCredentialEnvelope.encrypt(plaintext, passphrase, account, profile)
        listOf(
            { PortableCredentialEnvelope.decrypt(envelope, "wrong passphrase now".toCharArray(), account, profile) },
            { PortableCredentialEnvelope.decrypt(envelope, passphrase, "other-user", profile) },
            { PortableCredentialEnvelope.decrypt(envelope, passphrase, account, "other-profile") }
        ).forEach { attempt ->
            try {
                attempt()
                throw AssertionError("Expected authenticated decryption to fail")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message.orEmpty().contains("Incorrect") || e.message.orEmpty().contains("malformed"))
            }
        }
    }

    @Test fun `tampering and short passphrases fail closed`() {
        val envelope = PortableCredentialEnvelope.encrypt(plaintext, passphrase, account, profile)
        val parts = envelope.split('\n').toMutableList()
        val ciphertext = parts.last().toCharArray()
        // Mutate a full sextet: changing the final encoded character can alter only
        // unused Base64 padding bits while decoding to the same ciphertext bytes.
        ciphertext[0] = if (ciphertext[0] == 'A') 'B' else 'A'
        parts[parts.lastIndex] = String(ciphertext)
        try {
            PortableCredentialEnvelope.decrypt(parts.joinToString("\n"), passphrase, account, profile)
            throw AssertionError("Expected tampering to be rejected")
        } catch (_: IllegalArgumentException) { }
        try {
            PortableCredentialEnvelope.encrypt("payload", "too short".toCharArray(), account, profile)
            throw AssertionError("Expected a short passphrase to be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("12 characters"))
        }
    }

    @Test fun `merge keeps cloud credentials absent locally and allows local updates`() {
        val existingPlaintext = credentialJson(mapOf("simkl_token" to "cloud-only", "trakt_access_token" to "old-trakt"))
        val existing = PortableCredentialEnvelope.encrypt(existingPlaintext, passphrase, account, profile)
        val local = credentialJson(mapOf("tmdb_api_key" to "local-tmdb", "trakt_access_token" to "new-trakt"))

        val merged = PortableCredentialEnvelope.mergeAndEncrypt(local, existing, passphrase, account, profile)
        val values = JSONObject(PortableCredentialEnvelope.decrypt(merged, passphrase, account, profile))
            .getJSONObject("credentials")

        assertEquals("local-tmdb", values.getString("tmdb_api_key"))
        assertEquals("new-trakt", values.getString("trakt_access_token"))
        assertEquals("cloud-only", values.getString("simkl_token"))
    }

    @Test fun `wrong passphrase cannot replace existing envelope`() {
        val existing = PortableCredentialEnvelope.encrypt(
            credentialJson(mapOf("simkl_token" to "cloud-secret")), passphrase, account, profile
        )
        try {
            PortableCredentialEnvelope.mergeAndEncrypt(
                credentialJson(mapOf("tmdb_api_key" to "local-value")), existing,
                "different portable passphrase".toCharArray(), account, profile
            )
            throw AssertionError("Expected wrong passphrase to reject replacement")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("Incorrect"))
        }
        assertEquals("cloud-secret", JSONObject(PortableCredentialEnvelope.decrypt(existing, passphrase, account, profile))
            .getJSONObject("credentials").getString("simkl_token"))
    }

    @Test fun `merge rejects malformed existing decrypted content`() {
        val malformed = PortableCredentialEnvelope.encrypt("{}", passphrase, account, profile)
        try {
            PortableCredentialEnvelope.mergeAndEncrypt(
                credentialJson(mapOf("tmdb_api_key" to "local-value")), malformed, passphrase, account, profile
            )
            throw AssertionError("Expected malformed envelope payload to be rejected")
        } catch (e: IllegalArgumentException) {
            assertFalse(e.message.isNullOrBlank())
        }
    }

    @Test fun `full addon URL credentials are encrypted and recover exactly`() {
        val privateUrl = "https://user:secret@example.test/addon/manifest.json?token=raw-secret"
        val payload = JSONObject().put("format", "plavio-integration-credentials").put("version", 1)
            .put("credentials", JSONObject())
            .put("addon_urls", JSONObject().put("addon-main", JSONObject()
                .put("base_url", privateUrl).put("enabled", true))).toString()
        val envelope = PortableCredentialEnvelope.encrypt(payload, passphrase, account, profile)

        assertFalse(envelope.contains(privateUrl))
        assertFalse(envelope.contains("raw-secret"))
        assertEquals(payload, PortableCredentialEnvelope.decrypt(envelope, passphrase, account, profile))
        assertEquals(privateUrl, PortableCredentialEnvelope.validatePlaintext(
            PortableCredentialEnvelope.decrypt(envelope, passphrase, account, profile)
        ).addonUrls.getValue("addon-main").baseUrl)
    }

    @Test fun `merge preserves cloud-only addon URLs while local values replace same ID`() {
        val remoteOnlyUrl = "https://cloud-user:remote-pass@cloud.example/private/manifest.json?key=remote"
        val replacedUrl = "https://old.example/manifest.json"
        val localUrl = "https://local-user:local-pass@local.example/custom/path?auth=local"
        val remote = PortableCredentialEnvelope.encrypt(addonJson(mapOf(
            "cloud-only" to PortableAddonUrlConfig(remoteOnlyUrl, true),
            "shared" to PortableAddonUrlConfig(replacedUrl, false)
        )), passphrase, account, profile)
        val merged = PortableCredentialEnvelope.mergeAndEncrypt(
            addonJson(mapOf("shared" to PortableAddonUrlConfig(localUrl, true))),
            remote, passphrase, account, profile
        )
        val restored = PortableCredentialEnvelope.validatePlaintext(
            PortableCredentialEnvelope.decrypt(merged, passphrase, account, profile)
        ).addonUrls

        assertEquals(PortableAddonUrlConfig(remoteOnlyUrl, true), restored["cloud-only"])
        assertEquals(PortableAddonUrlConfig(localUrl, true), restored["shared"])
        assertFalse(merged.contains(remoteOnlyUrl))
        assertFalse(merged.contains(localUrl))
    }

    @Test fun `malformed JSON and addon URL field types are rejected`() {
        listOf(
            "not-json",
            """{"format":"plavio-integration-credentials","version":1,"credentials":{},"addon_urls":[]}""",
            """{"format":"plavio-integration-credentials","version":1,"credentials":{},"addon_urls":{"x":{"base_url":42,"enabled":true}}}""",
            """{"format":"plavio-integration-credentials","version":1,"credentials":{},"addon_urls":{"x":{"base_url":"file:///private","enabled":true}}}""",
            """{"format":"plavio-integration-credentials","version":1,"credentials":{"unsupported":"x"}}"""
        ).forEach { invalid ->
            try {
                PortableCredentialEnvelope.validatePlaintext(invalid)
                throw AssertionError("Expected malformed private payload to be rejected")
            } catch (_: Exception) { }
        }
    }

    @Test fun `addon count and URL size are bounded`() {
        val tooMany = JSONObject()
        repeat(1_001) { index ->
            tooMany.put("addon-$index", JSONObject().put("base_url", "https://example.test/$index")
                .put("enabled", true))
        }
        val base = JSONObject().put("format", "plavio-integration-credentials").put("version", 1)
            .put("credentials", JSONObject())
        listOf(
            base.put("addon_urls", tooMany).toString(),
            JSONObject().put("format", "plavio-integration-credentials").put("version", 1)
                .put("credentials", JSONObject()).put("addon_urls", JSONObject().put("x",
                    JSONObject().put("base_url", "https://example.test/" + "x".repeat(8_200)).put("enabled", true))).toString()
        ).forEach { invalid ->
            try {
                PortableCredentialEnvelope.validatePlaintext(invalid)
                throw AssertionError("Expected oversized private payload to be rejected")
            } catch (_: Exception) { }
        }
    }

    @Test fun `legacy envelope without addon URLs remains valid`() {
        val legacy = credentialJson(mapOf("simkl_token" to "legacy-secret"))
        val validated = PortableCredentialEnvelope.validatePlaintext(legacy)
        assertEquals("legacy-secret", validated.credentials["simkl_token"])
        assertTrue(validated.addonUrls.isEmpty())
    }

    private fun credentialJson(values: Map<String, String>): String {
        val credentials = JSONObject()
        values.forEach { (key, value) -> credentials.put(key, value) }
        return JSONObject().put("format", "plavio-integration-credentials")
            .put("version", 1).put("credentials", credentials).toString()
    }

    private fun addonJson(values: Map<String, PortableAddonUrlConfig>): String {
        val addons = JSONObject()
        values.forEach { (id, config) -> addons.put(id, JSONObject()
            .put("base_url", config.baseUrl).put("enabled", config.enabled)) }
        return JSONObject().put("format", "plavio-integration-credentials")
            .put("version", 1).put("credentials", JSONObject()).put("addon_urls", addons).toString()
    }
}
