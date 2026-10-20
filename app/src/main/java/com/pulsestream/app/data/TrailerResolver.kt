package com.pulsestream.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

/**
 * Resolves YouTube trailer ids to playable stream URLs via NewPipeExtractor
 * (same approach as ShortsBox). Runs fully off the main thread.
 */
object TrailerResolver {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var inited = false

    private fun ensureInit() {
        if (inited) return
        synchronized(this) {
            if (inited) return
            NewPipe.init(object : Downloader() {
                override fun execute(request: Request): Response {
                    val builder = okhttp3.Request.Builder().url(request.url())
                    request.headers().forEach { (name, values) ->
                        values.forEach { builder.header(name, it) }
                    }
                    val data = request.dataToSend()
                    if (request.httpMethod() == "POST") {
                        builder.post((data ?: ByteArray(0)).toRequestBody())
                    } else {
                        builder.get()
                    }
                    http.newCall(builder.build()).execute().use { resp ->
                        return Response(
                            resp.code,
                            resp.message,
                            resp.headers.toMultimap(),
                            resp.body?.string(),
                            request.url()
                        )
                    }
                }
            })
            inited = true
        }
    }

    /** A valid trailer selection; audioUrl is required when the video is video-only. */
    data class PlaybackSelection(val videoUrl: String, val audioUrl: String? = null)

    /**
     * Resolves a trailer into either one muxed stream or an explicit video/audio
     * pair. Never returns a video-only URL without its separate audio track.
     */
    suspend fun resolvePlayback(ytId: String): PlaybackSelection? = withContext(Dispatchers.IO) {
        runCatching {
            ensureInit()
            val info = StreamInfo.getInfo(
                NewPipe.getService(0),
                "https://www.youtube.com/watch?v=$ytId"
            )
            val progressive = info.videoStreams
                .filter { it.format == MediaFormat.MPEG_4 && !it.isVideoOnly }
            val muxed = progressive.firstOrNull { it.height in 700..760 }
                ?: progressive.maxByOrNull { it.height }
                ?: info.videoStreams.firstOrNull { !it.isVideoOnly }
            val muxedUrl = muxed?.url?.takeIf { it.isNotBlank() }
            if (muxedUrl != null) return@runCatching PlaybackSelection(muxedUrl)

            val videoOnly = info.videoStreams.filter { it.isVideoOnly }
                .firstOrNull { it.height in 700..760 }
                ?: info.videoStreams.filter { it.isVideoOnly }.maxByOrNull { it.height }
                ?: return@runCatching null
            val videoUrl = videoOnly.url?.takeIf { it.isNotBlank() }
                ?: return@runCatching null
            val audio = info.audioStreams.maxByOrNull { it.averageBitrate }
                ?: return@runCatching null
            val audioUrl = audio.url?.takeIf { it.isNotBlank() }
                ?: return@runCatching null
            PlaybackSelection(videoUrl, audioUrl)
        }.getOrNull()
    }

    /**
     * Compatibility helper for callers that only accept a single URL.
     * Returns null rather than silently selecting a video-only stream; callers
     * supporting mergeAudioUrl should use [resolvePlayback].
     */
    suspend fun resolveUrl(ytId: String): String? = resolvePlayback(ytId)
        ?.takeIf { it.audioUrl == null }
        ?.videoUrl

}
