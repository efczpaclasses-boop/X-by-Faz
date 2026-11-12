package com.faz.xbyfaz.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.faz.xbyfaz.core.model.Post
import com.faz.xbyfaz.core.model.formatClock
import com.faz.xbyfaz.core.model.formatCount
import com.faz.xbyfaz.core.model.formatWhen
import com.faz.xbyfaz.core.ui.Avatar
import com.faz.xbyfaz.core.ui.EmptyState
import com.faz.xbyfaz.core.ui.ErrorPanel
import com.faz.xbyfaz.core.ui.Eyebrow
import com.faz.xbyfaz.core.ui.FeedPostCard
import com.faz.xbyfaz.core.ui.InterFamily
import com.faz.xbyfaz.core.ui.LoadingState
import com.faz.xbyfaz.core.ui.StatsRow
import com.faz.xbyfaz.core.ui.VideoCard
import com.faz.xbyfaz.core.ui.XButton
import com.faz.xbyfaz.core.ui.XColors
import com.faz.xbyfaz.core.ui.imageModel

@Composable
fun HomeRoute(
    onPlay: (List<Post>, String) -> Unit,
    onOpen: (Post) -> Unit,
    onProfile: (String) -> Unit,
    restoreId: String?,
) {
    TimelineRoute(hiltViewModel<HomeViewModel>(), "Your feed", showHero = true, onPlay, onOpen, onProfile, restoreId)
}

@Composable
fun FollowingRoute(onPlay: (List<Post>, String) -> Unit, onOpen: (Post) -> Unit, onProfile: (String) -> Unit) {
    TimelineRoute(hiltViewModel<FollowingViewModel>(), "Following", false, onPlay, onOpen, onProfile, null)
}

@Composable
fun BookmarksRoute(onPlay: (List<Post>, String) -> Unit, onOpen: (Post) -> Unit, onProfile: (String) -> Unit) {
    TimelineRoute(hiltViewModel<BookmarksViewModel>(), "Bookmarks", false, onPlay, onOpen, onProfile, null)
}

@Composable
fun LikesRoute(onPlay: (List<Post>, String) -> Unit, onOpen: (Post) -> Unit, onProfile: (String) -> Unit) {
    TimelineRoute(hiltViewModel<LikesViewModel>(), "Likes", false, onPlay, onOpen, onProfile, null)
}

@Composable
private fun TimelineRoute(
    vm: TimelineViewModel,
    title: String,
    showHero: Boolean,
    onPlay: (List<Post>, String) -> Unit,
    onOpen: (Post) -> Unit,
    onProfile: (String) -> Unit,
    restoreId: String?,
) {
    val ui = vm.ui
    val listState = rememberLazyListState()
    val videos = ui.posts.filter { it.hasVideo }
    LaunchedEffect(restoreId, ui.posts.size) {
        val id = restoreId ?: return@LaunchedEffect
        val index = ui.posts.indexOfFirst { it.id == id }
        if (index >= 0) listState.scrollToItem((if (showHero) 2 else 1) + index)
    }
    LaunchedEffect(listState, ui.posts.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (ui.posts.isNotEmpty() && last >= listState.layoutInfo.totalItemsCount - 3) vm.loadMore() }
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 80.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(top = 16.dp, end = 28.dp)) {
                Text(title, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
            }
        }
        if (showHero && videos.isNotEmpty()) {
            item { Hero(videos.first(), onPlay = { onPlay(videos, videos.first().id) }, onProfile = { onProfile(videos.first().author.handle) }) }
        }
        if (videos.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 28.dp)) {
                    Text("Videos", color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(16.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(28.dp), contentPadding = PaddingValues(end = 80.dp, bottom = 24.dp)) {
                        items(videos, key = { it.id }) { post ->
                            VideoCard(
                                post,
                                progress = post.video?.videoId?.let { ui.progress[it] },
                                onClick = { onPlay(videos, post.id) },
                            )
                        }
                    }
                }
            }
        }
        if (ui.loading && ui.posts.isEmpty()) {
            item { LoadingState("Reading your timeline from X…") }
        }
        ui.error?.let { failure -> item { ErrorPanel(failure, vm::refresh, vm::skip) } }
        ui.warning?.let { failure -> item { ErrorPanel(failure, vm::refresh, vm::skip) } }
        if (!ui.loading && ui.posts.isEmpty() && ui.error == null && ui.warning == null) {
            item { EmptyState("Nothing here yet", "When this section has posts, they show up in the order X returns them. Text and photos open for reading. Videos play full screen.") }
        }
        items(ui.posts, key = { it.id }) { post ->
            Box(Modifier.padding(bottom = 16.dp, end = 48.dp)) {
                FeedPostCard(post) {
                    if (post.hasVideo) onPlay(videos, post.id) else onOpen(post)
                }
            }
        }
        if (ui.loadingMore) item { LoadingState("Loading more…") }
    }
}

@Composable
private fun Hero(post: Post, onPlay: () -> Unit, onProfile: () -> Unit) {
    val art = imageModel(post.video?.posterUrl ?: post.photos.firstOrNull())
    Box(Modifier.fillMaxWidth().height(240.dp).padding(end = 24.dp)) {
        if (art != null) {
            AsyncImage(art, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(XColors.Surface1))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(XColors.Bg, XColors.Bg.copy(alpha = 0.55f), Color.Transparent)),
            ),
        )
        Column(Modifier.padding(top = 72.dp).fillMaxWidth(0.72f)) {
            Eyebrow("Featured from your feed")
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(post.author.name, post.author.handle, post.author.avatarUrl, 52.dp)
                Text(
                    "${post.author.name}  @${post.author.handle}" + post.createdAt?.let { " · ${formatWhen(it)}" }.orEmpty(),
                    color = XColors.Text2,
                    fontFamily = InterFamily,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(post.text, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp, maxLines = 3)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                post.video?.durationMs?.takeIf { it > 0 }?.let { Text(formatClock(it), color = XColors.Text2, fontFamily = InterFamily, fontSize = 18.sp) }
                post.viewCount?.let { Text("${formatCount(it)} views", color = XColors.Text2, fontFamily = InterFamily, fontSize = 18.sp) }
            }
            Spacer(Modifier.height(8.dp))
            StatsRow(post)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                XButton("Play", onPlay, primary = true, icon = Icons.Filled.PlayArrow)
                XButton("Profile", onProfile, icon = Icons.Filled.Person)
            }
        }
    }
}
