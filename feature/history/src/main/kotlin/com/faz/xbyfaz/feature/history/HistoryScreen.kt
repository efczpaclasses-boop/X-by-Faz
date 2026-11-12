package com.faz.xbyfaz.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faz.xbyfaz.core.database.HistoryRepository
import com.faz.xbyfaz.core.database.SettingsRepository
import com.faz.xbyfaz.core.model.Mp4Variant
import com.faz.xbyfaz.core.model.Playable
import com.faz.xbyfaz.core.model.WatchEntry
import com.faz.xbyfaz.core.model.formatClock
import com.faz.xbyfaz.core.ui.EmptyState
import com.faz.xbyfaz.core.ui.InterFamily
import com.faz.xbyfaz.core.ui.ScreenTitle
import com.faz.xbyfaz.core.ui.XButton
import com.faz.xbyfaz.core.ui.XChip
import com.faz.xbyfaz.core.ui.XColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val entries = history.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settingsState = settings.settings

    fun setEnabled(value: Boolean) {
        viewModelScope.launch { settings.update { it.copy(historyEnabled = value) } }
    }

    fun remove(videoId: String) {
        viewModelScope.launch { history.delete(videoId) }
    }

    fun clear() {
        viewModelScope.launch { history.clear() }
    }
}

fun WatchEntry.toPlayable(fromStart: Boolean): Playable = Playable(
    videoId = videoId,
    postId = postId,
    title = title,
    authorName = authorName,
    authorHandle = authorHandle,
    avatarUrl = null,
    posterUrl = posterUrl,
    hlsUrl = mediaUrl?.takeIf { it.contains(".m3u8") },
    mp4Urls = mediaUrl?.takeUnless { it.contains(".m3u8") }?.let { listOf(Mp4Variant(it, null)) }.orEmpty(),
    permalink = permalink,
    durationMs = durationMs,
    protectedStream = mediaUrl == null,
    source = "history",
)

@Composable
fun HistoryRoute(onResume: (WatchEntry) -> Unit, onRewatch: (WatchEntry) -> Unit) {
    val vm: HistoryViewModel = hiltViewModel()
    val entries by vm.entries.collectAsState()
    val settings by vm.settingsState.collectAsState()
    Column(Modifier.fillMaxSize().padding(top = 36.dp, end = 48.dp)) {
        ScreenTitle("Watch history", "Resume, rewatch, or clear what this TV remembers.")
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            XChip(if (settings.historyEnabled) "History on" else "History off", settings.historyEnabled) { vm.setEnabled(!settings.historyEnabled) }
            XButton("Clear history", vm::clear)
        }
        Spacer(Modifier.height(18.dp))
        if (entries.isEmpty()) {
            EmptyState("No videos yet", "Played videos are stored on this device: post, account, position, date, and whether you finished.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(entries, key = { it.videoId }) { entry ->
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(entry.title, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
                        Text(
                            "@${entry.authorHandle} · ${entry.source} · ${formatClock(entry.positionMs)} / ${formatClock(entry.durationMs)}" + if (entry.completed) " · finished" else "",
                            color = XColors.Text2,
                            fontFamily = InterFamily,
                            fontSize = 18.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            XChip("Resume", false) { onResume(entry) }
                            XChip("Rewatch", false) { onRewatch(entry) }
                            XChip("Remove", false) { vm.remove(entry.videoId) }
                        }
                    }
                }
            }
        }
    }
}
