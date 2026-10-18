package com.pulsestream.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.ui.AspectRatioFrameLayout
import com.pulsestream.app.ui.theme.AccentTheme

/** Aspect-ratio modes offered by the in-player screen-size button. */
val ASPECT_MODES: List<Pair<Int, String>> = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom"
)

/**
 * Curated subtitle language options offered in the picker
 * (ISO 639-1 code to display name), sorted alphabetically.
 */
val SUBTITLE_LANGUAGE_OPTIONS: List<Pair<String, String>> = listOf(
    "en" to "English",
    "ar" to "Arabic",
    "bn" to "Bengali",
    "bs" to "Bosnian",
    "bg" to "Bulgarian",
    "zh" to "Chinese",
    "hr" to "Croatian",
    "cs" to "Czech",
    "da" to "Danish",
    "nl" to "Dutch",
    "fi" to "Finnish",
    "fr" to "French",
    "de" to "German",
    "el" to "Greek",
    "gu" to "Gujarati",
    "he" to "Hebrew",
    "hi" to "Hindi",
    "hu" to "Hungarian",
    "id" to "Indonesian",
    "it" to "Italian",
    "ja" to "Japanese",
    "ko" to "Korean",
    "ms" to "Malay",
    "mr" to "Marathi",
    "no" to "Norwegian",
    "fa" to "Persian",
    "pl" to "Polish",
    "pt" to "Portuguese",
    "pa" to "Punjabi",
    "ro" to "Romanian",
    "ru" to "Russian",
    "sr" to "Serbian",
    "sk" to "Slovak",
    "sl" to "Slovenian",
    "es" to "Spanish",
    "sv" to "Swedish",
    "ta" to "Tamil",
    "te" to "Telugu",
    "th" to "Thai",
    "tr" to "Turkish",
    "uk" to "Ukrainian",
    "ur" to "Urdu",
    "vi" to "Vietnamese"
).sortedBy { it.second }

/** Display name for a preferred-language code, or the code itself. */
fun subtitleLanguageName(code: String): String =
    SUBTITLE_LANGUAGE_OPTIONS.find { it.first == code }?.second ?: code.uppercase()

/**
 * Audio language options: the same curated ISO 639-1 set as subtitles
 * (an audio track's language tag usually matches these codes).
 */
val AUDIO_LANGUAGE_OPTIONS: List<Pair<String, String>> = SUBTITLE_LANGUAGE_OPTIONS

/** Display name for a preferred audio-language code, or the code itself. */
fun audioLanguageName(code: String): String =
    AUDIO_LANGUAGE_OPTIONS.find { it.first == code }?.second ?: code.uppercase()

/** Subtitle text-color choices (ARGB) offered in Settings > Playback. */
val SUBTITLE_COLOR_OPTIONS: List<Pair<Int, String>> = listOf(
    0xFFFFFFFF.toInt() to "White",
    0xFFFFFF00.toInt() to "Yellow",
    0xFF00FFFF.toInt() to "Cyan",
    0xFF00FF00.toInt() to "Green",
    0xFFFF69B4.toInt() to "Pink",
    0xFFFFA500.toInt() to "Orange",
    0xFFFF0000.toInt() to "Red"
)

/** Subtitle background/shade choices (ARGB; includes semi-transparent). */
val SUBTITLE_BG_OPTIONS: List<Pair<Int, String>> = listOf(
    0x00000000 to "None",
    0xCC000000.toInt() to "Black",
    0xAA222222.toInt() to "Dark gray",
    0xAAFFFFFF.toInt() to "White",
    0xAA0000FF.toInt() to "Blue"
)

/** Subtitle text-size choices (label to scale factor). */
val SUBTITLE_SIZE_OPTIONS: List<Pair<Float, String>> = listOf(
    0.75f to "Small",
    1.0f to "Normal",
    1.3f to "Large",
    1.6f to "Extra large"
)

/** Subtitle edge/outline style choices (label to stored index). */
val SUBTITLE_EDGE_OPTIONS: List<Pair<Int, String>> = listOf(
    0 to "None",
    1 to "Outline",
    2 to "Drop shadow",
    3 to "Raised",
    4 to "Depressed"
)

fun subtitleColorName(color: Int): String =
    SUBTITLE_COLOR_OPTIONS.find { it.first == color }?.second ?: "Custom"

fun subtitleBgName(color: Int): String =
    SUBTITLE_BG_OPTIONS.find { it.first == color }?.second ?: "Custom"

