package com.pulsestream.app.data

import java.util.Base64
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-encrypted portable file wrapper. Does not make credentials eligible for export. */
object BackupCrypto {
    private const val ITERATIONS = 250_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun isEncrypted(payload: String): Boolean = payload.startsWith("PLAVIO-ENCRYPTED-BACKUP:1\n")

    fun encrypt(payload: String, password: CharArray): String {
        require(password.size >= 10) { "Use a backup password with at least 10 characters." }
        require(payload.toByteArray(Charsets.UTF_8).size <= 10 * 1024 * 1024) { "Backup exceeds the 10 MiB safety limit." }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        return listOf("PLAVIO-ENCRYPTED-BACKUP:1", encode(salt), encode(iv), encode(encrypted)).joinToString("\n")
    }

    fun decrypt(payload: String, password: CharArray): String {
        require(isEncrypted(payload)) { "This isn't a supported encrypted Plavio backup." }
        val parts = payload.split('\n')
        require(parts.size == 4) { "Encrypted backup data is malformed." }
        val salt = decode(parts[1]); val iv = decode(parts[2]); val ciphertext = decode(parts[3])
        require(salt.size == SALT_BYTES && iv.size == IV_BYTES && ciphertext.size in (TAG_BITS / 8 + 1)..(10 * 1024 * 1024 + TAG_BITS / 8)) {
            "Encrypted backup data is malformed or too large."
        }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Incorrect password or damaged encrypted backup.")
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        require(password.size >= 10) { "Use a backup password with at least 10 characters." }
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().withoutPadding().encodeToString(bytes)
    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}
