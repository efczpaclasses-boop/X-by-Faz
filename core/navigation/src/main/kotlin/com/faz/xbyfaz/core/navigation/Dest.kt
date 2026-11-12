package com.faz.xbyfaz.core.navigation

sealed interface Dest {
    data object Home : Dest
    data object Following : Dest
    data object Videos : Dest
    data object Bookmarks : Dest
    data object Likes : Dest
    data object Search : Dest
    data object Profiles : Dest
    data class Profile(val handle: String) : Dest
    data object History : Dest
    data object Settings : Dest
    data class PostDetail(val postId: String) : Dest
    data object Player : Dest

    val rail: Boolean
        get() = this is Home || this is Following || this is Videos || this is Bookmarks ||
            this is Likes || this is Search || this is Profiles || this is History || this is Settings
}
