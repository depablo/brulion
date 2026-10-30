#!/usr/bin/env bash
# Builds a signed release and the update.json the app reads.
#
#   scripts/release.sh <versionName> <changelog text or file>
#
# Bumps brulion.versionCode/versionName in gradle.properties, builds the release APK
# and writes dist/<versionName>/ with brulion-<versionName>.apk and update.json.
# Then commit, push and run scripts/publish.sh to put them on GitHub.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ $# -lt 2 ]]; then
    echo "usage: $0 <versionName> <changelog text or file>" >&2
    exit 1
fi
version_name=$1
changelog=$2
[[ -f $changelog ]] && changelog=$(cat "$changelog")

if [[ ! -f keystore.properties ]]; then
    echo "keystore.properties is missing; release builds must be signed with the release key" >&2
    exit 1
fi
if [[ -z $(grep '^brulion.updateUrl=' gradle.properties | cut -d= -f2-) ]]; then
    echo "warning: brulion.updateUrl is empty, this build won't check for updates" >&2
fi

old_code=$(grep '^brulion.versionCode=' gradle.properties | cut -d= -f2)
version_code=$((old_code + 1))
sed -i "s/^brulion.versionCode=.*/brulion.versionCode=$version_code/; s/^brulion.versionName=.*/brulion.versionName=$version_name/" gradle.properties

export JAVA_HOME=${JAVA_HOME:-/home/pawel/tools/jdk}
./gradlew -q clean assembleRelease

out=dist/$version_name
apk=brulion-$version_name.apk
mkdir -p "$out"
cp app/build/outputs/apk/release/app-release.apk "$out/$apk"
sha=$(sha256sum "$out/$apk" | cut -d' ' -f1)
min_sdk=$(grep -oE 'minSdk = [0-9]+' app/build.gradle.kts | grep -oE '[0-9]+')

python3 - "$out/update.json" "$version_code" "$version_name" "$apk" "$sha" "$changelog" "$min_sdk" <<'PY'
import json, sys
path, code, name, apk, sha, changelog, min_sdk = sys.argv[1:]
with open(path, "w", encoding="utf-8") as f:
    json.dump({"versionCode": int(code), "versionName": name, "apkUrl": apk, "sha256": sha,
               "changelog": changelog, "minSdk": int(min_sdk)}, f, ensure_ascii=False, indent=2)
    f.write("\n")
PY

echo "Built $version_name ($version_code):"
ls -l "$out"
echo
echo "Next: git commit -am \"Release $version_name\" && git push && scripts/publish.sh $version_name"
