#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/../.."
output="$(mktemp -d)"
trap 'rm -rf "$output"' EXIT
javac -d "$output" app/src/main/java/org/jellyfin/androidtv/ui/playback/network/NetworkTransferMeter.java tools/network-tests/NetworkTransferMeterTest.java
java -cp "$output" NetworkTransferMeterTest
