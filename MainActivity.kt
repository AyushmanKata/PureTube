package com.puretube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { AppRoot() }
            }
        }
    }
}

private enum class Tab { Home, Search, Library }

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.Home) }
    // One-shot request: set by a second tap on the Search tab, consumed by the Search screen.
    var focusSearch by remember { mutableStateOf(false) }

    BackHandler(enabled = vm.watchVideo != null) { vm.closeVideo() }
    BackHandler(enabled = vm.watchVideo == null && vm.channelRef != null) { vm.closeChannel() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.Home,
                        onClick = { tab = Tab.Home },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Search,
                        onClick = {
                            // 1st tap: open the search page. Tap again: focus the box and open the keyboard.
                            if (tab == Tab.Search) focusSearch = true else tab = Tab.Search
                        },
                        icon = { Icon(Icons.Default.Search, null) },
                        label = { Text("Search") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Library,
                        onClick = { tab = Tab.Library },
                        icon = { Icon(Icons.Default.VideoLibrary, null) },
                        label = { Text("Library") },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    Tab.Home -> HomeScreen(vm)
                    Tab.Search -> SearchScreen(vm, focusSearch) { focusSearch = false }
                    Tab.Library -> LibraryScreen(vm)
                }
            }
        }

        if (vm.channelRef != null) {
            Surface(Modifier.fillMaxSize()) { ChannelScreen(vm) }
        }
        if (vm.watchVideo != null) {
            Surface(Modifier.fillMaxSize()) { WatchScreen(vm) }
        }
    }
}

// ---------------------------------------------------------------- Home (recommendations)

@Composable
private fun HomeScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadFeed() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("PureTube", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.loadFeed(force = true) }, enabled = !vm.feedLoading) {
                Icon(Icons.Default.Refresh, "Refresh recommendations")
            }
        }
        if (vm.feedLoading && vm.feed.isNotEmpty()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        when {
            vm.feed.isEmpty() && vm.feedLoading -> Centered { CircularProgressIndicator() }
            vm.feed.isEmpty() -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        vm.feedError ?: "Nothing to show yet.\nWatch or subscribe to get recommendations.",
                        color = if (vm.feedError != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = { vm.loadFeed(force = true) }) { Text("Try again") }
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(vm.feed, key = { it.url }) { v -> VideoRow(v) { vm.openVideo(v) } }
            }
        }
    }
}

// ---------------------------------------------------------------- Search

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SearchScreen(vm: AppViewModel, focusRequest: Boolean, onFocusHandled: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Second tap on the Search tab: focus the text field and open the keyboard.
    LaunchedEffect(focusRequest) {
        if (focusRequest) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
            onFocusHandled()
        }
    }

    // Back closes the suggestions first (the keyboard itself consumes the first back press).
    BackHandler(enabled = focused) { focusManager.clearFocus() }

    // Infinite scroll for results.
    LaunchedEffect(listState, vm.results.size) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= vm.results.size - 3
        }.collect { nearEnd -> if (nearEnd && vm.results.isNotEmpty()) vm.loadMore() }
    }

    fun submit(text: String) {
        vm.search(text)
        focusManager.clearFocus()
        keyboard?.hide()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
            singleLine = true,
            placeholder = { Text("Search videos") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Default.Close, "Clear") }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit(vm.query) }),
        )

        if (focused) {
            SuggestionList(vm, onPick = { submit(it) })
        } else {
            when {
                vm.results.isEmpty() && vm.searching -> Centered { CircularProgressIndicator() }
                vm.results.isEmpty() && vm.searchError != null ->
                    Centered { Text(vm.searchError!!, color = MaterialTheme.colorScheme.error) }
                vm.results.isEmpty() && vm.searched -> Centered { Text("No results.") }
                vm.results.isEmpty() -> Centered { Text("Search for something to watch.\nNo ads. No Shorts.") }
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(vm.results, key = { it.url }) { v -> VideoRow(v) { vm.openVideo(v) } }
                    if (vm.searching) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionList(vm: AppViewModel, onPick: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (vm.query.isBlank()) {
            if (vm.recent.isNotEmpty()) {
                item(key = "recent-header") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Recent searches", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.clearRecent() }) { Text("Clear all") }
                    }
                }
            }
            items(vm.recent, key = { "r:$it" }) { r ->
                SuggestionRow(text = r, recent = true, onClick = { onPick(r) }, onRemove = { vm.removeRecent(r) })
            }
        } else {
            val typed = vm.query.trim()
            if (vm.suggestions.none { it.equals(typed, ignoreCase = true) }) {
                item(key = "typed") { SuggestionRow(text = typed, recent = false, onClick = { onPick(typed) }) }
            }
            items(vm.suggestions, key = { "s:$it" }) { s ->
                SuggestionRow(text = s, recent = false, onClick = { onPick(s) })
            }
        }
    }
}

