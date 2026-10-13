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
| UI flow | `app/src/androidTest` | see below | Pick → password → save, with the system pickers stubbed (Espresso-Intents) |
| Lint | app + test code | `./gradlew lintDebug` | API-level crashes, string format errors |

`PdfRenderer`'s password loading and `write()` exist only on a real Android 15+ runtime, not in Robolectric,
so unlocking is tested on the emulator.

Device tests (emulator `Pixel_10_b`, API 37):
```bash
export ANDROID_SERIAL=emulator-5556
./gradlew connectedDebugAndroidTest
```
This app keeps no data, so `connectedDebugAndroidTest` uninstalling it afterwards is fine.

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
