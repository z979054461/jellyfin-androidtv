#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/../.."
output="$(mktemp -d)"
trap 'rm -rf "$output"' EXIT
javac -d "$output" app/src/main/java/org/jellyfin/androidtv/ui/playback/network/NetworkSpeedLabel.java tools/network-label-tests/NetworkSpeedLabelTest.java
java -cp "$output" NetworkSpeedLabelTest
