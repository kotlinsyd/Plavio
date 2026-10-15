package com.pulsestream.app.player

import android.content.Context
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

/**
 * MediaCodec selector that excludes hardware decoders for audio codecs known to
 * lack hardware support on many devices (EAC3, AC3, DTS, TrueHD).
 *
 * By returning an empty list for these codecs, ExoPlayer is forced to use the
 * FFmpeg extension renderer (software decoding) instead of failing on MediaCodec.
 * This avoids the "no audio" silent failure on devices like OnePlus 13R that
 * lack Dolby/DTS hardware decoders.
 */
@UnstableApi
class NoHardwareDolbySelector : MediaCodecSelector {

    companion object {
        private val SOFTWARE_ONLY_CODECS = setOf(
            MimeTypes.AUDIO_E_AC3,      // Dolby Digital Plus
            MimeTypes.AUDIO_E_AC3_JOC,  // Dolby Digital Plus with JOC (Atmos)
            MimeTypes.AUDIO_AC3,        // Dolby Digital
            MimeTypes.AUDIO_DTS,        // DTS
            MimeTypes.AUDIO_DTS_HD,     // DTS-HD
            MimeTypes.AUDIO_TRUEHD      // Dolby TrueHD
        )
    }

    private val defaultSelector = MediaCodecSelector.DEFAULT

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        // For Dolby/DTS codecs, return empty list to force FFmpeg software decoding.
        // This prevents MediaCodec from being selected (and failing silently).
        if (mimeType in SOFTWARE_ONLY_CODECS) {
            return emptyList()
        }
        // For all other codecs, use default behavior.
        return defaultSelector.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
    }
}

/**
 * Custom renderers factory that forces FFmpeg software decoding for Dolby/DTS
 * audio codecs by excluding them from MediaCodec selection.
 *
 * Unlike the previous PlavioRenderersFactory (which added a duplicate FFmpeg
 * renderer and broke audio on some devices), this approach uses a custom
 * MediaCodecSelector to prevent MediaCodec from claiming these formats.
 * The standard FFmpeg extension renderer (added via EXTENSION_RENDERER_MODE_PREFER)
 * then handles them via software decoding.
 */
@UnstableApi
class PlavioAudioRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
        setMediaCodecSelector(NoHardwareDolbySelector())
    }
}
