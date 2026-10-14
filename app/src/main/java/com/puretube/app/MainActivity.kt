package com.puretube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
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

private enum class Tab(val label: String) { Home("Home"), Library("Library") }

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.Home) }

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
                        label = { Text(Tab.Home.label) },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Library,
                        onClick = { tab = Tab.Library },
                        icon = { Icon(Icons.Default.VideoLibrary, null) },
                        label = { Text(Tab.Library.label) },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    Tab.Home -> HomeScreen(vm)
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

// ---------------------------------------------------------------- Home

@Composable
private fun HomeScreen(vm: AppViewModel) {
    val listState = rememberLazyListState()

    // Infinite scroll: load more near the end of the list.
    LaunchedEffect(listState, vm.results.size) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= vm.results.size - 3
        }.collect { nearEnd -> if (nearEnd && vm.results.isNotEmpty()) vm.loadMore() }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            singleLine = true,
            placeholder = { Text("Search videos") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
        )

        when {
            vm.results.isEmpty() && vm.searching -> Centered { CircularProgressIndicator() }
            vm.results.isEmpty() && vm.searchError != null ->
                Centered { Text(vm.searchError!!, color = MaterialTheme.colorScheme.error) }
            vm.results.isEmpty() -> Centered { Text("Search for something to watch.\nNo ads. No Shorts.") }
            else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                items(vm.results, key = { it.url }) { v ->
                    VideoRow(v) { vm.openVideo(v) }
                }
                if (vm.searching) item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                }
            }
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
                Icon(Icons.Default.ArrowBack, "Back", tint = androidx.compose.ui.graphics.Color.White)
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
    val subscribed = vm.isSubscribed(ref)
    if (subscribed) {
        OutlinedButton(onClick = { vm.toggleSubscribe(ref) }) { Text("Subscribed") }
    } else {
        Button(
            onClick = { vm.toggleSubscribe(ref) },
            colors = ButtonDefaults.buttonColors(),
        ) { Text("Subscribe") }
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
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
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
