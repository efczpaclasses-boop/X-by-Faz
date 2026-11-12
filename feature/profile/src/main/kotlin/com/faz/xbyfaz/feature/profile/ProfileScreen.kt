package com.faz.xbyfaz.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faz.xbyfaz.core.database.SettingsRepository
import com.faz.xbyfaz.core.model.AppFailure
import com.faz.xbyfaz.core.model.Post
import com.faz.xbyfaz.core.model.Profile
import com.faz.xbyfaz.core.model.asFailure
import com.faz.xbyfaz.core.model.formatCount
import com.faz.xbyfaz.core.model.formatWhen
import com.faz.xbyfaz.core.model.rememberRecent
import com.faz.xbyfaz.core.ui.Avatar
import com.faz.xbyfaz.core.ui.EmptyState
import com.faz.xbyfaz.core.ui.ErrorPanel
import com.faz.xbyfaz.core.ui.FeedPostCard
import com.faz.xbyfaz.core.ui.InterFamily
import com.faz.xbyfaz.core.ui.LoadingState
import com.faz.xbyfaz.core.ui.ScreenTitle
import com.faz.xbyfaz.core.ui.SystemTextField
import com.faz.xbyfaz.core.ui.VideoCard
import com.faz.xbyfaz.core.ui.XButton
import com.faz.xbyfaz.core.ui.XChip
import com.faz.xbyfaz.core.ui.XColors
import com.faz.xbyfaz.core.web.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val content: ContentRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    var draft by mutableStateOf("")
    var profile by mutableStateOf<Profile?>(null)
    var posts by mutableStateOf<List<Post>>(emptyList())
    var videosOnly by mutableStateOf(true)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<AppFailure?>(null)
    val recents get() = settings.current().recentProfiles

    fun open(handle: String) {
        val clean = handle.trim().removePrefix("@")
        if (clean.isEmpty()) return
        viewModelScope.launch {
            loading = true
            error = null
            try {
                settings.update { it.copy(recentProfiles = rememberRecent(it.recentProfiles, clean)) }
                profile = content.profile(clean)
                posts = content.profileFeed(clean, videosOnly, null).posts
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                error = t as? AppFailure ?: t.asFailure()
            } finally {
                loading = false
            }
        }
    }

    fun setTab(videos: Boolean) {
        videosOnly = videos
        profile?.let { open(it.handle) }
    }
}

@Composable
fun ProfilesHomeRoute(onOpen: (String) -> Unit) {
    val vm: ProfileViewModel = hiltViewModel()
    Column(Modifier.fillMaxSize().padding(top = 36.dp, end = 48.dp)) {
        ScreenTitle("Profiles", "Open an account by handle.")
        Spacer(Modifier.height(12.dp))
        SystemTextField(vm.draft, { vm.draft = it }, "@handle", { onOpen(vm.draft) })
        Spacer(Modifier.height(10.dp))
        XButton("Open profile", { onOpen(vm.draft) }, primary = true)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.recents.forEach { handle -> XChip("@$handle", false) { onOpen(handle) } }
        }
    }
}

@Composable
fun ProfileRoute(handle: String, onPlay: (List<Post>, String) -> Unit, onOpenPost: (Post) -> Unit) {
    val vm: ProfileViewModel = hiltViewModel()
    LaunchedEffect(handle) { vm.open(handle) }
    val profile = vm.profile
    Column(Modifier.fillMaxSize().padding(top = 24.dp, end = 36.dp)) {
        when {
            vm.loading && profile == null -> LoadingState("Opening @$handle…")
            vm.error != null && profile == null -> ErrorPanel(vm.error!!, { vm.open(handle) }, null)
            profile != null -> {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Avatar(profile.name, profile.handle, profile.avatarUrl, 140.dp)
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp)
                        Text("@${profile.handle}", color = XColors.Text2, fontFamily = InterFamily, fontSize = 22.sp)
                    }
                    val videos = vm.posts.filter { it.hasVideo }
                    if (videos.isNotEmpty()) {
                        XButton("Play all videos", { onPlay(videos, videos.first().id) }, primary = true, icon = Icons.Filled.PlayArrow)
                    }
                }
                if (profile.bio.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(profile.bio, color = XColors.Text, fontFamily = InterFamily, fontSize = 22.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    profile.followers?.let { Stat("Followers", it) }
                    profile.following?.let { Stat("Following", it) }
                    profile.posts?.let { Stat("Posts", it) }
                    profile.location?.let { Text(it, color = XColors.Text2, fontFamily = InterFamily, fontSize = 18.sp) }
                    profile.joined?.let { Text("Joined ${formatWhen(it).ifBlank { it.take(16) }}", color = XColors.Text2, fontFamily = InterFamily, fontSize = 18.sp) }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    XChip("Posts", !vm.videosOnly) { vm.setTab(false) }
                    XChip("Videos", vm.videosOnly) { vm.setTab(true) }
                }
                Spacer(Modifier.height(16.dp))
                if (vm.loading) LoadingState("Loading posts…")
                else if (vm.posts.isEmpty()) EmptyState("No posts loaded", "This tab didn't return posts for @${profile.handle}.")
                else if (vm.videosOnly) {
                    val videos = vm.posts.filter { it.hasVideo }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(videos, key = { it.id }) { post ->
                            VideoCard(post, null, { onPlay(videos, post.id) }, width = 320.dp)
                        }
                    }
                } else {
                    vm.posts.take(8).forEach { post ->
                        FeedPostCard(post) { onOpenPost(post) }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Long) {
    Text("${formatCount(value)} $label", color = XColors.Text2, fontFamily = InterFamily, fontSize = 20.sp)
}
