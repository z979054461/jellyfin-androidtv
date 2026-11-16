#!/usr/bin/env bash
# Called only after full checks and signed APK verification.
set -euo pipefail
# Only an explicit 404 means absent. Authentication/network errors fail closed.
api_optional() {
  if gh api "$1" > "$2" 2> "$RUNNER_TEMP/danmaku-api-error.txt"; then
    return 0
  fi
  if grep -Fq '(HTTP 404)' "$RUNNER_TEMP/danmaku-api-error.txt"; then
    return 1
  fi
  echo 'Cannot verify existing release state; refusing publication.' >&2
  exit 1
}
TAG_JSON="$RUNNER_TEMP/danmaku-tag.json"
if api_optional "repos/$GITHUB_REPOSITORY/git/ref/tags/$RELEASE_TAG" "$TAG_JSON"; then
  # Resolve annotated tags as well as lightweight tags, without moving either.
  for attempt in 1 2 3 4 5; do
    TYPE=$(jq -r '.object.type' "$TAG_JSON")
    SHA=$(jq -r '.object.sha' "$TAG_JSON")
    if [ "$TYPE" = commit ]; then break; fi
    test "$TYPE" = tag
    gh api "repos/$GITHUB_REPOSITORY/git/tags/$SHA" > "$TAG_JSON.next"
    mv "$TAG_JSON.next" "$TAG_JSON"
  done
  test "$(jq -r '.object.type' "$TAG_JSON")" = commit
  test "$(jq -r '.object.sha' "$TAG_JSON")" = "$GITHUB_SHA"
else
  # Create only this new tag, explicitly at the fully checked revision.
  gh api --method POST "repos/$GITHUB_REPOSITORY/git/refs" \
    -f ref="refs/tags/$RELEASE_TAG" -f sha="$GITHUB_SHA" > "$TAG_JSON"
  test "$(jq -r '.object.sha' "$TAG_JSON")" = "$GITHUB_SHA"
fi
RELEASE_JSON="$RUNNER_TEMP/danmaku-release.json"
# Tag lookup excludes drafts. Use the authenticated, fully paginated release list,
# then address the exact release ID for all subsequent reads and publication.
find_release() {
  gh api --paginate --slurp "repos/$GITHUB_REPOSITORY/releases?per_page=100" \
    > "$RUNNER_TEMP/danmaku-release-pages.json"
  jq --arg tag "$RELEASE_TAG" '[.[][] | select(.tag_name == $tag)]' \
    "$RUNNER_TEMP/danmaku-release-pages.json" > "$RUNNER_TEMP/danmaku-release-matches.json"
  COUNT=$(jq 'length' "$RUNNER_TEMP/danmaku-release-matches.json")
  if [ "$COUNT" -gt 1 ]; then
    echo 'Multiple releases match the tag; refusing publication.' >&2
    exit 1
  fi
  RELEASE_ID=$(jq -r '.[0].id // empty' "$RUNNER_TEMP/danmaku-release-matches.json")
}
find_release
if [ -z "$RELEASE_ID" ]; then
  # Keep partial uploads private; never replace an existing draft or its assets.
  gh release create "$RELEASE_TAG" --verify-tag --draft \
    --title 'Jellyfin TV 0.20.0 弹幕定制版 1' \
    --notes-file docs/releases/danmaku-0.20.0-1.md \
    "build/danmaku-release/$RELEASE_APK"
  find_release
fi
[[ "$RELEASE_ID" =~ ^[0-9]+$ ]]
gh api "repos/$GITHUB_REPOSITORY/releases/$RELEASE_ID" > "$RELEASE_JSON"
jq -e --arg tag "$RELEASE_TAG" --argjson id "$RELEASE_ID" \
  '.id == $id and .tag_name == $tag' "$RELEASE_JSON" > /dev/null
EXPECTED_DIGEST=$(sha256sum "build/danmaku-release/$RELEASE_APK" | cut -d ' ' -f 1)
# Exactly one asset is permitted: no checksum files, keys, configs or logs.
jq -e --arg name "$RELEASE_APK" --arg digest "sha256:$EXPECTED_DIGEST" '
  (.assets | length) == 1 and
  .assets[0].name == $name and .assets[0].state == "uploaded" and
  .assets[0].digest == $digest
' "$RELEASE_JSON" > /dev/null
ASSET_ID=$(jq -r '.assets[0].id' "$RELEASE_JSON")
gh api -H 'Accept: application/octet-stream' \
  "repos/$GITHUB_REPOSITORY/releases/assets/$ASSET_ID" > "$RUNNER_TEMP/danmaku-uploaded.apk"
test "$(sha256sum "$RUNNER_TEMP/danmaku-uploaded.apk" | cut -d ' ' -f 1)" = "$EXPECTED_DIGEST"
if [ "$(jq -r '.draft' "$RELEASE_JSON")" = true ]; then
  gh api --method PATCH "repos/$GITHUB_REPOSITORY/releases/$RELEASE_ID" \
    -F draft=false -F prerelease=false -f make_latest=true > "$RUNNER_TEMP/danmaku-published.json"
  jq -e --arg tag "$RELEASE_TAG" --argjson id "$RELEASE_ID" \
    '.id == $id and .tag_name == $tag and .draft == false and .prerelease == false' \
    "$RUNNER_TEMP/danmaku-published.json" > /dev/null
else
  # A matching public release is a no-op; never rewrite its assets or notes.
  test "$(jq -r '.prerelease' "$RELEASE_JSON")" = false
fi
echo "Verified $RELEASE_TAG at $GITHUB_SHA; APK SHA-256: $EXPECTED_DIGEST" >> "$GITHUB_STEP_SUMMARY"
