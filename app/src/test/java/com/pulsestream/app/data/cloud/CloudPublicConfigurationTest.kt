package com.pulsestream.app.data.cloud

import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline configuration gate. No account/network access; assertions never echo a key. */
class CloudPublicConfigurationTest {
    private fun configured(name: String): String {
        val field = CloudManager::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(null) as String
    }

    @Test fun endpointIsTheIntendedHttpsProjectWithoutCredentialsOrQuery() {
        val endpoint = URI(configured("SUPABASE_URL"))
        assertTrue("Cloud endpoint must remain the intended HTTPS project",
            endpoint.scheme == "https" &&
                endpoint.host == "vpngqutsjxrdgaihogor.supabase.co" &&
                endpoint.rawUserInfo == null && endpoint.rawQuery == null &&
                endpoint.rawFragment == null && endpoint.port == -1 &&
                endpoint.rawPath.isNullOrEmpty())
    }

    @Test fun embeddedKeyMatchesTheReadOnlyVerifiedActiveLegacyAnonConfiguration() {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(configured("SUPABASE_ANON_KEY").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        // Pin the accepted configuration, not merely plausible claims. Update only after
        // read-only public-key retrieval and a successful public auth-settings check.
        assertTrue("Public client configuration differs from the verified active anon key",
            digest == "47b16100ee01249d12a06c3b10794991a3797171a9c2a99b2b7f979ab1038491")
    }

    @Test fun embeddedLegacyKeyIsAnonForThisProjectAndNotExpiredOrPrivileged() {
        val key = configured("SUPABASE_ANON_KEY")
        val parts = key.split('.')
        assertTrue("Expected a legacy public client JWT", parts.size == 3)
        // Parsing failures deliberately omit values and exception details.
        val claims = try {
            Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)).jsonObject
        } catch (_: Exception) {
            throw AssertionError("Public client configuration has invalid claims")
        }
        assertTrue("Client key must use only the unprivileged anon role",
            claims["role"]?.jsonPrimitive?.content == "anon")
        assertTrue("Client key must belong to the configured project",
            claims["ref"]?.jsonPrimitive?.content == "vpngqutsjxrdgaihogor")
        assertTrue("Expected the Supabase legacy issuer",
            claims["iss"]?.jsonPrimitive?.content == "supabase")
        val now = Instant.now().epochSecond
        assertTrue("Public client key must not be expired",
            (claims["exp"]?.jsonPrimitive?.long ?: 0) > now)
        assertTrue("Public client key issue time must not be in the future",
            (claims["iat"]?.jsonPrimitive?.long ?: Long.MAX_VALUE) <= now + 300)
    }
}
