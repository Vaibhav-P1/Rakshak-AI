# Rakshak V2 — Development Progress

> Single source of truth for what was done in each development phase.
> The phase plan comes from section **S** of [`RAKSHAK_PRODUCTION_AUDIT.md`](../RAKSHAK_PRODUCTION_AUDIT.md).
> Rules: never erase earlier phase history; record only work that was actually implemented and tested; record real results, including failures and warnings.

## Project Status

- **Current phase:** Phase 0 — Repo Hygiene (complete, awaiting commit) → next: Phase 1 (not started; waiting for instruction)
- **Overall status:** 1 of 8 phases complete (Phase 0: PASSED WITH NOTES). The app is **not production-ready**.
- **Last updated:** 2026-10-06
- **Current branch:** `main`
- **Latest commit:** `52bb367` — "feat: improve offline voice guard using Vosk new hot word is (help help help)" *(Phase 0 changes are staged/uncommitted on top of this)*

| Phase | Name | Status |
|---|---|---|
| 0 | Repo Hygiene | ✅ PASSED WITH NOTES (uncommitted) |
| 1 | SOS Reliability Core | Not Started |
| 2 | Permissions, Security, Policy | Not Started |
| 3 | Triggers | Not Started |
| 4 | UI/UX Polish | Not Started |
| 5 | Platform & Release Engineering | Not Started |
| 6 | Internal → Closed Testing | Not Started |
| 7 | Production | Not Started |

---

## Pre-phase — Production Readiness Audit

- **Date:** 2026-10-06
- **Output:** [`RAKSHAK_PRODUCTION_AUDIT.md`](../RAKSHAK_PRODUCTION_AUDIT.md) (sections A–T, with findings tagged VERIFIED / NEEDS VERIFICATION)
- **Method:** Full source read, offline debug build, merged-manifest inspection, APK content and ELF-alignment check, git-history secret search. No source changes.
- **Key baseline facts:** debug APK 99.2 MB; `libvosk.so` is 4 KB-aligned (not 16 KB); targetSdk 34; zero tests; 15 critical bugs ranked in audit section P.

---

## Phase 0 — Repo Hygiene

### Status
**PASSED WITH NOTES** — work is complete and verified locally; not yet committed.

### Objective
Make the repository clean, honest and reproducible before any functional work:
- untrack machine-local and IDE files
- remove unused build configuration and dependencies
- make a fresh clone build without private files (`google-services.json`)
- correct the README so it matches the actual code
- add CI that builds, lints and runs unit tests

### Work Completed
1. **Untracked local/IDE files:** `git rm --cached` on `local.properties` and all 13 tracked `.idea/` files. Local copies are kept on disk.
2. **Expanded `.gitignore`:** added `.gradle/`, `build/`, `.kotlin/`, `local.properties`, `.idea/`, `*.iml`, `.DS_Store`, `captures/`, `.externalNativeBuild/`, `.cxx/`, `google-services.json`, and signing material (`*.jks`, `*.keystore`, `keystore.properties`).
3. **`gradlew` made executable in git** (`git update-index --chmod=+x`, mode 100644 → 100755). Without this, Linux CI fails with "permission denied".
4. **Removed the unused `kotlin-kapt` plugin.** Only KSP is used (Room compiler).
5. **Removed the snapshot repository** `https://oss.sonatype.org/content/repositories/snapshots/` from `settings.gradle.kts`.
6. **Disabled Jetifier:** removed `android.enableJetifier=true`. `./gradlew :app:checkJetifier` first confirmed that no legacy support libraries are used.
7. **Removed unused dependencies:** `androidx.work:work-runtime-ktx:2.9.0` and `androidx.datastore:datastore-preferences:1.0.0`. A grep confirmed no source references.
8. **Removed Firebase entirely:** the `com.google.gms.google-services` plugin (root and app), `firebase-bom:33.1.0` and `firebase-analytics`. No source code referenced Firebase.
9. **Added a lint baseline:** `lint { baseline = file("lint-baseline.xml"); abortOnError = true }` in `app/build.gradle.kts`, and generated `app/lint-baseline.xml` with 42 existing issues (1 error, 41 warnings).
10. **Added CI:** `.github/workflows/android-ci.yml` runs on push to `main` and on pull requests (Ubuntu, Temurin JDK 17, `gradle/actions/setup-gradle@v4`). Steps: `assembleDebug` → `testDebugUnitTest` → `lintDebug`. It uploads the lint HTML and unit-test reports as artifacts.
11. **Rewrote `README.md`** to match the code: Vosk offline recognition instead of SpeechRecognizer; trigger phrase "help help help" instead of "help rakshak"; a single location snapshot, not live sharing; no countdown on voice/volume/widget triggers; `ShakeDetectorService` marked inactive; build needs no secrets; performance table with unmeasured numbers removed; outdated SpeechRecognizer troubleshooting removed. Added a CI badge, a Known Limitations section, a roadmap and a link to the audit.

