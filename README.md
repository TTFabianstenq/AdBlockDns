# AdBlock DNS

Android / Android TV app that asks for **VPN permission** and sets device DNS to AdGuard:

- Primary: `94.140.14.14`
- Secondary: `94.140.15.15`

This is a local VpnService DNS override. It is **not** a YouTube ad-skipper. AdGuard DNS blocks many ad/tracker **domains**. Video ads served from the same domains as the video (YouTube) usually still play.

## Build

GitHub Actions builds a debug APK on every push to `main`.

Download: **Actions → Build APK → Artifacts → AdBlockDns-debug**

## Install

Sideload `app-debug.apk`. Enable unknown sources. Open the app → Start DNS VPN → accept the Android VPN dialog.
