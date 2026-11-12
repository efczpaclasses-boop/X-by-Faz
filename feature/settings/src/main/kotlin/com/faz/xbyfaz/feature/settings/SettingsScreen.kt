package com.faz.xbyfaz.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faz.xbyfaz.core.auth.SessionRepository
import com.faz.xbyfaz.core.database.DiagnosticsRepository
import com.faz.xbyfaz.core.database.HistoryRepository
import com.faz.xbyfaz.core.database.SettingsRepository
import com.faz.xbyfaz.core.model.AppSettings
import com.faz.xbyfaz.core.model.PreloadMode
import com.faz.xbyfaz.core.model.TextScale
import com.faz.xbyfaz.core.model.VideoQuality
import com.faz.xbyfaz.core.player.MediaCacheHolder
import com.faz.xbyfaz.core.ui.Eyebrow
import com.faz.xbyfaz.core.ui.InterFamily
import com.faz.xbyfaz.core.ui.ScreenTitle
import com.faz.xbyfaz.core.ui.XButton
import com.faz.xbyfaz.core.ui.XChip
import com.faz.xbyfaz.core.ui.XColors
import com.faz.xbyfaz.core.web.EngineRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

private enum class Pane { Playback, Performance, Appearance, Account, About, Diagnostics }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val historyRepository: HistoryRepository,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val cache: MediaCacheHolder,
    val engine: EngineRouter,
) : ViewModel() {
    val settings = settingsRepository.settings
    val diagnostics = diagnosticsRepository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val session = sessionRepository.state

    fun update(block: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(block) }
    }

    fun clearCache() {
        viewModelScope.launch {
            runCatching { cache.clear() }
            runCatching { cache.clearImages() }
        }
    }

    fun clearDiagnostics() {
        viewModelScope.launch { diagnosticsRepository.clear() }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            sessionRepository.signOut()
            onDone()
        }
    }

    fun clearData(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { historyRepository.clear() }
            runCatching { cache.clear() }
            runCatching { cache.clearImages() }
            runCatching { settingsRepository.reset() }
            runCatching { sessionRepository.signOut() }
            onDone()
        }
    }
}

