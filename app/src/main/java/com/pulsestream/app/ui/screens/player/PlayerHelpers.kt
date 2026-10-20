@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pulsestream.app.ui.screens.player

import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import com.pulsestream.app.data.ResolvedSubtitle

/**
 * Format recovery accepts gateways that reject HEAD or hide playlist MIME types.
 * Uses the same TLS and credential-scoping rules as normal playback.
 */
internal fun probePlaybackContentType(url: String, headers: Map<String, String>): String? {
    return com.pulsestream.app.data.PlaybackContentProbe.probe(url, headers)
}

/** Start ExoPlayer's silent prepare before waiting on persisted resume progress. */
internal suspend fun preparePlayerBeforeSavedPositionLookup(
    preparePlayer: () -> Unit,
    loadSavedPosition: suspend () -> Long,
    prepareEarly: Boolean = true
): Long {
    if (prepareEarly) preparePlayer()
    return loadSavedPosition()
}

/** Transient on-screen feedback for gestures, speed, aspect and track changes. */
internal sealed interface PlayerIndicator {
    data class Volume(val percent: Int) : PlayerIndicator
    data class Brightness(val percent: Int) : PlayerIndicator
    data class Seek(val text: String) : PlayerIndicator
    data class Speed(val label: String) : PlayerIndicator
    data class Message(val text: String) : PlayerIndicator
}

internal val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

internal fun speedLabel(s: Float): String =
    if (s == s.toInt().toFloat()) "${s.toInt()}x" else "${s}x"

/** Stable identity of a resolved subtitle list (cache files are reused across re-resolves). */
internal fun subtitleIdentity(list: List<ResolvedSubtitle>): String =
    list.joinToString("|") { it.file.absolutePath + "#" + it.mimeType }

internal fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** ISO 639-2/B (and -1) codes seen in the wild → display names. */
internal val LANG_NAMES = mapOf(
    "eng" to "English", "en" to "English",
    "spa" to "Spanish", "es" to "Spanish",
    "fre" to "French", "fra" to "French", "fr" to "French",
    "deu" to "German", "ger" to "German", "de" to "German",
    "ita" to "Italian", "it" to "Italian",
    "por" to "Portuguese", "pob" to "Portuguese (BR)", "pt" to "Portuguese",
    "rus" to "Russian", "ru" to "Russian",
    "ara" to "Arabic", "ar" to "Arabic",
    "hin" to "Hindi", "hi" to "Hindi",
    "urd" to "Urdu", "ur" to "Urdu",
    "tur" to "Turkish", "tr" to "Turkish",
    "zho" to "Chinese", "chi" to "Chinese", "zh" to "Chinese",
    "jpn" to "Japanese", "ja" to "Japanese",
    "kor" to "Korean", "ko" to "Korean",
    "nld" to "Dutch", "dut" to "Dutch", "nl" to "Dutch",
    "swe" to "Swedish", "sv" to "Swedish",
    "nor" to "Norwegian", "nob" to "Norwegian", "no" to "Norwegian",
    "dan" to "Danish", "da" to "Danish",
    "fin" to "Finnish", "fi" to "Finnish",
    "pol" to "Polish", "pl" to "Polish",
    "ces" to "Czech", "cze" to "Czech", "cs" to "Czech",
    "slk" to "Slovak", "slo" to "Slovak", "sk" to "Slovak",
    "ron" to "Romanian", "rum" to "Romanian", "ro" to "Romanian",
    "ell" to "Greek", "gre" to "Greek", "el" to "Greek",
    "heb" to "Hebrew", "he" to "Hebrew",
    "tha" to "Thai", "th" to "Thai",
    "vie" to "Vietnamese", "vi" to "Vietnamese",
    "ind" to "Indonesian", "id" to "Indonesian",
    "msa" to "Malay", "may" to "Malay", "ms" to "Malay",
    "tam" to "Tamil", "ta" to "Tamil",
    "tel" to "Telugu", "te" to "Telugu",
    "ben" to "Bengali", "bn" to "Bengali",
    "mar" to "Marathi", "mr" to "Marathi",
    "pan" to "Punjabi", "pa" to "Punjabi",
    "guj" to "Gujarati", "gu" to "Gujarati",
    "ukr" to "Ukrainian", "uk" to "Ukrainian",
    "hun" to "Hungarian", "hu" to "Hungarian",
    "srp" to "Serbian", "sr" to "Serbian",
    "hrv" to "Croatian", "hr" to "Croatian",
    "bos" to "Bosnian", "ba" to "Bosnian",
    "slv" to "Slovenian", "sl" to "Slovenian",
    "bul" to "Bulgarian", "bg" to "Bulgarian",
    "cat" to "Catalan", "ca" to "Catalan"
)

internal fun displayLanguage(code: String?): String? {
    if (code.isNullOrBlank() || code == "und") return null
    return LANG_NAMES[code.lowercase()] ?: code.uppercase()
}

internal data class TrackOption(
    val group: TrackGroup,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean
)

internal fun trackLabel(format: Format, fallbackIndex: Int): String {
    if (!format.label.isNullOrBlank()) return format.label!!
    return displayLanguage(format.language) ?: "Track ${fallbackIndex + 1}"
}

internal fun trackOptions(player: Player, type: Int): List<TrackOption> =
    player.currentTracks.groups
        .filter { it.type == type }
        .flatMapIndexed { gi, g ->
            (0 until g.length).map { i ->
                TrackOption(g.mediaTrackGroup, i, trackLabel(g.getTrackFormat(i), gi), g.isSelected)
            }
        }

internal fun hasSelectedTrack(player: Player, type: Int): Boolean =
    player.currentTracks.groups.any { it.type == type && it.isSelected }

/** End of player helpers. */
