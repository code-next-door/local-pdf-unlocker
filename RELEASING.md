# Releasing

Releases are built and signed by GitHub Actions (`.github/workflows/release.yml`) when a version tag is
pushed. The APK and its SHA-256 checksum are attached to a GitHub Release, ready to share.

## One-time setup: the signing key

Android only installs an update if it's signed with the same key as the installed app. **Keep the key and
its password safe** (e.g. in a password manager): if the key is lost, friends have to uninstall the app to
install a new version. (The app keeps no data, so that costs them nothing but the hassle.)

1. Create the key (`keytool` comes with the JDK):

   ```bash
   keytool -genkeypair -v -keystore ~/pdf-unlocker-release.jks -alias pdfunlocker -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=PDF Unlocker"
   ```

2. Give it to GitHub as repository secrets (`gh` asks for the password when it's not piped in):

   ```bash
   base64 -i ~/pdf-unlocker-release.jks | gh secret set PDFUNLOCKER_KEYSTORE_BASE64
   gh secret set PDFUNLOCKER_KEYSTORE_PASSWORD
   gh secret set PDFUNLOCKER_KEY_ALIAS --body pdfunlocker
   ```

3. Back up `~/pdf-unlocker-release.jks` and the password somewhere safe. Never commit it (`.gitignore`
   blocks `*.jks`).

## Each release

1. All checks green on the release commit, on the emulator (`export ANDROID_SERIAL=emulator-5556`):

   ```bash
   ./gradlew --no-daemon testDebugUnitTest lintDebug connectedDebugAndroidTest
   ./gradlew --no-daemon :releasetest:connectedMinifiedAndroidTest
   ```

   The second one checks the shrunk (R8) build, as shipped, from outside the app.

2. README true: description, privacy note, minimum Android version, limits.

3. Tag and push; the version comes from the tag (`v1.2.3` → version 1.2.3, code 10203):

   ```bash
   git tag v0.1.0 && git push origin v0.1.0
   ```

4. The release appears under GitHub → Releases a few minutes later. Check that the workflow passed and the
   published checksum matches the APK. Friends install `pdf-unlocker-<version>.apk` (Android asks them to
   allow installs from their browser or file manager).

No upgrade check is needed while the app stores nothing: an update has no data to keep.
