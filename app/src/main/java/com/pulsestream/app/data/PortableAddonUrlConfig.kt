package com.pulsestream.app.data

/**
 * A raw addon endpoint and the user's desired enabled state, intended only for
 * the authenticated/encrypted cross-device private snapshot—not plaintext backups.
 */
data class PortableAddonUrlConfig(val baseUrl: String, val enabled: Boolean)
