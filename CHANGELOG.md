# Changelog

All notable changes to SynapseCtrl. Every release on GitHub, in Obtainium and in the CtrlF-Droid repo uses the entry for its version.

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
