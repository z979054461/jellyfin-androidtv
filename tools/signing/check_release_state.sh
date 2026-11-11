#!/usr/bin/env bash
# Read-only gate: a previously published version is never rebuilt or republished.
set -euo pipefail
PAGES=$(gh api --paginate --slurp "repos/$GITHUB_REPOSITORY/releases?per_page=100")
MATCHES=$(jq --arg tag "$RELEASE_TAG" '[.[][] | select(.tag_name == $tag)]' <<< "$PAGES")
COUNT=$(jq 'length' <<< "$MATCHES")
if [ "$COUNT" -gt 1 ]; then
    echo 'Multiple releases match the target tag; refusing to proceed.' >&2
    exit 1
fi
if [ "$COUNT" = 0 ]; then
    echo 'should_run=true' >> "$GITHUB_OUTPUT"
    exit 0
fi
RELEASE_ID=$(jq -r '.[0].id' <<< "$MATCHES")
[[ "$RELEASE_ID" =~ ^[0-9]+$ ]]
RELEASE=$(gh api "repos/$GITHUB_REPOSITORY/releases/$RELEASE_ID")
jq -e --arg tag "$RELEASE_TAG" --argjson id "$RELEASE_ID" \
    '.id == $id and .tag_name == $tag and (.draft | type) == "boolean"' <<< "$RELEASE" > /dev/null
if [ "$(jq -r '.draft' <<< "$RELEASE")" = true ]; then
    # Drafts still require full checks, signature verification and strict commit matching.
    echo 'should_run=true' >> "$GITHUB_OUTPUT"
    exit 0
fi
jq -e --arg name "$RELEASE_APK" '
    .prerelease == false and (.assets | length) == 1 and
    .assets[0].name == $name and .assets[0].state == "uploaded" and
    (.assets[0].size > 0 and .assets[0].size < 209715200) and
    (.assets[0].digest | type == "string" and test("^sha256:[a-f0-9]{64}$"))
' <<< "$RELEASE" > /dev/null
TAG=$(gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/$RELEASE_TAG")
for attempt in 1 2 3 4 5; do
    TYPE=$(jq -r '.object.type' <<< "$TAG")
    SHA=$(jq -r '.object.sha' <<< "$TAG")
    if [ "$TYPE" = commit ]; then break; fi
    test "$TYPE" = tag
    [[ "$SHA" =~ ^[a-f0-9]{40}$ ]]
    TAG=$(gh api "repos/$GITHUB_REPOSITORY/git/tags/$SHA")
done
test "$(jq -r '.object.type' <<< "$TAG")" = commit
SHA=$(jq -r '.object.sha' <<< "$TAG")
[[ "$SHA" =~ ^[a-f0-9]{40}$ ]]
# This is an existence check, not a fresh attestation of the old APK. No remote
# writes or new artifact are allowed here; publish_release.sh retains SHA equality.
echo 'should_run=false' >> "$GITHUB_OUTPUT"
echo "$RELEASE_TAG is already published at $SHA; skipping build/signing/publication without changing it." >> "$GITHUB_STEP_SUMMARY"
