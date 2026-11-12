package com.faz.xbyfaz.feature.search

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.faz.xbyfaz.core.model.rememberRecent
import com.faz.xbyfaz.core.ui.Avatar
import com.faz.xbyfaz.core.ui.EmptyState
import com.faz.xbyfaz.core.ui.ErrorPanel
import com.faz.xbyfaz.core.ui.InterFamily
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
class SearchViewModel @Inject constructor(
    private val content: ContentRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    var query by mutableStateOf("")
    var accounts by mutableStateOf<List<Profile>>(emptyList())
    var posts by mutableStateOf<List<Post>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<AppFailure?>(null)
    var voiceNote by mutableStateOf<String?>(null)
    val recents get() = settings.current().recentSearches

    fun type(text: String) { query = (query + text).take(80) }
    fun backspace() { query = query.dropLast(1) }
    fun voice(text: String) { query = text.take(80); submit() }

    fun submit() {
        val q = query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            loading = true
            error = null
            try {
                settings.update { it.copy(recentSearches = rememberRecent(it.recentSearches, q)) }
                val page = content.search(q, null)
                accounts = page.accounts
                posts = page.posts
                error = page.warning
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                error = t as? AppFailure ?: t.asFailure()
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun SearchRoute(onPlay: (List<Post>, String) -> Unit, onProfile: (String) -> Unit, onOpen: (Post) -> Unit) {
    val vm: SearchViewModel = hiltViewModel()
    val context = LocalContext.current
    val voiceAvailable = rememberVoiceAvailable()
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) vm.voice(spoken)
    }
    Row(Modifier.fillMaxSize().padding(top = 28.dp, end = 36.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        Column(Modifier.width(360.dp)) {
            Text("Search", color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            Spacer(Modifier.height(6.dp))
            Text("Accounts, videos, posts, or a hashtag. OK opens the TV keyboard.", color = XColors.Text2, fontFamily = InterFamily, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            SystemTextField(vm.query, { vm.query = it }, "Search X", vm::submit)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XButton("Search", vm::submit, primary = true)
                XButton("Voice", {
                    if (!voiceAvailable) {
                        vm.voiceNote = "Voice input isn't available on this TV."
                    } else {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        }
                        voiceLauncher.launch(intent)
                    }
                }, icon = Icons.Filled.Mic)
            }
            vm.voiceNote?.let { Text(it, color = XColors.Text3, fontFamily = InterFamily, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(18.dp))
            Text("Recent", color = XColors.Text3, fontFamily = InterFamily, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.recents.take(4).forEach { recent ->
                    XChip(recent, false) { vm.query = recent; vm.submit() }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            when {
                vm.loading -> com.faz.xbyfaz.core.ui.LoadingState("Searching X…")
                vm.error != null -> ErrorPanel(vm.error!!, vm::submit) { vm.error = null }
                vm.query.isNotBlank() && vm.accounts.isEmpty() && vm.posts.isEmpty() -> EmptyState("No matches", "Nothing readable came back for that search. Try a handle or a shorter word.")
                else -> {
                    if (vm.accounts.isNotEmpty()) {
                        Text("Accounts", color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(vm.accounts, key = { it.handle }) { account ->
                                Column {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Avatar(account.name, account.handle, account.avatarUrl, 64.dp)
                                        Column {
                                            Text(account.name, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                            Text("@${account.handle}", color = XColors.Text2, fontFamily = InterFamily, fontSize = 16.sp)
                                        }
                                    }
                                    XChip("Open", false) { onProfile(account.handle) }
                                }
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                    }
                    val videos = vm.posts.filter { it.hasVideo }
                    if (videos.isNotEmpty()) {
                        Text("Videos", color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            items(videos, key = { it.id }) { post ->
                                VideoCard(post, null, { onPlay(videos, post.id) }, width = 340.dp)
                            }
                        }
                    }
                    if (vm.posts.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Text("Posts", color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        vm.posts.take(6).forEach { post ->
                            Spacer(Modifier.height(10.dp))
                            XChip(post.text.take(80), false) { if (post.hasVideo) onPlay(videos, post.id) else onOpen(post) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberVoiceAvailable(): Boolean {
    val context = LocalContext.current
    return androidx.compose.runtime.remember {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.resolveActivity(context.packageManager) != null
    }
}
