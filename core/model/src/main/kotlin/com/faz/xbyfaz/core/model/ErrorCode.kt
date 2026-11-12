package com.faz.xbyfaz.core.model

/**
 * Owner-facing error codes. Every entry is documented in ERROR_CODES.md.
 * A failure should be shown with [code], a short sentence, and Retry / Skip.
 */
enum class ErrorCode(val code: String, val title: String, val ownerHint: String) {
    AUTH_001(
        "XF-AUTH-001",
        "Sign-in page didn't load",
        "The in-app page could not open X's login. Check the TV's network, then retry.",
    ),
    AUTH_002(
        "XF-AUTH-002",
        "Embedded sign-in was blocked",
        "X refused or left the official login page. Retry once. Password sign-in and 2FA still happen on X's page; passkeys often do not work inside a TV WebView.",
    ),
    AUTH_003(
        "XF-AUTH-003",
        "Session expired",
        "The saved x.com session is no longer signed in. Sign in again. X by Faz does not keep your password.",
    ),
    AUTH_004(
        "XF-AUTH-004",
        "Session not confirmed",
        "The login page closed without a usable x.com session cookie. Stay on X's page until the home timeline appears, then return.",
    ),
    AUTH_005(
        "XF-AUTH-005",
        "Could not clear the session",
        "Cookies could not be removed. Try Sign out again, then Clear data.",
    ),
    AUTH_006(
        "XF-AUTH-006",
        "Sign-in page stayed blank",
        "X's login page did not draw anything. Retry. If it keeps happening, the TV's network or WebView is blocking x.com.",
    ),
    AUTH_007(
        "XF-AUTH-007",
        "Phone sign-in could not fill the form",
        "The details from your phone did not match the fields on X's page. The password was discarded. Type on the TV instead. Nothing was saved.",
    ),
    AUTH_008(
        "XF-AUTH-008",
        "Phone sign-in has no network address",
        "This TV has no LAN address the phone can reach. Use Sign in with X on the remote instead.",
    ),
    AUTH_009(
        "XF-AUTH-009",
        "No username choice on X's page",
        "X showed a sign-in chooser without an email, username, or phone option. Google and Apple are skipped. Type on the TV, or retry.",
    ),
    AUTH_010(
        "XF-AUTH-010",
        "Username field not found",
        "The username, email, or phone field did not appear. The password was discarded. Type on the TV.",
    ),
    AUTH_011(
        "XF-AUTH-011",
        "Password field not found",
        "X did not show a password field after the username. The password was discarded. Type on the TV.",
    ),
    AUTH_012(
        "XF-AUTH-012",
        "Verification field not found",
        "X asked for a check, but no code field was on the page. Type the code on the TV.",
    ),
    WEB_001(
        "XF-WEB-001",
        "Integration config invalid",
        "The website integration config packaged with the app is missing or unreadable. Reinstall the app.",
    ),
    WEB_010(
        "XF-WEB-010",
        "Page timed out",
        "x.com did not finish loading. The TV network may be slow, or X is blocking the embedded page.",
    ),
    WEB_011(
        "XF-WEB-011",
        "No posts could be read",
        "The page loaded but neither the site's own response nor the visible timeline contained posts. The section may be empty, private, or changed.",
    ),
    WEB_012(
        "XF-WEB-012",
        "Feed parse failed",
        "A timeline response arrived but did not match the parser. X likely changed the page structure. Photograph this code.",
    ),
    WEB_013(
        "XF-WEB-013",
        "Profile parse failed",
        "The profile page did not yield a readable account. The handle may be wrong, suspended, or the page structure changed.",
    ),
    WEB_014(
        "XF-WEB-014",
        "Search parse failed",
        "Search results could not be read from the page. Retry, or try a shorter query.",
    ),
    WEB_015(
        "XF-WEB-015",
        "Video metadata parse failed",
        "A post looked like video but its media metadata could not be read. The in-page player can still be tried.",
    ),
    WEB_016(
        "XF-WEB-016",
        "Couldn't load another page",
        "Scrolling for older posts did not return a further page. What is already on screen is kept.",
    ),
    WEB_017(
        "XF-WEB-017",
        "Bookmarks unavailable",
        "Bookmarks could not be opened with this session. They may be empty, private, or the page structure changed.",
    ),
    WEB_018(
        "XF-WEB-018",
        "Likes unavailable",
        "Likes could not be opened. The signed-in handle may be unknown, or the likes page is blocked.",
    ),
    WEB_019(
        "XF-WEB-019",
        "Following feed unavailable",
        "The Following tab could not be selected on x.com/home. The For You home feed is unaffected.",
    ),
    WEB_020(
        "XF-WEB-020",
        "Bridge refused this page",
        "The reader ignored a page that was not x.com or twitter.com. Nothing from that page was stored.",
    ),
    WEB_021(
        "XF-WEB-021",
        "Page script failed",
        "The in-page reader threw before it could return posts. Retry. If it persists, the site structure changed.",
    ),
    WEB_022(
        "XF-WEB-022",
        "X needs you to confirm it's you",
        "X or Cloudflare opened a check, such as Just a moment. Finish it on the TV with the remote. The app does not solve that check for you. When X returns to your feed, it reloads.",
    ),
    WEB_023(
        "XF-WEB-023",
        "Update Android System WebView",
        "The human check started over three times. Update Android System WebView from the Play Store. This TV's WebView is old, and the app does not solve the check.",
    ),
    PLAY_001(
        "XF-PLAY-001",
        "No direct media URL",
        "This post has no ordinary MP4 or HLS URL the TV player can open. Protected streams are not unlocked.",
    ),
    PLAY_002(
        "XF-PLAY-002",
        "Playback failed",
        "ExoPlayer could not play the media URL the page exposed. Retry, or skip to the next video.",
    ),
    PLAY_003(
        "XF-PLAY-003",
        "In-page player failed",
        "The full-screen x.com player fallback could not find a video element. The post may be deleted or login-only.",
    ),
    PLAY_004(
        "XF-PLAY-004",
        "Queue is empty",
        "There is no playable video left in this queue. Go back and pick another post.",
    ),
    PLAY_005(
        "XF-PLAY-005",
        "Media cache failed",
        "The on-device video cache could not be read or cleared. Playback can continue without cache; restart the app if it keeps failing.",
    ),
    DATA_001(
        "XF-DATA-001",
        "Watch history wasn't saved",
        "Room could not write the resume point. Playback continues. Check free space.",
    ),
    DATA_002(
        "XF-DATA-002",
        "Settings weren't saved",
        "DataStore could not read or write a setting. The previous value stays in effect.",
    ),
    DATA_003(
        "XF-DATA-003",
        "Diagnostics log failed",
        "The error list itself could not be saved. The on-screen code is still valid to photograph.",
    ),
    NET_001(
        "XF-NET-001",
        "Media download failed",
        "The video host did not return data. The URL may have expired. Retry, which loads the post again.",
    ),
    APP_001(
        "XF-APP-001",
        "Unexpected error",
        "Something in the app failed and was caught. The app should still be open. Retry the last action.",
    ),
    ;

    companion object {
        fun fromId(code: String?): ErrorCode? = entries.firstOrNull { it.code == code }
    }
}

class AppFailure(
    val error: ErrorCode,
    val userMessage: String,
    detail: String? = null,
    cause: Throwable? = null,
) : Exception(userMessage, cause) {
    val safeDetail: String = Sanitizer.clean(detail)
}

fun Throwable.asFailure(): AppFailure = when (this) {
    is AppFailure -> this
    else -> AppFailure(
        error = ErrorCode.APP_001,
        userMessage = "Something went wrong. Try again.",
        detail = "${this.javaClass.simpleName}: ${message.orEmpty()}",
        cause = this,
    )
}