### Files Changed
| Change | File |
|---|---|
| Created | `.github/workflows/android-ci.yml` |
| Created | `app/lint-baseline.xml` (generated, 42 issues) |
| Created | `RAKSHAK_PRODUCTION_AUDIT.md` (from the audit, before Phase 0) |
| Created | `docs/DEVELOPMENT_PROGRESS.md` (this file, after Phase 0) |
| Modified | `.gitignore` |
| Modified | `README.md` |
| Modified | `build.gradle.kts` (google-services plugin removed) |
| Modified | `app/build.gradle.kts` (kapt and google-services plugins, WorkManager, DataStore and Firebase removed; `lint {}` block added) |
| Modified | `settings.gradle.kts` (snapshot repo removed) |
| Modified | `gradle.properties` (Jetifier removed) |
| Mode change | `gradlew` (made executable) |
| Untracked (kept locally) | `local.properties`, `.idea/**` (13 files) |
| **Not changed** | Everything under `app/src/` (no Kotlin, manifest or resource changes) |

### Tests / Verification
| Check | Command | Result |
|---|---|---|
| Jetifier need | `./gradlew --offline :app:checkJetifier` | ✅ "does not use any legacy support libraries" |
| Removed libraries unreferenced | grep for `firebase\|androidx.work\|datastore\|kapt` in `app/src` | ✅ no matches |
| Build (offline) | `./gradlew --offline :app:assembleDebug ...` | ❌ **Failed (cache only):** after Firebase was removed, `play-services-location` resolves `androidx.fragment:1.0.0`, whose transitive artifacts (coordinatorlayout, drawerlayout, etc.) were not in the local Gradle cache. Not a code problem |
| Build + unit tests + lint (online) | `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | Build ✅, unit tests ✅ (**0 tests exist**), lint ❌: 1 error + 41 warnings (pre-existing) |
| Baseline generation | `./gradlew :app:updateLintBaseline` | ❌ First attempts failed (AGP 8.4 aborts on lint errors even when updating the baseline). ✅ Succeeded with `abortOnError` temporarily `false`; restored to `true` afterwards |
| Full CI command, in repo | `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | ✅ BUILD SUCCESSFUL |
| Fresh-clone simulation | Copied only `git ls-files -co --exclude-standard` (no `local.properties`, no `google-services.json`) to a temp dir; set `ANDROID_HOME`; ran the same three tasks with `--no-daemon` | ✅ BUILD SUCCESSFUL in 44 s. Warnings: SDK XML version 4 notice; "Unable to strip libjnidispatch.so, libvosk.so" |
| Merged manifest | Inspected `merged_manifest/debug/.../AndroidManifest.xml` | ✅ `AD_ID`, `ACCESS_ADSERVICES_*`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `BIND_GET_INSTALL_REFERRER_SERVICE` are gone; 13 app permissions remain, unchanged |
| APK size | `stat` on `app-debug.apk` | 99,223,235 → **97,103,355 bytes** (~99.2 → ~97.1 MB) |
| Source untouched | `git diff --stat -- app/src` | ✅ empty |

