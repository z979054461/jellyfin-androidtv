#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/../.."
output="$(mktemp -d)"
trap 'rm -rf "$output"' EXIT
javac -d "$output" app/src/main/java/org/jellyfin/androidtv/ui/playback/danmaku/DanmakuComment.java app/src/main/java/org/jellyfin/androidtv/ui/playback/danmaku/DanmakuParser.java app/src/main/java/org/jellyfin/androidtv/ui/playback/danmaku/DanmakuTimeline.java tools/danmaku-tests/DanmakuCoreTest.java
java -cp "$output" DanmakuCoreTest
