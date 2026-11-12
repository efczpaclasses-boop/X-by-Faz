package com.faz.xbyfaz.core.database

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.faz.xbyfaz.core.model.AppFailure
import com.faz.xbyfaz.core.model.AppSettings
import com.faz.xbyfaz.core.model.DiagnosticEntry
import com.faz.xbyfaz.core.model.ErrorCode
import com.faz.xbyfaz.core.model.PreloadMode
import com.faz.xbyfaz.core.model.Sanitizer
import com.faz.xbyfaz.core.model.TextScale
import com.faz.xbyfaz.core.model.VideoQuality
import com.faz.xbyfaz.core.model.WatchEntry
import com.faz.xbyfaz.core.model.asFailure
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore("xbyfaz_settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.settingsStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        scope.launch {
            try {
                store.data.map { it.toSettings() }.collect { _settings.value = it }
            } catch (t: CancellationException) {
                throw t
            } catch (_: Throwable) {
                // Keep defaults. The screen that writes will surface XF-DATA-002.
            }
        }
    }

    fun current(): AppSettings = _settings.value

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        try {
            store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            throw AppFailure(ErrorCode.DATA_002, "That setting couldn't be saved.", t.message, t)
        }
    }

    suspend fun clearIdentity() {
        update { it.copy(handle = null, displayName = null) }
    }

    suspend fun reset() {
        try {
            store.edit { it.clear() }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            throw AppFailure(ErrorCode.DATA_002, "Settings couldn't be cleared.", t.message, t)
        }
    }
}

private fun Preferences.toSettings(): AppSettings = AppSettings(
    autoplayNext = this[Keys.AUTOPLAY] ?: true,
    quality = VideoQuality.fromStored(this[Keys.QUALITY]),
    resume = this[Keys.RESUME] ?: true,
    scaleFill = this[Keys.FILL] ?: false,
    controlsTimeoutSec = this[Keys.TIMEOUT] ?: 4,
    preload = PreloadMode.fromStored(this[Keys.PRELOAD]),
    cacheMb = this[Keys.CACHE] ?: 512,
    dataSaver = this[Keys.SAVER] ?: false,
    compactThumbnails = this[Keys.COMPACT] ?: false,
    textScale = TextScale.fromStored(this[Keys.TEXT]),
    remoteHints = this[Keys.HINTS] ?: true,
    showOverlay = this[Keys.OVERLAY] ?: true,
    historyEnabled = this[Keys.HISTORY] ?: true,
    handle = this[Keys.HANDLE],
    displayName = this[Keys.NAME],
    recentSearches = decode(this[Keys.SEARCHES]),
    recentProfiles = decode(this[Keys.PROFILES]),
)

private fun MutablePreferences.write(s: AppSettings) {
    this[Keys.AUTOPLAY] = s.autoplayNext
    this[Keys.QUALITY] = s.quality.name
    this[Keys.RESUME] = s.resume
    this[Keys.FILL] = s.scaleFill
    this[Keys.TIMEOUT] = s.controlsTimeoutSec
    this[Keys.PRELOAD] = s.preload.name
    this[Keys.CACHE] = s.cacheMb
    this[Keys.SAVER] = s.dataSaver
    this[Keys.COMPACT] = s.compactThumbnails
    this[Keys.TEXT] = s.textScale.name
    this[Keys.HINTS] = s.remoteHints
    this[Keys.OVERLAY] = s.showOverlay
    this[Keys.HISTORY] = s.historyEnabled
    val handle = s.handle
    val displayName = s.displayName
    if (handle == null) remove(Keys.HANDLE) else this[Keys.HANDLE] = handle
    if (displayName == null) remove(Keys.NAME) else this[Keys.NAME] = displayName
    this[Keys.SEARCHES] = encode(s.recentSearches)
    this[Keys.PROFILES] = encode(s.recentProfiles)
}

private fun encode(values: List<String>) = values.joinToString("\n")
private fun decode(raw: String?) = raw?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

private object Keys {
    val AUTOPLAY = booleanPreferencesKey("autoplay_next")
    val QUALITY = stringPreferencesKey("quality")
    val RESUME = booleanPreferencesKey("resume")
    val FILL = booleanPreferencesKey("scale_fill")
    val TIMEOUT = intPreferencesKey("controls_timeout")
    val PRELOAD = stringPreferencesKey("preload")
    val CACHE = intPreferencesKey("cache_mb")
    val SAVER = booleanPreferencesKey("data_saver")
    val COMPACT = booleanPreferencesKey("compact_thumbs")
    val TEXT = stringPreferencesKey("text_scale")
    val HINTS = booleanPreferencesKey("remote_hints")
    val OVERLAY = booleanPreferencesKey("show_overlay")
    val HISTORY = booleanPreferencesKey("history_enabled")
    val HANDLE = stringPreferencesKey("handle")
    val NAME = stringPreferencesKey("display_name")
    val SEARCHES = stringPreferencesKey("recent_searches")
    val PROFILES = stringPreferencesKey("recent_profiles")
}

@Singleton
class HistoryRepository @Inject constructor(private val dao: HistoryDao) {
    fun observe(): Flow<List<WatchEntry>> = dao.observe().map { list -> list.map { it.toEntry() } }

    suspend fun get(videoId: String): WatchEntry? = dao.get(videoId)?.toEntry()

    suspend fun upsert(entry: WatchEntry) {
        try {
            dao.upsert(entry.copy(mediaUrl = Sanitizer.safeToStoreUrl(entry.mediaUrl)).toEntity())
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            throw t.asFailure().let {
                AppFailure(ErrorCode.DATA_001, "Watch history couldn't be saved.", it.safeDetail, t)
            }
        }
    }

    suspend fun delete(videoId: String) = dao.delete(videoId)
    suspend fun clear() = dao.clear()
}

@Singleton
class DiagnosticsRepository @Inject constructor(private val dao: DiagnosticsDao) {
    fun observe(): Flow<List<DiagnosticEntry>> = dao.observe().map { list -> list.map { it.toEntry() } }

    suspend fun record(failure: AppFailure, screen: String) {
        try {
            dao.insert(
                DiagnosticEventEntity(
                    code = failure.error.code,
                    message = Sanitizer.clean(failure.userMessage),
                    detail = failure.safeDetail,
                    screen = screen,
                    timestamp = System.currentTimeMillis(),
                ),
            )
            dao.trim()
        } catch (_: CancellationException) {
            // A cancelled screen should not record a fake failure.
        } catch (_: Throwable) {
            // XF-DATA-003: never crash because the log itself failed.
        }
    }

    suspend fun record(throwable: Throwable, screen: String) {
        if (throwable is CancellationException) return
        record(throwable as? AppFailure ?: throwable.asFailure(), screen)
    }

    suspend fun clear() {
        dao.clear()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): XDatabase =
        Room.databaseBuilder(context, XDatabase::class.java, "xbyfaz.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun history(db: XDatabase): HistoryDao = db.history()
    @Provides fun diagnostics(db: XDatabase): DiagnosticsDao = db.diagnostics()
}
