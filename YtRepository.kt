package com.puretube.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

/** Holds pagination state for one search. */
class SearchSession(val handler: SearchQueryHandler, var page: Page?)

/**
 * All YouTube access goes through NewPipeExtractor, which talks to YouTube's
 * internal endpoints and returns raw stream URLs. Nothing here uses YouTube's
 * own player, so no ad-insertion logic ever runs.
 */
class YtRepository(downloader: OkHttpDownloader) {

    private val yt = ServiceList.YouTube

    private val FALLBACK_QUERIES = listOf("trending today", "popular videos", "new this week")

    init {
        NewPipe.init(downloader)
    }

    // ---------- search ----------

    suspend fun search(query: String): Pair<SearchSession, List<VideoItem>> = io {
        val handler = yt.searchQHFactory.fromQuery(query, listOf("videos"), "")
        val info = SearchInfo.getInfo(yt, handler)
        SearchSession(handler, info.nextPage) to info.relatedItems.toVideos()
    }

    suspend fun searchMore(session: SearchSession): List<VideoItem> = io {
        val page = session.page ?: return@io emptyList()
        val result = SearchInfo.getMoreItems(yt, session.handler, page)
        session.page = result.nextPage
        result.items.toVideos()
    }

    // ---------- watch ----------

    suspend fun watch(url: String): WatchData = io {
        val info = StreamInfo.getInfo(yt, url)

        val video = VideoItem(
            url = url,
            title = info.name,
            channel = info.uploaderName.orEmpty(),
            channelUrl = info.uploaderUrl,
            thumbnail = bestImage(info.thumbnails),
            durationSec = info.duration,
            views = info.viewCount,
            uploaded = info.textualUploadDate,
        )
        val channel = info.uploaderUrl?.takeIf { it.isNotBlank() }?.let {
            ChannelRef(it, info.uploaderName.orEmpty(), bestImage(info.uploaderAvatars))
        }

        WatchData(
            video = video,
            description = info.description?.content.orEmpty(),
            qualities = buildQualities(info),
            related = info.relatedItems.toVideos(),
            channel = channel,
        )
    }

    private fun buildQualities(info: StreamInfo): List<QualityOption> {
        val progressive = DeliveryMethod.PROGRESSIVE_HTTP

        // Best AAC/M4A audio (broadest device support); fall back to anything progressive.
        val audioAll = info.audioStreams.filter { it.deliveryMethod == progressive }
        val bestAudio = audioAll.filter { it.format == MediaFormat.M4A }
            .ifEmpty { audioAll }
            .maxByOrNull { it.averageBitrate }

        val out = mutableListOf<QualityOption>()

        // Adaptive (video-only + separate audio): this is how 720p+ is served.
        if (bestAudio != null) {
            val videoAll = info.videoOnlyStreams.filter { it.deliveryMethod == progressive }
            // Prefer H.264/MP4 (hardware decoded almost everywhere) over VP9/AV1.
            val videos = videoAll.filter { it.format == MediaFormat.MPEG_4 }.ifEmpty { videoAll }
            videos.groupBy { it.heightPx() }
                .filterKeys { it > 0 }
                .forEach { (h, list) ->
                    val v = list.first()
                    out += QualityOption("${h}p", h, v.content, bestAudio.content)
                }
        }

        // Muxed (video+audio in one URL), typically capped at 360p/720p.
        info.videoStreams.filter { it.deliveryMethod == progressive }
            .forEach { v ->
                val h = v.heightPx()
                if (h > 0 && out.none { it.height == h }) {
                    out += QualityOption("${h}p", h, v.content, null)
                }
            }

        return out.sortedByDescending { it.height }
    }

    private fun VideoStream.heightPx(): Int {
        if (height > 0) return height
        return Regex("(\\d+)p").find(resolution.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    // ---------- channel ----------

    suspend fun channel(ref: ChannelRef): ChannelPage = io {
        val info = ChannelInfo.getInfo(yt, ref.url)
        val tab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
        val items = tab?.let { ChannelTabInfo.getInfo(yt, it).relatedItems }.orEmpty()
        ChannelPage(
            channel = ChannelRef(ref.url, info.name ?: ref.name, bestImage(info.avatars) ?: ref.avatar),
            videos = items.toVideos(),
        )
    }

    // ---------- suggestions ----------

    suspend fun suggestions(query: String): List<String> = io {
        yt.suggestionExtractor.suggestionList(query)
    }

    // ---------- recommendations ----------

    /**
     * Local "recommendations" (no account, no tracking): related videos of what you
     * watched recently plus the newest uploads of channels you subscribe to,
     * interleaved. Falls back to a few generic searches on a fresh install.
     */
    suspend fun recommendations(history: List<VideoItem>, subs: List<ChannelRef>): List<VideoItem> =
        coroutineScope {
            val watched = history.map { it.url }.toHashSet()
            val sources: List<Deferred<List<VideoItem>>> =
                history.take(3).map { h -> async { safe { relatedOf(h.url) } } } +
                    subs.shuffled().take(5).map { c -> async { safe { channel(c).videos.take(6) } } }

            var lists = sources.awaitAll()
            if (lists.all { it.isEmpty() }) {
                lists = FALLBACK_QUERIES.map { q -> async { safe { search(q).second.take(8) } } }.awaitAll()
            }
            interleave(lists).filterNot { it.url in watched }
        }

    private suspend fun relatedOf(url: String): List<VideoItem> = io {
        StreamInfo.getInfo(yt, url).relatedItems.toVideos().take(10)
    }

    /** Round-robin across sources so one channel/video doesn't dominate the feed. */
    private fun interleave(lists: List<List<VideoItem>>): List<VideoItem> {
        val seen = HashSet<String>()
        val out = ArrayList<VideoItem>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) {
            for (list in lists) {
                list.getOrNull(i)?.let { if (seen.add(it.url)) out.add(it) }
            }
        }
        return out
    }

    private suspend fun <T> safe(block: suspend () -> List<T>): List<T> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

    // ---------- helpers ----------

    /**
     * The extractor lists thumbnails/avatars smallest-first, so taking the first one
     * gives a tiny, blurry image. Pick the largest; when sizes are unknown (-1),
     * the later entry wins.
     */
    private fun bestImage(images: List<Image>): String? =
        images.withIndex()
            .maxWithOrNull(compareBy({ it.value.height }, { it.value.width }, { it.index }))
            ?.value?.url

    /** Maps results to [VideoItem] and drops Shorts (and any non-video items). */
    private fun List<InfoItem>.toVideos(): List<VideoItem> =
        filterIsInstance<StreamInfoItem>()
            .filterNot { isShort(it) }
            .map {
                VideoItem(
                    url = it.url,
                    title = it.name,
                    channel = it.uploaderName.orEmpty(),
                    channelUrl = it.uploaderUrl,
                    thumbnail = bestImage(it.thumbnails),
                    durationSec = it.duration,
                    views = it.viewCount,
                    uploaded = it.textualUploadDate,
                )
            }

    private fun isShort(item: StreamInfoItem): Boolean =
        item.isShortFormContent || item.url.contains("/shorts/")

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}
