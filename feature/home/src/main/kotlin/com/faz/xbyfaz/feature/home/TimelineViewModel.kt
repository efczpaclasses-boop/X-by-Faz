package com.faz.xbyfaz.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faz.xbyfaz.core.auth.SessionRepository
import com.faz.xbyfaz.core.database.HistoryRepository
import com.faz.xbyfaz.core.model.AppFailure
import com.faz.xbyfaz.core.model.FeedKind
import com.faz.xbyfaz.core.model.Post
import com.faz.xbyfaz.core.model.asFailure
import com.faz.xbyfaz.core.web.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimelineUi(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val posts: List<Post> = emptyList(),
    val progress: Map<String, Float> = emptyMap(),
    val error: AppFailure? = null,
    val warning: AppFailure? = null,
)

open class TimelineViewModel(
    private val kind: FeedKind,
    private val content: ContentRepository,
    private val session: SessionRepository,
    history: HistoryRepository,
) : ViewModel() {
    var ui by mutableStateOf(TimelineUi())
        private set
    private var cursor: String? = null
    private var more = true

    init {
        viewModelScope.launch {
            history.observe().collect { entries ->
                ui = ui.copy(progress = entries.associate { entry ->
                    entry.videoId to if (entry.durationMs > 0) entry.positionMs.toFloat() / entry.durationMs else 0f
                })
            }
        }
        refresh()
    }

    fun refresh() {
        cursor = null
        more = true
        load(reset = true)
    }

    fun loadMore() {
        if (!more || ui.loading || ui.loadingMore) return
        load(reset = false)
    }

    fun skip() {
        ui = ui.copy(error = null, warning = null)
    }

    private fun load(reset: Boolean) {
        viewModelScope.launch {
            ui = if (reset) ui.copy(loading = ui.posts.isEmpty(), loadingMore = ui.posts.isNotEmpty(), error = null) else ui.copy(loadingMore = true)
            try {
                val page = content.feed(kind, if (reset) null else cursor, session.state.value.handle)
                val merged = if (reset) page.posts else (ui.posts + page.posts).distinctBy { it.id }
                val grew = merged.size > (if (reset) 0 else ui.posts.size)
                more = grew && page.cursor != null
                cursor = page.cursor
                ui = ui.copy(loading = false, loadingMore = false, posts = merged, warning = page.warning, error = null)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                ui = ui.copy(loading = false, loadingMore = false, error = t as? AppFailure ?: t.asFailure())
            }
        }
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(content: ContentRepository, session: SessionRepository, history: HistoryRepository) :
    TimelineViewModel(FeedKind.Home, content, session, history)

@HiltViewModel
class FollowingViewModel @Inject constructor(content: ContentRepository, session: SessionRepository, history: HistoryRepository) :
    TimelineViewModel(FeedKind.Following, content, session, history)

@HiltViewModel
class BookmarksViewModel @Inject constructor(content: ContentRepository, session: SessionRepository, history: HistoryRepository) :
    TimelineViewModel(FeedKind.Bookmarks, content, session, history)

@HiltViewModel
class LikesViewModel @Inject constructor(content: ContentRepository, session: SessionRepository, history: HistoryRepository) :
    TimelineViewModel(FeedKind.Likes, content, session, history)