fun subtitleSizeName(scale: Float): String =
    SUBTITLE_SIZE_OPTIONS.find { it.first == scale }?.second ?: "${(scale * 100).toInt()}%"

fun subtitleEdgeName(index: Int): String =
    SUBTITLE_EDGE_OPTIONS.find { it.first == index }?.second ?: "Drop shadow"

/**
 * True if a subtitle/track language tag matches a preferred language code.
 * Handles ISO 639-1 vs 639-2/B differences ("en" vs "eng", "bs" vs "bos")
 * and OpenSubtitles quirks ("pob" = Brazilian Portuguese) via canonical
 * mapping — plus BCP-47 subtag matching ("en" matches "en-US"/"en_US").
 *
 * Matching is exact or at a subtag boundary only: "hi" must NOT match "hil".
 */
fun subtitleLangMatches(tag: String?, preferredCode: String): Boolean {
    val t = tag?.lowercase()?.trim().orEmpty()
    if (t.isEmpty()) return false
    val p = preferredCode.lowercase().trim()
    // ISO 639-2/B (and OpenSubtitles quirks) → canonical 639-1 primary subtag.
    fun canon(code: String): String {
        val parts = code.split('-', '_')
        val head = when (parts[0]) {
            "eng" -> "en"
            "spa" -> "es"
            "fre", "fra" -> "fr"
            "deu", "ger" -> "de"
            "ita" -> "it"
            "por", "pob" -> "pt"
            "rus" -> "ru"
            "ara" -> "ar"
            "hin" -> "hi"
            "urd" -> "ur"
            "tur" -> "tr"
            "zho", "chi" -> "zh"
            "jpn" -> "ja"
            "kor" -> "ko"
            "nld", "dut" -> "nl"
            "swe" -> "sv"
            "nor", "nob" -> "no"
            "dan" -> "da"
            "fin" -> "fi"
            "pol" -> "pl"
            "ces", "cze" -> "cs"
            "slk", "slo" -> "sk"
            "ron", "rum" -> "ro"
            "ell", "gre" -> "el"
            "heb" -> "he"
            "tha" -> "th"
            "vie" -> "vi"
            "ind" -> "id"
            "msa", "may" -> "ms"
            "tam" -> "ta"
            "tel" -> "te"
            "ben" -> "bn"
            "mar" -> "mr"
            "pan" -> "pa"
            "guj" -> "gu"
            "ukr" -> "uk"
            "hun" -> "hu"
            "srp", "scc" -> "sr"
            "hrv" -> "hr"
            "bos" -> "bs"
            "slv" -> "sl"
            "bul" -> "bg"
            "cat" -> "ca"
            else -> parts[0]
        }
        return (listOf(head) + parts.drop(1)).joinToString("-")
    }
    val normT = canon(t)
    val normP = canon(p)
    if (normT == normP) return true
    // Subtag-boundary prefix only — never a bare string prefix.
    fun startsSubtag(longer: String, shorter: String): Boolean =
        longer.length > shorter.length &&
            longer.startsWith(shorter) &&
            longer[shorter.length] == '-'
    return startsSubtag(normT, normP) || startsSubtag(normP, normT)
}

/** Quality buckets offered in Settings > Streams (canonical sort keys). */
val QUALITY_ORDER_OPTIONS: List<String> = listOf("2160p", "1080p", "720p", "480p")

/** Autoplay quality choices: "Auto" follows the quality order above. */
val AUTOPLAY_QUALITY_OPTIONS: List<String> = listOf("Auto") + QUALITY_ORDER_OPTIONS

/** Autoplay max-size choices (GB; 0 = no limit). */
val AUTOPLAY_SIZE_OPTIONS: List<Pair<Int, String>> = listOf(
    0 to "No limit",
    2 to "Under 2 GB",
    5 to "Under 5 GB",
    10 to "Under 10 GB",
    20 to "Under 20 GB"
)

fun autoplaySizeName(gb: Int): String =
    AUTOPLAY_SIZE_OPTIONS.find { it.first == gb }?.second ?: if (gb <= 0) "No limit" else "Under $gb GB"

/**
 * Buffer presets offered in Settings > Playback > Buffer & Cache.
 * Each preset maps to (minBufferMs, maxBufferMs, bufferForPlaybackMs).
 */
