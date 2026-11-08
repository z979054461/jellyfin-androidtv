#!/usr/bin/env sh
set -eu
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" \
  "$ROOT/app/src/main/java/org/jellyfin/androidtv/update/InstallerHandoffGate.java" \
  "$ROOT/app/src/main/java/org/jellyfin/androidtv/update/InstallerLaunchPolicy.java" \
  "$ROOT/app/src/main/java/org/jellyfin/androidtv/update/UpdateFocusPolicy.java" \
  "$ROOT/tools/installer-tests/InstallerPolicyTest.java"
java -cp "$OUT" InstallerPolicyTest
