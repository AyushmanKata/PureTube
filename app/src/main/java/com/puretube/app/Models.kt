package com.puretube.app

import kotlinx.serialization.Serializable

@Serializable
data class VideoItem(
    val url: String,
    val title: String,
    val channel: String,
    val channelUrl: String? = null,
    val thumbnail: String? = null,
    val durationSec: Long = -1,
    val views: Long = -1,
    val uploaded: String? = null,
)

@Serializable
data class ChannelRef(
    val url: String,
    val name: String,
    val avatar: String? = null,
)

@Serializable
data class HistoryEntry(
    val video: VideoItem,
    val watchedAt: Long,
)

/** [audio] == null means [video] is a muxed (video+audio) stream. */
data class QualityOption(
    val label: String,
    val height: Int,
    val video: String,
    val audio: String?,
)

data class WatchData(
    val video: VideoItem,
    val description: String,
    val qualities: List<QualityOption>,
    val related: List<VideoItem>,
    val channel: ChannelRef?,
)

data class ChannelPage(
    val channel: ChannelRef,
    val videos: List<VideoItem>,
)
