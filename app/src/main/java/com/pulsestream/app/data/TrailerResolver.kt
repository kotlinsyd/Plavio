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
import org.schabi.newpipe.extractor.stream.VideoStream
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

    /**
     * Best progressive (audio+video) MP4 URL for a YouTube video id, or null.
     * Prefers 720p (good balance for trailers); falls back to the highest
     * available progressive stream, then any video stream.
     */
    suspend fun resolveUrl(ytId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            ensureInit()
            val info = StreamInfo.getInfo(
                NewPipe.getService(0),
                "https://www.youtube.com/watch?v=$ytId"
            )
            val progressive = info.videoStreams
                .filter { it.format == MediaFormat.MPEG_4 && !it.isVideoOnly }
            // Prefer 720p, else the highest progressive MP4…
            progressive.firstOrNull { it.height in 700..760 }?.url
                ?: progressive.maxByOrNull { it.height }?.url
                // …then any progressive stream, then anything with a URL.
                ?: info.videoStreams.firstOrNull { !it.isVideoOnly }?.url
                ?: info.videoStreams.firstOrNull()?.url
        }.getOrNull()
    }
}
