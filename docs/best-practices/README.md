# Best practices (reusable)

Research and lessons collected while building local-first Android apps (Kotlin, Jetpack Compose, no server).
Written to be **project-neutral**: copied 2026-10-13 from the author's `wealth-app` repo, keeping only what
applies to an app without a database. What's specific to this app lives in [../TESTING.md](../TESTING.md).

| Doc | Covers |
|---|---|
| [testing-jvm.md](testing-jvm.md) | Test-first workflow, mutation check, Robolectric, ViewModels and coroutines, fakes, flake-free CI |
| [testing-compose-ui.md](testing-compose-ui.md) | Device UI tests with Compose + Espresso: stable taps, lazy lists, StrictMode, navigation map, accessibility and large-text checks |
| [security-local-data.md](security-local-data.md) | Local-only principles, no logging of personal data, recents screenshots, network use, repo hygiene, dependencies |
| [accessible-compose-ui.md](accessible-compose-ui.md) | Large text, touch targets, screen readers, gestures with alternatives, colour, calm and clear messages |
| [release-and-upgrades.md](release-and-upgrades.md) | Testing the shrunk (R8) build, upgrade checks from the last release, signing, release checklist |
| [dev-environment.md](dev-environment.md) | JDK/SDK, Gradle speed and gotchas, running long checks in the background, git hygiene |

Not copied (no stored data, money or statements here): data-safety, money, statement-parsing.

**Freshness:** written 2026-10-09 (AGP 9.4, Kotlin 2.4, Compose BOM 2026.09, compile/target SDK 37). Library
behaviour and APIs change: re-check anything marked *(time-sensitive)* before relying on it.

**Keep it alive:** when a bug teaches something general, add it to the right doc in the same commit as the
fix (one or two lines: the rule and why). Project-specific details go to `docs/TESTING.md` instead.