@Composable
fun SettingsRoute(versionName: String, onSignedOut: () -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val settings by vm.settings.collectAsState()
    val events by vm.diagnostics.collectAsState()
    val session by vm.session.collectAsState()
    var pane by rememberSaveable { mutableIntStateOf(0) }
    val current = Pane.entries[pane]
    Row(Modifier.fillMaxSize().padding(top = 28.dp, end = 36.dp)) {
        Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenTitle("Settings")
            Spacer(Modifier.height(8.dp))
            Pane.entries.forEachIndexed { index, item ->
                XChip(item.name, current == item) { pane = index }
            }
            Spacer(Modifier.height(16.dp))
            Text("X by Faz  v$versionName", color = XColors.Text3, fontFamily = InterFamily, fontSize = 16.sp)
            Text("Unofficial, not affiliated with X Corp.", color = XColors.Text3, fontFamily = InterFamily, fontSize = 14.sp)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp)) {
            Eyebrow(current.name)
            Spacer(Modifier.height(12.dp))
            when (current) {
                Pane.Playback -> {
                    ToggleRow("Auto-play next video", "Start the next video in the queue when one ends", settings.autoplayNext) {
                        vm.update { it.copy(autoplayNext = !it.autoplayNext) }
                    }
                    ChoiceRow("Video quality", VideoQuality.entries.map { it.label() }, settings.quality.label()) { label ->
                        vm.update { it.copy(quality = VideoQuality.entries.first { q -> q.label() == label }) }
                    }
                    ToggleRow("Resume playback", "Continue a video you didn't finish", settings.resume) {
                        vm.update { it.copy(resume = !it.resume) }
                    }
                    ChoiceRow("Video scaling", listOf("Fit", "Fill"), if (settings.scaleFill) "Fill" else "Fit") { label ->
                        vm.update { it.copy(scaleFill = label == "Fill") }
                    }
                    ChoiceRow("Controls timeout", listOf("3 seconds", "4 seconds", "6 seconds", "8 seconds"), "${settings.controlsTimeoutSec} seconds") { label ->
                        vm.update { it.copy(controlsTimeoutSec = label.substringBefore(' ').toInt()) }
                    }
                }
                Pane.Performance -> {
                    ChoiceRow("Preloading", PreloadMode.entries.map { it.name }, settings.preload.name) { label ->
                        vm.update { it.copy(preload = PreloadMode.valueOf(label)) }
                    }
                    ChoiceRow("Cache size", listOf("256 MB", "512 MB", "1024 MB", "2048 MB"), "${settings.cacheMb} MB") { label ->
                        vm.update { it.copy(cacheMb = label.substringBefore(' ').toInt()) }
                    }
                    Text("Cache size applies the next time the app starts.", color = XColors.Text3, fontFamily = InterFamily, fontSize = 16.sp)
                    ToggleRow("Data saver", "Cap quality around 720p and turn preloading off", settings.dataSaver) {
                        vm.update { it.copy(dataSaver = !it.dataSaver) }
                    }
                    Spacer(Modifier.height(12.dp))
                    XButton("Clear cache", vm::clearCache)
                }
                Pane.Appearance -> {
                    ChoiceRow("Thumbnail layout", listOf("Comfortable", "Compact"), if (settings.compactThumbnails) "Compact" else "Comfortable") {
                        vm.update { s -> s.copy(compactThumbnails = it == "Compact") }
                    }
                    ChoiceRow("Text size", TextScale.entries.map { it.label }, settings.textScale.label) { label ->
                        vm.update { it.copy(textScale = TextScale.entries.first { scale -> scale.label == label }) }
                    }
                    ToggleRow("Remote hints", "Show the key legend on the player", settings.remoteHints) {
                        vm.update { it.copy(remoteHints = !it.remoteHints) }
                    }
                    ToggleRow("Player overlay", "Show title, progress, and up next when controls appear", settings.showOverlay) {
                        vm.update { it.copy(showOverlay = !it.showOverlay) }
                    }
                }
                Pane.Account -> {
                    Text(
                        session.handle?.let { "@$it" } ?: "Not signed in",
                        color = XColors.Text,
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                    )
                    Text("One website session is kept on this TV. Switching accounts signs out, then opens X's login.", color = XColors.Text2, fontFamily = InterFamily, fontSize = 18.sp)
                    Spacer(Modifier.height(16.dp))
                    XButton("Switch account", { vm.signOut(onSignedOut) })
                    Spacer(Modifier.height(10.dp))
                    XButton("Sign out", { vm.signOut(onSignedOut) })
                    Spacer(Modifier.height(10.dp))
                    XButton("Clear data", { vm.clearData(onSignedOut) })
                    Text("Clear data removes history, cache, and settings, then signs out. Diagnostic codes are kept until you clear them on that screen.", color = XColors.Text3, fontFamily = InterFamily, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Pane.About -> {
                    Text("X by Faz", color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Unofficial, not affiliated with X Corp.", color = XColors.Text2, fontFamily = InterFamily, fontSize = 20.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "X by Faz is a personal-use app for watching videos from your own X account on a television. It is not an official X product. Your password is typed only into X's own login page. This app does not collect it, does not send it anywhere, and does not log cookies or tokens.",
                        color = XColors.Text,
                        fontFamily = InterFamily,
                        fontSize = 20.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Version $versionName · reader ${vm.engine.id}", color = XColors.Text3, fontFamily = InterFamily, fontSize = 18.sp)
                    Text("If a section fails, the short code (for example XF-WEB-012) is listed under Diagnostics.", color = XColors.Text3, fontFamily = InterFamily, fontSize = 18.sp)
                }
                Pane.Diagnostics -> {
                    Text("Recent codes. Photograph this list or select the text if a keyboard is paired.", color = XColors.Text2, fontFamily = InterFamily, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    XButton("Clear diagnostics", vm::clearDiagnostics)
                    Spacer(Modifier.height(12.dp))
                    if (events.isEmpty()) {
                        Text("No errors recorded.", color = XColors.Text3, fontFamily = InterFamily, fontSize = 18.sp)
                    } else {
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                events.forEach { event ->
                                    Column {
                                        Text(event.code, color = XColors.Accent, fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                                        Text(event.message, color = XColors.Text, fontFamily = InterFamily, fontSize = 18.sp)
                                        Text(
                                            "${event.screen} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(event.timestamp))}",
                                            color = XColors.Text3,
                                            fontFamily = InterFamily,
                                            fontSize = 14.sp,
                                        )
                                        if (event.detail.isNotBlank()) {
                                            Text(event.detail, color = XColors.Text3, fontFamily = InterFamily, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, body: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
            Text(body, color = XColors.Text2, fontFamily = InterFamily, fontSize = 18.sp)
        }
        XChip(if (on) "On" else "Off", on, onClick = onClick)
    }
}

@Composable
private fun ChoiceRow(title: String, choices: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice -> XChip(choice, choice == selected) { onSelect(choice) } }
        }
    }
}