**Not verified:**
- The GitHub Actions workflow has **not run on GitHub yet**; it will run on the first push.
- Local verification used **JDK 21** (JDK 17 isn't installed locally); CI uses JDK 17.
- No device or manual app testing was done. Phase 0 changed no runtime code apart from removing Firebase, whose runtime effect (no analytics initialization) was not tested on a device.

**Lint baseline contents (42 issues):** GradleDependency ×14, ObsoleteSdkInt ×10, UnusedResources ×6, UnusedAttribute ×3, HardcodedText ×3, MonochromeLauncherIcon ×2, UnsafeImplicitIntentLaunch ×1 (**error**), OldTargetApi ×1, InlinedApi ×1, BatteryLife ×1.

### Result
**PASSED WITH NOTES**

The repository now builds from a clean checkout with no private files, ships no analytics SDK, and has CI that enforces build, unit tests and no new lint issues. The README accurately describes current behavior and limitations.

Notes: the changes are uncommitted, CI hasn't run remotely yet, and no tests exist yet.

### Important Decisions
1. **Firebase was removed, not made optional.** The app's positioning is "offline, no analytics". Any future crash reporting (planned for Phase 5) must be opt-in, disclosed in the privacy policy and in Data Safety, and must not reintroduce `AD_ID`.
2. **Lint baseline strategy.** Existing issues are frozen in `app/lint-baseline.xml` and CI fails on new ones. Future phases should **remove entries from the baseline as they fix issues** (regenerate it after fixes), and never add new issues to it to silence lint.
3. **No `app/src` changes in Phase 0.** The known `UnsafeImplicitIntentLaunch` error (`VoiceGuardService.kt:259`, a broadcast nobody consumes) is deferred to Phase 1, because fixing it touches the SOS trigger path.
4. **Git history was not rewritten.** Purging committed build artifacts or the Vosk model from history requires a force-push and is left to the owner's decision.
5. **CI uses JDK 17** to match `compileOptions` and `jvmTarget = 17`.
6. **The Vosk model stays in plain git for now.** Moving it to LFS, Play Asset Delivery or a download is a later decision, tied to the Voice Guard decision in Phase 3.

### Issues / Follow-ups
- [ ] **Commit Phase 0** and push. Confirm the GitHub Actions run is green, then update the commit field below.
- [ ] Owner decision: rewrite history to purge old `app/build/**` blobs (the `.git` directory is 133 MB).
- [ ] The README states the MIT license, but there's no `LICENSE` file. Add one (owner decision).
- [ ] `INTERNET`, `FOREGROUND_SERVICE_DATA_SYNC`, `READ_CONTACTS` and `WAKE_LOCK` are still declared in the manifest. Removal is planned for **Phase 2**.
- [ ] Lint error `UnsafeImplicitIntentLaunch` (`VoiceGuardService.kt:259`). Fix in **Phase 1** and remove it from the baseline.
- [ ] ProGuard rules still keep `androidx.compose.**` and Gson. Clean up in **Phase 5** when enabling R8.
- [ ] Add `.gitattributes` for consistent line endings. Git reported LF→CRLF warnings on Windows for the Gradle files.
- [ ] The local machine has no JDK 17. Install it to match CI exactly (optional).

### Commit
**Not committed yet.** The changes are staged/unstaged on `main` on top of `52bb367`.
Suggested message: `chore: Phase 0 repo hygiene — untrack local files, remove unused deps & Firebase, add lint baseline and CI, correct README`

---

## Phase 1 — SOS Reliability Core

### Status
Not Started

### Objective
Make the SOS pipeline reliable: send SMS immediately without waiting for or depending on location; follow up with location when a fresh fix arrives; never abort SOS because of a missing location permission or a failed location FGS type; track per-contact SMS sent results; keep a persistent result notification with Cancel / "I'm safe" actions; persist SOS state across process death; apply location freshness and accuracy rules and accept coarse location; send a GSM-7 message. Extract an `SosOrchestrator` with injected dependencies and unit-test it. Also fix the `UnsafeImplicitIntentLaunch` lint error.

### Work Completed
—

### Tests / Verification
—

### Result
—

### Important Decisions
—

### Issues / Follow-ups
—

### Commit
—

---

## Phase 2 — Permissions, Security, Policy

### Status
Not Started

### Objective
Contextual, non-blocking permission flows with a Settings fallback (no more all-or-nothing gate); accept approximate location. Remove unneeded permissions (`READ_CONTACTS`, `FOREGROUND_SERVICE_DATA_SYNC`, `INTERNET`, and `WAKE_LOCK` if unused). Fix the exported widget receiver. Strip sensitive logging in release. Write the privacy policy and in-app prominent disclosures. Add the emergency-services disclaimer and a 112 shortcut.

### Work Completed
—

### Tests / Verification
—

### Result
—

### Important Decisions
—

### Issues / Follow-ups
—

### Commit
—

---

## Phase 3 — Triggers

### Status
Not Started

### Objective
Countdown and cancel for the widget and Volume Guard; restrict the accessibility config to key events with a disclosure screen; add a Quick Settings tile trigger; **decide Voice Guard's fate** (exclude from v1, or rebuild with grammar mode, no audio focus, lifecycle and state sync, model integrity checks and 16 KB-compatible native libs, behind a "Beta" label).

### Work Completed
—

### Tests / Verification
—

### Result
—

### Important Decisions
—

### Issues / Follow-ups
—

### Commit
—

---

## Phase 4 — UI/UX Polish

### Status
Not Started

### Objective
A real Material 3 theme and tokens; string resources plus Hindi; WCAG contrast fixes; edge-to-edge and insets; a scrollable, responsive Home screen; contact edit and validation (E.164, dedupe, cap); onboarding; "Send test alert"; SOS history.

### Work Completed
—

### Tests / Verification
—

### Result
—

### Important Decisions
—

### Issues / Follow-ups
—

### Commit
—

---

## Phase 5 — Platform & Release Engineering

### Status
Not Started

### Objective
Upgrade Kotlin to 2.x, AGP, Gradle and the Compose BOM; targetSdk/compileSdk 35 → 36; R8 and resource shrinking with correct keep rules; release signing (Play App Signing), AAB, a versioning scheme; opt-in crash reporting; Baseline Profile; device-matrix testing.

### Work Completed
—

### Tests / Verification
—

### Result
—

### Important Decisions
—

### Issues / Follow-ups
—

### Commit
—

---

## Phase 6 — Internal → Closed Testing

### Status
Not Started

### Objective
Internal track, then closed testing with at least 12 testers for at least 14 days. A structured device test script (lock screen, no SIM, dual-SIM, OEM battery savers, Android 14/15/16). Submit the SMS, FGS and Accessibility declarations with videos. Complete the Data Safety form.

### Work Completed
—

### Tests / Verification
—

### Result
—

### Important Decisions
—

### Issues / Follow-ups
—

### Commit
—

---

## Phase 7 — Production

### Status
Not Started

### Objective
Staged rollout (10% → 50% → 100%); monitor Play Vitals (crashes, ANRs, wake locks); support contact in the listing; changelog.

### Work Completed
—

### Tests / Verification
—

### Result
—

### Important Decisions
—

### Issues / Follow-ups
—

### Commit
—
