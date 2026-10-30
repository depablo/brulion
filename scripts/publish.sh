#!/usr/bin/env bash
# Publishes a release built by scripts/release.sh as a GitHub release, which is
# where the app looks for update.json (brulion.updateUrl).
#
#   scripts/publish.sh <versionName>
#
# The version bump must be committed and pushed first: the tag points at HEAD.
set -euo pipefail
cd "$(dirname "$0")/.."

repo=depablo/brulion
version_name=${1:?usage: $0 <versionName>}
out=dist/$version_name
apk=$out/brulion-$version_name.apk

[[ -f $apk && -f $out/update.json ]] || { echo "missing $out; run scripts/release.sh first" >&2; exit 1; }
if [[ -n $(git status --porcelain) ]]; then
    echo "uncommitted changes; commit the version bump first" >&2
    exit 1
fi
git fetch -q origin
if [[ $(git rev-parse HEAD) != $(git rev-parse '@{u}') ]]; then
    echo "HEAD is not pushed; push before publishing" >&2
    exit 1
fi

notes=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["changelog"])' "$out/update.json")
gh release create "v$version_name" "$apk" "$out/update.json" \
    --repo "$repo" --target "$(git rev-parse HEAD)" \
    --title "Brulion $version_name" --notes "$notes"
echo "Published: https://github.com/$repo/releases/tag/v$version_name"
