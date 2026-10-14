package com.puretube.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView

/**
 * Plays the raw stream URLs resolved by the extractor. For adaptive qualities the
 * video-only and audio-only URLs are merged into a single source.
 * Switching quality keeps the current position.
 */
@Composable
fun VideoPlayer(quality: QualityOption?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }

    LaunchedEffect(quality) {
        if (quality == null) return@LaunchedEffect
        val position = player.currentPosition
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(WEB_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val factory = ProgressiveMediaSource.Factory(http)
        val video: MediaSource = factory.createMediaSource(MediaItem.fromUri(quality.video))
        val source: MediaSource = if (quality.audio != null) {
            MergingMediaSource(video, factory.createMediaSource(MediaItem.fromUri(quality.audio)))
        } else {
            video
        }
        player.setMediaSource(source)
        player.prepare()
        if (position > 0) player.seekTo(position)
        player.playWhenReady = true
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
    )
}
