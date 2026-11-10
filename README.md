# Handshake

Shake your Android phone, even with the screen off, and a gantry gripper drops in a card with your LinkedIn and WhatsApp QR codes. Built for CoRL networking.

## Install

1. On your phone, open this repo's **Releases** page and download `handshake.apk` from the latest release.
2. Open the file. If Android asks, allow your browser or Files app to install unknown apps.
3. Open **Handshake**, fill in your LinkedIn and WhatsApp number, then tap **Allow** on each item under *Phone setup*.
4. Switch **Handshake armed** on.

## Nothing Phone (2a) notes

- Battery: *Settings → Apps → Handshake → Battery → Unrestricted* (the in-app button opens this).
- Lock screen: grant *Display over other apps* and *Full-screen alerts* so a shake can wake the screen.
- The armed notification has **Show now** and **Disarm** buttons.

## How the shake works

The app runs a foreground service holding the accelerometer at ~50 Hz. A low-pass filter removes gravity, and a trigger needs 4 threshold crossings within 2 s (two natural back-and-forth shakes; default trigger about 0.9 g). The proximity sensor blocks triggers while the phone is in a pocket. Tune the threshold with the live IMU trace in the app.

## Build

Every push to `main` builds a signed APK with GitHub Actions and publishes it as a release. Each build is signed with a fresh key, so uninstall the old version before installing a newer build.
