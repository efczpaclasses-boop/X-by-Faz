package com.faz.xbyfaz.core.web

import com.faz.xbyfaz.core.database.DiagnosticsRepository
import com.faz.xbyfaz.core.model.FeedKind
import com.faz.xbyfaz.core.model.FeedPage
import com.faz.xbyfaz.core.model.Post
import com.faz.xbyfaz.core.model.Profile
import com.faz.xbyfaz.core.model.SearchPage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostStore @Inject constructor() {
    private val posts = ConcurrentHashMap<String, Post>()
    private val profiles = ConcurrentHashMap<String, Profile>()

    fun putPosts(items: List<Post>) {
        items.forEach { posts[it.id] = it }
    }

    fun putProfile(profile: Profile) {
        profiles[profile.handle.lowercase()] = profile
    }

    fun post(id: String): Post? = posts[id]
    fun profile(handle: String): Profile? = profiles[handle.lowercase()]
}

@Singleton
class ContentRepository @Inject constructor(
    private val engine: WebsiteIntegrationEngine,
    private val store: PostStore,
    private val diagnostics: DiagnosticsRepository,
) {
    suspend fun feed(kind: FeedKind, cursor: String?, handle: String?): FeedPage {
        val page = engine.feed(kind, cursor, handle)
        store.putPosts(page.posts)
        page.warning?.let { diagnostics.record(it, kind.name) }
        return page
    }

    suspend fun search(query: String, cursor: String?): SearchPage {
        val page = engine.search(query, cursor)
        store.putPosts(page.posts)
        page.accounts.forEach(store::putProfile)
        page.warning?.let { diagnostics.record(it, "search") }
        return page
    }

    suspend fun profile(handle: String): Profile {
        val profile = engine.profile(handle)
        store.putProfile(profile)
        return profile
    }

    suspend fun profileFeed(handle: String, videosOnly: Boolean, cursor: String?): FeedPage {
        val page = engine.profileFeed(handle, videosOnly, cursor)
        store.putPosts(page.posts)
        page.warning?.let { diagnostics.record(it, "profile") }
        return page
    }

    suspend fun post(id: String): Post {
        store.post(id)?.let { return it }
        val loaded = engine.post(id)
        store.putPosts(listOf(loaded))
        return loaded
    }
}

@Singleton
class EngineRouter @Inject constructor(
    private val real: XWebIntegrationEngine,
) : WebsiteIntegrationEngine {
    override val id: String get() = real.id
    override suspend fun feed(kind: FeedKind, cursor: String?, handle: String?) = real.feed(kind, cursor, handle)
    override suspend fun search(query: String, cursor: String?) = real.search(query, cursor)
    override suspend fun profile(handle: String) = real.profile(handle)
    override suspend fun profileFeed(handle: String, videosOnly: Boolean, cursor: String?) = real.profileFeed(handle, videosOnly, cursor)
    override suspend fun post(id: String) = real.post(id)
    override suspend fun currentAccount() = real.currentAccount()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WebBindModule {
    @Binds
    abstract fun bindEngine(router: EngineRouter): WebsiteIntegrationEngine
}
