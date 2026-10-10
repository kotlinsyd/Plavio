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

    /** Accent color theme name (see AccentTheme). */
    var accentName: String
        get() = prefs.getString(KEY_ACCENT, AccentTheme.PULSE.name) ?: AccentTheme.PULSE.name
        set(value) = prefs.edit().putString(KEY_ACCENT, value).apply()

    /** Pure-black backgrounds (AMOLED-friendly). */
    var amoledBlack: Boolean
        get() = prefs.getBoolean(KEY_AMOLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AMOLED, value).apply()

    companion object {
        private const val KEY_AUTOPLAY = "autoplay"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_SHOW_SKIP = "show_skip_button"
        private const val KEY_ACCENT = "accent_theme"
        private const val KEY_AMOLED = "amoled_black"
        private const val KEY_PREINSTALL_PREFIX = "preinstall_done_"
        private const val KEY_SIMKL_TOKEN = "simkl_token"
        private const val KEY_SIMKL_USERNAME = "simkl_username"
    }
}
