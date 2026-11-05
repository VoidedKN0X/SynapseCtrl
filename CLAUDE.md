# SynapseCtrl

Android admin app for a self-hosted Matrix Synapse server (users, invites, rooms, media, reports, server health). It's an admin tool, not a chat client; ChatCtrl is the chat client. Part of the VKNXWorks Ctrl series: same look, structure and libraries as AdCtrl, ProxCtrl and the other Ctrl apps (Kotlin, Compose, Material 3, manual `AppContainer` DI, OkHttp + kotlinx.serialization, the shared "Ctrl / Signal" look in `ui/theme` and `ui/components/Ctrl.kt`). Google-free and telemetry-free: no Google Play services, Firebase, ML Kit, Play Core, Tink or WebView. It must build for F-Droid, for arm64-v8a only.

## Working on this repo

- Push fixes and updates straight to `main`, with no branches or pull requests.
- Every push needs a detailed `CHANGELOG.md` entry.
- Every user-visible string goes in `app/src/main/res/values/strings.xml`.
- Check every Synapse endpoint against `docs/admin_api/` and `synapse/rest/admin/` in element-hq/synapse at the server's version tag; never guess paths, versions (v1/v2/v3) or field names. Parse responses leniently (missing or new fields must never crash).
- The only network destinations are the configured Synapse servers. The access token goes in the Authorization header only, never in a URL, and is never logged. Passwords are used once to log in and never stored.
- Destructive actions (deactivate/erase user, delete room, delete local media) show what will happen and need the user or room ID typed in. Test them on a throwaway test user and test room, never on the owner's own account or real rooms.
- CI runs a Google-free dependency audit on every build; keep it passing.

## Dependency verification

Gradle checks every library and plugin download against the sha256 checksums in `gradle/verification-metadata.xml` (checksums only, no PGP). When a dependency, plugin or the Gradle version changes, start the `verification-metadata.yml` workflow (`gh api -X POST repos/VoidedKN0X/SynapseCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), take the file from its log (gzipped and base64-encoded between the BEGIN/END markers), check the diff (only Maven Central, Google's Maven and the Gradle plugin portal) and commit it in the same push. Never turn verification off or make it lenient.

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "SynapseCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.
- CI signs with the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD` and `KEY_PASSWORD` secrets and the `KEY_ALIAS` variable (`synapse`), and checks the APK is signed with that key. Never commit a keystore, its base64 or its passwords.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.
