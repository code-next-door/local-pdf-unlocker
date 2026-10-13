# Testing PDF Unlocker

General rules live in [best-practices/](best-practices/README.md). This file covers only what's specific to
this app.

## The non-negotiables
- Test first. Tests written after the code get a mutation check (break the real code, watch a test fail).
- **No network, no real documents.** PDF fixtures are made up and their passwords are in the test.
- `./gradlew testDebugUnitTest` and `./gradlew lintDebug` green before every push; device tests too for
  changes to unlocking or the UI.
- The picked file is never written to. Tests check the original's bytes are unchanged.

## Layers
| Layer | Where | Command | What it covers |
|---|---|---|---|
| Pure logic | `app/src/test` | `./gradlew testDebugUnitTest` | Anything without Android's PDF APIs (e.g. the suggested file name) |
| Unlocking | `app/src/androidTest` | see below | `PdfRenderer` on a real device: locked / wrong password / right password / saved copy opens without one |
| UI flow | `app/src/androidTest` | see below | Pick → password → save, with the system pickers stubbed (Espresso-Intents); saved passwords; the list behind the real system prompt |
| Release build | `releasetest/` | `./gradlew :releasetest:connectedMinifiedAndroidTest` | The R8-shrunk app from outside (UI Automator): open with, unlock, remembered password |
| Lint | app + test code | `./gradlew lintDebug` | API-level crashes, string format errors |

`PdfRenderer`'s password loading and `write()` exist only on a real Android 15+ runtime, not in Robolectric,
so unlocking is tested on the emulator.

Device tests (emulator `Pixel_10_b`, API 37). One class: add
`-Pandroid.testInstrumentationRunnerArguments.class=io.github.codenextdoor.pdfunlocker.UnlockFlowTest`
(one class per run: a comma-separated list only ran the first).
```bash
export ANDROID_SERIAL=emulator-5556
./gradlew connectedDebugAndroidTest
```
This app keeps no data, so `connectedDebugAndroidTest` uninstalling it afterwards is fine.

## Saved passwords in tests
- The store is one file, `filesDir/saved-passwords.bin`. Every UI test that opens `user-locked.pdf` deletes it
  in `@Before`: a remembered `user123` would unlock the PDF by itself and skip the password steps.
  `releasetest` runs `pm clear` on the minified app instead.
- `SavedPasswordsFlowTest` drives the **real** system fingerprint/PIN prompt: it sets a throwaway PIN
  (`locksettings set-pin 1357`) on the test emulator and clears it afterwards (and first, in case a run died).
  On API 37 the prompt is a PIN pad (`cred_pin_pad`, digit buttons, `key_enter`). If a run is killed half-way,
  clear it by hand: `adb shell locksettings clear --old 1357`.
- `PasswordStoreTest`/`SecretBoxTest` use their own file and Keystore alias and delete both afterwards.

## Private copies and streams
- The app copies every incoming PDF to `cacheDir/pdfs/` and reads only that copy; tests check the folder is
  empty after saving and after Cancel.
- `TestPdfProvider` (test APK) serves `content://io.github.codenextdoor.pdfunlocker.test.pdfs/<asset>` as a file
  and `…/stream/<asset>` through a pipe, like apps that stream an attachment.

## Fixtures
- `app/src/androidTest/assets/`: small PDFs made for the tests (plain, user-password locked, owner-password
  only, not a PDF). How each was made, with the `qpdf` commands to rebuild them:
  [FIXTURES.md](../app/src/androidTest/assets/FIXTURES.md).

## What PdfRenderer really does (checked on API 37, 2026-10-14)
- `write(out, removePasswordProtection = true)` removes encryption only if the file was opened **with a
  password**. A PDF with only an owner password (print/edit restrictions) opens without one, and its copy
  keeps the restrictions. The copy still opens anywhere without a password.
- `write()` closes `out` itself and **stays silent when it can't write** (it leaves an empty file).
  `PdfUnlocker.save` checks the size through a `dup()` of the descriptor; pipes report -1 and can't be checked.

## Known gaps
- Owner-password-only PDFs keep their print/edit restrictions (see above). Removing them would need a PDF
  library; the author chose none.
- A save to a provider that hands out a pipe (some cloud apps) can't be size-checked.
- The accessibility checks in `UnlockScreenTest` catch missing labels, not contrast (see
  best-practices/testing-compose-ui.md). Colours come from the theme only.
