package com.faz.xbyfaz.core.auth

import android.webkit.CookieManager
import android.webkit.WebStorage
import com.faz.xbyfaz.core.database.SettingsRepository
import com.faz.xbyfaz.core.model.AppFailure
import com.faz.xbyfaz.core.model.ErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class Session(
    val checking: Boolean = true,
    val signedIn: Boolean = false,
    val handle: String? = null,
    val displayName: String? = null,
)

@Singleton
class SessionRepository @Inject constructor(
    private val settings: SettingsRepository,
) {
    private val _state = MutableStateFlow(Session())
    val state: StateFlow<Session> = _state.asStateFlow()

    fun hasAuthCookie(): Boolean {
        val raw = try {
            CookieManager.getInstance().getCookie("https://x.com")
        } catch (_: Throwable) {
            null
        } ?: return false
        return raw.split(';').any { part ->
            val trimmed = part.trim()
            trimmed.startsWith("auth_token=") && trimmed.substringAfter('=').isNotBlank()
        }
    }

    suspend fun refresh() {
        val signedIn = hasAuthCookie()
        val cached = settings.current()
        _state.value = Session(
            checking = false,
            signedIn = signedIn,
            handle = if (signedIn) cached.handle else null,
            displayName = if (signedIn) cached.displayName else null,
        )
        if (!signedIn && (cached.handle != null || cached.displayName != null)) {
            try {
                settings.clearIdentity()
            } catch (_: Throwable) {
                // Identity cache is not a secret. Leave the signed-out state as-is.
            }
        }
    }

    suspend fun rememberAccount(handle: String?, displayName: String?) {
        if (handle.isNullOrBlank()) return
        settings.update { it.copy(handle = handle.removePrefix("@"), displayName = displayName ?: it.displayName) }
        _state.value = _state.value.copy(signedIn = hasAuthCookie(), handle = handle.removePrefix("@"), displayName = displayName ?: _state.value.displayName, checking = false)
    }

    suspend fun signOut() {
        try {
            withContext(Dispatchers.Main) {
                val manager = CookieManager.getInstance()
                suspendCancellableCoroutine { cont ->
                    manager.removeAllCookies { cont.resume(Unit) }
                }
                manager.flush()
                WebStorage.getInstance().deleteAllData()
            }
            settings.clearIdentity()
            _state.value = Session(checking = false, signedIn = false, handle = null, displayName = null)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            throw AppFailure(ErrorCode.AUTH_005, "The on-device session could not be cleared.", t.javaClass.simpleName, t)
        }
    }
}
