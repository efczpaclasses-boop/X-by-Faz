# X by Faz error codes

These codes are shown on screen when the website structure, sign-in, or playback fails. Each one is safe to photograph. Details on the Diagnostics screen are redacted: passwords, cookies, and auth tokens are never stored.

The app catches these failures. One of them should not close the app.

| Code | Title | What the owner should know |
|---|---|---|
| XF-AUTH-001 | Sign-in page didn't load | The in-app page could not open X's login. Check the TV's network, then retry. |
| XF-AUTH-002 | Embedded sign-in was blocked | X refused the embedded login or left x.com. Retry. Password and 2FA still happen on X's page. Passkeys often do not work in a TV WebView. |
| XF-AUTH-003 | Session expired | The saved x.com session is no longer signed in. Sign in again. The app does not keep the password. |
| XF-AUTH-004 | Session not confirmed | Login closed without an x.com session cookie. Stay on X's page until the home timeline appears. |
| XF-AUTH-005 | Could not clear the session | Cookies could not be removed. Try Sign out again, then Clear data. |
| XF-AUTH-006 | Sign-in page stayed blank | X's login page did not draw anything. Retry. The TV's network or WebView may be blocking x.com. |
| XF-AUTH-007 | Phone sign-in could not fill the form | Details from the phone did not match X's fields. They were discarded. Type on the TV instead. Nothing was saved. |
| XF-AUTH-008 | Phone sign-in has no network address | The TV has no LAN address a phone can open. Use Sign in with X on the remote. |
| XF-AUTH-009 | No username choice on X's page | The chooser had no email, username, or phone option. Google and Apple are skipped. Retry, or type on the TV. |
| XF-AUTH-010 | Username field not found | The username, email, or phone field did not appear. The password was discarded. Type on the TV. |
| XF-AUTH-011 | Password field not found | X did not show a password field after the username. The password was discarded. Type on the TV. |
| XF-AUTH-012 | Verification field not found | X asked for a check, but no code field was on the page. Type the code on the TV. |
| XF-WEB-001 | Integration config invalid | The packaged website-integration config is missing or unreadable. Reinstall. |
| XF-WEB-010 | Page timed out | x.com did not finish loading. The network may be slow, or the embedded page was blocked. |
| XF-WEB-011 | No posts could be read | The page loaded but no posts were found. The section may be empty, private, or changed. |
| XF-WEB-012 | Feed parse failed | A timeline response arrived in an unexpected shape. X likely changed the site. Photograph this code. |
| XF-WEB-013 | Profile parse failed | The profile page was not readable. Check the handle, or the page structure changed. |
| XF-WEB-014 | Search parse failed | Search results could not be read. Retry or shorten the query. |
| XF-WEB-015 | Video metadata parse failed | A video post's media metadata could not be read. The in-page player can still be tried. |
| XF-WEB-016 | Couldn't load another page | Older posts did not load. Posts already on screen are kept. |
| XF-WEB-017 | Bookmarks unavailable | Bookmarks could not be opened. They may be empty, private, or the page changed. |
| XF-WEB-018 | Likes unavailable | Likes could not be opened. The signed-in handle may be unknown, or the page is blocked. |
| XF-WEB-019 | Following feed unavailable | The Following tab on x.com/home could not be selected. Home itself still works. |
| XF-WEB-020 | Bridge refused this page | The reader ignored a page that was not x.com or twitter.com. Nothing from that page was kept. |
| XF-WEB-021 | Page script failed | The in-page reader failed before it could return posts. |
| XF-WEB-022 | X needs you to confirm it's you | X or Cloudflare opened a check, such as Just a moment. Finish it on the TV with the remote. The app does not solve that check. The feed reloads after X returns home. |
| XF-WEB-023 | Update Android System WebView | The human check started over three times. Update Android System WebView from the Play Store. The app does not solve the check. |
| XF-PLAY-001 | No direct media URL | No ordinary MP4 or HLS URL was available. Protected streams are not unlocked. |
| XF-PLAY-002 | Playback failed | The TV player could not play the media URL the page exposed. |
| XF-PLAY-003 | In-page player failed | The full-screen x.com fallback could not find a video element. |
| XF-PLAY-004 | Queue is empty | No playable video is left in this queue. |
| XF-PLAY-005 | Media cache failed | The on-device video cache could not be used or cleared. |
| XF-DATA-001 | Watch history wasn't saved | The resume point could not be written. Playback continues. |
| XF-DATA-002 | Settings weren't saved | A setting could not be read or written. The previous value stays. |
| XF-DATA-003 | Diagnostics log failed | The error list could not be saved. The on-screen code can still be photographed. |
| XF-NET-001 | Media download failed | The video host did not return data. The URL may have expired. |
| XF-APP-001 | Unexpected error | An unexpected failure was caught. Retry the last action. The app should still be open. |

## Retry and Skip

- **Retry** runs the same load or playback again.
- **Skip** dismisses the error. On a feed, whatever already loaded stays on screen. In the player, Skip drops the current video and plays the next one.

## What is never included

Diagnostics do not contain `auth_token`, `ct0`, passwords, cookie headers, email addresses, or phone numbers. Those strings are redacted before anything is saved. The app does not transmit error reports anywhere; the owner photographs or copies them.
