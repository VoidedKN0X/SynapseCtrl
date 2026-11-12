# SynapseCtrl

**Administer your self-hosted Matrix Synapse server from your phone.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/SynapseCtrl?label=download)](https://github.com/VoidedKN0X/SynapseCtrl/releases/latest) ![Android 15+](https://img.shields.io/badge/Android-15%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

SynapseCtrl is an Android admin app for [Synapse](https://github.com/element-hq/synapse), the Matrix homeserver. It uses Synapse's admin API to manage users, invites, rooms, media, reports and server health. It's an admin tool, not a chat client: for chatting, use [ChatCtrl](https://github.com/VoidedKN0X/ChatCtrl) or any other Matrix app.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

## Features

- **Overview:** Synapse version, active and deactivated users, rooms, local media size and open event reports, an attention list (failing health checks, new reports, servers failing over federation, media over your limit, database updates running) and **health checks**: Synapse reachable, client and server discovery (`/.well-known/matrix/client` and `/server`), federation (version and signing keys where other servers connect) and **calls** (the MatrixRTC LiveKit service in client discovery, and that it answers). No outside tester service is used.
- **Users:** search, filter (active, admins, deactivated, guests) and sort, with avatars, admin and status tags, last seen and device count. **Create users** with a generated password. Each user has their profile, devices with IP and last seen (log out one or all), joined rooms, uploaded media and rate limit override, and actions: **reset password**, **server admin**, **shadow-ban**, **suspend**, **lock**, **quarantine all media**, **server notice** and **deactivate** (with the GDPR erase option explained).
- **Invites:** registration tokens with uses, remaining sign-ups and expiry; create, change and delete them, and **share a ready-made invite** through Android's share sheet, with the server, the token and how to sign up with ChatCtrl or another Matrix app.
- **Rooms:** search, filter (public, empty, large) and sort, with members, encryption, directory and federation tags. Each room shows its settings, state and members, and offers **make me room admin**, **block**, **quarantine all media**, **remove a local member** and **delete** (block, purge and moving members to a new room), with live progress from the server.
- **Media:** storage per user, each user's files (newest or biggest) with quarantine and delete, **purge cached remote media**, **delete old or large local media** with a count beforehand, and quarantine by media ID.
- **Reports:** event reports with the reported message, reporter, sender and room, and user reports; delete handled reports, and go straight to the sender's account.
- **Federation:** every server yours talks to, failing ones first, and **reset connection** for a server that's back.
- **Server notices** to one user or to every active local user, with a preview.
- **Notifications** from SynapseCtrl's own background checks: new event reports, failing health checks, unreachable servers, new users and media over a limit, with optional **push through ntfy**.
- **Home screen widget** (online or not, active users, open reports) and **app shortcuts**: New invite, Users, Reports.
- **Safe by design:** read-only mode per server, a confirmation for every change, the user or room ID typed in for deactivating, deleting a room and deleting old local media, app lock with **Confirm changes**, certificate pinning, **Hide in screenshots** and **Hide in recent apps**, and access tokens encrypted with the Android Keystore.
- **Several servers**, and sign-in with an admin password (SynapseCtrl gets its own token and never stores the password) or a pasted access token.

## Requirements

- Android 15 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- A Synapse server and an account that is a **server admin** (see [Create an admin account](#create-an-admin-account))
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google services, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).

## Install

1. On your phone, open the [Releases page](https://github.com/VoidedKN0X/SynapseCtrl/releases/latest). Scroll down to **Assets** and tap the file that ends in `.apk` (for example `SynapseCtrl-v1.0.5.apk`) to download it. The **Source code** (zip) and (tar.gz) files are not the app; you don't need them.
2. Open the downloaded file. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds SynapseCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/SynapseCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/SynapseCtrl` in Obtainium by hand.

## Getting started

1. Open SynapseCtrl and enter the address where Synapse answers, like `https://chat.example.org`, or its LAN address like `http://192.168.1.20:8008` (see [Keep the admin API off the internet](#keep-the-admin-api-off-the-internet)). Without `http://`, SynapseCtrl uses HTTPS. SynapseCtrl only ever connects to this exact address; there's no second or fallback address.
2. Sign in with an admin account: its username (`axel` or `@axel:example.org`) and password, or paste an access token.
   - With a password, SynapseCtrl logs in once and gets its own access token and device, **SynapseCtrl (Android)**. The password is never stored.
   - A pasted token is logged out when you remove the server from SynapseCtrl, so don't paste the token your chat app uses.
3. If Synapse uses a self-signed certificate, compare the fingerprint SynapseCtrl shows with your certificate's (on the server: `openssl x509 -in cert.pem -noout -fingerprint -sha256`), then tap **Trust**.
4. SynapseCtrl checks that the account is a server admin and shows the Synapse version. Choose whether to use **read-only mode**, and tap **Continue**.

Your servers, **Settings** and **About** are on the **You** page: tap your profile picture at the top right of any tab. **Remove server** logs SynapseCtrl's access token out on the server first, so it stops working there too.

## Is it safe to install?

- **Open source.** All of SynapseCtrl's code is in this repo, under the GPL-3.0 licence. Anyone can read it.
- **Built by GitHub Actions, not on a personal computer.** Every release on the Releases page is built from the code in this repo by [the build workflow](.github/workflows/build.yml), and you can see each build's log in the **Actions** tab.
- **Always signed with the same release key.** Android refuses to install an update that's signed with a different key, so nobody else can replace SynapseCtrl on your phone with their own version. The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key, and the build checks it too. The SHA-256 fingerprint of the signing certificate is:

  ```
  A3:28:7A:58:29:FD:9F:19:C0:BA:DB:48:5F:65:39:CF:0A:90:8E:B4:A7:87:C0:06:04:46:D0:60:4F:74:8C:E2
  ```

  Advanced users can compare it with [AppVerifier](https://github.com/soupslurpr/AppVerifier).

**Check a download.** Every release is built and published by GitHub Actions, never on a personal computer:

- The release notes end with the APK's **SHA-256** checksum. Compare it with `sha256sum SynapseCtrl-vX.Y.Z.apk` (or `Get-FileHash` on Windows).
- Each release APK has a signed **build provenance attestation**. With the [GitHub CLI](https://cli.github.com/), `gh attestation verify SynapseCtrl-vX.Y.Z.apk -R VoidedKN0X/SynapseCtrl` proves it was built by this repo's workflow from the tagged commit.
- The signing-certificate fingerprint above stays the same for every release.

**Permissions**

| Permission | What SynapseCtrl uses it for |
| --- | --- |
| Internet (`INTERNET`) | Talking to the Synapse servers you add, and reading their public discovery addresses for the health checks. Nothing else can be reached. |
| Fingerprint (`USE_BIOMETRIC`) | Unlocking the app lock with your fingerprint, if you turn that on. |
| Notifications (`POST_NOTIFICATIONS`) | Notifications from the background checks. Only asked for when you turn those on. |
| Network state (`ACCESS_NETWORK_STATE`) | Running background checks and widget refreshes only when the phone has a connection. It never reads the Wi-Fi name. |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | Scheduling the background checks and widget refreshes again after the phone restarts. Added by Android's scheduler for background work (WorkManager). |
| Keep awake (`WAKE_LOCK`) | Letting a background check finish while the screen is off. Added by WorkManager. |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | An internal permission only SynapseCtrl itself holds (added by AndroidX), so other apps can't send to its private receivers. |
| Hide other apps' floating windows over the PIN screen | While the lock or PIN screen, or a delete, deactivate, kick, remove-server, certificate or plain-HTTP confirmation, is showing, other apps' floating windows (chat heads, overlays) are hidden over it, so nothing can cover or imitate it. No prompt; overlays work as usual everywhere else. |

That's the full list. SynapseCtrl has no access to your files, contacts, location, camera or microphone, and doesn't run a foreground service.

What SynapseCtrl stores and sends is explained under [Privacy](#privacy).

## Create an admin account

SynapseCtrl only works with an account that is a Synapse **server admin**. On the server (for example in the Synapse container), either:

- **Create a new admin user:** `register_new_matrix_user -c /etc/matrix-synapse/homeserver.yaml --admin http://localhost:8008` (use the path to your `homeserver.yaml`; it needs `registration_shared_secret` set in it), or
- **Make an existing user admin** in Synapse's database: `UPDATE users SET admin = 1 WHERE name = '@axel:example.org';` (with `sqlite3` or `psql`, whichever database Synapse uses), or from another admin account with the admin API.

A separate admin account for SynapseCtrl, rather than your everyday chat account, keeps admin rights off the account you chat with.

## Keep the admin API off the internet

The admin API (`/_synapse/admin`) can do anything on your server, so it's best not reachable from the internet at all. Matrix clients and other servers never need it.

**In Nginx Proxy Manager:** edit the proxy host for your Synapse address, open the **Advanced** tab and add:

```nginx
location /_synapse/admin {
    deny all;
}
```

Then point SynapseCtrl straight at Synapse's LAN address, like `http://192.168.1.20:8008`, and use it at home or over your VPN. Plain HTTP is allowed for addresses on your own network, after you switch it on for that server. That includes [Tailscale](https://tailscale.com): its `100.x.y.y` addresses (100.64.0.0/10), its `fd7a:115c:a1e0::` IPv6 addresses and MagicDNS names ending in `.ts.net` count as your own network too, so `http://100.101.102.103:8008` or `http://synapse.tailnet-name.ts.net:8008` works. A name must still point to a private or Tailscale address, or SynapseCtrl won't send your token to it. Synapse must listen on that address: in `homeserver.yaml`, the HTTP listener with the `client` resource needs `bind_addresses` that include the LAN interface (for example `['0.0.0.0']`).

If SynapseCtrl says the server answers but not on `/_synapse/admin`, it's talking to an address where the admin API is blocked: use the LAN address instead. SynapseCtrl never switches addresses on its own.

## Matrix Authentication Service (MAS)

If your Synapse uses [Matrix Authentication Service](https://github.com/element-hq/matrix-authentication-service) for logins, it doesn't offer password login, and SynapseCtrl says so. Paste an access token that has admin rights instead. With MAS, such a token can be made with `mas-cli manage issue-compatibility-token <username> --yes-i-want-to-grant-synapse-admin-privileges`. With MAS, MAS manages some account actions (like passwords and creating accounts) itself, so Synapse may refuse them from the admin API; SynapseCtrl then shows Synapse's error.

## App lock

Turn it on: tap your profile picture › **Settings › Privacy and security › App lock**. It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, checked by the phone's secure hardware. The PIN is still needed after the phone restarts and at least every 72 hours.
- **Your admin access tokens are encrypted with the PIN while SynapseCtrl is locked.** The key only exists in memory while the app is unlocked, and nothing is sent to your servers until you unlock. The PIN itself is never stored.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts.
- **Forgot your PIN?** It can't be recovered, because it encrypts your tokens. The lock screen offers to remove your servers so you can add them again. The old tokens stay valid on the server until you log them out there.
- **Confirm changes** (optional) asks for your fingerprint or app PIN before anything that changes a server. One confirmation covers the next minute.
- **Background checks** for notifications have to reach your servers while SynapseCtrl is locked. While they're on, your tokens are only encrypted with the Android Keystore, as without the app lock.

**Hide in screenshots** and **Hide in recent apps** (in the same section, both off by default): the first keeps SynapseCtrl out of screenshots and screen recordings, the second shows a blank card in the recent-apps view. Server setup, the lock screen, the PIN screens, creating a user, resetting a password, the Invites page and your ntfy push address are always kept out of screenshots.

## Using SynapseCtrl safely

- **Test destructive actions on a throwaway test user and test room first**, never on your own account or real rooms. SynapseCtrl doesn't offer admin, ban, lock or deactivate actions for the account it's signed in with.
- **Deactivate**, **delete room** and **delete old local media** can't be undone: SynapseCtrl says what will happen and asks you to type the user ID, room ID or server name first. Everything else that changes something asks for a confirmation, except quarantining or releasing a single file from its menu on a user's media page, which happens at once and can be undone the same way.
- **Removing someone from a room** isn't in Synapse's admin API, so SynapseCtrl kicks them as your admin account. That only works when your account is in the room with enough power: use **Make me room admin** first.
- **Delete old local media** goes by when a file was last opened, not uploaded, and Synapse has no dry run: the count SynapseCtrl shows first is an estimate from each user's media list.
- **Server notices** need `server_notices` set up in `homeserver.yaml`; without it, Synapse's error is shown.
- **Registration tokens** only matter with `registration_requires_token: true` (and `enable_registration: true`) in `homeserver.yaml`.

## Notifications

Synapse can't send notifications itself, so SynapseCtrl checks your servers in the background: tap your profile picture › **Settings › Notifications** and turn on **Check in the background** (off until you do; every 15, 30, 60 or 120 minutes). Each kind can be switched on or off:

- **New event reports** (on): someone on your server reported a message
- **Health check failing** (on): client or server discovery, federation or calls stopped working
- **Server unreachable** (on): two checks in a row couldn't reach Synapse, or its token or admin access stopped working
- **New users** (on): someone signed up with a registration token, or an account was created
- **Media storage over a limit** (off): local media grew past the size you pick (5 GB to 1 TB)

Synapse has no built-in webhook or push for reports or new registrations; only a custom Synapse module could add one. SynapseCtrl's background checks are therefore the main source of notifications, and ntfy can wake them up early.

### Push through ntfy (optional)

Android runs background checks at most every 15 minutes, often later. With [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) (UnifiedPush) a push can start the check straight away. A push is only a wake-up call: SynapseCtrl ignores what it says, checks your servers as usual and notifies about what it finds, at most once a minute.

1. Install ntfy and connect it to your ntfy server.
2. In SynapseCtrl: tap your profile picture › **Settings › Notifications**, turn on **Check in the background** and **Check when ntfy pushes**, then tap **Push address** to copy it. Treat it like a password.
3. Let something on your network post to that address, like a cron job: `*/5 * * * * curl -fsS -d check 'https://ntfy.example.com/upAbC123?up=1' >/dev/null`

On ColorOS and other phones that close background apps, allow SynapseCtrl and ntfy to run in the background (the Battery rows on the same page open the right settings).

## Privacy

**Telemetry-free.** SynapseCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It only talks to the Synapse servers you add, and refuses to connect anywhere else. The health checks also read your server's public discovery endpoints (on your server name and the hosts its `.well-known` files name), over HTTPS, without your token. With push turned on, the ntfy app on your phone delivers wake-up calls to SynapseCtrl; SynapseCtrl itself never connects to your ntfy server. See [PRIVACY.md](PRIVACY.md).

- **Links:** the Support, Report a bug, Source code and Licence rows in About open github.com or gnu.org in your browser, and **Install ntfy** on the Notifications page (shown when ntfy is missing) opens ntfy's page on f-droid.org. The app itself never contacts these sites.
- **Google-free:** no Google Play services, Firebase or other Google code, and nothing is sent to Google.

## How SynapseCtrl is made

SynapseCtrl is a one-person project by VoidedKN0X (VKNXWorks). Much of the code is written with the help of AI (Claude, by Anthropic). I decide what the app does, review the changes and use the app myself. Every change is listed in the [changelog](CHANGELOG.md), and you can read every line of code here.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/SynapseCtrl.git
   cd SynapseCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

**Dependency verification.** Every library and build plugin download is checked against the sha256 checksums in `gradle/verification-metadata.xml`, and the build fails on any mismatch. When you change a dependency, a plugin or the Gradle version, run the **Dependency verification metadata** workflow (Actions tab, *Run workflow*), check the diff of the file it prints and commit it in the same push.

## Support the project

SynapseCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Starring the repo and [reporting bugs](https://github.com/VoidedKN0X/SynapseCtrl/issues/new/choose) helps too: pick **Bug report** or **Feature request** and fill in the form.

Found a security problem? Please don't open a public issue; see [SECURITY.md](SECURITY.md).

In the app, the **About** section at the bottom of the **You** page (tap your profile picture) has **Support SynapseCtrl** and **Report a bug** rows that open these pages.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same "Ctrl / Signal" design, dark or light with your phone.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CallCtrl](https://github.com/VoidedKN0X/CallCtrl) | Phone app: dialer, call history and in-call screen |
| [ChatCtrl](https://github.com/VoidedKN0X/ChatCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [ClockCtrl](https://github.com/VoidedKN0X/ClockCtrl) | Alarms, timers, stopwatch and world clock |
| [ContactCtrl](https://github.com/VoidedKN0X/ContactCtrl) | Contacts: browse, edit, import and export |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [DateCtrl](https://github.com/VoidedKN0X/DateCtrl) | Calendar for the calendars on your phone and your accounts |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [LijnCtrl](https://github.com/VoidedKN0X/LijnCtrl) | Live De Lijn bus and tram departures |
| [LumenCtrl](https://github.com/VoidedKN0X/LumenCtrl) | Private, offline photo and video editor |
| [MailCtrl](https://github.com/VoidedKN0X/MailCtrl) | Email for Gmail and Proton Mail |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [RecorderCtrl](https://github.com/VoidedKN0X/RecorderCtrl) | Private, offline voice recorder |
| [RideCtrl](https://github.com/VoidedKN0X/RideCtrl) | Offline maintenance, fuel and mod log for your vehicles |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [SumCtrl](https://github.com/VoidedKN0X/SumCtrl) | Offline calculator, unit converter and work tools |
| **SynapseCtrl** (this app) | Administer your Matrix Synapse server |
| [TextCtrl](https://github.com/VoidedKN0X/TextCtrl) | Private SMS and MMS messaging |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |
| [WrtCtrl](https://github.com/VoidedKN0X/WrtCtrl) | Manage your OpenWrt router |

## License

SynapseCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

The bundled fonts are licensed under the SIL Open Font License 1.1: [Space Grotesk](https://github.com/floriankarsten/space-grotesk) (see `FONT-LICENSE-SpaceGrotesk.txt`) and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) (see `FONT-LICENSE-JetBrainsMono.txt`).

SynapseCtrl is an independent app and isn't made by or affiliated with Element (New Vector Ltd) or The Matrix.org Foundation. Matrix and Synapse are trademarks of their owners.
