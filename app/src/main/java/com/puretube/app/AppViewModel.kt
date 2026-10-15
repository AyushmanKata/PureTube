package com.puretube.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = YtRepository(
        OkHttpDownloader(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build(),
        ),
    )
    private val store = Store(app)

    // ----- home feed (recommendations) -----
    val feed = mutableStateListOf<VideoItem>()
    var feedLoading by mutableStateOf(false)
        private set
    var feedError by mutableStateOf<String?>(null)
        private set
    private var feedLoaded = false

    fun loadFeed(force: Boolean = false) {
        if (feedLoading || (feedLoaded && !force)) return
        viewModelScope.launch {
            feedLoading = true
            feedError = null
            try {
                val items = repo.recommendations(history.map { it.video }, subscriptions.toList())
                feed.clear()
                feed.addAll(items)
                feedLoaded = items.isNotEmpty()
                if (items.isEmpty()) feedError = "Couldn't load recommendations"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                feedError = e.message ?: "Couldn't load recommendations"
            } finally {
                feedLoading = false
            }
        }
    }

    // ----- search -----
    var query by mutableStateOf("")
        private set
    val results = mutableStateListOf<VideoItem>()
    var searching by mutableStateOf(false)
        private set
    var searched by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    private var session: SearchSession? = null
    private var searchJob: Job? = null

    val suggestions = mutableStateListOf<String>()
    private var suggestJob: Job? = null

    /** Called on every keystroke: updates the text and fetches (debounced) suggestions. */
    fun onQueryChange(text: String) {
        query = text
        suggestJob?.cancel()
        if (text.isBlank()) {
            suggestions.clear()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)
            try {
                val list = repo.suggestions(text.trim()).take(8)
                suggestions.clear()
                suggestions.addAll(list)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // suggestions are best-effort
            }
        }
    }

    fun search(text: String = query) {
        val q = text.trim()
        if (q.isEmpty()) return
        query = q
        suggestJob?.cancel()
        suggestions.clear()
        addRecent(q)

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searching = true
            searched = true
            searchError = null
            results.clear()
            try {
                val (s, items) = repo.search(q)
                session = s
                results.addAll(items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchError = e.message ?: "Search failed"
            } finally {
                searching = false
            }
        }
    }

    fun loadMore() {
        val s = session ?: return
        if (searching || s.page == null) return
        viewModelScope.launch {
            searching = true
            try {
                val more = repo.searchMore(s)
                val seen = results.map { it.url }.toHashSet()
                results.addAll(more.filter { seen.add(it.url) })
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // keep what we have
            } finally {
                searching = false
            }
        }
    }

    // ----- recent searches -----
    val recent = mutableStateListOf<String>().apply { addAll(store.loadRecent()) }

    private fun addRecent(q: String) {
        recent.removeAll { it.equals(q, ignoreCase = true) }
        recent.add(0, q)
        store.saveRecent(recent.toList())
    }

    fun removeRecent(q: String) {
        recent.remove(q)
        store.saveRecent(recent.toList())
    }

    fun clearRecent() {
        recent.clear()
        store.saveRecent(emptyList())
    }

    // ----- watch -----
    var watchVideo by mutableStateOf<VideoItem?>(null)
        private set
    var watchData by mutableStateOf<WatchData?>(null)
        private set
    var watchError by mutableStateOf<String?>(null)
        private set
    var selectedQuality by mutableStateOf<QualityOption?>(null)
    private var watchJob: Job? = null

    fun openVideo(video: VideoItem) {
        watchJob?.cancel()
        watchVideo = video
        watchData = null
        watchError = null
        selectedQuality = null
        watchJob = viewModelScope.launch {
            try {
                val data = repo.watch(video.url)
                watchData = data
                // Default: best quality up to 1080p.
                selectedQuality = data.qualities.firstOrNull { it.height <= 1080 }
                    ?: data.qualities.firstOrNull()
                if (selectedQuality == null) watchError = "No playable streams found"
                addHistory(data.video)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                watchError = e.message ?: "Could not load video"
            }
        }
    }

    fun closeVideo() {
        watchJob?.cancel()
        watchVideo = null
        watchData = null
        watchError = null
        selectedQuality = null
    }

    // ----- channel -----
    var channelRef by mutableStateOf<ChannelRef?>(null)
        private set
    var channelPage by mutableStateOf<ChannelPage?>(null)
        private set
    var channelError by mutableStateOf<String?>(null)
        private set
    private var channelJob: Job? = null

    fun openChannel(ref: ChannelRef) {
        channelJob?.cancel()
        channelRef = ref
        channelPage = null
        channelError = null
        channelJob = viewModelScope.launch {
            try {
                channelPage = repo.channel(ref)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                channelError = e.message ?: "Could not load channel"
            }
        }
    }

    fun closeChannel() {
        channelJob?.cancel()
        channelRef = null
        channelPage = null
        channelError = null
    }

    // ----- history -----
    val history = mutableStateListOf<HistoryEntry>().apply { addAll(store.loadHistory()) }

    private fun addHistory(video: VideoItem) {
        history.removeAll { it.video.url == video.url }
        history.add(0, HistoryEntry(video, System.currentTimeMillis()))
        store.saveHistory(history.toList())
    }

    fun clearHistory() {
        history.clear()
        store.saveHistory(emptyList())
    }

    // ----- subscriptions -----
    val subscriptions = mutableStateListOf<ChannelRef>().apply { addAll(store.loadSubscriptions()) }

    fun isSubscribed(ref: ChannelRef?): Boolean = ref != null && subscriptions.any { it.url == ref.url }

    fun toggleSubscribe(ref: ChannelRef) {
        if (!subscriptions.removeAll { it.url == ref.url }) subscriptions.add(0, ref)
        store.saveSubscriptions(subscriptions.toList())
    }
}
