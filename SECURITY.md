# Security policy

Thank you for helping keep SynapseCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/SynapseCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of SynapseCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

SynapseCtrl holds a server admin access token, which can do anything on your Synapse server. So these matter most:

- Your admin access token, passwords or other data leaking to anyone other than your own Synapse server (for example through a redirect, a federation or discovery lookup, or an avatar address)
- Ways around the app lock or **Confirm changes**, or screens with tokens, registration tokens or passwords showing up in screenshots when they shouldn't
- A change on your server (deactivating a user, deleting a room, deleting media) happening without the confirmation SynapseCtrl promises
- Another app on the phone being able to make SynapseCtrl do something you didn't ask for (through its shortcuts, widget, notifications or the ntfy push receiver)
- Anything that lets someone run code or read files they shouldn't

Problems in Synapse itself should go to [the Synapse project](https://github.com/element-hq/synapse/security).

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
A3:28:7A:58:29:FD:9F:19:C0:BA:DB:48:5F:65:39:CF:0A:90:8E:B4:A7:87:C0:06:04:46:D0:60:4F:74:8C:E2
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.
