#!/usr/bin/env bash
# Share the release verifier's tooling preflight with unsigned PR builds.
set -euo pipefail

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK_ROOT" ] || [ ! -d "$SDK_ROOT" ]; then
    echo 'An existing Android SDK is required (ANDROID_HOME or ANDROID_SDK_ROOT).' >&2
    exit 1
fi
TOOLS="$SDK_ROOT/build-tools/37.0.0"

has_tools() {
    [ -x "$TOOLS/apksigner" ] && [ -x "$TOOLS/aapt" ]
}

if ! has_tools; then
    # sdkmanager is not necessarily on PATH on GitHub-hosted runners.
    SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
    if [ ! -x "$SDKMANAGER" ]; then
        SDKMANAGER=''
        while IFS= read -r candidate; do
            if [ -x "$candidate" ]; then
                SDKMANAGER="$candidate"
                break
            fi
        done < <(printf '%s\n' "$SDK_ROOT"/cmdline-tools/*/bin/sdkmanager | sort -Vr)
    fi
    if [ -z "$SDKMANAGER" ]; then
        echo 'Build-tools 37.0.0 are missing and no SDK-local sdkmanager is available.' >&2
        exit 1
    fi
    # Gradle resolves the compile SDK platform. Do not assume android-37 vs android-37.0.
    "$SDKMANAGER" "--sdk_root=$SDK_ROOT" 'build-tools;37.0.0'
fi

if ! has_tools; then
    echo 'Build-tools 37.0.0 must provide executable apksigner and aapt.' >&2
    exit 1
fi
if [ -n "${GITHUB_ENV:-}" ]; then
    printf 'RELEASE_BUILD_TOOLS=%s\n' "$TOOLS" >> "$GITHUB_ENV"
fi
printf 'Verified Android build-tools 37.0.0.\n'
