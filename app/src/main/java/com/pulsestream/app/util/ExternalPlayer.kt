package com.pulsestream.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.pulsestream.app.data.PlaybackHeaders
import com.pulsestream.app.data.PlaybackRequest
import java.io.File

/**
 * Opens a [PlaybackRequest] in an external video player (VLC, MX Player…)
 * via ACTION_VIEW + the system chooser.
 *
 * The built-in player is untouched — this is only used when the user's
 * "Preferred player" setting says so.
 */
object ExternalPlayer {

    private const val PREFS = "plavio_external_player"
    private const val KEY_HIDE_WARNING = "hide_protected_warning"

    /**
     * True when the stream carries headers beyond the harmless trio —
     * the receiving app gets only the sanitized subset (credentials and
     * cookies are never handed off, see [play]), so a protected link
     * may simply not play outside. Until v0.14.3 such links were
     * hard-blocked; the owner's order (2026-10-26) replaced the block
     * with a one-time warning — see [needsWarning].
     */
    fun hasProtectedHeaders(request: PlaybackRequest): Boolean =
        PlaybackHeaders.buildPlaybackHeaders(request.headers, false).keys.any {
            it.lowercase() !in setOf("user-agent", "accept", "accept-language")
        }

    /** The warning's "Don't show again" choice, persisted per device. */
    fun warningSuppressed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_WARNING, false)

    fun setWarningSuppressed(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HIDE_WARNING, value).apply()
    }

    /**
     * True when the caller should show the protected-link warning
     * popup before handing off: the link is protected AND the user
     * hasn't ticked "Don't show again". Normal links never warn.
     */
    fun needsWarning(context: Context, request: PlaybackRequest): Boolean =
        hasProtectedHeaders(request) && !warningSuppressed(context)

    /**
     * Tries to hand [request] to an external player. Never refuses a
     * link for its headers (owner's order 2026-10-26) — callers gate
     * protected links behind the warning popup via [needsWarning].
     *
     * @return true if a player was launched; false when no player could
     * handle it (a toast is shown explaining why) — the caller should then
     * fall back to the built-in player.
     */
    fun play(context: Context, request: PlaybackRequest): Boolean {
        val (uri, mime) = resolveUri(context, request.streamUrl)
        if (uri == null) {
            Toast.makeText(
                context,
                "Can't open this stream in an external player",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime ?: "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Required when launched from a non-Activity context.
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Title shown by players that support it (VLC, MX Player).
            putExtra("title", request.title)
            // VLC reads a String[] "headers" extra. Normalize headers and
            // deliberately omit credentials/cookies: the receiving app and
            // any redirects it follows are outside our origin boundary.
            val headers = PlaybackHeaders.externalPlayerHeaders(request.headers)
            if (headers.isNotEmpty()) {
                putExtra(
                    "headers",
                    headers.map { (k, v) -> "$k: $v" }.toTypedArray()
                )
            }
        }
        return try {
            context.startActivity(Intent.createChooser(intent, "Play video with"))
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No video player found — install VLC to play externally",
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    /**
     * Maps a stream URL to a (Uri, mimeType) pair an external app can open.
     * Local download files go through FileProvider so other apps can read
     * them; remote URLs are passed through with an inferred container type.
     * Returns null when the URL can't be handed off.
     */
    private fun resolveUri(context: Context, url: String): Pair<Uri?, String?> {
        return try {
            if (url.startsWith("file://", ignoreCase = true)) {
                val path = Uri.parse(url).path ?: return null to null
                val file = File(path)
                if (!file.exists()) return null to null
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                contentUri to "video/*"
            } else {
                val uri = Uri.parse(url) ?: return null to null
                uri to PlaybackHeaders.inferMimeType(url)
            }
        } catch (e: Exception) {
            // Covers IllegalArgumentException from FileProvider (file outside
            // the configured roots) and malformed URLs — caller falls back
            // to the built-in player.
            null to null
        }
    }
}
