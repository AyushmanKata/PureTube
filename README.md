# PureTube

Minimal Android YouTube client: **no ads, no Shorts**, local history and subscriptions (no Google account).

## Build
1. Open this folder in Android Studio (Ladybug+ / current stable; JDK 17+).
2. Let Gradle sync (needs Android SDK 37 and network access to Google, Maven Central, JitPack).
3. Run on a device/emulator (Android 8.0+), or `./gradlew assembleDebug`.

## How it works
- `YtRepository` uses **NewPipeExtractor** to search, read video info and get raw stream URLs.
  No YouTube player is used, so ad insertion never runs.
- Shorts are dropped from search, related and channel lists (`isShortFormContent` or `/shorts/` URL).
- `VideoPlayer` (Media3 ExoPlayer): 720p+ streams are video-only, so video and audio URLs are merged
  with `MergingMediaSource`. Quality switches keep the playback position.
- `Store` keeps history and subscriptions in SharedPreferences (JSON).

## Layout
| File | Role |
|---|---|
| `YtRepository.kt` | Extractor calls, Shorts filter, quality list |
| `OkHttpDownloader.kt` | HTTP bridge for NewPipeExtractor |
| `AppViewModel.kt` | UI state, search paging, history, subscriptions |
| `VideoPlayer.kt` | ExoPlayer + merged sources |
| `MainActivity.kt` | Compose UI: Home, Library, Channel, Watch |
| `Store.kt`, `Models.kt` | Persistence and data classes |

## Known limits
- Not compiled or run when this was generated. Expect small fixes on first build.
- YouTube changes break extractors regularly; bump the `NewPipeExtractor` version when that happens.
  Some streams may 403 or throttle without a PO token (NewPipe itself needs this).
- Not yet built: background play, picture-in-picture, fullscreen, comments, channel feed, SponsorBlock.
- Likely violates YouTube's Terms of Service. Personal use only; not publishable on Google Play.
