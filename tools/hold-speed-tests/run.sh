#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/../.."
output="$(mktemp -d)"
trap 'rm -rf "$output"' EXIT
javac -d "$output" app/src/main/java/org/jellyfin/androidtv/ui/playback/HoldToDoubleSpeed.java app/src/test/java/org/jellyfin/androidtv/ui/playback/HoldToDoubleSpeedTestCases.java
java -cp "$output" org.jellyfin.androidtv.ui.playback.HoldToDoubleSpeedTestCases
