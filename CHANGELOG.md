# Changelog

All notable changes to SynapseCtrl. Every release on GitHub, in Obtainium and in the CtrlF-Droid repo uses the entry for its version.

## Unreleased

Built and checked by CI only: nothing here had been run on a phone when it was written.

### New

- **Health check: Large requests.** A reverse proxy in front of Synapse can keep answering small requests while it rejects every large one (an upload limit that is too small, or a missing temporary folder for request bodies, which gives an nginx "500" page). Chat apps then fail on uploads, key backups and long messages while everything else looks fine. The Overview now sends one test request of about 300 kB to your server's public address (the one client discovery names), without signing in. Synapse refuses it with its normal "missing token" answer, so **nothing is stored or changed on your server**. If that answer comes back, the check passes. If the proxy answers with its own error page instead (413 or any 5xx that isn't Synapse's JSON), the check fails, shows the status and the proxy's name, and counts as a failing health check for the attention list and the **Health check failing** notification. Any other odd answer, or a connection cut off half-way, is a warning. To keep data use low, the test is repeated at most every 3 hours (every 30 minutes while it isn't passing); pulling down on the Overview runs it at once.
- **Health check: LiveKit media server.** Next to the existing calls check (the MatrixRTC service named in client discovery answers), the Overview now also asks LiveKit itself for its health answer, at the place the two documented setups put it: `/livekit/sfu` next to `/livekit/jwt`, or the root of the host when the call service is announced without a path. "OK" passes, "Not Ready" or no answer fails, a server error is a warning. When LiveKit can't be found there (it has its own address), the row says it wasn't checked instead of raising an alarm. Only GET requests, without your token.
- **Sessions and push** on a user's page: every connection Synapse has on record for the account (IP address, when, which app, per device) and the account's pushers (which app or e-mail address gets its notifications, and through which push gateway).
- **E-mail and phone** on a user's page: the e-mail addresses and phone numbers linked to the account, and a dialog to add or remove them. Synapse replaces the whole list when it is saved, so SynapseCtrl shows how many entries are added and removed first, and applies your change to the list as the server has it at that moment.
- **Find an account by e-mail address.** Type a complete e-mail address in the Users search and a row appears above the list that looks up which account it belongs to and opens it. (The normal search only looks at user IDs and display names.)
- **Redact all messages** of a user (not offered for the account you're signed in with): removes the content of everything the account sent, in every room it is in or banned from. You type the user ID to confirm, can give a reason (visible to everyone) and choose how many events per room are gone through (1000, newest first, unless you change it). The server does the work in the background; a dialog follows it and reports events that couldn't be redacted.
- **Message browser** for a room: the room's events straight from the server's database, newest first, 30 at a time, with sender and time; tap one to see all of it and copy it. Encrypted messages show as encrypted: the server can't read them either.
- **SynapseCtrl in Dutch.** The whole app is translated into Dutch (Belgian Dutch, informal). Android picks the language from your phone's settings: Dutch on a phone set to Dutch, English otherwise. There is no language switch in the app; Android 13 and later also let you choose a language per app in the phone's settings. Technical names (Synapse, LiveKit, ntfy, file paths, Matrix IDs) stay as they are, and so do error texts that come from your server.
- **Back up and restore settings** (profile picture › Settings). **Save settings to a file** writes a small file with SynapseCtrl's own settings: Hide in screenshots, Hide in recent apps, which events the background checks notify about, how often they run and the media limit. Servers, access tokens, the app PIN, trusted certificates and the ntfy push address are never written to it. **Restore settings from a file** first shows what the file would set and asks. Restoring never touches the app lock, never turns background checks or push on or off (that changes how your tokens are encrypted), never turns a privacy switch off, and never adds a server or a certificate.
- **Purge old history** of a room: deletes messages older than a number of days from the server's database to free space. Messages that came from other servers are deleted; your own users' messages only when you switch that on. The room's settings, memberships and newest message always stay. You type the room ID to confirm, and a dialog follows the server's progress.

## v1.2.0 (2026-11-21)

Built and checked by CI only: nothing in this release had been run on a phone when it was published. It is the first release built with the new build tools (Android Gradle Plugin 9, stricter code shrinking), so update over your installed version and check that your data and settings are still there.

### Changed

- **The Overview no longer adds up all media on every refresh.** To show the local media size, SynapseCtrl goes through the media statistics of every user, 100 users per request, one request after the other (up to 20). It did that every time the Overview refreshed, which is every time you open the tab more than 30 seconds after the last refresh. The total is now kept (in memory only) for 10 minutes and reused when the Overview refreshes by itself. **Pulling down to refresh always adds it up again**, and so does the first refresh after you delete media in the app (one file under a user's media, or **Delete old local media**). What you might notice: for up to 10 minutes the media size can be older than the other numbers, for example right after someone uploaded a lot; pull down to get the current one.
- **The rest of the Overview shows without waiting for the media size.** Users, rooms, reports, the attention list and the health checks now appear as soon as they are loaded; the media size is added up after that. While it is being added up, the tile shows the last known size (or a dash the first time) and the refresh indicator at the top keeps turning. A **Media over your limit** line in the attention list can therefore appear a moment after the rest. If adding it up fails, the tile shows a dash, as before.
- **One request fewer for the user numbers.** The Overview asked your server three times for user counts (all, active, deactivated). It now asks for active and deactivated and adds them up for the total, which is the same number: Synapse counts both kinds when no filter is given. If one of the two requests fails, the total is left out for that refresh (it used to be asked on its own), and the "new users" notification simply compares again at the next check.
- **Background checks skip the media statistics when they don't need them.** A background check only reads the media size for the **Media storage over a limit** notification. With that notification off (the default), the check no longer asks for the media statistics at all; with it on, it reuses a total that is less than 10 minutes old, for example from the Overview you just looked at, and so the notification can come up to 10 minutes later than before in that case.
- **Newer build tools and libraries; nothing changes in how SynapseCtrl looks or works.** SynapseCtrl is now built with the current stable toolchain: Android Gradle Plugin 8.11.1 → 9.5.1, Gradle 8.14.3 → 9.9.2, Kotlin 2.2.0 → 2.4.21 (with its Compose and serialization compiler plugins) and Compose BOM 2025.07.00 → 2026.09.00 (Compose 1.12.1, Material 3 1.4.0). The libraries move to the versions the newest Ctrl apps use: AndroidX Activity 1.10.1 → 1.13.0, Core 1.16.0 → 1.18.0, Lifecycle 2.9.1 → 2.10.0, Navigation 2.9.1 → 2.9.8, DataStore 1.1.7 → 1.3.0, kotlinx.serialization 1.9.0 → 1.13.0 and kotlinx.coroutines 1.10.2 → 1.11.0. No library was added, and CI's Google-free dependency audit still passes.
- **Compiled against the Android 17 SDK.** Compose 1.12 needs `compileSdk` 37, so that goes from 36 to 37. The Android versions SynapseCtrl runs on and targets are unchanged (`minSdk` 35, `targetSdk` 36), so Android treats the app exactly as before.
- **The app is shrunk by a newer R8.** Android Gradle Plugin 9 compiles Kotlin itself (the separate Kotlin Android plugin is gone from the build files) and brings the R8 version that understands Kotlin 2.4. SynapseCtrl's own keep rules are unchanged.
- The checksums Gradle verifies every download against (`gradle/verification-metadata.xml`) were regenerated on CI for the new versions. Built and checked by CI only; not yet run on a phone.

### Fixed

- **Fixed before release: the app would have crashed at start with the new build tools.** SynapseCtrl uses WorkManager for its background work, and WorkManager stores its jobs in a small Room database that is created by name when the app starts. The shrinker (R8) in Android Gradle Plugin 9 is stricter than the old one and removed the constructor that needs, which makes the app stop right at launch (a known problem with Room 2.6.1 under Android Gradle Plugin 9). A keep rule for that constructor, the same one newer Room versions ship themselves, is now in `app/proguard-rules.pro`. No released version was affected: the new build tools have not been in any release. Built by CI only; not yet run on a phone.
- **The refresh indicator can't get stuck any more.** Leaving the Overview while it was still loading could leave it marked as loading until the next refresh finished. It is now always cleared when a load ends or is interrupted.

## Project updates (2026-11-20)

Nothing in the app changed.

### Security

- **The signing key is no longer available to the build.** Until now the release key and its passwords were present in the same GitHub Actions job that runs Gradle, so any build plugin or library could in principle have read them. The build job now has no secrets at all and produces an unsigned APK. A separate job, which checks out no code and runs no Gradle, signs it with `apksigner`, and then refuses to hand it on unless it is signed by exactly the certificate published in SECURITY.md and README.md and its contents are identical to what the build job made. Releases are signed with the same key and the same signature scheme (v2) as before, so updates install as usual. No release has been published by the new workflow yet; the next version will be the first.
- **The build checks that the published fingerprint and the enforced one are the same.** If `build.yml`, SECURITY.md and README.md ever disagree about the signing certificate, the build fails.

### Project

- **Builds no longer use a saved Gradle cache.** Ctrl apps had builds fail with "Dependency verification failed ... from repository Gradle Central Plugin Repository", and keep failing until the caches were deleted by hand. The cause, reproduced on DateCtrl: with a restored cache Gradle still fetches the plugin files from Maven Central on every build; when Maven Central can't be reached for a moment, Gradle takes the Kotlin plugin "marker" file from the Gradle plugin portal instead, the portal serves a different file under the same name, verification rightly refuses it, and the failed run then saved that state as the cache for every later run. Now every build and the weekly code scan start from a clean Gradle home, and the plugin portal is no longer a plugin source at all (`settings.gradle.kts`): everything the build needs is on Maven Central and Google's Maven. Dependency verification itself is unchanged and as strict as before.

## v1.1.0 (2026-11-20)

Built and checked by CI only: nothing in this release had been run on a phone when it was published.

### Removed

- **The home screen widget is gone.** The **Synapse status** widget (online or not, active users, open reports) has been removed from SynapseCtrl, together with its background refresh. If you had it on your home screen, Android shows an empty or "can't load" box in its place after updating: remove that box by hand. SynapseCtrl cancels the widget's leftover refresh job and deletes the small file with the widget's last numbers the next time you open the app. App shortcuts (New invite, Users, Reports) are unchanged. SynapseCtrl never had a Quick Settings tile.

### Changed

- **Faster start with a pinned certificate.** The connection setup for a server with a self-signed certificate you trusted used to be made on the main thread while the app was starting, twice. It's now made once, on a background thread, with the first request.
- **One connection object at start instead of two.** SynapseCtrl made the active server's connection twice while starting; it now makes it once.
- **Smoother scrolling in Users and Rooms.** The rows of both lists are now marked as one kind of row, so rows that scroll off the screen are reused for the ones scrolling in, next to the count line and the **Load more** button.

### Fixed

- **Profile pictures come back after a network hiccup.** When loading a picture failed because the network dropped, SynapseCtrl remembered the picture as missing and showed the letter instead until the app was restarted. A network failure is no longer remembered: the picture is tried again the next time it's shown. Pictures the server really doesn't have are still remembered as missing.

### Privacy

- The widget's saved numbers (active users and open reports) are no longer kept on the phone, since the widget is gone.

## v1.0.10 (2026-11-20)

### Changed

- **The app icon matches the other Ctrl apps.** SynapseCtrl's icon was drawn in a darker set of greys than the rest of the series: a near-black background, a darker keycap and a front bar you could hardly see. It now uses the same background, keycap and bar colours as the other Ctrl apps, so the icons look like one family side by side. The purple symbol and the themed (monochrome) icon are unchanged.

## v1.0.9 (2026-11-13)

A security and privacy check-up. Nothing works differently, and you don't need to sign in again.

### Security

- **Access tokens in the phone's security chip:** on phones with StrongBox (a separate security chip, such as Titan M on Pixels), the key that encrypts your saved access tokens is now made inside that chip, and falls back to the normal Android Keystore on phones without one. This applies when the key is first made (a new install, or after clearing SynapseCtrl's data); tokens you already saved stay encrypted with the key they have.
- **Health checks read only small answers:** the Overview's health checks follow your server's well-known files to other hosts, so they now read at most 256 KB from each answer (real ones are a few hundred bytes). A host that sends more simply fails that check instead of filling the phone's memory.
- **Checked build tools:** the build now also checks the SHA-256 of the Gradle build tool it downloads, as it already did for every library.

### Performance

- Reading a saved token no longer opens the Android Keystore again each time, so background checks and the widget connect a little sooner.

## v1.0.8 (2026-11-12)

### Changed

- **Slightly smaller download.** The bundled JetBrains Mono font (numbers, readouts and labels) no longer carries the Cyrillic, Vietnamese and maths-alphabet letters SynapseCtrl never shows itself, which takes about 0.1 MB off the download and 0.2 MB off the installed app. Everything looks the same; if a name or a log line shown in that font uses one of those scripts, those letters are now drawn with your phone's own font.

## v1.0.7 (2026-11-05)

### Changed

- **Needs Android 15 or newer.** Android 13 no longer gets security updates and Android 14's updates end in early 2027, so SynapseCtrl now supports Android 15, 16 and 17 only. On older phones the version you have keeps working, but it won't get updates.

## v1.0.6 (2026-11-05)

### Security

- **No overlays over the lock screen and confirmations.** While the lock screen, a PIN screen (turning the lock on or off, changing the PIN, confirming a change), "Forgot PIN?", or a confirmation to delete, deactivate, erase, quarantine, kick, remove a server, trust a certificate or use plain HTTP is showing, other apps' floating windows (chat heads, screen overlays) are hidden over it, so no other app can cover it or put a fake screen on top. They come back as soon as that screen closes, and work as usual everywhere else. This uses a new install-time permission, "Hide other apps' floating windows"; there's no prompt.
- **PIN screens hidden from accessibility misuse.** On Android 14 and newer, the lock and PIN screens are marked as sensitive, so apps that misuse accessibility access can't read them. Real accessibility tools such as TalkBack keep working. Sign-in and other password fields aren't marked, so password managers keep filling them as before.
- **Stricter intents from other apps.** On Android 16 and newer, other apps can only reach SynapseCtrl with an intent that really matches it. The New invite, Users and Reports launcher shortcuts are now declared for this, so they, the widget and push through ntfy work as before.

### Project

- **Checked dependencies:** every library and build plugin download is now checked against sha256 checksums in `gradle/verification-metadata.xml`, and the build fails on any mismatch.
- **Verifiable releases:** this is the first release with a signed build provenance attestation and the APK's SHA-256 in the release notes. The README explains how to check a download with `gh attestation verify`.

## Project updates (2026-11-05, workflows)

No new app version: these changes are to the repo's build workflows only.

### Project

- **Signed build provenance:** every release from now on gets a GitHub build attestation, signed through Sigstore, which proves the APK was built by this repo's workflow from the tagged commit. You can check it with `gh attestation verify`.
- **Checksum in the release notes:** the release notes end with the APK's SHA-256, so you can check a download before installing it.
- **Least privilege:** the workflows get no permissions by default; each job gets only what it needs, and only the release job can write to the repo.
- **Workflow security check:** a new zizmor check runs on every change to the workflows, offline, with zizmor pinned by its checksum.
- **Code scanning:** CodeQL analyses the app's code every week and on every app change; results go to the repo's Security tab.
- **Dependency checksums:** a new manually started workflow writes `gradle/verification-metadata.xml`, the list of sha256 checksums every library and build plugin download is checked against.

## v1.0.5 (2026-11-05)

Plain HTTP now works over Tailscale, and one more way for other apps to reach into SynapseCtrl is gone.

### Fixed

- **Plain HTTP works over Tailscale.** SynapseCtrl only allows `http://` for addresses on your own network, but it didn't count Tailscale as your own network, so an address like `http://100.101.102.103:8008` or `http://synapse.tailnet-name.ts.net:8008` was refused even with **Allow plain HTTP for this server** switched on. Tailscale's `100.64.0.0/10` addresses and MagicDNS names ending in `.ts.net` now count as private (its `fd7a:115c:a1e0::` IPv6 addresses already did). You still have to switch on plain HTTP for that server and confirm the warning, and a name must still point to a private or Tailscale address, or SynapseCtrl won't send your access token to it.

### Security

- **Other apps can no longer poke SynapseCtrl's start-up profile.** An AndroidX library added a receiver that lets other apps ask SynapseCtrl to write or skip its start-up performance profile. SynapseCtrl doesn't need it, so it's removed. Nothing changes in how the app works.

### Project

- **README:** says plain HTTP works over Tailscale, with example addresses.

## v1.0.4 (2026-11-05)

A privacy and security tidy-up: one permission less, and the README lists everything SynapseCtrl can do on your phone.

### Privacy

- **One permission removed: "run foreground service" (`FOREGROUND_SERVICE`).** Android's background-work library added it, but SynapseCtrl never runs a foreground service: background checks and widget refreshes run as normal background work. Nothing changes in how notifications or the widget work.
- **Less logging in release builds.** Verbose, debug and info log lines, from SynapseCtrl and from the libraries it uses, are now stripped from the app when it's built, so nothing can end up in the phone's system log through them.

### Project

- **Build checks:** every build now checks that the Gradle wrapper in the repo is the official one before running it, and the repo ignores `google-services.json` so a Google services file can never be committed by accident.
- **README:** the permissions table lists every permission the app has, with the reason for each; the privacy section lists every site the app can open (only as links you tap) and says it's Google-free; the Ctrl series list now has all 28 apps, including CallCtrl, ClockCtrl, ContactCtrl, DateCtrl, LijnCtrl, MailCtrl, RecorderCtrl and TextCtrl.

## Project updates (2026-11-03)

### Project

- **README:** the Ctrl series list now includes SumCtrl, the offline calculator, unit converter and work-tools app. No app changes, so no new version.

## v1.0.3 (2026-11-03)

The chat app in the Ctrl series is now called ChatCtrl.

### Changed

- **Invites and new accounts name ChatCtrl.** The invite you share from **Invites** now tells people to install **ChatCtrl** (from the CtrlF-Droid repo) or any Matrix app, like Element X. The note on **Create user** says the new user can sign in with ChatCtrl or any Matrix app.
- **README:** the Ctrl series list links to ChatCtrl.

## v1.0.2 (2026-11-02)

A security and trust update: more screens with secrets are kept out of screenshots, and avatars are checked more strictly.

### Security

- **The Invites page is always kept out of screenshots, screen recordings and the recent apps view**, like server setup. Registration tokens let anyone sign up on your server, so they're treated like passwords now, whatever **Hide in screenshots** says.
- **The new-token dialog and the reset-password dialog** are kept out of screenshots themselves too. A dialog is its own window on Android, so before this it could still show up in a screenshot when **Hide in screenshots** was off, even though the screen behind it was hidden.
- **Your ntfy push address** is kept out of screenshots while it's shown on the Notifications page, because it holds the secret ntfy topic.
- **Avatars are checked before they're loaded.** A profile picture address is set by other people (in their profile or a room), so SynapseCtrl now only loads one that is a normal Matrix media address. Anything else shows the first letter instead and never goes into a request to your server.

### Project

- New **SECURITY.md**: how to report a security problem privately, which versions get fixes, and the release key's fingerprint.
- New **issue forms** for bug reports and feature requests, and a link to report security problems privately instead of in a public issue.
- README: a step-by-step **Install** for people new to GitHub, a new **Is it safe to install?** section (open source, built by GitHub Actions, signed with the same key, every permission explained), **How SynapseCtrl is made**, and the full Ctrl series list. The empty Screenshots section is gone.

## v1.0.1 (2026-11-02)

### Fixed

- **Bottom sheets no longer show a stray grey box or outline.** The sheet's 1dp border was drawn before the sheet slid into place, so a grey rectangle cut across the open sheet partway down and a rounded outline was left near the top of the screen. Sheets are now a plain surface with 26dp top corners and no outline; the dimmed background already sets them apart. Their colour, corners, drag handle, dim and behaviour are unchanged.
- Applies to every bottom sheet in the app, as they all use the same shared sheet. Dialogs keep their thin border, which was already drawn in the right place. The sliding tab bar was checked and is not affected.

## v1.0.0 (2026-10-31)

SynapseCtrl is complete: everything for running your Synapse server from your phone is in this release.

### New

- **Overview** with Synapse's version, active and deactivated users, rooms, local media size and open event reports. Pull down to load it again.
- **Needs attention** on the Overview: failing health checks, open event reports, servers your server can't reach over federation, local media over your limit and database updates that are running. Tap one to go where it can be handled.
- **Health checks** on the Overview, each with a pass, warning or fail and a short explanation: Synapse reachable at the saved address, client discovery (`/.well-known/matrix/client` points apps to your server, and that address answers), server discovery (`/.well-known/matrix/server`), federation (your server answers other servers with its version and signing keys) and calls (client discovery lists your MatrixRTC LiveKit service, and it answers). No outside tester service is used.
- **Users tab:** every local user with avatar, display name, user ID, admin, deactivated, shadow-banned, suspended and guest tags, when they were last seen and how many devices they have. Search by name or user ID, filter by active, admins, deactivated, guests or all, and sort by user ID, display name, newest or last seen.
- **Create users** with a username, optional display name, a generated strong password you can copy, and an optional server admin switch. SynapseCtrl checks the user ID is free first, so an existing account is never changed by mistake.
- **User page** with the profile (change the display name), creation and last-seen time, devices with their IP address and last activity (log out one, or all), joined rooms, uploaded media and the rate limit.
- **User actions:** reset the password (generated and copyable, optionally logging out every device), make or remove server admin, shadow-ban, suspend, lock the account, override or remove the rate limit, quarantine all their media, send them a server notice, and **deactivate** with an optional GDPR erase, explained, which needs the user ID typed in. Admin, ban, lock and deactivate aren't offered for the account SynapseCtrl is signed in with.
- **Invites:** registration tokens with how often they were used, how many sign-ups are left and when they expire. Create one (random or your own, with a number of uses and 1 day to no end date), change or delete it, and **share a ready-made invite** through Android's share sheet with your server, the token and how to sign up with ChatCtrl or another Matrix app.
- **Rooms tab:** every room with avatar, name or address, local and total members, creator and state events, and space, public, encrypted and local-only tags. Search by name, address or room ID, filter by public, empty or large, and sort by name, members, local members or state events.
- **Room page** with members (local people first, tap to open their account), access and encryption settings, room version, history visibility, state events by type and whether it's blocked.
- **Room actions:** make your admin account room admin, remove a local member (as a kick by your admin account), block or unblock the room, quarantine all its media, and **delete the room** with block, purge and an optional new room for the members, after typing the room ID. The deletion runs on the server and SynapseCtrl shows its progress, who was removed and any new room.
- **Media tab:** local media size and file count, the users with the most media, and each user's files (newest or biggest first) with quarantine, lift quarantine, copy the mxc:// address and delete.
- **Media cleanup:** purge cached remote media not used for 7 to 365 days, and delete local media not opened for 30 days to 2 years and larger than a size you pick, keeping profile pictures if you want. SynapseCtrl counts the matching files first and asks you to type the server name before deleting.
- **Quarantine by media ID**, for a single file from your server or another.
- **Reports tab:** event reports newest first with the reason, score, reporter, sender and room. A report's page shows the reported message itself, and leads to the sender's account and the room; delete a report once it's handled. **User reports** (about a whole account) are listed too.
- **Federation:** every server yours talks to, failing ones first with since when and the next try, and **reset connection** for a server that's back up.
- **Server notices** to one user or to every active local user (bots and bridges left out), with a preview and a confirmation.
- **More notifications** from the background checks: new event reports, failing health checks, new users and local media over a limit you set (5 GB to 1 TB), next to unreachable servers.
- **Home screen widget** with your server online or not, active users and open reports. Tap it to open SynapseCtrl. It refreshes with the Overview, every background check and every 30 minutes, and only says SynapseCtrl is locked while your token is sealed by the app lock.
- **App shortcuts** (long-press the icon): New invite, Users and Reports. With the app lock on, SynapseCtrl asks for your PIN first, and a new invite is only created after you confirm it.
- **Avatars** of users and rooms, loaded as small thumbnails from your own server.

### Changed

- The Users, Rooms, Media and Reports tabs now do what they said was coming.
- The Overview shows the server's numbers, attention list and health checks instead of the sign-in summary, and has rows for Invites, Federation and Server notices.

### Security

- The health checks use their own connection that only makes HTTPS GET requests without your token, to your server name and the hosts its discovery files name.
- Read-only mode hides every new action and refuses to send any change, and every change asks for a confirmation (and your fingerprint or app PIN with **Confirm changes** on).

## v0.1.0 (2026-10-31)

The first version of SynapseCtrl, an admin app for your self-hosted Matrix Synapse server. This version signs in to your server and checks admin access; users, invites, rooms, media, reports and health checks follow in the next versions.

### New

- **Add a Synapse server** by its address, like `https://chat.example.org`, or its LAN address, like `http://192.168.1.20:8008`, when the admin API is only open on your network. SynapseCtrl only ever connects to the exact address you enter.
- **Sign in with a password:** SynapseCtrl logs in once with your admin account's username and password and gets its own access token and device, "SynapseCtrl (Android)". The password is never stored.
- **Or paste an access token** from an admin account, for servers without password login (such as servers using Matrix Authentication Service).
- **Admin check:** before a server is saved, SynapseCtrl confirms the account is a server admin and shows the Synapse version. A token SynapseCtrl just made for an account that isn't an admin is logged out again straight away.
- **Clear errors** for the usual setup problems: wrong password, an account that isn't a server admin, an address where `/_synapse/admin` is blocked (with a hint to use the LAN address), a server without password login, and certificate problems.
- **Self-signed certificates:** SynapseCtrl shows the certificate's SHA-256 fingerprint to compare, and from then on accepts only that exact certificate for that server.
- **Plain HTTP** for addresses on your own network, switched on per server after a warning.
- **Overview** with the server's name, Synapse version, whether it's online, the signed-in admin account and how the connection is secured. Pull down to check again.
- **Tabs for Users, Rooms, Media and Reports**, which show what's coming in the next versions.
- **Several servers:** add, switch between, change or remove servers under Servers on the You page (tap your profile picture).
- **Removing a server logs its access token out** on the server first, so it stops working there too. If the server can't be reached, SynapseCtrl still removes it and tells you to log the token out on the server yourself.
- **Read-only mode** per server: hides every action that changes something, and refuses to send one.
- **App lock** with its own PIN and optional fingerprint unlock. While SynapseCtrl is locked, your access tokens stay encrypted with the PIN and nothing is sent to your servers. **Confirm changes** can ask for the PIN or fingerprint before anything changes on a server.
- **Hide in screenshots** and **Hide in recent apps**, two separate switches, both off by default.
- **Notifications** from SynapseCtrl's own background checks (off until you turn them on): a server that can't be reached twice in a row, or whose access token or admin access stopped working.
- **Push through ntfy** (optional): anything that posts to SynapseCtrl's push address makes it check your servers right away.
- **Signal look**, dark or light with your phone's theme.

### Security

- Access tokens are stored encrypted with a key in the Android Keystore, are only sent in the Authorization header (never in a web address), and are never written to the system log.
- SynapseCtrl never follows redirects and refuses to connect anywhere except your saved servers.
- Google-free and telemetry-free: no Google libraries or services, no analytics, crash reporting or tracking.
