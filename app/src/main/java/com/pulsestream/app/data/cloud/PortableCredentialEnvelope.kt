package com.pulsestream.app.data.cloud

import com.pulsestream.app.data.PortableAddonUrlConfig
import org.json.JSONObject
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable, passphrase-protected envelope for integration credentials (never Android Keystore ciphertext). */
internal object PortableCredentialEnvelope {
    private const val HEADER = "PLAVIO-CREDENTIALS:1"
    private const val ITERATIONS = 310_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val MAX_PLAINTEXT_BYTES = 1024 * 1024
    private const val MAX_ENVELOPE_CHARS = 2 * 1024 * 1024
    private const val MAX_ADDONS = 1_000
    private const val MAX_ADDON_ID_CHARS = 300
    private const val MAX_ADDON_URL_CHARS = 8_192
    private val allowedCredentialKeys = setOf("simkl_token", "torbox_api_key", "realdebrid_api_key", "trakt_access_token", "trakt_refresh_token")

    internal data class Payload(
        val credentials: Map<String, String>,
        val addonUrls: Map<String, PortableAddonUrlConfig>
    )

    fun encrypt(plaintext: String, passphrase: CharArray, accountId: String, profileId: String): String {
        require(passphrase.size >= 12) { "Choose a portable sync passphrase with at least 12 characters." }
        require(accountId.isNotBlank() && profileId.isNotBlank()) { "Account and profile scope are required." }
        val bytes = plaintext.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MAX_PLAINTEXT_BYTES) { "Integration credentials exceed the safe envelope size limit." }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(associatedData(accountId, profileId))
        val ciphertext = cipher.doFinal(bytes)
        return listOf(HEADER, encode(salt), encode(iv), encode(ciphertext)).joinToString("\n")
            .also { require(it.length <= MAX_ENVELOPE_CHARS) { "Encrypted integration envelope is too large." } }
    }

    fun decrypt(envelope: String, passphrase: CharArray, accountId: String, profileId: String): String {
        require(passphrase.size >= 12) { "Enter the portable sync passphrase (at least 12 characters)." }
        require(envelope.length <= MAX_ENVELOPE_CHARS) { "Encrypted integration envelope exceeds the safety limit." }
        val parts = envelope.split('\n')
        require(parts.size == 4 && parts[0] == HEADER) { "Encrypted integration envelope is unsupported or malformed." }
        val salt = decode(parts[1]); val iv = decode(parts[2]); val ciphertext = decode(parts[3])
        require(salt.size == SALT_BYTES && iv.size == IV_BYTES && ciphertext.size in 17..(MAX_PLAINTEXT_BYTES + 16)) {
            "Encrypted integration envelope is malformed or too large."
        }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(associatedData(accountId, profileId))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Incorrect portable sync passphrase, wrong account/profile, or damaged credentials envelope.")
        }
    }

    /** Parse and strictly validate every private payload value before any local mutation. */
    internal fun validatePlaintext(plaintext: String): Payload {
        require(plaintext.toByteArray(Charsets.UTF_8).size <= MAX_PLAINTEXT_BYTES) { "Encrypted integration credentials exceed the safe size limit." }
        val root = JSONObject(plaintext)
        require(root.optString("format") == "plavio-integration-credentials" && root.optInt("version") == 1) {
            "Encrypted integration credentials have an unsupported format."
        }
        val rootKeys = root.keys().asSequence().toSet()
        require(rootKeys.all { it in setOf("format", "version", "credentials", "addon_urls") }) {
            "Encrypted integration credentials contain an unsupported field."
        }
        val values = root.optJSONObject("credentials") ?: throw IllegalArgumentException("Encrypted integration credentials are malformed.")
        val credentials = linkedMapOf<String, String>()
        val credentialKeys = values.keys()
        while (credentialKeys.hasNext()) {
            val key = credentialKeys.next()
            require(key in allowedCredentialKeys && values.opt(key) is String) { "Encrypted integration credentials contain an unsupported value." }
            val value = values.getString(key)
            require(value.isNotBlank() && value.length <= 16_384) { "Encrypted integration credentials contain an invalid value." }
            credentials[key] = value
        }
        val urls = linkedMapOf<String, PortableAddonUrlConfig>()
        if (root.has("addon_urls")) {
            val addonValues = root.optJSONObject("addon_urls") ?: throw IllegalArgumentException("Encrypted addon URL configurations are malformed.")
            require(addonValues.length() <= MAX_ADDONS) { "Encrypted addon URL configurations exceed the safe count limit." }
            val ids = addonValues.keys()
            while (ids.hasNext()) {
                val id = ids.next()
                require(id.isNotBlank() && id.length <= MAX_ADDON_ID_CHARS && id == id.trim() && id.none { it.isISOControl() }) {
                    "Encrypted addon URL configurations contain an invalid addon ID."
                }
                val entry = addonValues.optJSONObject(id) ?: throw IllegalArgumentException("Encrypted addon URL configuration is malformed.")
                val fields = entry.keys().asSequence().toSet()
                require(fields == setOf("base_url", "enabled") && entry.opt("base_url") is String && entry.opt("enabled") is Boolean) {
                    "Encrypted addon URL configuration has invalid fields or types."
                }
                val baseUrl = entry.getString("base_url")
                require(baseUrl.length in 1..MAX_ADDON_URL_CHARS && isValidAddonUrl(baseUrl)) {
                    "Encrypted addon URL configuration contains an invalid URL."
                }
                urls[id] = PortableAddonUrlConfig(baseUrl, entry.getBoolean("enabled"))
            }
        }
        return Payload(credentials, urls)
    }

    /** Re-encrypt local values without dropping valid remote-only keys or addon configurations. */
    internal fun mergeAndEncrypt(
        localPlaintext: String,
        existingEnvelope: String?,
        passphrase: CharArray,
        accountId: String,
        profileId: String
    ): String {
        val mergedCredentials = linkedMapOf<String, String>()
        val mergedAddonUrls = linkedMapOf<String, PortableAddonUrlConfig>()
        if (existingEnvelope != null) {
            // Deliberately authenticate existing ciphertext and validate its complete payload before replacement.
            val previous = validatePlaintext(decrypt(existingEnvelope, passphrase, accountId, profileId))
            mergedCredentials.putAll(previous.credentials)
            mergedAddonUrls.putAll(previous.addonUrls)
        }
        val local = validatePlaintext(localPlaintext)
        mergedCredentials.putAll(local.credentials)
        mergedAddonUrls.putAll(local.addonUrls)
        val credentials = JSONObject().also { obj -> mergedCredentials.forEach { (key, value) -> obj.put(key, value) } }
        val addons = JSONObject().also { obj -> mergedAddonUrls.forEach { (id, config) ->
            obj.put(id, JSONObject().put("base_url", config.baseUrl).put("enabled", config.enabled))
        } }
        val plaintext = JSONObject().put("format", "plavio-integration-credentials")
            .put("version", 1).put("credentials", credentials).put("addon_urls", addons).toString()
        // Enforce output size after the union as well as validating both inputs.
        require(plaintext.toByteArray(Charsets.UTF_8).size <= MAX_PLAINTEXT_BYTES) { "Integration credentials exceed the safe envelope size limit." }
        return encrypt(plaintext, passphrase, accountId, profileId)
    }

    private fun isValidAddonUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.isAbsolute && uri.scheme.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    } catch (_: Exception) { false }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        val bytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
    }

    private fun associatedData(accountId: String, profileId: String): ByteArray =
        "$HEADER\u0000account=$accountId\u0000profile=$profileId".toByteArray(Charsets.UTF_8)

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
}
