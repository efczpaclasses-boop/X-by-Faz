package com.faz.xbyfaz.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.faz.xbyfaz.core.model.AppFailure
import com.faz.xbyfaz.core.model.Post
import com.faz.xbyfaz.core.model.asFailure
import com.faz.xbyfaz.core.model.formatWhen
import com.faz.xbyfaz.core.ui.Avatar
import com.faz.xbyfaz.core.ui.ErrorPanel
import com.faz.xbyfaz.core.ui.InterFamily
import com.faz.xbyfaz.core.ui.LoadingState
import com.faz.xbyfaz.core.ui.StatsRow
import com.faz.xbyfaz.core.ui.XButton
import com.faz.xbyfaz.core.ui.XColors
import com.faz.xbyfaz.core.ui.imageModel
import com.faz.xbyfaz.core.web.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PostDetailViewModel @Inject constructor(private val content: ContentRepository) : ViewModel() {
    var post by mutableStateOf<Post?>(null)
    var error by mutableStateOf<AppFailure?>(null)
    var loading by mutableStateOf(true)
    private var currentId: String? = null

    fun open(id: String) {
        if (currentId == id && post != null) return
        currentId = id
        viewModelScope.launch {
            loading = true
            error = null
            try {
                post = content.post(id)
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
fun PostDetailRoute(postId: String, onPlay: (Post) -> Unit, onProfile: (String) -> Unit) {
    val vm: PostDetailViewModel = hiltViewModel()
    LaunchedEffect(postId) { vm.open(postId) }
    val post = vm.post
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 36.dp, end = 64.dp, bottom = 48.dp)) {
        when {
            vm.loading && post == null -> LoadingState("Opening the post…")
            vm.error != null && post == null -> ErrorPanel(vm.error!!, { vm.open(postId) }, null)
            post != null -> {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Avatar(post.author.name, post.author.handle, post.author.avatarUrl, 84.dp)
                    Column {
                        Text(post.author.name, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp)
                        Text("@${post.author.handle}" + post.createdAt?.let { " · ${formatWhen(it)}" }.orEmpty(), color = XColors.Text2, fontFamily = InterFamily, fontSize = 22.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(post.text, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 42.sp)
                post.photos.forEach { photo ->
                    val model = imageModel(photo) ?: return@forEach
                    Spacer(Modifier.height(18.dp))
                    AsyncImage(model, contentDescription = null, modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Fit)
                }
                Spacer(Modifier.height(18.dp))
                StatsRow(post)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (post.hasVideo) XButton("Play video", { onPlay(post) }, primary = true, icon = Icons.Filled.PlayArrow)
                    XButton("View profile", { onProfile(post.author.handle) })
                }
            }
        }
    }
}
