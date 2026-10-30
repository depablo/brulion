# Releasing Brulion

Brulion isn't in any store; release builds update themselves from a static
`update.json` file.

## One-time setup

- `keystore.properties` (not in git) points to the release keystore,
  `~/.android/brulion-release.jks`. **Back up both, somewhere other than this
  laptop** (e.g. a password manager or an encrypted drive). Without the key no
  update can be installed over the app; users would have to uninstall and log
  in again.
- `brulion.updateUrl` in `gradle.properties` points to the latest GitHub release:
  `https://github.com/depablo/brulion/releases/latest/download/update.json`.
  The APK address in `update.json` is relative to it, so both files are
  attached to the same release. Don't mark releases as pre-release: `latest`
  skips those.
- `gh` logged in to an account that can create releases in depablo/brulion.

## Each release

```
scripts/release.sh 0.2.0 "Co nowego: …"     # or a path to a changelog file
git commit -am "Release 0.2.0" && git push
scripts/publish.sh 0.2.0
```

`release.sh` bumps `brulion.versionCode`/`versionName`, builds a signed APK and
writes `dist/0.2.0/` with `brulion-0.2.0.apk` and `update.json`. `publish.sh`
creates the GitHub release `v0.2.0` at the pushed HEAD with both files attached.

The source of every published APK is in this public repository (GPL v3).

## How the app updates

Settings → Aktualizacje checks on demand; with "Sprawdzaj automatycznie" a daily
job checks and shows a notification once per version. Installing downloads the
APK, checks its SHA-256 from `update.json` and that it has the same package and
signing certificate as the installed app, then hands it to Android's installer.
The first update asks the user to allow installing apps from Brulion.

What actually protects users is the **certificate check**. The SHA-256 comes
from the same `update.json` as the APK, so it only catches a damaged download:
whoever can replace the APK can replace the checksum too. An APK signed with
any other key is rejected, which is why the release key must stay private.

Debug builds are `com.bonsaponsi.brulion.debug` ("Brulion debug"), install next to the
release app and never check for updates.
