# Personal-fork update checks

Run `bash tools/update-tests/run.sh` with JDK 17+ (JDK 21 is used by CI). This directly compiles the production URL/version/digest/size policy and runs 35 policy assertions and 28 cache/selection regression assertions without an Android SDK. The matching Kotest suite is in `app/src/test/kotlin/update/UpdatePolicyTests.kt`.

Android integration checks require a signed release installation and a second APK with a strictly greater version code, the same release package ID and the same certificate. No private signing key belongs in the app or tests.

- In Settings > About > Check for updates, entering the page must not issue a request. Debug installs must explain the one-time separate release installation.
- Click Check: verify fixed anonymous GitHub Releases API access, newest matching `jellyfin-danmaku-<versionCode>.apk` selection including prereleases, exclusion of drafts/debug names, and no APK request until Download is clicked.
- Verify empty/reordered releases, equal/older versions, missing/malformed digest, HTTP/rate-limit/offline failures, wrong size and disallowed redirect URLs fail safely.
- During Download, press Back or Cancel. The active call must stop and its partial file must be removed; returning or repeated clicks must not start another download automatically.
- Serve a truncated/tampered APK, mismatched package, debug APK, equal/older actual manifest version, split APK or different signing certificate. None may enable installation.
- With a valid update, verify the system source-permission screen opens only when its explicit action is clicked. Returning from settings must not automatically launch installation.
- Click Open Android installer: verify a fresh hash/package/version/cert check, content URI from the non-exported provider, read-only URI grant, and system confirmation. Back/cancel must not be represented as successful installation.
- Confirm remote D-pad focus and Back work through check, download, cancel, permission and installer return, including a rotated/recreated screen and process restart. Reopening starts idle; completed APKs are only in app cache.

Only the pure checks and XML/patch validation have been run locally. APK parsing, network integration, system installer behavior and real-TV remote interaction require the Android build/device validation above.

Cache behavior: at most three APK/partial files, each APK bounded to 200 MiB. Files older than seven days and the oldest unprotected files at capacity are pruned. The currently displayed verified APK is pinned until the screen is destroyed or a new check starts. A seven-day lease is persisted before system-installer handoff so leaving the app or process recreation cannot immediately evict an asynchronously read APK. Files created within the last hour are also protected. If all slots are protected, a new download fails rather than evicting them. Lease files without their APK are cleaned up on the next download.

Candidate metadata regression: a missing/invalid digest, untrusted URL or invalid size skips that newer asset; other valid candidates remain eligible. If newer matching assets exist but none are verifiable, checking reports failure rather than no update. Standalone tests exercise the selection-result gate; actual GitHub JSON/network parsing still needs Android integration validation.
