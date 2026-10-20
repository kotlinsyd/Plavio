package com.pulsestream.app.data

import java.net.URLDecoder

/** Extracts only a valid v1 BitTorrent identity; never treats arbitrary URL text as a hash. */
fun torrentHashFromMagnet(url: String?): String? {
    if (url == null || !url.startsWith("magnet:?", ignoreCase = true)) return null
    val values = url.substringAfter('?').substringBefore('#').split('&').mapNotNull { part ->
        val key = part.substringBefore('=')
        if (!key.equals("xt", ignoreCase = true)) null
        else runCatching { URLDecoder.decode(part.substringAfter('=', ""), "UTF-8") }.getOrNull()
    }
    val hashes = values.filter { it.startsWith("urn:btih:", ignoreCase = true) }.mapNotNull {
        val hash = it.substringAfterLast(':')
        when {
            hash.matches(Regex("[0-9a-fA-F]{40}")) -> hash.lowercase()
            hash.matches(Regex("[a-zA-Z2-7]{32}")) -> {
                var bits = 0
                var buffer = 0
                val bytes = ArrayList<Int>(20)
                for (char in hash.uppercase()) {
                    val value = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(char)
                    buffer = (buffer shl 5) or value
                    bits += 5
                    if (bits >= 8) {
                        bits -= 8
                        bytes += (buffer shr bits) and 255
                    }
                }
                bytes.joinToString("") { value -> "%02x".format(value) }
            }
            else -> null
        }
    }.distinct()
    return hashes.singleOrNull()
}
