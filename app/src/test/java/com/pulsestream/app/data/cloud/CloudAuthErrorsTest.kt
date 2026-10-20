package com.pulsestream.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAuthErrorsTest {
    @Test fun acceptsHttpsEndpointAndConfiguredPublicKeyFlag() {
        assertNull(CloudAuthErrors.configurationError("https://cloud.example.org", true))
    }

    @Test fun rejectsMissingKeyPlaceholderAndUnsafeEndpoint() {
        assertTrue(CloudAuthErrors.configurationError("https://cloud.example.org", false) != null)
        assertTrue(CloudAuthErrors.configurationError("https://your-project.example.org", true) != null)
        assertTrue(CloudAuthErrors.configurationError("http://cloud.example.org", true) != null)
    }

    @Test fun distinguishesRejectedApiConfigurationFromWrongCredentials() {
        val configMessage = CloudAuthErrors.responseMessage(
            401, "{\"message\":\"Invalid API key\"}", "Login failed. Check your email and password."
        )
        assertTrue(configMessage.contains("API configuration"))
        assertTrue(configMessage.contains("not an account password problem"))
        assertFalse(configMessage.contains("Invalid API key"))

        val credentialsFallback = "Login failed. Check your email and password."
        assertEquals(
            credentialsFallback,
            CloudAuthErrors.responseMessage(400, "{\"message\":\"Invalid login credentials\"}", credentialsFallback)
        )
    }

    @Test fun diagnosesMissingAuthEndpointWithoutEchoingBackendDetails() {
        val message = CloudAuthErrors.responseMessage(404, "private server detail", "Login failed.")
        assertTrue(message.contains("endpoint was not found"))
        assertFalse(message.contains("private server detail"))
    }

    @Test fun retainsSafeEmailAndTemporaryServiceGuidance() {
        assertTrue(CloudAuthErrors.responseMessage(400, "email_not_confirmed", "fallback").contains("verify your email"))
        assertTrue(CloudAuthErrors.responseMessage(503, "", "fallback").contains("temporarily unavailable"))
    }

    @Test fun statusOnlyRateLimitIsNotMisreportedAsWrongPassword() {
        assertTrue(CloudAuthErrors.responseMessage(429, "", "Wrong password").contains("Too many attempts"))
    }

    @Test fun structuredOrMalformedProviderErrorsUseSafeFallback() {
        assertEquals("Safe fallback", CloudAuthErrors.responseMessage(400,
            "{\"message\":{\"private\":\"detail\"}}", "Safe fallback"))
        assertEquals("Safe fallback", CloudAuthErrors.responseMessage(400,
            "not-json private detail", "Safe fallback"))
    }
}
