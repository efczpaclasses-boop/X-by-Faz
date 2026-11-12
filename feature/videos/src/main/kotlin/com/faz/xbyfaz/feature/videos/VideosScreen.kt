package com.faz.xbyfaz.feature.videos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faz.xbyfaz.core.auth.SessionRepository
import com.faz.xbyfaz.core.model.AppFailure
import com.faz.xbyfaz.core.model.FeedKind
import com.faz.xbyfaz.core.model.Post
import com.faz.xbyfaz.core.model.asFailure
import com.faz.xbyfaz.core.model.parseTwitterDate
import com.faz.xbyfaz.core.ui.EmptyState
import com.faz.xbyfaz.core.ui.ErrorPanel
import com.faz.xbyfaz.core.ui.InterFamily
import com.faz.xbyfaz.core.ui.LoadingState
import com.faz.xbyfaz.core.ui.ScreenTitle
import com.faz.xbyfaz.core.ui.VideoCard
import com.faz.xbyfaz.core.ui.XChip
import com.faz.xbyfaz.core.ui.XColors
import com.faz.xbyfaz.core.web.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VideoFilter { All, Recent, Following, Bookmarked }

@HiltViewModel
class VideosViewModel @Inject constructor(
    private val content: ContentRepository,
    private val session: SessionRepository,
) : ViewModel() {
    var filter by mutableStateOf(VideoFilter.All)
    var posts by mutableStateOf<List<Post>>(emptyList())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<AppFailure?>(null)
    var warning by mutableStateOf<AppFailure?>(null)
    var newestFirst by mutableStateOf(true)
    init { load() }

    fun select(next: VideoFilter) {
        filter = next
        load()
    }

    fun toggleSort() {
        newestFirst = !newestFirst
        posts = sort(posts)
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val kind = when (filter) {
                    VideoFilter.Following -> FeedKind.Following
                    VideoFilter.Bookmarked -> FeedKind.Bookmarks
                    else -> FeedKind.Home
                }
                val page = content.feed(kind, null, session.state.value.handle)
                warning = page.warning
                posts = sort(page.posts.filter { it.hasVideo })
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                error = t as? AppFailure ?: t.asFailure()
            } finally {
                loading = false
            }
        }
    }

    private fun sort(items: List<Post>): List<Post> {
        val sorted = items.sortedByDescending { parseTwitterDate(it.createdAt.orEmpty()) ?: Long.MAX_VALUE }
        return if (newestFirst) sorted else sorted.asReversed()
    }
}

@Composable
fun VideosRoute(onPlay: (List<Post>, String) -> Unit) {
    val vm: VideosViewModel = hiltViewModel()
    Column(Modifier.fillMaxSize().padding(top = 16.dp, end = 20.dp)) {
        ScreenTitle("Videos", "From your timeline, newest first.")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoFilter.entries.forEach { filter ->
                XChip(filter.name, vm.filter == filter) { vm.select(filter) }
            }
            Spacer(Modifier.weight(1f))
            XChip(if (vm.newestFirst) "Newest" else "Oldest", true, onClick = vm::toggleSort)
        }
        Spacer(Modifier.height(12.dp))
        when {
            vm.loading -> LoadingState("Gathering videos…")
            vm.error != null -> ErrorPanel(vm.error!!, vm::load, { vm.error = null })
            vm.posts.isEmpty() -> {
                vm.warning?.let { ErrorPanel(it, vm::load) { vm.warning = null } }
                    ?: EmptyState("No videos in this filter", "If this part of your account has videos, they show up here.")
            }
            else -> {
                vm.warning?.let { ErrorPanel(it, vm::load) { vm.warning = null } }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(240.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 28.dp, end = 12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(vm.posts, key = { it.id }) { post ->
                        VideoCard(post, progress = null, onClick = { onPlay(vm.posts, post.id) }, width = 240.dp)
                    }
                }
            }
        }
    }
}
