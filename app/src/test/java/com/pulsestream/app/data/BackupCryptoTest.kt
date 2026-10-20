package com.pulsestream.app.data

import org.junit.Assert.*
import org.junit.Test

class BackupCryptoTest {
    @Test fun `encrypted payload round trips and reveals no plaintext`() {
        val secretData = "watchlist title and api key must not appear in clear text"
        val encrypted = BackupCrypto.encrypt(secretData, "correct horse battery".toCharArray())
        assertTrue(BackupCrypto.isEncrypted(encrypted))
        assertFalse(encrypted.contains("watchlist title"))
        assertFalse(encrypted.contains("api key"))
        assertEquals(secretData, BackupCrypto.decrypt(encrypted, "correct horse battery".toCharArray()))
    }

    @Test fun `wrong password fails authenticated decryption`() {
        val encrypted = BackupCrypto.encrypt("payload", "first password".toCharArray())
        try {
            BackupCrypto.decrypt(encrypted, "other password".toCharArray())
            fail("wrong password should not decrypt")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("Incorrect password"))
        }
    }

    @Test fun `modified ciphertext is rejected`() {
        val encrypted = BackupCrypto.encrypt("payload", "valid password".toCharArray())
        val parts = encrypted.split('\n').toMutableList()
        // Alter authenticated bytes, not unused low bits of an unpadded Base64 character.
        val data = java.util.Base64.getDecoder().decode(parts.last())
        data[0] = (data[0].toInt() xor 1).toByte()
        parts[parts.lastIndex] = java.util.Base64.getEncoder().withoutPadding().encodeToString(data)
        try {
            BackupCrypto.decrypt(parts.joinToString("\n"), "valid password".toCharArray())
            fail("tampered ciphertext should not decrypt")
        } catch (e: Exception) {
            assertTrue(BackupCrypto.isEncrypted(parts.joinToString("\n")))
        }
    }
    @org.junit.Test fun maximumSizePlaintextRoundTripsIncludingAuthenticationTag() {
        val text = "a".repeat(10 * 1024 * 1024)
        val password = "boundary-password".toCharArray()
        val encrypted = BackupCrypto.encrypt(text, password)
        org.junit.Assert.assertEquals(text, BackupCrypto.decrypt(encrypted, password))
        org.junit.Assert.assertEquals(encrypted, BackupManager.readBounded(encrypted.byteInputStream()))
    }

}
