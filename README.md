# X by Faz

Unofficial personal-use Android TV / Google TV app for watching videos from your own X account. It is not an official X product and is not affiliated with X Corp.

The launcher name is **X by Faz**. The wordmark is an original play-tile plus the words “X by Faz”. It does not use the X Corp logo.

## What it does

- Sign in on X’s own login page, full screen. You can type with the remote, or scan a QR code and type on your phone. The phone only talks to this TV. The TV types those details into X’s form and then forgets them. Nothing is written to disk or logs.
- The website session stays in the app’s WebView cookie store and is restored on the next launch. Cookies and tokens are not written to logs or the diagnostics list.
- Home shows your timeline in order: text, images, and videos, plus a video row. Text and image posts open in a reading view. Videos play full screen.
- Also: Following, Videos (All / Recent / Following / Bookmarked / Profiles), Bookmarks, Likes, Search, Profiles, Watch history, and Settings.
- The player uses one Media3 ExoPlayer for ordinary MP4 and HLS URLs the page already exposed. If there is no direct URL, or playback fails, it falls back to a full-screen X page with the remote still controlling play, pause, and seek. Protected streams are not unlocked.
- Up / Down move to the previous / next video and stay full screen. Left / Right seek 10 seconds. OK pauses. Hold OK opens options. Back returns to the feed item you opened.

There is no sample feed. Until you are signed in, or when a section cannot be read, the screen stays empty and shows an error code.

## Requirements

- JDK 21 (the project compiles Java/Kotlin bytecode for 17)
- Android SDK 35, build-tools 35
- An Android TV or Google TV device, or an Android TV emulator, on landscape 1080p or 4K

## Debug build

```bash
export ANDROID_HOME="$HOME/Android/Sdk"   # or your SDK path
./gradlew assembleDebug
```

There is one app: `app/build/outputs/apk/debug/app-debug.apk`, application id `com.faz.xbyfaz`, launcher name **X by Faz**. You type the account yourself, on the TV or on your phone via the QR code. The app does not ship a saved password.

Unit tests for the queue, parsers, playback selection, history rules, and error-code list:

```bash
./gradlew :core:model:test
```

## Release build and signing

Create a keystore once:

```bash
keytool -genkeypair -v \
  -keystore xbyfaz-release.jks \
  -alias xbyfaz \
  -keyalg RSA -keysize 2048 -validity 10000
```

Copy `keystore.properties.example` to `keystore.properties` (that file is gitignored) and point `storeFile` at the keystore. Then:

```bash
./gradlew assembleRelease
```

The release APK is `app/build/outputs/apk/release/app-release.apk`. If `keystore.properties` is missing, the release build is signed with the debug key so it can still be assembled locally. Replace that before you install a build you plan to keep.

Minification is off. The WebView bridge is sensitive to shrinking; `app/proguard-rules.pro` keeps the JavaScript interface if you turn minification on later.

## Install on Android TV

### ADB

1. On the TV: Settings → System → About, click Android TV OS build several times until developer options turn on.
2. Settings → System → Developer options → USB debugging (or Network debugging) on.
3. From a computer on the same network:

```bash
adb connect TV_IP:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app appears in the Android TV launcher as **X by Faz**.

### Downloader-style install

Google TV and Fire TV sideload flows often use the Downloader app (or another file app) because many TVs have no browser that can install an APK directly.

1. Install Downloader from the Play Store if it is available, or sideload it once with ADB (`adb install downloader.apk`).
2. Put `app-debug.apk` on a web server you control, or copy it to a USB drive and open it with a file manager.
3. In Downloader, enter the `https://` URL of the APK and download it.
4. When the TV asks, allow installs from that source and confirm the install.
5. Open **X by Faz** from the Apps row. You may need to enable “Install unknown apps” for Downloader under Settings → Apps.

Do not publish this APK to a store. It is a personal-use client.

## Remote

