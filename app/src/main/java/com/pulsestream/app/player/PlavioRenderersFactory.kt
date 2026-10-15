package com.pulsestream.app.player

import android.content.Context
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.util.ArrayList

/**
 * Custom renderers factory that ensures FFmpeg software decoding is tried
 * BEFORE MediaCodec for audio codecs commonly unsupported by Android hardware
 * decoders (EAC3, AC3, DTS, TrueHD).
 *
 * The standard DefaultRenderersFactory with EXTENSION_RENDERER_MODE_PREFER should handle this,
 * but in practice MediaCodec is still selected first on some devices, causing playback failures
 * for Dolby Digital Plus (EAC3) and similar formats. This factory adds an FFmpeg audio renderer
 * at the front of the list, so ExoPlayer tries software decoding first for all audio.
 *
 * The FFmpeg native library (libffmpegJNI.so) is bundled with the app and includes
 * the EAC3, AC3, DTS, and TrueHD decoders.
 */
@UnstableApi
class PlavioRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        // Add a dedicated FFmpeg audio renderer FIRST in the list.
        // ExoPlayer tries renderers in order, so this ensures software decoding
        // is attempted before MediaCodec for any format FFmpeg supports,
        // including EAC3/AC3/DTS/TrueHD which often fail on MediaCodec.
        //
        // The standard FfmpegAudioRenderer.supportsFormatInternal already correctly
        // identifies EAC3/AC3/DTS/TrueHD as FORMAT_HANDLED when the native library
        // has the decoders (which ours does). By placing it first, we guarantee
        // it's selected over MediaCodec for these formats.
        if (FfmpegLibrary.isAvailable()) {
            out.add(
                FfmpegAudioRenderer(
                    eventHandler,
                    eventListener,
                    audioSink
                )
            )
        }

        // Then add the standard renderers. Our FFmpeg renderer is first, so it wins
        // for formats it supports. MediaCodec remains as fallback for everything else.
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out
        )
    }
}
