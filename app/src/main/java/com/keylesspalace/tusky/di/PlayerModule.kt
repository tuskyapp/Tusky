/*
 * Copyright 2024 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.di

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.metadata.MetadataRenderer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.flac.FlacExtractor
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ogg.OggExtractor
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.ttml.TtmlParser
import androidx.media3.extractor.text.webvtt.Mp4WebvttParser
import androidx.media3.extractor.text.webvtt.WebvttParser
import androidx.media3.extractor.wav.WavExtractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
@OptIn(UnstableApi::class)
object PlayerModule {
    @Provides
    fun provideAudioSink(@ApplicationContext context: Context): AudioSink {
        return DefaultAudioSink.Builder(context)
            .build()
    }

    @Provides
    fun provideRenderersFactory(
        @ApplicationContext context: Context,
        audioSink: AudioSink
    ): RenderersFactory {
        return RenderersFactory { eventHandler,
                                  videoRendererEventListener,
                                  audioRendererEventListener,
                                  textRendererOutput,
                                  metadataRendererOutput ->
            arrayOf(
                MediaCodecVideoRenderer.Builder(context)
                    .setMediaCodecSelector(MediaCodecSelector.DEFAULT)
                    .setAllowedJoiningTimeMs(DefaultRenderersFactory.DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS)
                    .setEnableDecoderFallback(true)
                    .setEventHandler(eventHandler)
                    .setEventListener(videoRendererEventListener)
                    .setMaxDroppedFramesToNotify(DefaultRenderersFactory.MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
                    .build(),
                MediaCodecAudioRenderer(
                    context,
                    MediaCodecSelector.DEFAULT,
                    // enableDecoderFallback = true
                    true,
                    eventHandler,
                    audioRendererEventListener,
                    audioSink
                ),
                TextRenderer(
                    textRendererOutput,
                    eventHandler.looper
                ),
                MetadataRenderer(
                    metadataRendererOutput,
                    eventHandler.looper
                )
            )
        }
    }

    @Provides
    fun providesSubtitleParserFactory(): SubtitleParser.Factory {
        return object : SubtitleParser.Factory {
            override fun supportsFormat(format: Format): Boolean {
                return when (format.sampleMimeType) {
                    MimeTypes.TEXT_VTT,
                    MimeTypes.APPLICATION_MP4VTT,
                    MimeTypes.APPLICATION_TTML -> true

                    else -> false
                }
            }

            override fun getCueReplacementBehavior(format: Format): Int {
                return when (val mimeType = format.sampleMimeType) {
                    MimeTypes.TEXT_VTT -> WebvttParser.CUE_REPLACEMENT_BEHAVIOR
                    MimeTypes.APPLICATION_MP4VTT -> Mp4WebvttParser.CUE_REPLACEMENT_BEHAVIOR
                    MimeTypes.APPLICATION_TTML -> TtmlParser.CUE_REPLACEMENT_BEHAVIOR
                    else -> throw IllegalArgumentException("Unsupported MIME type: $mimeType")
                }
            }

            override fun create(format: Format): SubtitleParser {
                return when (val mimeType = format.sampleMimeType) {
                    MimeTypes.TEXT_VTT -> WebvttParser()
                    MimeTypes.APPLICATION_MP4VTT -> Mp4WebvttParser()
                    MimeTypes.APPLICATION_TTML -> TtmlParser()
                    else -> throw IllegalArgumentException("Unsupported MIME type: $mimeType")
                }
            }
        }
    }

    @Provides
    fun provideExtractorsFactory(subtitleParserFactory: SubtitleParser.Factory): ExtractorsFactory {
        // Extractors order is optimized according to
        // https://docs.google.com/document/d/1w2mKaWMxfz2Ei8-LdxqbPs1VLe_oudB-eryXXw9OvQQ
        return ExtractorsFactory {
            arrayOf(
                FlacExtractor(),
                WavExtractor(),
                Mp4Extractor(subtitleParserFactory),
                FragmentedMp4Extractor(subtitleParserFactory),
                OggExtractor(),
                MatroskaExtractor(subtitleParserFactory),
                Mp3Extractor()
            )
        }
    }

    @Provides
    fun provideDataSourceFactory(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): DataSource.Factory {
        return DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient))
    }

    @Provides
    fun provideMediaSourceFactory(
        dataSourceFactory: DataSource.Factory,
        extractorsFactory: ExtractorsFactory
    ): MediaSource.Factory {
        // Only progressive download is supported for Mastodon attachments
        return ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
    }

    @Provides
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        renderersFactory: RenderersFactory,
        mediaSourceFactory: MediaSource.Factory
    ): ExoPlayer {
        return ExoPlayer.Builder(context, renderersFactory, mediaSourceFactory)
            .setLooper(Looper.getMainLooper())
            .setHandleAudioBecomingNoisy(true) // automatically pause when unplugging headphones
            .setWakeMode(C.WAKE_MODE_NONE) // playback is always in the foreground
            .build()
    }
}
