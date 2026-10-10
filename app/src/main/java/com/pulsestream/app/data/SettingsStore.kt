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
 * True if a subtitle/track language tag matches a preferred language code.
 * Lenient: handles ISO 639-1 vs 639-2/B differences ("en" vs "eng",
 * "bs" vs "bos") and OpenSubtitles quirks ("pob" = Brazilian Portuguese)
 * via prefix/alias matching.
 */
fun subtitleLangMatches(tag: String?, preferredCode: String): Boolean {
    val t = tag?.lowercase()?.trim().orEmpty()
    if (t.isEmpty()) return false
    val p = preferredCode.lowercase().trim()
    if (t == p) return true
    // Alias normalization.
    fun norm(code: String): String = when (code) {
        "bos" -> "bs"
        "pob" -> "pt"
        "scc" -> "sr"
        else -> code
    }
    val normT = norm(t)
    val normP = norm(p)
    if (normT == normP) return true
    return normT.startsWith(normP) || normP.startsWith(normT)
}

/** Tiny SharedPreferences-backed store for user settings (no extra dependency). */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pulse_settings", Context.MODE_PRIVATE)

    /** Play the first source automatically (v0.2.0 behavior) vs. manual play. */
    var autoplay: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()

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

    companion object {
        private const val KEY_AUTOPLAY = "autoplay"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_SHOW_SKIP = "show_skip_button"
        private const val KEY_SUBTITLE_LANGS = "subtitle_languages"
        private const val KEY_ACCENT = "accent_theme"
        private const val KEY_AMOLED = "amoled_black"
        private const val KEY_PREINSTALL_PREFIX = "preinstall_done_"
        private const val KEY_SIMKL_TOKEN = "simkl_token"
        private const val KEY_SIMKL_USERNAME = "simkl_username"
        private const val KEY_TRAKT_TOKEN = "trakt_access_token"
        private const val KEY_TRAKT_REFRESH = "trakt_refresh_token"
        private const val KEY_TRAKT_EXPIRY = "trakt_token_expiry"
        private const val KEY_TRAKT_USERNAME = "trakt_username"
    }
}