| Key | Player |
|---|---|
| Up | Previous video, stay full screen |
| Down | Next video, stay full screen |
| Left / Right | Seek −10s / +10s |
| OK | Pause or resume |
| Hold OK | Options (speed, fit/fill, restart) |
| Back | Return to the feed |

On the X login page, arrows move between fields and OK opens the keyboard or presses the focused button. Back leaves the page. The page is a full-screen window, not a popup.

**Sign in with your phone** shows a QR code and a URL such as `http://<tv-lan-ip>:<port>/?t=<token>`. The TV runs that page only while the QR screen or the following login screen is open. The phone page (X by Faz) asks for the username and password, then a verification code if X asks for one. Those values stay in the TV’s memory and are typed into X’s real form. Requests without the token are rejected. Leaving the screen or finishing login stops the server and wipes the values. If the form cannot be filled, the TV shows `XF-AUTH-007` and you type with the remote instead.

## Website integration

There is no official X API and no developer registration. `core/web` is a swappable engine (`WebsiteIntegrationEngine`). The real engine is `x-web-v1`:

- It loads x.com in an off-screen WebView that already has your session cookies.
- `assets/engine/hook.js` watches `fetch` and `XMLHttpRequest` responses the page itself receives, and only forwards timeline-shaped JSON. Direct-message URLs are ignored.
- The bridge runs only while the page host is x.com or twitter.com (`XF-WEB-020` if it is not).
- `assets/engine/dom.js` is the fallback reader, using selectors from `assets/engine/x-web-v1.json`.
- Secrets (`auth_token`, `ct0`, passwords, cookies, email, phone) are redacted before anything is parsed or saved.

If X changes the site, the screen shows a short code such as `XF-WEB-012` plus Retry and Skip. Settings → Diagnostics lists recent codes for a photo. Every code is in `ERROR_CODES.md`.

Login uses a tablet Chrome user agent and the TV’s own viewport, so X’s page is drawn at a readable size instead of a desktop page scaled to the panel. It does not skip 2FA, passkeys, or checks. If X opens a chooser, the app follows the email, username, or phone option and skips Google and Apple. A phone sign-in types those steps into that same page. A blank page shows `XF-AUTH-006`. A missing chooser, username field, password field, or code field shows `XF-AUTH-009` through `XF-AUTH-012` on the TV and on the phone.

Account switching signs out (cookies cleared) and opens the login page again. The WebView cookie jar holds one session.

## Modules

`app` plus `core/model`, `core/navigation`, `core/database` (Room history and diagnostics, DataStore settings), `core/network` (OkHttp for media only), `core/auth`, `core/web`, `core/player` (Media3 ExoPlayer, HLS, cache, preload), `core/ui`, and `feature/home`, `feature/videos`, `feature/search`, `feature/profile`, `feature/history`, `feature/settings`.

Inter is bundled under the SIL Open Font License. See `third_party/Inter-OFL.txt`.

## Verified in this workspace

- `./gradlew :core:model:test` — queue order and de-duplication, skip-unavailable, fast moves, key-repeat gate, timeline JSON parser, DOM parser, playback selection, history resume rules, secret redaction, and the error-code list.
- `./gradlew assembleDebug` — one debug APK, `com.faz.xbyfaz`.
- Android TV emulator, 1920×1080 at 320 dpi, WebView 113: the welcome screen fits, with one “X by Faz” wordmark and the disclaimer on screen. Sign-in is a full-screen activity. On this emulator image the same WebView 113 either crashes its renderer or fails the TLS handshake to x.com, so the onboarding chooser was not drawn here. A physical Google TV with that WebView did load the chooser; the app now follows its email, username, or phone option.
- Phone sign-in, reached with `adb forward` and curl: a missing or wrong token returns 403, the phone page is the X by Faz form, a credential post returns `{"ok":true}`, `/status` reports only the phase, and the password does not appear in logcat. Leaving the screen stops the server.

Not verified here: the x.com form itself on this emulator. Its WebView renderer crashes before the login fields are drawn, so a username was not typed on screen. A signed-in session, playback of your own videos, and a physical television were not tested.