val BUFFER_PRESETS: List<Pair<String, Triple<Int, Int, Int>>> = listOf(
    "Low" to Triple(10_000, 30_000, 1_500),
    "Balanced" to Triple(15_000, 50_000, 2_500),
    "Aggressive" to Triple(30_000, 100_000, 5_000)
)

fun bufferPresetName(minMs: Int, maxMs: Int, forPlaybackMs: Int): String =
    BUFFER_PRESETS.find { it.second == Triple(minMs, maxMs, forPlaybackMs) }?.first
        ?: "Custom (${minMs / 1000}s / ${maxMs / 1000}s)"

/** Disk cache size options in MB (0 = off). */
val DISK_CACHE_OPTIONS: List<Pair<Int, String>> = listOf(
    0 to "Off",
    100 to "100 MB",
    500 to "500 MB",
    1024 to "1 GB"
)

fun diskCacheName(mb: Int): String =
    DISK_CACHE_OPTIONS.find { it.first == mb }?.second
        ?: if (mb <= 0) "Off" else "$mb MB"

/** Which app plays video streams. */
enum class PlayerChoice(val label: String, val blurb: String) {
    BUILT_IN(
        "Built-in player",
        "Plavio's own player — gestures, subtitles, PiP, skip intro"
    ),
    EXTERNAL(
        "External player",
        "Open streams in VLC, MX Player or another app you choose"
    ),
    ASK(
        "Ask every time",
        "Choose the player each time you press play"
    );

    companion object {
        fun fromName(name: String?): PlayerChoice =
            values().find { it.name == name } ?: BUILT_IN
    }
}

