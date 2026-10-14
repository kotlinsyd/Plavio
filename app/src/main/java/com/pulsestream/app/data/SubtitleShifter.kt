package com.pulsestream.app.data

import java.io.File

/**
 * Subtitle timing shift (v0.9.0+).
 *
 * ExoPlayer has no subtitle-delay API for side-loaded subtitles, so timing
 * adjustment is done by rewriting the subtitle file with shifted timestamps
 * before it is attached to the player.
 *
 * Supported formats: SRT (`HH:MM:SS,mmm`) and WebVTT (`HH:MM:SS.mmm`).
 * Timestamps are clamped at 0 — a cue can never start before the beginning.
 * Shifted files are written to the app cache under a name that includes the
 * offset, so re-applying the same offset is instant (no re-parse).
 */

/** Cache sub-directory for timing-shifted subtitle files. */
private const val SHIFT_CACHE_DIR = "plavio_subs_shifted"

/** Matches an SRT timestamp: 00:01:23,456 */
private val SRT_TS = Regex("""(\d{2}):(\d{2}):(\d{2}),(\d{3})""")

/** Matches a WebVTT timestamp: 00:01:23.456 (2- or 3-part hours accepted). */
private val VTT_TS = Regex("""(\d{2,}):(\d{2}):(\d{2})\.(\d{3})""")

/**
 * Returns a copy of [resolved] with [offsetMs] applied to every timestamp.
 * When [offsetMs] is 0 the input list is returned unchanged (no file work).
 *
 * @param cacheDir the app cache dir (shifted files land in a sub-directory).
 */
fun shiftSubtitles(
    resolved: List<ResolvedSubtitle>,
    offsetMs: Long,
    cacheDir: File
): List<ResolvedSubtitle> {
    if (offsetMs == 0L || resolved.isEmpty()) return resolved
    val dir = File(cacheDir, SHIFT_CACHE_DIR).apply { mkdirs() }
    return resolved.map { r ->
        try {
            val shifted = shiftedFile(r.file, offsetMs, dir)
            r.copy(file = shifted)
        } catch (e: Exception) {
            // A shift failure must never break subtitles: fall back to the
            // original (unshifted) file.
            r
        }
    }
}

/** Human-readable offset label: "+500 ms", "-1.5 s", "0 ms". */
fun formatOffsetMs(offsetMs: Long): String = when {
    offsetMs == 0L -> "0 ms"
    offsetMs % 1000L == 0L -> {
        val s = offsetMs / 1000L
        (if (s > 0) "+" else "") + "$s s"
    }
    else -> (if (offsetMs > 0) "+" else "") + "$offsetMs ms"
}

private fun shiftedFile(source: File, offsetMs: Long, dir: File): File {
    val out = File(dir, "shift_${offsetMs}_${source.name}")
    if (out.exists() && out.length() > 0) return out
    val text = source.readText(Charsets.UTF_8)
    val isVtt = text.trimStart().startsWith("WEBVTT", ignoreCase = true)
    val shifted = if (isVtt) shiftVtt(text, offsetMs) else shiftSrt(text, offsetMs)
    out.writeText(shifted, Charsets.UTF_8)
    return out
}

private fun shiftSrt(text: String, offsetMs: Long): String =
    SRT_TS.replace(text) { m -> formatSrt(shiftMs(parseSrt(m), offsetMs)) }

private fun shiftVtt(text: String, offsetMs: Long): String =
    VTT_TS.replace(text) { m -> formatVtt(shiftMs(parseVtt(m), offsetMs)) }

private fun parseSrt(m: MatchResult): Long {
    val (h, min, s, ms) = m.destructured
    return h.toLong() * 3_600_000L + min.toLong() * 60_000L + s.toLong() * 1_000L + ms.toLong()
}

private fun parseVtt(m: MatchResult): Long = parseSrt(m)

private fun shiftMs(tsMs: Long, offsetMs: Long): Long =
    (tsMs + offsetMs).coerceAtLeast(0L)

private fun formatSrt(tsMs: Long): String {
    val h = tsMs / 3_600_000L
    val min = (tsMs % 3_600_000L) / 60_000L
    val s = (tsMs % 60_000L) / 1_000L
    val ms = tsMs % 1_000L
    return "%02d:%02d:%02d,%03d".format(h, min, s, ms)
}

private fun formatVtt(tsMs: Long): String {
    val h = tsMs / 3_600_000L
    val min = (tsMs % 3_600_000L) / 60_000L
    val s = (tsMs % 60_000L) / 1_000L
    val ms = tsMs % 1_000L
    return "%02d:%02d:%02d.%03d".format(h, min, s, ms)
}
