#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" "$ROOT/app/src/main/java/org/jellyfin/androidtv/update/UpdatePolicy.java" "$ROOT/tools/update-tests/UpdatePolicyTest.java" "$ROOT/app/src/main/java/org/jellyfin/androidtv/update/UpdateCache.java" "$ROOT/tools/update-tests/UpdateCacheTest.java"
java -cp "$OUT" UpdatePolicyTest
java -cp "$OUT" UpdateCacheTest
