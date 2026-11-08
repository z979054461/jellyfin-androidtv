# Native danmaku preview

This downstream feature is based on official Jellyfin Android TV **v0.19.10**,
commit `984181a3d6ab14e9a6d2dcc850c582e1c138bd95`. It is not an official
Jellyfin release and does not modify the upstream master branch.

## Scope

- Native internal-player overlay for seekable on-demand media.
- Bilibili-style XML from the connected Jellyfin server's
  `GET /api/danmu/{itemId}/raw`, compatible with the protocol exposed by
  [jellyfin-plugin-danmu](https://github.com/cxfksword/jellyfin-plugin-danmu).
- Scrolling (mode 1), bottom (4) and top (5) comments, collision avoidance,
  subtitle-safe lower 30%, outline, font size, row limit, opacity, duration and offset.
- Media-position-based frames: no wall-clock extrapolation. Pause/buffering,
  backward/forward seek and playback speed follow the internal player's timeline.
- Playback action in the primary controls, navigable with the remote.
  Danmaku is **on when no preference has been saved**; an explicit off preference is preserved. Positive offset displays comments earlier.
- Separate application ID `io.github.z979054461.jellyfin.danmaku` (debug suffix
  `.debug`), so it does not replace official Jellyfin or fengymi's client.

No server plugins are installed automatically. A missing plugin, HTTP error,
unsupported XML or empty data leaves normal video playback unchanged. The menu shows load status and comment counts, and provides an explicit reload action.

## Deliberate first-version limits

Live TV, external players, non-seekable streams (including generic HTTP
transcoding that may restart its timestamp at zero), source selection/manual
episode matching, advanced positioned comments and ASS rendering are not included.
Non-seekable streams are excluded rather than guessing an absolute timestamp.
The server plugin must already have the correct episode's XML.

The preview UI is localized in English and Simplified Chinese; other locales
intentionally fall back to English. Settings are local to this app installation,
not per Jellyfin user. No requests
are sent to third-party danmaku sites by the client. Requests use the existing
server base URL and current Jellyfin authorization header. Redirects are refused
to prevent credentials from being sent to a different endpoint.

## Resource and lifecycle bounds

- Maximum downloaded/decompressed response: 8 MiB; checked while reading,
  independently of Content-Length.
- 15-second HTTP call timeout; request cancelled on episode switch, disable,
  or view destruction. Generation checks discard stale results.
- UTF-8 XML only, no DTD/entity declarations or external entity resolution.
- At most 20,000 entries, 300 characters each, bounded XML nesting.
- Parsing off the main thread; lane admission precomputed only when data or
  geometry changes. Each frame uses binary search over the active time window.

## Build and tests

Use JDK 21, Android SDK platform 36 and the checked-in Gradle wrapper:

```sh
./gradlew assembleDebug lintDebug
tools/danmaku-tests/run.sh
```

The standalone core suite has no Android or third-party dependencies. It covers
parser failures/limits, seek/pause deterministic frames, fixed modes and dense
scrolling collision scenarios. It does not prove Android integration or playback
compatibility.

### Local verification (2026-11-02)

- Debug APK assembled and its debug signature verified.
- Existing app and preference tests: 30 executions across debug/release,
  no failures or skips. This sandbox required loading the existing ByteBuddy
  agent at test-JVM startup instead of external self-attachment; assertions
  and dependencies were unchanged.
- Dependency-free danmaku core: 108,680 assertions passed.
- Detekt checked; the new Kotlin files have no reported findings.
- Android lint runs, but the upstream project permits existing lint findings.
  This is not a claim that the entire repository has a clean lint report.
  Pure-Java core interoperability nullness hints remain intentionally
  dependency-free. The new UI explicitly uses English fallback outside Chinese.
- No physical TV/emulator or live server-plugin integration has been tested.

Before treating an APK as production-ready, test on a spare TV/emulator:
1. Default-on state, preserved explicit off preference, and missing-plugin status.
2. Correct comments with a real plugin and Jellyfin base-path reverse proxy.
3. Pause, buffering, resume, seek in both directions, 0.5x/1x/2x.
4. Seekable HLS transcoding and resumed playback; non-seekable streams stay empty.
5. Next/previous episode during a slow response; close/reopen playback.
6. Font/rows/opacity/duration/offset submenu focus, Back, repeated open/close.
7. Large XML, malformed XML, timeout/401/404 and network loss.
8. Ordinary subtitles and control focus remain readable and usable.

## Provenance and licensing

The upstream GPL v2 LICENSE and existing notices are unchanged. This overlay,
parser and timeline are independently implemented; no DanmakuFlameMaster source
or binary is included. The behavior and raw-XML protocol were investigated with
reference to [fengymi/jellyfin-androidtv](https://github.com/fengymi/jellyfin-androidtv)
v1300.19.6. That fork's custom Apache-2.0 renderer was not imported, avoiding an
additional GPLv2/Apache2 compatibility question and JitPack/NDK dependency.

This preview does not imply that existing upstream dependencies have undergone
a fresh comprehensive license audit.

## Preview 2

See [release notes](releases/danmaku-preview-2.md) for changes, installation and signing caveats.
The previous preview already contained a renderer; preview 2 improves discovery and diagnostics.
The preview 2 CI release workflow ran build/test/lint/detekt before publishing its debug prerelease.


## Signed release 3

See [release notes](releases/danmaku-release-3.md). New installs default to 80% font;
font presets span 50–150% in 10% steps and row limits span every integer 1–20.
The renderer still limits rows to the available subtitle-safe area. Existing
saved choices are not overwritten.

Playback information is a local, read-only snapshot of source metadata and
current player input formats. It does not send server requests or reveal media
URLs. The dialog refreshes only while visible and closes on episode changes.

The signed release package is separate from the debug preview. The About update
screen accepts only newer APKs from this fork with a GitHub SHA-256 digest,
matching package and matching signer. Installation is always the Android system
installer after an explicit user action. No background or silent updates occur.

CI checks still run when the four dedicated signing Secrets are unavailable,
but the workflow then skips publishing. Signing keys must never be committed or
included in build artifacts. A fixed key is required for all later upgrades.
