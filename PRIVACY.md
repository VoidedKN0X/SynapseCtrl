# Privacy

SynapseCtrl is built so that your server admin access stays between your phone and your own Synapse server.

- **Only your servers.** SynapseCtrl talks only to the Synapse servers you add, at the exact address you entered. Every other destination is blocked inside the app. There are no third-party SDKs, ads or analytics making network calls, and the app never contacts Google.
- **Telemetry-free.** No accounts, analytics, usage statistics, crash reporting or tracking of any kind.
- **Your password is never stored.** When you sign in with a password, SynapseCtrl uses it once to log in and gets its own access token. Only that token is saved.
- **Access tokens are encrypted.** Tokens are stored encrypted with a key held in the Android Keystore, which never leaves the phone. With the app lock on, they're also sealed with a key that only exists while SynapseCtrl is unlocked, unless you turn on background checks (which have to reach your servers while SynapseCtrl is locked). Tokens are only ever sent in the Authorization header, never in a web address, and never written to the system log.
- **Logged out when removed.** Removing a server logs its access token out on the server first, so it stops working there too.
- **App lock.** The app PIN is never stored, only used to unlock a key; fingerprint unlock uses a key in the phone's secure hardware. While SynapseCtrl is locked, nothing is sent to your servers except the background checks you turned on.
- **Notifications.** Background checks (off until you turn them on) only read from your own Synapse servers; they never change anything. What they last saw (which problems were notified) is kept in SynapseCtrl's private storage and never backed up. On the lock screen, notifications only say that something needs a look, without server names or details.
- **Push through ntfy (optional).** Off until you turn it on. SynapseCtrl registers only with the ntfy app on your phone, which gives it a push address on your own ntfy server; SynapseCtrl never connects to that server itself. A push only starts the usual background check (at most once a minute); its content is never shown, stored or used. Only broadcasts carrying the secret token of SynapseCtrl's registration are accepted, and turning push off removes the registration.
- **Nothing is backed up.** SynapseCtrl's data is excluded from Android backups and from moving to a new phone.
- **Secure connections.** HTTPS by default. Plain HTTP is only possible for addresses on your own network, per server, after a warning. A self-signed certificate is accepted only after you confirm its SHA-256 fingerprint, and only for that server.
- **Screenshots and recent apps.** **Hide in screenshots** and **Hide in recent apps** (Settings › Privacy and security, both off by default) blank SynapseCtrl in screenshots and screen recordings, and in the recent apps view. Server setup, the lock screen and the PIN screens are always kept out of screenshots, and with the app lock on the recent apps view is always blank.
- **Links.** The Support, Report a bug, Source code and Licence rows open in your browser only when you tap them.

SynapseCtrl uses network access, and the standard Android permissions for scheduled background work (so checks can run after a restart). The only permission it asks you for is showing notifications, when you turn on background checks.
