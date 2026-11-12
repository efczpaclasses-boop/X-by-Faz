package com.faz.xbyfaz.core.web

import com.faz.xbyfaz.core.model.FeedKind
import com.faz.xbyfaz.core.model.FeedPage
import com.faz.xbyfaz.core.model.Post
import com.faz.xbyfaz.core.model.Profile
import com.faz.xbyfaz.core.model.SearchPage

data class AccountIdentity(
    val handle: String,
    val displayName: String?,
)

/**
 * Swappable reader for an already signed-in x.com session.
 * Implementations must not collect passwords or call unofficial private APIs.
 */
interface WebsiteIntegrationEngine {
    val id: String
    suspend fun feed(kind: FeedKind, cursor: String?, handle: String?): FeedPage
    suspend fun search(query: String, cursor: String?): SearchPage
    suspend fun profile(handle: String): Profile
    suspend fun profileFeed(handle: String, videosOnly: Boolean, cursor: String?): FeedPage
    suspend fun post(id: String): Post
    suspend fun currentAccount(): AccountIdentity?
}