@Composable
private fun SuggestionRow(text: String, recent: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (recent) Icons.Default.History else Icons.Default.Search, null)
        Spacer(Modifier.width(16.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Remove") }
        }
    }
}

// ---------------------------------------------------------------- Library

@Composable
private fun LibraryScreen(vm: AppViewModel) {
    var section by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), Arrangement.spacedBy(8.dp)) {
            SectionChip("History", section == 0) { section = 0 }
            SectionChip("Subscriptions", section == 1) { section = 1 }
        }
        if (section == 0) {
            if (vm.history.isEmpty()) {
                Centered { Text("Nothing watched yet.") }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), Arrangement.End) {
                    TextButton(onClick = { vm.clearHistory() }) { Text("Clear history") }
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(vm.history, key = { it.video.url }) { h ->
                        VideoRow(h.video) { vm.openVideo(h.video) }
                    }
                }
            }
        } else {
            if (vm.subscriptions.isEmpty()) {
                Centered { Text("No subscriptions yet.\nOpen a video and tap Subscribe.") }
            } else {
                LazyColumn {
                    items(vm.subscriptions, key = { it.url }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.openChannel(c) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(c.avatar, 44)
                            Spacer(Modifier.width(12.dp))
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) }
    else OutlinedButton(onClick = onClick) { Text(label) }
}

// ---------------------------------------------------------------- Channel

@Composable
private fun ChannelScreen(vm: AppViewModel) {
    val ref = vm.channelRef ?: return
    val page = vm.channelPage
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.closeChannel() }) { Icon(Icons.Default.ArrowBack, "Back") }
            Avatar(page?.channel?.avatar ?: ref.avatar, 36)
            Spacer(Modifier.width(10.dp))
            Text(
                page?.channel?.name ?: ref.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SubscribeButton(vm, page?.channel ?: ref)
        }
        when {
            page != null -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(page.videos, key = { it.url }) { v -> VideoRow(v) { vm.openVideo(v) } }
            }
            vm.channelError != null -> Centered { Text(vm.channelError!!, color = MaterialTheme.colorScheme.error) }
            else -> Centered { CircularProgressIndicator() }
        }
    }
}

// ---------------------------------------------------------------- Watch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchScreen(vm: AppViewModel) {
    val data = vm.watchData
    val video = data?.video ?: vm.watchVideo ?: return

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            VideoPlayer(vm.selectedQuality, Modifier.fillMaxSize())
            if (data == null && vm.watchError == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            IconButton(onClick = { vm.closeVideo() }, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
            }
        }

        vm.watchError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(12.dp)) {
                    Text(video.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(meta(video), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ch = data?.channel
                        Row(
                            Modifier.weight(1f).clickable(enabled = ch != null) {
                                // The channel page sits under the watch overlay, so leave the video first.
                                ch?.let { vm.closeVideo(); vm.openChannel(it) }
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(ch?.avatar, 36)
                            Spacer(Modifier.width(10.dp))
                            Text(video.channel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ch?.let { SubscribeButton(vm, it) }
                    }

                    data?.let { QualityPicker(vm, it.qualities) }

                    if (!data?.description.isNullOrBlank()) {
                        var expanded by remember(video.url) { mutableStateOf(false) }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            data!!.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { expanded = !expanded },
                        )
                    }
                }
                HorizontalDivider()
            }
            data?.let { d ->
                items(d.related, key = { it.url }) { v -> VideoRow(v) { vm.openVideo(v) } }
            }
        }
    }
}

@Composable
private fun QualityPicker(vm: AppViewModel, options: List<QualityOption>) {
    if (options.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("Quality: ${vm.selectedQuality?.label ?: "-"}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { q ->
                DropdownMenuItem(
                    text = { Text(q.label + if (q.audio == null) " (muxed)" else "") },
                    onClick = { vm.selectedQuality = q; open = false },
                )
            }
        }
    }
}

@Composable
private fun SubscribeButton(vm: AppViewModel, ref: ChannelRef) {
    if (vm.isSubscribed(ref)) {
        OutlinedButton(onClick = { vm.toggleSubscribe(ref) }) { Text("Subscribed") }
    } else {
        Button(onClick = { vm.toggleSubscribe(ref) }) { Text("Subscribe") }
    }
}

// ---------------------------------------------------------------- shared bits

@Composable
private fun VideoRow(video: VideoItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 12.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (video.durationSec > 0) {
                Text(
                    formatDuration(video.durationSec),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta(video), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Avatar(url: String?, size: Int) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size.dp).clip(CircleShape),
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) { content() }
}

private fun meta(v: VideoItem): String =
    listOfNotNull(
        v.channel.takeIf { it.isNotBlank() },
        v.views.takeIf { it >= 0 }?.let { "${compact(it)} views" },
        v.uploaded,
    ).joinToString(" · ")

private fun compact(n: Long): String = when {
    n >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", n / 1e9)
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1e6)
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1e3)
    else -> n.toString()
}

private fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
