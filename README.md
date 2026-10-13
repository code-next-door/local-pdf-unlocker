# PDF Unlocker

A small Android app that removes the password from a PDF you already know the password for.

Pick a PDF; if it's locked, type its password; save an unlocked copy wherever you like. The original file
is never changed.

**Private by design:** everything happens on your phone. The app has no internet permission, so your
document and its password can't leave the device.

Android 15 or newer (it uses Android's own PDF engine, no extra libraries).

> Work in progress: no release yet.

## Build from source

Requirements: JDK 17 and the Android SDK. Android Studio installs both.

```bash
./gradlew assembleDebug
```

## License

Copyright © 2026 code-next-door.

PDF Unlocker is free software under the [GNU General Public License v3.0](LICENSE): you may use, study,
share and change it. If you publish a changed version, it must stay under the GPL with its full source code,
and keep this copyright notice.