/** Tiny SharedPreferences-backed store for user settings (no extra dependency). */
class SettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("pulse_settings", Context.MODE_PRIVATE)
    /**
     * Onboarding flag lives in its OWN prefs file: Android auto-backup must
     * never restore onboarding_done=true on a fresh install / new device,
     * or the first-run guide is silently skipped (see backup_rules.xml).
     */
    private val onboardingPrefs: SharedPreferences =
        appContext.getSharedPreferences("pulse_onboarding", Context.MODE_PRIVATE)

    /** Play the first source automatically (v0.2.0 behavior) vs. manual play. */
    var autoplay: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()

    /** Automatically start the next episode when one finishes (series only). */
    var autoPlayNext: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY_NEXT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOPLAY_NEXT, value).apply()

    /** Play series episodes in random order instead of sequential (v0.9.0+). */
    var shuffleEpisodes: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE_EPISODES, false)
        set(value) = prefs.edit().putBoolean(KEY_SHUFFLE_EPISODES, value).apply()

    /** Picture-in-Picture: allow the player to shrink into a floating window (v0.9.0+). */
    var pipEnabled: Boolean
        get() = prefs.getBoolean(KEY_PIP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PIP_ENABLED, value).apply()

    /**
     * New-episode alerts: a daily background check notifies when shows in
     * the watchlist release new episodes. Default ON; the Settings toggle
     * also requests the POST_NOTIFICATIONS runtime permission on Android 13+.
     */
    var episodeNotifications: Boolean
        get() = prefs.getBoolean(KEY_EPISODE_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_EPISODE_NOTIFICATIONS, value).apply()

    /**
     * Which player opens streams: the built-in Plavio player, an external
     * app chosen by the system chooser, or ask every time.
     * Stored as [PlayerChoice.name]; defaults to built-in.
     */
    var preferredPlayer: String
        get() = prefs.getString(KEY_PREFERRED_PLAYER, PlayerChoice.BUILT_IN.name)
            ?: PlayerChoice.BUILT_IN.name
        set(value) = prefs.edit().putString(KEY_PREFERRED_PLAYER, value).apply()

    /**
     * TMDB API key (user-supplied): enables clickable actors and in-app
     * recommendations ("You May Like"). Get a free key at
     * themoviedb.org/settings/api.
     */
    var tmdbApiKey: String
        get() = prefs.getString(KEY_TMDB_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TMDB_API_KEY, value).apply()

    // ------------------------------------------------------------------
    // Buffer & cache (v0.9.0+): player buffering + on-disk playback cache.
    // ------------------------------------------------------------------

    /** Minimum media buffered before playback can proceed (ms). */
    var bufferMinMs: Int
        get() = prefs.getInt(KEY_BUFFER_MIN_MS, 15_000).coerceIn(5_000, 120_000)
        set(value) = prefs.edit().putInt(KEY_BUFFER_MIN_MS, value.coerceIn(5_000, 120_000)).apply()

    /** Maximum media buffered ahead during playback (ms). */
    var bufferMaxMs: Int
        get() = prefs.getInt(KEY_BUFFER_MAX_MS, 50_000).coerceIn(10_000, 300_000)
        set(value) = prefs.edit().putInt(KEY_BUFFER_MAX_MS, value.coerceIn(10_000, 300_000)).apply()

    /** How much must be buffered before playback starts/resumes (ms). */
    var bufferForPlaybackMs: Int
        get() = prefs.getInt(KEY_BUFFER_FOR_PLAYBACK_MS, 2_500).coerceIn(1_000, 30_000)
        set(value) = prefs.edit().putInt(KEY_BUFFER_FOR_PLAYBACK_MS, value.coerceIn(1_000, 30_000)).apply()

    /**
     * On-disk playback cache size in MB (0 = off). Caches progressive
     * media so re-watches don't re-download. HLS/DASH segments are cached
     * too; live streams are unaffected (they're never re-watched).
     */
    var diskCacheMb: Int
        get() = prefs.getInt(KEY_DISK_CACHE_MB, 0).coerceAtLeast(0)
        set(value) = prefs.edit().putInt(KEY_DISK_CACHE_MB, value.coerceAtLeast(0)).apply()

    /** Keep the screen awake while the player is open. */
    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    /** Show the "Skip intro / recap / outro" button when segments are known. */
    var showSkipButton: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SKIP, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SKIP, value).apply()

    /** Per-addon flag so pre-installed addons are only auto-installed once. */
    fun isPreinstallDone(manifestId: String): Boolean =
        prefs.getBoolean(KEY_PREINSTALL_PREFIX + manifestId, false)

    fun setPreinstallDone(manifestId: String) =
        prefs.edit().putBoolean(KEY_PREINSTALL_PREFIX + manifestId, true).apply()

    /**
     * Clears every preinstall_done_* marker. Called when the database had
     * to be recreated from corruption: the DB is empty but the flags
     * survived, which would silently skip reinstalling the bundled addons
     * (PlavioMeta/Cinemeta/OpenSubtitles/SubSense). Clearing them lets
     * ensurePreinstalledAddons() run again.
     */
    fun clearPreinstallFlags() {
        val stale = prefs.all.keys.filter { it.startsWith(KEY_PREINSTALL_PREFIX) }
        if (stale.isNotEmpty()) {
            prefs.edit().apply { stale.forEach(::remove) }.apply()
        }
    }

    /**
     * "System default" subtitle appearance: when true the player skips its
     * own styling (no setStyle()) so Android's Settings > Accessibility >
     * Captions preferences apply instead.
     */
    var useSystemCaptions: Boolean
        get() = prefs.getBoolean(KEY_SYSTEM_CAPTIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_SYSTEM_CAPTIONS, value).apply()

    /** Simkl access token (null = not linked). */
    var simklToken: String?
        get() = prefs.getString(KEY_SIMKL_TOKEN, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_SIMKL_TOKEN).apply()
            else prefs.edit().putString(KEY_SIMKL_TOKEN, value).apply()
        }

    /** Simkl display name for the linked account. */
    var simklUsername: String?
        get() = prefs.getString(KEY_SIMKL_USERNAME, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_SIMKL_USERNAME).apply()
            else prefs.edit().putString(KEY_SIMKL_USERNAME, value).apply()
        }

    /** TorBox API key (null = not configured). Debrid-only: resolves cached torrent infoHashes to direct HTTP links. */
    var torboxApiKey: String?
        get() = prefs.getString(KEY_TORBOX_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value.isNullOrBlank()) prefs.edit().remove(KEY_TORBOX_API_KEY).apply()
            else prefs.edit().putString(KEY_TORBOX_API_KEY, value.trim()).apply()
        }

    /** Real-Debrid API key (null = not configured). Debrid-only. */
    var realDebridApiKey: String?
        get() = prefs.getString(KEY_REALDEBRID_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value.isNullOrBlank()) prefs.edit().remove(KEY_REALDEBRID_API_KEY).apply()
            else prefs.edit().putString(KEY_REALDEBRID_API_KEY, value.trim()).apply()
        }


    /** Trakt access token (null = not linked). */
    var traktAccessToken: String?
        get() = prefs.getString(KEY_TRAKT_TOKEN, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_TRAKT_TOKEN).apply()
            else prefs.edit().putString(KEY_TRAKT_TOKEN, value).apply()
        }

    /** Trakt refresh token. */
    var traktRefreshToken: String?
        get() = prefs.getString(KEY_TRAKT_REFRESH, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_TRAKT_REFRESH).apply()
            else prefs.edit().putString(KEY_TRAKT_REFRESH, value).apply()
        }

    /** Epoch millis when the Trakt access token should be refreshed. */
    var traktTokenExpiry: Long
        get() = prefs.getLong(KEY_TRAKT_EXPIRY, 0L)
        set(value) = prefs.edit().putLong(KEY_TRAKT_EXPIRY, value).apply()

    /** Trakt display name for the linked account. */
    var traktUsername: String?
        get() = prefs.getString(KEY_TRAKT_USERNAME, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_TRAKT_USERNAME).apply()
            else prefs.edit().putString(KEY_TRAKT_USERNAME, value).apply()
        }

    /** Accent color theme name (see AccentTheme). */
    var accentName: String
        get() = prefs.getString(KEY_ACCENT, AccentTheme.PULSE.name) ?: AccentTheme.PULSE.name
        set(value) = prefs.edit().putString(KEY_ACCENT, value).apply()

    /** Pure-black backgrounds (AMOLED-friendly). */
    var amoledBlack: Boolean
        get() = prefs.getBoolean(KEY_AMOLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AMOLED, value).apply()

    /** Stream badge pack key (custom pack "custom:<name>"). Empty = no badges. */
    var streamCardStyle: String
        get() = prefs.getString(KEY_CARD_STYLE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CARD_STYLE, value).apply()

    /**
     * Preferred subtitle languages (ISO 639-1 codes, up to 2).
     * Empty = no preference (first available subtitle is used).
     */
    var subtitleLanguages: List<String>
        get() = prefs.getString(KEY_SUBTITLE_LANGS, "")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(2)
            ?: emptyList()
        set(value) = prefs.edit()
            .putString(KEY_SUBTITLE_LANGS, value.take(2).joinToString(","))
            .apply()

    /** First-run onboarding completed (v0.8.21+). False = show the 3-step guide. */
    var onboardingDone: Boolean
        get() {
            if (onboardingPrefs.contains(KEY_ONBOARDING_DONE)) {
                return onboardingPrefs.getBoolean(KEY_ONBOARDING_DONE, false)
            }
            // One-time migration from the old shared file (pre-backup-rules builds).
            val legacy = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
            onboardingPrefs.edit().putBoolean(KEY_ONBOARDING_DONE, legacy).apply()
            return legacy
        }
        set(value) = onboardingPrefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    /**
     * Preferred audio language (ISO 639-1 code, single). Null = no preference
     * (ExoPlayer's default track selection). When set and a title's audio
     * tracks include it, the player auto-selects it at playback start.
     */
    var preferredAudioLanguage: String?
        get() = prefs.getString(KEY_AUDIO_LANG, null)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value == null) prefs.edit().remove(KEY_AUDIO_LANG).apply()
            else prefs.edit().putString(KEY_AUDIO_LANG, value).apply()
        }

    /** Subtitle text color (ARGB int). Default: white. */
    var subtitleTextColor: Int
        get() = prefs.getInt(KEY_SUB_COLOR, 0xFFFFFFFF.toInt())
        set(value) = prefs.edit().putInt(KEY_SUB_COLOR, value).apply()

    /** Subtitle text size scale (1.0 = default). Range 0.6 – 2.0. */
    var subtitleTextSizeScale: Float
        get() = prefs.getFloat(KEY_SUB_SIZE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SUB_SIZE, value.coerceIn(0.6f, 2.0f)).apply()

    /** Subtitle background color (ARGB int). Default: semi-transparent black. */
    var subtitleBackgroundColor: Int
        get() = prefs.getInt(KEY_SUB_BG, 0xCC000000.toInt())
        set(value) = prefs.edit().putInt(KEY_SUB_BG, value).apply()

    /**
     * Subtitle edge style: 0 = none, 1 = outline, 2 = drop shadow,
     * 3 = raised, 4 = depressed. Default: 2 (drop shadow).
     */
    var subtitleEdgeType: Int
        get() = prefs.getInt(KEY_SUB_EDGE, 2)
        set(value) = prefs.edit().putInt(KEY_SUB_EDGE, value.coerceIn(0, 4)).apply()

    /**
     * Global subtitle timing offset in milliseconds (v0.9.0+). Positive =
     * subtitles appear later, negative = earlier. Range ±10 seconds.
     * Applied by rewriting subtitle files with shifted timestamps before
     * they are attached to the player (ExoPlayer has no delay API for
     * side-loaded subtitles).
     */
    var subtitleOffsetMs: Long
        get() = prefs.getLong(KEY_SUBTITLE_OFFSET_MS, 0L).coerceIn(-10_000L, 10_000L)
        set(value) = prefs.edit().putLong(KEY_SUBTITLE_OFFSET_MS, value.coerceIn(-10_000L, 10_000L)).apply()

    /** Pinned Live TV channels, keys "addonId|metaId" ("m3u|url" for M3U). */
    var liveFavorites: Set<String>
        get() = prefs.getStringSet(KEY_LIVE_FAVS, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_LIVE_FAVS, value).apply()

    /** Recently watched Live TV channel keys, most-recent first (max 20). */
    var liveRecent: List<String>
        get() = prefs.getString(KEY_LIVE_RECENT, "")
            ?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(20)
            ?: emptyList()
        set(value) = prefs.edit()
            .putString(KEY_LIVE_RECENT, value.take(20).joinToString("\n")).apply()

    /** User-added M3U/IPTV playlist URLs. */
    var m3uPlaylists: List<String>
        get() = prefs.getString(KEY_M3U_LISTS, "")
            ?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: emptyList()
        set(value) = prefs.edit()
            .putString(KEY_M3U_LISTS, value.joinToString("\n")).apply()

    /**
     * Epoch millis of the last successful Home catalog refresh.
     * Per-section freshness: the Live tab writes its own rows into the same
     * catalog_cache table, so Home freshness must be tracked separately —
     * otherwise a Live visit would suppress Home's background refresh.
     */
    var homeCatalogsRefreshedAt: Long
        get() = prefs.getLong(KEY_HOME_REFRESHED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_HOME_REFRESHED_AT, value).apply()

    // ------------------------------------------------------------------
    // Streams section (v0.8.31+): per-source caps, quality order, autoplay.
    // ------------------------------------------------------------------

    /** Max streams shown per source addon in the stream picker (1–10). */
    var streamsPerSource: Int
        get() = prefs.getInt(KEY_STREAMS_PER_SOURCE, 5).coerceIn(1, 10)
        set(value) = prefs.edit().putInt(KEY_STREAMS_PER_SOURCE, value.coerceIn(1, 10)).apply()

    /**
     * Quality sort priority for the stream picker, top = highest priority.
     * Canonical buckets: "2160p", "1080p", "720p", "480p".
     */
    var qualityOrder: List<String>
        get() {
            val saved = prefs.getString(KEY_QUALITY_ORDER, null)
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList()
            val valid = saved.filter { it in QUALITY_ORDER_OPTIONS }
            return if (valid.isEmpty()) QUALITY_ORDER_OPTIONS else valid
        }
        set(value) = prefs.edit()
            .putString(KEY_QUALITY_ORDER, value.filter { it in QUALITY_ORDER_OPTIONS }.joinToString(","))
            .apply()

    /**
     * Stream autoplay: when ON, pressing Play auto-picks the first stream
     * matching the quality priority + size rule instead of the probed best.
     */
    var streamAutoplay: Boolean
        get() = prefs.getBoolean(KEY_STREAM_AUTOPLAY, false)
        set(value) = prefs.edit().putBoolean(KEY_STREAM_AUTOPLAY, value).apply()

    /** Preferred autoplay quality ("Auto" = follow qualityOrder, else a bucket). */
    var autoplayQuality: String
        get() = (prefs.getString(KEY_AUTOPLAY_QUALITY, "Auto") ?: "Auto")
            .takeIf { it == "Auto" || it in QUALITY_ORDER_OPTIONS } ?: "Auto"
        set(value) = prefs.edit()
            .putString(KEY_AUTOPLAY_QUALITY, if (value == "Auto" || value in QUALITY_ORDER_OPTIONS) value else "Auto")
            .apply()

    /** Autoplay max file size in GB (0 = no limit). */
    var autoplayMaxSizeGb: Int
        get() = prefs.getInt(KEY_AUTOPLAY_MAX_SIZE, 0).coerceAtLeast(0)
        set(value) = prefs.edit().putInt(KEY_AUTOPLAY_MAX_SIZE, value.coerceAtLeast(0)).apply()

    // ------------------------------------------------------------------
    // Backup & restore (v0.9.0+): raw prefs access for JSON export/import.
    // The onboarding prefs file is deliberately excluded — restoring
    // onboarding_done=true on a new device would skip the first-run guide.
    // ------------------------------------------------------------------

    /** Snapshot of every stored preference (excludes the onboarding file). */
    fun exportAllPrefs(): Map<String, *> = prefs.all.toMap()

    /**
     * Restores preferences from an export map. Unknown keys and wrong-typed
     * values are skipped; StringSet values are restored as string sets.
     */
    fun importAllPrefs(values: Map<String, Any?>) {
        val e = prefs.edit()
        for ((k, v) in values) {
            when (v) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
                is Double -> e.putFloat(k, v.toFloat())
                is String -> e.putString(k, v)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    e.putStringSet(k, (v as Set<*>).map { it.toString() }.toSet())
                }
                is List<*> -> {
                    // org.json turns JSON arrays into Lists; prefs string-sets
                    // are the only collection type we store.
                    e.putStringSet(k, v.map { it.toString() }.toSet())
                }
                null -> e.remove(k)
            }
        }
        e.apply()
    }

    companion object {
        private const val KEY_AUTOPLAY = "autoplay"
        private const val KEY_AUTOPLAY_NEXT = "autoplay_next_episode"
        private const val KEY_SHUFFLE_EPISODES = "shuffle_episodes"
        private const val KEY_PIP_ENABLED = "pip_enabled"
        private const val KEY_EPISODE_NOTIFICATIONS = "episode_notifications"
        private const val KEY_PREFERRED_PLAYER = "preferred_player"
        private const val KEY_BUFFER_MIN_MS = "buffer_min_ms"
        private const val KEY_BUFFER_MAX_MS = "buffer_max_ms"
        private const val KEY_BUFFER_FOR_PLAYBACK_MS = "buffer_for_playback_ms"
        private const val KEY_DISK_CACHE_MB = "disk_cache_mb"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_TMDB_API_KEY = "tmdb_api_key"
        private const val KEY_AUDIO_LANG = "preferred_audio_language"
        private const val KEY_SUB_COLOR = "subtitle_text_color"
        private const val KEY_SUB_SIZE = "subtitle_text_size_scale"
        private const val KEY_SUB_BG = "subtitle_background_color"
        private const val KEY_SUB_EDGE = "subtitle_edge_type"
        private const val KEY_SUBTITLE_OFFSET_MS = "subtitle_offset_ms"
        private const val KEY_SYSTEM_CAPTIONS = "use_system_captions"
        private const val KEY_LIVE_FAVS = "live_favorites"
        private const val KEY_LIVE_RECENT = "live_recent"
        private const val KEY_M3U_LISTS = "m3u_playlists"
        private const val KEY_HOME_REFRESHED_AT = "home_catalogs_refreshed_at"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_SHOW_SKIP = "show_skip_button"
        private const val KEY_SUBTITLE_LANGS = "subtitle_languages"
        private const val KEY_ACCENT = "accent_theme"
        private const val KEY_AMOLED = "amoled_black"
        private const val KEY_CARD_STYLE = "stream_card_style"
        private const val KEY_PREINSTALL_PREFIX = "preinstall_done_"
        private const val KEY_SIMKL_TOKEN = "simkl_token"
        private const val KEY_SIMKL_USERNAME = "simkl_username"
        private const val KEY_TRAKT_TOKEN = "trakt_access_token"
        private const val KEY_TRAKT_REFRESH = "trakt_refresh_token"
        private const val KEY_TRAKT_EXPIRY = "trakt_token_expiry"
        private const val KEY_TRAKT_USERNAME = "trakt_username"
        private const val KEY_TORBOX_API_KEY = "torbox_api_key"
        private const val KEY_REALDEBRID_API_KEY = "realdebrid_api_key"
        private const val KEY_STREAMS_PER_SOURCE = "streams_per_source"
        private const val KEY_QUALITY_ORDER = "quality_order"
        private const val KEY_STREAM_AUTOPLAY = "stream_autoplay"
        private const val KEY_AUTOPLAY_QUALITY = "autoplay_quality"
        private const val KEY_AUTOPLAY_MAX_SIZE = "autoplay_max_size_gb"
    }
}
