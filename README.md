# PDF Unlocker

A small Android app that removes the password from a PDF you already know the password for.

Pick a PDF; if it's locked, type its password; save an unlocked copy wherever you like. The original file
is never changed.

You can also start from another app: tap a PDF attachment in your email (or a file in Files or Drive) and
choose **PDF Unlocker** under "Open with", or share the PDF to it. The copy can be saved anywhere Android's
"Save as" screen offers, including Google Drive: the Drive app does the upload, not this app.

**Private by design:** everything happens on your phone. The app has no internet permission, so your
document and its password can't leave the device.

**Saved passwords:** a password that unlocks a PDF is saved, encrypted with a key kept in your phone's
security hardware, so the next PDF with the same password (say, next month's bank statement) opens by
itself: the app tries saved passwords first and only asks if none works. Other apps can't read them, they're
never backed up, and uninstalling the app deletes them. **Saved passwords** on the start screen lists them
(after your phone's fingerprint, face or PIN), where you can add or delete one.

**One limit:** some PDFs open without a password but block printing or editing (an "owner password"). The
copy of such a file still opens anywhere, but keeps those blocks: Android only removes them when the file
was opened with that owner password.

Android 15 or newer (it uses Android's own PDF engine, no extra libraries).

## Install

Download `pdf-unlocker-<version>.apk` from the
[latest release](https://github.com/code-next-door/local-pdf-unlocker/releases/latest) on your phone and open
it. Android asks you to allow installs from your browser or file manager once. Each release lists the file's
SHA-256 checksum.

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
