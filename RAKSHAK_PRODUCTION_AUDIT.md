# RAKSHAK AI — PRODUCTION READINESS AUDIT

> **Audit date:** 2026-10-06
> **Branch / commit:** `main` @ `52bb367` ("feat: improve offline voice guard using Vosk new hot word is (help help help)")
> **Scope:** Every tracked source file, the manifest, resources, build scripts and git history (19 Kotlin files, about 2,750 lines).
> **Method:** Static review of all code, an offline debug build (`./gradlew --offline :app:assembleDebug` succeeded), the Kotlin compiler warnings, the merged-manifest report, the APK contents, the ELF alignment of the native libraries, and a git-history search for secrets.
> **Source code was not modified during the audit.**

---

## How to read this report

Every finding carries one of these tags:

| Tag | Meaning |
|---|---|
| **[VERIFIED]** | Confirmed directly from the repository source, the build output, the APK contents or git history. File and line references are given where they apply. |
| **[NEEDS VERIFICATION]** | Inferred from Android platform behavior or Google Play policy, but not confirmed on a real device or against the current policy text. It must be tested or checked before you rely on it. |

Section T at the end lists every item that needs verification in one place.

---

## Table of Contents

- [A. Executive Summary](#a-executive-summary)
- [B. Current Architecture](#b-current-architecture)
- [C. Current Features](#c-current-features)
- [D. UI/UX Assessment](#d-uiux-assessment)
- [E. SOS Reliability Assessment](#e-sos-reliability-assessment)
- [F. Voice Guard Assessment](#f-voice-guard-assessment)
- [G. Volume Guard Assessment](#g-volume-guard-assessment)
- [H. Location Assessment](#h-location-assessment)
- [I. Emergency Contacts Assessment](#i-emergency-contacts-assessment)
- [J. Permission Matrix](#j-permission-matrix)
- [K. Security Assessment](#k-security-assessment)
- [L. Performance Assessment](#l-performance-assessment)
- [M. Build & Dependency Assessment](#m-build--dependency-assessment)
- [N. Testing Assessment](#n-testing-assessment)
- [O. Play Store Readiness](#o-play-store-readiness)
- [P. Critical Bugs](#p-critical-bugs)
- [Q. Technical Debt](#q-technical-debt)
- [R. Recommended Features](#r-recommended-features)
- [S. Recommended Development Roadmap](#s-recommended-development-roadmap)
- [T. Verified Findings vs. Items Needing Verification](#t-verified-findings-vs-items-needing-verification)

---

## A. Executive Summary

Rakshak is a small, readable codebase with a good idea and a nice visual style. **It is not production-ready.** Several defects can make SOS **fail silently**, and for a safety app that is the one failure that cannot be accepted.

### The 10 most important findings

1. **[VERIFIED]** **SOS sends no SMS at all if precise location isn't granted** (`SOSService.kt:71-80`). The README says SMS still goes out without location. That is only true when GPS is off, not when the permission is missing.
2. **[VERIFIED]** **Users who pick "Approximate location" (Android 12+) can never get past the permission screen.** The app requires `ACCESS_FINE_LOCATION` plus five other permissions, all of them, before it shows anything (`MainActivity.kt:104`).
3. **[VERIFIED]** **A stale cached location can be sent as "my current location".** `lastLocation` wins the race with no check on age or accuracy (`LocationHelper.kt:51-59`).
4. **[VERIFIED]** **"Alert Sent ✓" means only that the SMS was handed to the radio, not that it was sent.** No receiver listens for the SMS-sent result. On top of that, the success and failure notifications are deleted 4 seconds later (`SOSService.kt:194`).
5. **[VERIFIED]** **Any installed app can trigger your SOS.** The widget receiver is exported and accepts a custom action (`AndroidManifest.xml:76-87`).
6. **[VERIFIED]** **The wake phrase is "help help help", not "help rakshak".** The UI, strings and README all say "Help Rakshak" (`VoiceGuardService.kt:237-241` vs `HomeScreen.kt:219`).
7. **[VERIFIED]** **Voice Guard takes permanent audio focus**, which pauses the user's music and podcasts. It also leaks recognizers on every restart and holds a wake lock.
8. **[VERIFIED]** **Play Store blockers:** targetSdk 34 (too low), `libvosk.so` is **not 16 KB page-aligned** (ELF headers checked), there's no release signing, minify is off, and the debug APK is **99 MB**. The README claims ~15 MB.
9. **[VERIFIED]** **Firebase Analytics is bundled** and adds `AD_ID` and AdServices permissions, while the README says "no analytics, no tracking". That is a privacy and Data Safety misrepresentation risk.
10. **[VERIFIED]** **There are zero tests.**

### Verdict

The architecture is a reasonable start, but the SOS pipeline needs a rebuild for reliability before any feature work. Voice Guard as built is **not suitable for Play release**. Volume Guard (AccessibilityService) is **a significant policy-review risk**.

---

## B. Current Architecture

### B.1 Module and package structure [VERIFIED]

There is one module, `:app`, with package `com.safety.rakshak`.

| Layer | Files | Notes |
|---|---|---|
| Entry | `MainActivity.kt` (313 lines) | Activity, NavHost, the **whole permission screen**, design tokens and a broadcast receiver |
| UI | `ui/HomeScreen.kt` (485), `ui/ContactsScreen.kt` (449), `ui/theme/Theme.kt`, `ui/theme/Type.kt` | Composables also start services directly |
| ViewModel | `viewmodel/MainViewModel.kt` (63) | One `AndroidViewModel` shared by both screens |
| Data | `data/EmergencyContact.kt`, `EmergencyContactDao.kt`, `EmergencyContactRepository.kt`, `RakshakDatabase.kt` | Room v1, manual singleton |
| Services | `SOSService`, `VoiceGuardService`, `RakshakAccessibilityService`, `ShakeDetectorService` | **`ShakeDetectorService` is not in the manifest and is never started: dead code** |
| Widget | `widget/Soswidget.kt` (the class is `SOSWidget`) | AppWidgetProvider with RemoteViews |
| Utils | `LocationHelper`, `SMSHelper`, `PermissionHelper` | `PermissionHelper` is **never used** |
| Workers | none | WorkManager is a dependency but unused |
| DI | none | Manual construction everywhere |
| Navigation | Navigation-Compose with string routes `"home"` and `"contacts"` | Permission gate sits outside the NavHost |
| Resources | `strings.xml`, `themes.xml`, widget layout and drawables, accessibility config, widget info, backup rules, launcher icons | Most UI strings are hardcoded in Kotlin rather than in `strings.xml` |
| Assets | `vosk-model-small-en-us-0.15/` (~68 MB) | Copied to `filesDir` at runtime |

### B.2 How it actually works [VERIFIED]

```
Compose UI ──(startForegroundService)──► SOSService ──► Room DAO (direct)
                                              ├──► LocationHelper (FusedLocation)
                                              └──► SMSHelper (SmsManager)
VoiceGuardService (Vosk) ──► SOSService  (+ broadcast → MainActivity → VM flag nobody reads)
AccessibilityService ──► SOSService
Widget click ──► SOSWidget receiver ──► SOSService
```

### B.3 Architectural assessment

| Check | Status | Notes |
|---|---|---|
| MVVM | Partial | The ViewModel holds only contacts and UI flags. SOS logic sits in a Service and in composables |
| UI / business / data separation | Weak | No domain layer. The UI starts services itself |
| State management | Weak | `isVoiceGuardActive` lives in memory and isn't synced with the service. `sosTriggered` is never read |
| ViewModel usage | Basic | A single `AndroidViewModel` |
| Repository pattern | Partial | A repository exists, but `SOSService` and the widget use the DAO directly |
| Lifecycle awareness | Partial | Uses `collectAsState()`, not `collectAsStateWithLifecycle()` |
| Coroutines | Mixed | Ad-hoc `CoroutineScope`s, `Handler`s and a raw `Thread` |
| Flow / StateFlow | Used | Room `Flow` → `StateFlow` in the ViewModel |
| Context misuse | Minor | `AndroidViewModel` with the application context is fine |
| Memory leaks | Present in services | See sections F and L |
| Activity / Service responsibilities | Blurred | `MainActivity` holds the permission UI and design tokens. `SOSService` owns all business logic |

### B.4 Architectural problems [VERIFIED]

- **MVVM only in part.** The ViewModel holds contacts, an in-memory `isVoiceGuardActive` flag and a `sosTriggered` flag. **Nothing ever reads `sosTriggered`** (`MainViewModel.kt:24`), so the `MainActivity` broadcast receiver is dead code.
- **The UI owns business logic.** `HomeScreen.kt:221-231` and `:478-486` start services from composables. The ViewModel does not handle SOS triggers.
- **Voice Guard state is not the source of truth.** The flag lives in ViewModel memory and is never synced with whether the service is actually running. Swipe the app away with Voice Guard on, reopen it, and the toggle shows OFF while the service may still be listening.
- **SOS logic lives inside an Android `Service`** and has no domain layer. That makes it untestable.
- **No shared design system.** Color tokens are copied into three files, and redefined again as locals inside `ContactCard` (`ContactsScreen.kt:385-388`). `MaterialTheme` is set up but mostly bypassed.
- **Coroutines:** the services use hand-made `CoroutineScope`s and `Handler`s. `updateWidget` launches `CoroutineScope(Dispatchers.IO)` from a BroadcastReceiver without `goAsync()`, so the scope leaks and the process may die mid-update.
- **Lifecycle:** it uses `collectAsState()` instead of `collectAsStateWithLifecycle()` (minor). No Activity memory leaks found. The receiver is unregistered in `onDestroy`.
- **No real leaks of Context into the ViewModel.** `AndroidViewModel` with the application context is fine.

---

## C. Current Features

Each claimed feature, checked against the code. **[VERIFIED]**

| Claimed feature | Status | Reality |
|---|---|---|
| One-tap SOS | ✅ / ⚠️ | In app: 3-second countdown, "Send Now" and Cancel. Widget, Volume and Voice have **no countdown or cancel** |
| Emergency SMS | ✅ / ❌ | Sends via `SmsManager`. Delivery and sent status are never checked |
| "Live Google Maps location sharing" | ❌ | **Not live.** It sends a single, possibly stale coordinate link once |
| Voice Guard, "help rakshak" | ⚠️ | Uses Vosk offline recognition, triggered by **three "help"s in one utterance**. "rakshak" is not checked at all |
| Android SpeechRecognizer API | ❌ | **Not used anywhere** (the README/resume claim is outdated) |
| Volume Guard | ✅ | AccessibilityService that detects Vol Up + Vol Down held together |
| Home screen widget | ✅ / ⚠️ | One-tap, immediate SOS with no confirmation |
| Offline SMS + GPS | ✅ / ⚠️ | The core flow needs no internet. Firebase Analytics does use the network |
| Room DB | ✅ | One table |
| Foreground services | ✅ | `SOSService` (location), `VoiceGuardService` (microphone) |
| Android 14+ FGS compliance | ⚠️ | Partial. Types are declared, but starting a location-type service from the background is risky (see E) |
| FusedLocationProviderClient | ✅ | Used in `LocationHelper` |
| AppWidget API | ✅ | `SOSWidget` |
| AccessibilityService | ✅ | `RakshakAccessibilityService` |
| Shake detection (README) | ❌ | Code exists but the service is not registered, so it can't run |
| "Rakshak **AI**" | ⚠️ | There is no AI apart from offline speech recognition. Be precise in interviews |

---

## D. UI/UX Assessment

**Overall:** a clean, consistent dark visual style. However, the theme is hardcoded rather than built on Material 3, so the app is always dark and Material 3 is only half used. It also has accessibility contrast failures and no responsiveness.

### D.1 Screen scores [VERIFIED from code; visual rendering not checked on a device]

| Screen | Score | Why |
|---|---|---|
| **Permission screen** | **3/10** | Blocks the **whole app** until all 6 permissions are granted. If permissions are permanently denied, "Grant Permissions" does nothing and there's no path to Settings, so the user is stuck for good. Asking for mic and contacts up front looks invasive. "Approximate location" users stay stuck forever. |
| **Home screen** | **6/10** | Strong visual hierarchy and a clear SOS target. Problems: not scrollable, with fixed 220 dp button, banners and cards inside a `weight(1f)` column, so it breaks on small screens, large font scales and landscape. Hardcoded `padding(top = 56.dp)` instead of window insets. The Voice Guard subtitle says "Say 'Help Rakshak'", which is wrong. When there are no contacts, SOS is fully disabled with no fallback such as calling 112. The edit button is 32 dp. Two infinite animations run constantly. |
| **SOS countdown dialog** | **6/10** | A good pattern overall. Its state uses `remember` rather than `rememberSaveable`, so rotating the device **silently cancels the SOS**. Tapping outside the dialog also cancels it. There's no haptic feedback and no visible progress afterwards. |
| **Contacts screen** | **6/10** | Has a good empty state and a delete confirmation. **No edit** (the DAO supports it but the UI doesn't). No duplicate check, no country-code handling, no limit, no primary contact (the `isPrimary` field is never set). The FABs have no labels, and a bare "person" icon is ambiguous for "pick from contacts". |
| **Widget** | **4/10** | One tap sends SOS instantly, so accidental triggers in a pocket or by a child are likely. The contact count only refreshes every 30 minutes. Strings and colors are hardcoded. |
| **Notifications (as UX)** | **3/10** | The result notification disappears after 4 seconds, so users may never learn the SOS failed. The icon is the default Android-robot vector (`drawable/ic_launcher_foreground.xml` is the template file). |

### D.2 Cross-cutting UI issues

| Area | Finding | Tag |
|---|---|---|
| Contrast | `TextMuted #3D4455` on `#0A0C10` ≈ **2.0:1**. `TextSecondary #6B7280` ≈ **4.0:1** on 12–13 sp text. Both fail WCAG AA (4.5:1) | VERIFIED (calculated) |
| Material 3 usage | `MaterialTheme` is configured, but nearly every color and text size is hardcoded, so the typography and color scheme are bypassed | VERIFIED |
| Dark/light mode | The UI is always dark. `Theme.kt` sets the status bar to `colorScheme.primary`, which is a *dynamic wallpaper color*, and sets light-status-bar icons in light mode | VERIFIED |
| Launch | The XML theme is `android:Theme.Material.Light.NoActionBar`, so there's a **white flash at launch**. There's no SplashScreen API | VERIFIED (code) / visual effect NEEDS VERIFICATION |
| Adaptive icon | `mipmap-anydpi-v26` uses `@drawable/ic_launcher_background`, which is the default green template grid | VERIFIED (file) / how it renders NEEDS VERIFICATION |
| Edge-to-edge | Not handled. This becomes mandatory once targetSdk is 35 or higher | VERIFIED |
| Responsive layout | Home is not scrollable, uses fixed sizes, and has no landscape or tablet handling | VERIFIED |
| Localization | Almost every string is hardcoded in Kotlin. Supporting Hindi or other Indian languages is impossible without refactoring | VERIFIED |
| Loading states | None for Voice Guard model loading (the toggle shows "on" even if the model failed) | VERIFIED |
| Error states | No in-app SOS outcome. Notification only, and it's deleted after 4 s | VERIFIED |
| Empty states | Good on Contacts. On Home, a disabled SOS with a warning | VERIFIED |
| Permission states | All-or-nothing gate. No permanently-denied handling | VERIFIED |
| Touch targets | Edit button 32 dp, delete button 36 dp (visual size below 48 dp) | VERIFIED (Compose may expand the hit area: NEEDS VERIFICATION) |
| Accessibility | No `semantics` or role descriptions on the SOS button, and no TalkBack testing has been done | VERIFIED |
| Animation | Pulse rings and shield animations are pleasant. They run continuously while visible | VERIFIED |

---

## E. SOS Reliability Assessment

### E.1 The actual flow [VERIFIED]

```
User taps SOS (HomeScreen.kt:259)
    ↓ countdown dialog (3 s, remember-state — lost on rotation)
UI: triggerSOS(context) (HomeScreen.kt:478) — composable starts service directly
    ↓
SOSService.onStartCommand → isRunning guard (in-memory only)
    ↓ checks ACCESS_FINE_LOCATION → if missing: stopSelf() — NO SMS, NO notification   ← CRITICAL
    ↓ startForeground(type=LOCATION) → if it throws: stopSelf() — NO SMS               ← CRITICAL
Emergency contacts: Room getAllContactsList()
    ↓ if empty: "SOS Error" notification (deleted after 4 s)
Location: LocationHelper.getCurrentLocation() with 10 s timeout
    ↓ lastLocation returns immediately if non-null — any age                         ← CRITICAL
SMS: SMSHelper.sendSOSMessage() → sendMultipartTextMessage per contact
    ↓ sentIntents → implicit broadcast that NOBODY receives
Confirmation: "Alert Sent ✓" (= handed to SmsManager, not actually sent)
    ↓ 4 s later: stopForeground(REMOVE) — confirmation/failure notification deleted
```

### E.2 Edge cases

| Scenario | What happens today | Tag |
|---|---|---|
| Location unavailable | 10-second wait, then the SMS says "Location unavailable". OK | VERIFIED |
| GPS disabled | The request never returns a fix, and `lastLocation` is possibly stale. There's no prompt to enable location (`SettingsClient`) | VERIFIED (code) |
| Location permission denied or only approximate | **SOS aborts completely, no SMS** | VERIFIED |
| …and the crash on that path | Calling `stopSelf()` before `startForeground()` after `startForegroundService()` can crash the app ("did not then call startForeground") | NEEDS VERIFICATION on device |
| SMS permission denied | "SOS Failed" notification, deleted after 4 s. No fallback (such as opening the SMS app with the text pre-filled) | VERIFIED |
| No SIM / airplane mode | **Reports success.** The sent-status `PendingIntent` result is ignored | VERIFIED (code) |
| Dual-SIM with no default SMS SIM | Uses `SmsManager`'s default subscription. Behavior varies by OEM | NEEDS VERIFICATION |
| No network (cellular) | Same as no SIM: false success | VERIFIED (code) |
| Empty contact list | In-app button disabled. Widget, Voice and Volume start the service, which shows "No emergency contacts" and then deletes the notification | VERIFIED |
| App backgrounded | The service runs as an FGS. On Android 14+, starting a **location-type** FGS from the background needs while-in-use eligibility. Widget and notification starts are exempt. Voice Guard and accessibility starts are **uncertain, and if they fail, the current code drops the entire SOS** | Code path VERIFIED; platform behavior NEEDS VERIFICATION on Android 14/15 with the screen locked |
| Backgrounded during the in-app countdown | `startForegroundService` is called from a `LaunchedEffect` after the delay. If the app has left the foreground long enough, Android 12+ may throw `ForegroundServiceStartNotAllowedException` (uncaught) | NEEDS VERIFICATION (a short grace period likely covers 3 s) |
| Process killed mid-SOS | `START_NOT_STICKY` with no persisted state, so the SOS is lost with no retry | VERIFIED |
| Accidental trigger | In-app is protected by the countdown. Widget, volume and voice are **not** | VERIFIED |
| Cancellation | Only during the in-app countdown. No "Cancel / I'm safe" action and no follow-up SMS | VERIFIED |
| Duplicates | Blocked only while `isRunning` (about 4 s). Retriggering after that sends again. Voice has a 10 s cooldown, volume 2 s | VERIFIED |
| Rotation during countdown | Dialog state is lost, so the SOS is silently cancelled | VERIFIED (code) |

### E.3 Other SMS issues

- **[VERIFIED]** The 🚨 emoji forces UCS-2 encoding (70 characters per part), so each alert is **about 3–4 SMS parts per contact**. That costs the user money and is slower.
- **[NEEDS VERIFICATION]** Android shows a confirmation dialog after about 30 SMS in 30 minutes. It isn't confirmed whether multipart parts count separately.
- **[VERIFIED]** `SMSHelper.kt:104` logs contact names and phone numbers.
- **[VERIFIED]** `SmsManager` calls run on the main thread (acceptable).
- **[VERIFIED]** Partial failure is reported as full success (`SMSHelper.kt:115-117`).
- **[VERIFIED]** The SMS has no location accuracy, no timestamp and no indication of whether location services are off.

### E.4 SOS reliability issue checklist

- [ ] SMS must not depend on the location permission
- [ ] SMS must not depend on the location FGS type succeeding
- [ ] Send immediately; send location as a follow-up
- [ ] Check location freshness and accuracy
- [ ] Track per-contact SMS sent results
- [ ] Keep a persistent result notification
- [ ] Countdown and cancel for every trigger
- [ ] "I'm safe" follow-up message
- [ ] Persist SOS state so it survives process death
- [ ] Use GSM-7 message text (no emoji)
- [ ] Report partial failures accurately
- [ ] Offer a fallback when SMS is impossible (SMS intent, 112 dial)

---

## F. Voice Guard Assessment

**What it does [VERIFIED]:** It's a foreground service with type `microphone`. It copies the 68 MB Vosk model from assets into `filesDir`, then runs **open-vocabulary** recognition continuously. SOS fires when one final result contains "help" three or more times.

### F.1 Findings

| Area | Finding | Tag |
|---|---|---|
| Speech recognition | Vosk `SpeechService` and `Recognizer`. Android `SpeechRecognizer` is not used | VERIFIED |
| Wake phrase | The code checks `helpCount >= 3`. The UI, `strings.xml` and README say "Help Rakshak" | VERIFIED |
| "rakshak" in vocabulary | Probably absent from the small English model | NEEDS VERIFICATION |
| Recognition mode | No grammar is set (the log says "with grammar", which is false). Full-vocabulary decoding wastes CPU and raises false positives. A restricted grammar such as `["help", "[unk]"]` would be cheaper and more accurate | VERIFIED (code) |
| False positives | TV, songs, games, or children saying "help help help". There's no countdown before SOS fires | VERIFIED (logic) / real-world rate NEEDS VERIFICATION |
| False negatives | Screaming, crying, accents, Hindi or regional words ("bachao"), noisy streets. Requires a pause before a final result is produced | NEEDS VERIFICATION (field testing) |
| Microphone permission | Requested at the all-or-nothing gate, out of context. If revoked later, `startForeground` may throw (uncaught) | VERIFIED (code) |
| Foreground service | `startForeground(id, notification)` without an explicit type. Relies on the manifest type `microphone` | VERIFIED |
| **Audio focus** | `requestAudioFocus(null, STREAM_VOICE_CALL, AUDIOFOCUS_GAIN)` runs on every restart and is never abandoned. **This pauses the user's music and podcasts.** It's a deprecated API and serious UX damage | VERIFIED |
| Resource leaks | `onError` and `onTimeout` create a new `Recognizer` and `SpeechService` without shutting down the old ones. `Recognizer` and `Model` are never closed. Starting twice (after a toggle-state desync) runs **two** recognizers | VERIFIED |
| Model extraction | If extraction is interrupted, the directory exists but is partial, so it is **never re-extracted** and loading fails permanently. The `copyAssetFolder` return value is ignored. There's no versioning. Install footprint is about 68 MB APK + 68 MB extracted | VERIFIED |
| Wake lock | `PARTIAL_WAKE_LOCK` is acquired in `onCreate` with a 1-hour timeout. After an hour it silently lapses | VERIFIED |
| Battery | A wake lock combined with continuous decoding means **heavy battery drain**, and Play Vitals flags "excessive wake locks" | Magnitude NEEDS VERIFICATION (measure with Battery Historian / profiler) |
| Lifecycle | Toggle state is in ViewModel memory and not synced with the service | VERIFIED |
| Process death | `START_STICKY` restarts with a `null` intent, which matches neither action. The service sits idle, holds the wake lock and doesn't listen | VERIFIED (code) |
| Background mic restart | On Android 14+, a microphone FGS cannot be restarted from the background | NEEDS VERIFICATION on device |
| Stop path | Sending `STOP` to a service that isn't running calls `startForegroundService` → `stopSelf` without `startForeground`, which can **crash the app** | Code path VERIFIED; crash NEEDS VERIFICATION |
| Failure recovery | It retries every 2 s on error without cleaning up, so a mic-busy error can loop | VERIFIED (code) |
| Errors in UI | "Model load failed" appears only in the notification. The UI toggle stays ON | VERIFIED |
| Privacy | Recognized speech is written to logcat (`VoiceGuardService.kt:201,206,235`) | VERIFIED |
| Reboot | Not restored after reboot (correct, since Android 15 forbids starting a mic FGS at boot), but the user is never told | VERIFIED |
| Android compatibility | `libvosk.so` is not 16 KB-aligned (see M) | VERIFIED |

### F.2 Play Store suitability

**Not suitable in its current form.** An always-listening microphone FGS is allowed only with an FGS-type declaration, a demo video, a prominent disclosure, and a clear user-initiated start. Combined with the battery drain, false triggers and the focus-stealing bug, the recommendation is to ship v1 **without Voice Guard**, or label it "Beta" and only after it's been rebuilt:

- [ ] Grammar-restricted recognition
- [ ] No audio focus request
- [ ] Short countdown with cancel before sending
- [ ] Proper start/stop lifecycle; close `Recognizer` and `Model`
- [ ] UI state synced with the real service state
- [ ] Model integrity check / re-extraction
- [ ] 16 KB-compatible native libraries
- [ ] Honest battery messaging
- [ ] No speech text in logs

---

## G. Volume Guard Assessment

**How it works [VERIFIED]:** `RakshakAccessibilityService.onKeyEvent` tracks the Volume Up and Volume Down down/up states. When both are held, it starts `SOSService` directly, with no countdown, and a 2-second retrigger lock.

| Area | Finding | Tag |
|---|---|---|
| Audio APIs | None. Detection is purely through accessibility key-event filtering | VERIFIED |
| Config is overbroad | `typeAllMask`, `canRetrieveWindowContent=true` and `flagRetrieveInteractiveWindows`. **Only `flagRequestFilterKeyEvents` is needed.** The code comment saying the extra flags are "needed for lock screen" isn't supported by anything in the code or docs. These flags let the service read every screen, which is a major red flag in policy review | VERIFIED |
| Play policy | Using the AccessibilityService API for a **non-accessibility purpose** requires a Play Console declaration, a prominent in-app disclosure, and affirmative consent. Review is strict and approval isn't guaranteed. **This is the top policy risk** | NEEDS VERIFICATION against current policy text |
| Collision | Android's own **Accessibility Shortcut** is "hold both volume keys". Some OEMs also map Vol Up + Down to screenshots or other shortcuts | NEEDS VERIFICATION per device |
| Behavior | The first key press passes through, so volume changes by one step before the combination registers. The service consumes keys only while both are held | VERIFIED (code) |
| Stuck state | If an `ACTION_UP` is ever missed, state can stick and the next single press of the other key triggers SOS (unlikely but possible) | NEEDS VERIFICATION |
| Accidental triggers | Pocket or bag presses, with **no countdown or cancel** | VERIFIED (no countdown) |
| Background / lock screen | Works if the OEM keeps the service alive. The README already notes OnePlus restrictions | NEEDS VERIFICATION per OEM |
| Battery impact | Negligible | VERIFIED (no polling or work) |
| Permissions | User must enable it manually in Accessibility settings | VERIFIED |
| Android 14+ | Starting a location FGS from an accessibility service: whether it's eligible is **uncertain**. If it isn't, the current code drops the SOS (see E) | NEEDS VERIFICATION |

**Recommendation:** Restrict the config to key events only. Add a disclosure screen and a countdown with a cancel option. Also offer a **Quick Settings tile** and a **lock-screen-accessible notification action** as policy-safe alternatives, so the product doesn't depend on Accessibility approval.

---

## H. Location Assessment

| Item | Finding | Tag |
|---|---|---|
| API | `FusedLocationProviderClient` with `requestLocationUpdates(HIGH_ACCURACY, maxUpdates=1)` racing `lastLocation` | VERIFIED |
| **Staleness** | Any non-null `lastLocation` is used immediately, even if it's hours old or from another city. There's no `time` or `accuracy` check. **This is safety-critical** | VERIFIED |
| Better API | `getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationToken)` with a fallback to a *fresh* last location (for example, under 2 minutes old) | Recommendation |
| Precise vs approximate | Only FINE is checked. Users who chose approximate are **blocked** at the permission screen, and the SOS aborts. Coarse location should be accepted and degrade gracefully | VERIFIED |
| Background location | Not requested. ✅ Correct, and keep it that way | VERIFIED |
| Timeout | 10 seconds. Reasonable, but the SMS should go out immediately and a follow-up SMS should carry the location when it arrives. Right now the user waits up to 10 s before *anything* is sent | VERIFIED |
| GPS off | No prompt and no detection. The SMS should state that location services are off | VERIFIED |
| Failure handling | `SecurityException` and others are caught and return `null`. Safe | VERIFIED |
| SMS content | No accuracy (±m) and no timestamp. Recipients can't judge how reliable the location is | VERIFIED |
| Ongoing tracking | None. One snapshot only, so the "live location" claim is inaccurate | VERIFIED |
| Battery | Fine: one-shot only | VERIFIED |
| Android version behavior | The approximate-location option (Android 12+) breaks the app; see above | VERIFIED (code) |

---

## I. Emergency Contacts Assessment

| Item | Finding | Tag |
|---|---|---|
| Data model | `id, name, phoneNumber, isPrimary`. `isPrimary` is never set from the UI | VERIFIED |
| Room entity / DB | v1 with `exportSchema = false` and no migration strategy, so the first schema change risks data loss or crashes. `@Insert(REPLACE)` with auto-ID is effectively a plain insert | VERIFIED |
| CRUD | Create ✅, Read ✅, Delete ✅ (with confirmation), **Update: no UI** | VERIFIED |
| Validation | Only "at least 7 digits". No E.164 normalization or country code, so `9876543210` and `+919876543210` are treated as different numbers. Picked contacts have characters stripped; manual entries don't | VERIFIED |
| Duplicates | Allowed (no unique index) | VERIFIED |
| Invalid numbers | Anything with 7 or more digits is accepted | VERIFIED |
| Empty state | Good on the Contacts screen; SOS disabled on Home | VERIFIED |
| Deletion | Confirmation dialog ✅ | VERIFIED |
| Editing | Not available in the UI | VERIFIED |
| Limit | None. Twenty contacts × 4 SMS parts gives 80 SMS per SOS, which may hit Android's SMS rate-limit dialog | Limit VERIFIED; rate-limit behavior NEEDS VERIFICATION |
| Contacts picker | `ACTION_PICK` on `Phone.CONTENT_TYPE` gets a temporary URI grant, so **`READ_CONTACTS` is unnecessary** | VERIFIED (code). Test once the permission is removed |
| Self-number | Not prevented | VERIFIED |
| Persistence | Room plus cloud backup of `rakshak_database` | VERIFIED |
| Backup completeness | The backup rule includes only the main DB file, not the `-wal` file, so recently added contacts may be missing from a restore (Room uses WAL by default) | NEEDS VERIFICATION |
| Widget sync | The count doesn't update when contacts change | VERIFIED |

### Suggested improvements

- [ ] E.164 normalization (libphonenumber or a simple +91 default)
- [ ] Unique index on the normalized number
- [ ] Edit contact UI
- [ ] Cap of about 5 contacts (the usual pattern in safety apps)
- [ ] Primary contact selection
- [ ] "Send test alert" per contact
- [ ] Prevent adding your own number
- [ ] Export the Room schema and add migrations
- [ ] Refresh the widget when contacts change

---

## J. Permission Matrix

| Permission | Why it is needed | Where / when requested | If denied | Needed for v1? | Policy concern |
|---|---|---|---|---|---|
| `SEND_SMS` | Send SOS | Permission gate at first launch, all-or-nothing (`MainActivity.kt:87-97`) | App blocked. If revoked later, SOS fails | **Yes** (core) | **Restricted permission.** Requires the SMS/Call Log Permissions Declaration. Personal-safety/emergency apps are believed to fall under an allowed exception **[NEEDS VERIFICATION against current policy text]**, and approval isn't automatic |
| `ACCESS_FINE_LOCATION` | Precise SOS location | Gate | App blocked; SOS aborts | Yes (but should be optional) | Must accept a coarse-only grant |
| `ACCESS_COARSE_LOCATION` | Fallback | Gate | Same | Yes | — |
| `RECORD_AUDIO` | Voice Guard | Gate at first launch (out of context) | **Whole app blocked**, even though only Voice Guard needs it | Only if Voice Guard ships | Prominent disclosure; ask only when Voice Guard is enabled |
| `READ_CONTACTS` | "Pick contacts" | Gate | Whole app blocked | **No.** The picker works without it | Unnecessary sensitive permission, so remove it |
| `POST_NOTIFICATIONS` (13+) | Status notifications | Gate | Whole app blocked | Yes, but should not block the app | — |
| `FOREGROUND_SERVICE` | FGS | Install-time | — | Yes | — |
| `FOREGROUND_SERVICE_LOCATION` | `SOSService` | Install-time | — | Yes | FGS type declaration and video in Play Console |
| `FOREGROUND_SERVICE_MICROPHONE` | Voice Guard | Install-time | — | Only with Voice Guard | Declaration and video |
| `FOREGROUND_SERVICE_DATA_SYNC` | Only for the unregistered `ShakeDetectorService` | Install-time | — | **No.** Remove it | Shake detection is not "data sync", so this would be misuse if ever used |
| `WAKE_LOCK` | Voice Guard / Shake | Install-time | — | Probably not | Vitals: excessive wake locks |
| `INTERNET` | Only Firebase Analytics | Install-time | — | **No** if analytics is removed | Contradicts the "offline" claim |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Battery banner | Banner shown when Voice Guard is on (`HomeScreen.kt:185-197`) | Background detection killed on some OEMs | Questionable | Play restricts this to specific use cases **[NEEDS VERIFICATION]**. Prefer `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, which needs no permission |
| `BIND_ACCESSIBILITY_SERVICE` | Volume Guard | User enables it in Settings via a banner | Volume Guard inactive | Optional | **High review risk** (see G) |
| *Merged from Firebase:* `AD_ID`, `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_NETWORK_STATE`, `BIND_GET_INSTALL_REFERRER_SERVICE` | Analytics | Automatic (merged manifest) | — | **No** | Advertising-ID declaration and Data Safety disclosure. Hurts user trust |
| *Merged from WorkManager:* `RECEIVE_BOOT_COMPLETED` | Unused library | Automatic | — | No | Unused dependency |

There is no phone-state, call or exact-alarm permission, and no background location. ✅ [VERIFIED from the merged-manifest report]

---

## K. Security Assessment

| Severity | Finding | Location | Tag |
|---|---|---|---|
| **High** | **Exported widget receiver with a custom action.** Any app can send an explicit broadcast with `com.safety.rakshak.WIDGET_SOS` and silently text all contacts. The widget's PendingIntent is already explicit, so the action doesn't belong in the intent-filter. Route widget clicks to a non-exported component | `AndroidManifest.xml:76-82` | VERIFIED (config). Exploit not tested on a device |
| Medium | **Sensitive data in logs** (contact names, phone numbers, recognized speech), with no R8 and no log stripping in release | `SMSHelper.kt:77,104,107`, `VoiceGuardService.kt:201-235` | VERIFIED |
| Medium | Accessibility service can read all window content without needing to | `accessibility_service_config.xml` | VERIFIED |
| Medium | Firebase Analytics shipped without consent or disclosure, which contradicts the README's privacy section | `app/build.gradle.kts:105-110` | VERIFIED |
| Low | SMS-sent `PendingIntent` wraps an **implicit** intent, so another app could register for it. It's immutable, so the risk is low, but make it explicit | `SMSHelper.kt:85-90` | VERIFIED |
| Low | Cloud backup of the contacts DB (third-party PII). Acceptable, and useful for restore, if disclosed | `data_extraction_rules.xml` | VERIFIED |
| Low | Snapshot Maven repo (`oss.sonatype.org/.../snapshots`) is in `settings.gradle.kts` without need. It's a supply-chain surface, and that host is believed to have been retired | `settings.gradle.kts:13` | Presence VERIFIED; host status NEEDS VERIFICATION |
| Info | `google-services.json` is gitignored and **was never committed**. The full history was searched and no `AIza` key was found, so the "API leak" is not present in git ✅. (Firebase Android API keys aren't true secrets anyway.) | — | VERIFIED |
| Info | `local.properties` is tracked (since the initial commit) and contains a local path with your Windows username. The `.idea/` files are partly tracked | repo | VERIFIED |
| Info | **Build artifacts were committed in history** (dex files and APKs up to 44 MB), so `.git` is 133 MB. A recruiter's clone is slow | git history | VERIFIED |
| ✅ | No deep links. Services aren't exported. The accessibility service is protected by `BIND_ACCESSIBILITY_SERVICE`. The dynamic receiver uses `RECEIVER_NOT_EXPORTED`. No hardcoded API keys or tokens in source. A local DB without encryption is acceptable for this data | — | VERIFIED |

---

## L. Performance Assessment

| Area | Finding | Tag |
|---|---|---|
| Battery | Voice Guard is the main drain (open-vocabulary decoding, a wake lock, restart loops). The others are negligible | Code VERIFIED; magnitude NEEDS VERIFICATION |
| Memory leaks | Vosk recognizers and SpeechService instances pile up on each error or timeout. `Model` is never closed. A widget coroutine scope leaks on each update | VERIFIED |
| Long-running services | Voice Guard, plus an idle sticky restart that holds the wake lock | VERIFIED |
| Main thread | Acceptable. DB work runs on IO; Vosk model loading runs on a raw `Thread` | VERIFIED |
| Recompositions | Animation values are read inside `graphicsLayer` ✅, which avoids recomposition. Two infinite transitions run whenever Home is visible (a minor GPU cost). Tokens are re-allocated in `ContactCard` on every composition (trivial) | VERIFIED |
| Database | One-shot queries on a tiny table. No issue | VERIFIED |
| Location updates | One-shot only. No issue | VERIFIED |
| Speech processing | Continuous full-vocabulary decoding (see F) | VERIFIED |
| Network | Only Firebase | VERIFIED |
| **APK size** | **99 MB debug APK.** That's 68 MB of model plus about 37 MB of `libvosk.so` across four ABIs, plus libjnidispatch for **mips/mips64/armeabi** (dead ABIs), plus all of material-icons-extended without R8 | VERIFIED (built APK inspected) |
| Size remedies | An AAB with ABI splits and R8 would shrink it a lot. Long term, deliver the model through Play Asset Delivery or as an on-demand download | Recommendation |

---

## M. Build & Dependency Assessment

### M.1 Versions [VERIFIED]

| Item | Value | Status |
|---|---|---|
| compileSdk / targetSdk / minSdk | 34 / **34** / 26 | **targetSdk is a Play blocker.** New apps and updates must target API 35 or higher, and the Aug-2026 deadline moves this to API 36 **[NEEDS VERIFICATION in Play Console]** |
| AGP | 8.4.1 | Outdated |
| Gradle | 8.6 | Outdated |
| Kotlin | 1.9.22 (KSP 1.9.22-1.0.17) | Outdated; the K2 / Kotlin 2.x Compose compiler plugin is now standard |
| Compose compiler / BOM | 1.5.8 / 2024.02.00 | Outdated |
| core-ktx | 1.12.0 | Outdated |
| lifecycle (runtime, viewmodel-compose, runtime-compose) | 2.7.0 | Outdated |
| activity-compose | 1.8.2 | Outdated |
| navigation-compose | 2.7.6 | Outdated |
| Room | 2.6.1 | Outdated |
| play-services-location | 21.1.0 | Outdated |
| WorkManager | 2.9.0 | **Unused** |
| DataStore | 1.0.0 | **Unused** |
| Firebase BOM / analytics | 33.1.0 | Unused in code; privacy concern |
| accompanist-permissions | 0.34.0 | Experimental API; fine for now but better replaced with plain ActivityResult APIs |
| vosk-android / JNA | 0.3.47 / 5.13.0 | See 16 KB finding |
| JDK target | 17 | OK |

> Exact "latest" version numbers were not looked up during this audit. Check the current releases when you plan upgrades.

### M.2 Build issues

| Issue | Tag |
|---|---|
| **`libvosk.so` LOAD segments are aligned to 4 KB, not 16 KB** (arm64-v8a and x86_64 checked; `x86_64/libjnidispatch.so` is also 4 KB; `arm64-v8a/libjnidispatch.so` is 64 KB ✅). Google Play requires 16 KB page-size support for apps targeting Android 15+, so **this is a release blocker** if Vosk ships | Alignment VERIFIED; policy applicability NEEDS VERIFICATION |
| Unused dependencies: WorkManager, DataStore, firebase-analytics (no code uses them) | VERIFIED |
| Unused plugin: `kotlin-kapt` (only KSP is used); it slows the build | VERIFIED |
| `android.enableJetifier=true` not needed | VERIFIED (no support-library dependencies seen) |
| Release build: `isMinifyEnabled = false`, **no signingConfig**, no `isShrinkResources` | VERIFIED |
| ProGuard rules: `-keep class androidx.compose.** { *; }` and Gson rules (Gson isn't used). These defeat R8 if minify is enabled | VERIFIED |
| Versioning: `versionCode 1`, `versionName "1.0"`; needs a scheme | VERIFIED |
| Requires `app/google-services.json`, which is gitignored, so **a fresh clone won't build**. The README's install steps are therefore broken | VERIFIED (plugin applied + file ignored) |
| Snapshot repository in `settings.gradle.kts` | VERIFIED |

### M.3 Compiler warnings (from the audit build) [VERIFIED]

- `MainActivity.kt:139` — `Icons.Default.Message` deprecated (use `Icons.AutoMirrored.Filled.Message`)
- `ContactsScreen.kt:232` — `Icons.Default.ArrowBack` deprecated
- `HomeScreen.kt:204`, `HomeScreen.kt:238` — `Icons.Default.VolumeUp` deprecated
- `VoiceGuardService.kt:177` — `requestAudioFocus(listener, int, int)` deprecated

> Android Lint was **not** run during the audit. Running it is a good next step.

---

## N. Testing Assessment

**[VERIFIED] There are no tests: no `src/test` and no `src/androidTest`.** The test dependencies are declared but unused.

| Test type | Present? |
|---|---|
| Unit tests | ❌ |
| Instrumentation tests | ❌ |
| UI (Compose) tests | ❌ |
| Service tests | ❌ |
| Database tests | ❌ |

### Critical gaps, in priority order

1. [ ] SOS orchestration: no contacts, no location, no permission, partial SMS failure, timeout, duplicate triggers.
2. [ ] Location selection logic: staleness and accuracy rules.
3. [ ] SMS message building: length, encoding, content.
4. [ ] Phone normalization and validation.
5. [ ] Room DAO: insert, delete, duplicates, and a migration test once versioned.
6. [ ] Wake-phrase matcher: false-positive and false-negative cases as unit tests on plain strings.
7. [ ] Volume key state machine.
8. [ ] Compose UI tests: permission states, countdown cancel, empty contacts.
9. [ ] Manual device matrix: Android 10, 12, 13, 14, 15 and 16, screen locked, dual-SIM, no SIM, airplane mode, and battery-saver OEMs (Xiaomi, Oppo/OnePlus, Samsung).

The SOS logic currently sits inside a `Service` with helpers created via `new`, so it **can't be unit tested** until it's extracted into a plain class with injected interfaces.

---

## O. Play Store Readiness

### O.1 Technical blockers

- [ ] targetSdk 34 → 35/36, which also brings edge-to-edge and the Android 15/16 behavior changes **[VERIFIED target; deadline NEEDS VERIFICATION]**
- [ ] 16 KB alignment of `libvosk.so` (if Voice Guard ships) **[VERIFIED]**
- [ ] Release signing configuration **[VERIFIED missing]**
- [ ] R8 / resource shrinking **[VERIFIED disabled]**
- [ ] Ship an AAB and reduce size (99 MB debug APK) **[VERIFIED]**
- [ ] SOS failure modes from section E (a safety app that fails silently is also a review and reputation risk) **[VERIFIED]**
- [ ] A fresh clone must build without the private `google-services.json` **[VERIFIED]**

### O.2 Permission and policy declarations

- [ ] SMS permission declaration form **[policy details NEEDS VERIFICATION]**
- [ ] AccessibilityService declaration with prominent disclosure and consent, or drop Volume Guard from v1
- [ ] Foreground service type declarations (location, microphone) with demo videos
- [ ] `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` justification, or switch to the settings intent
- [ ] Advertising-ID declaration (because of Firebase), or remove Firebase

### O.3 Privacy requirements

- [ ] **Privacy policy** (missing; mandatory, especially with SMS, location and microphone)
- [ ] **In-app prominent disclosures** before the permission requests (missing)
- [ ] Data Safety form: location, phone numbers, contacts, audio processed on-device, plus whatever Firebase collects
- [ ] Fix the README's "no analytics, no tracking" claim, which is currently false

### O.4 User-facing disclosures

- [ ] Disclaimer: the app doesn't replace emergency services, SMS can fail, and users should call 112
- [ ] Explanation of carrier SMS charges
- [ ] Explanation of what Voice Guard and Volume Guard do, and their battery and accessibility implications

### O.5 Metadata and operations

- [ ] Store listing assets (screenshots, feature graphic, icon)
- [ ] Localized store descriptions
- [ ] In-app privacy and about screen
- [ ] Crash reporting (missing)
- [ ] Analytics: **not recommended** unless opt-in and genuinely useful
- [ ] Support email and in-app feedback
- [ ] Testing (missing; see N)

### O.6 Account requirement

New personal Play developer accounts must run a **closed test with at least 12 testers for 14 days** before production access. Plan for this. **[NEEDS VERIFICATION for your account type]**

---

## P. Critical Bugs

Ranked by impact.

| # | Bug | Evidence | Impact | Tag |
|---|---|---|---|---|
| 1 | SOS aborts, with no SMS, when FINE location isn't granted | `SOSService.kt:71-80` | Emergency message never sent | VERIFIED |
| 2 | SOS aborts if `startForeground(LOCATION)` throws (Android 14 background-start rules) | `SOSService.kt:83-97` | Voice and volume triggers may silently fail when locked | Code VERIFIED; trigger condition NEEDS VERIFICATION |
| 3 | Stale `lastLocation` is sent as current | `LocationHelper.kt:51-59` | Rescuers go to the wrong place | VERIFIED |
| 4 | False "Alert Sent" (sent status ignored) | `SMSHelper.kt:85-102` | User believes help is coming | VERIFIED |
| 5 | Result notification deleted after 4 s | `SOSService.kt:190-200` | User never sees failure | VERIFIED |
| 6 | Exported widget receiver lets any app trigger SOS | `AndroidManifest.xml:76-82` | Abuse, spam SMS, cost | VERIFIED |
| 7 | Approximate-location users can never pass the permission gate | `MainActivity.kt:104` | App unusable | VERIFIED |
| 8 | Permanent denial leaves the user stuck with no Settings path | `MainActivity.kt:295-309` | App unusable | VERIFIED |
| 9 | `stopSelf()` before `startForeground()` after `startForegroundService()` | `SOSService.kt:78`, `VoiceGuardService.kt:159-168` | App crash | Code VERIFIED; crash NEEDS VERIFICATION |
| 10 | Voice Guard steals audio focus permanently | `VoiceGuardService.kt:176-181` | User's music stops; bad reviews | VERIFIED |
| 11 | Recognizer leaks and duplicate listeners | `VoiceGuardService.kt:170-190` | Battery, mic contention, crashes | VERIFIED |
| 12 | Corrupt or partial model is never re-extracted | `VoiceGuardService.kt:95-98` | Voice Guard broken forever | VERIFIED |
| 13 | Wake phrase mismatch | `VoiceGuardService.kt:237-241` vs UI | Users say the wrong phrase | VERIFIED |
| 14 | Rotating the device during the countdown silently cancels SOS | `HomeScreen.kt:60-61` | SOS lost | VERIFIED |
| 15 | Voice Guard toggle state desyncs from the service | `MainViewModel.kt:21` | Double services or crash on stop | VERIFIED |

---

## Q. Technical Debt

All items **[VERIFIED]**.

- [ ] Dead code: `ShakeDetectorService`, `PermissionHelper`, `sosTriggered` plus the `MainActivity` receiver, `updateContact`, `isPrimary`, `getContactCount`, `deleteAllContacts`
- [ ] Color tokens duplicated in four places. Material theme is configured but bypassed
- [ ] Hardcoded strings
- [ ] No DI, no domain layer, and services built around Handlers
- [ ] Room schema not exported, so there's no migration path
- [ ] Misleading comments, such as "activity trampoline" in the widget, "with grammar", and "FLAG_FROM_BACKGROUND"
- [ ] README contradicts the code in about 8 places: SpeechRecognizer, wake phrase, APK size, analytics, live location, shake detection, "SMS still sends", and that it builds after clone. **This matters for interviews:** a reviewer who reads the code will notice
- [ ] Unused dependencies, the kapt plugin, Jetifier, the snapshot repo, and ProGuard rules for absent libraries
- [ ] Repo hygiene: tracked `local.properties` and `.idea`, build outputs in history, and the 68 MB model stored in plain git
- [ ] Deprecated API usage (icons, `requestAudioFocus`)

---

## R. Recommended Features

### R.1 Must Have (before any public release)

| # | Recommendation | Why it improves the product |
|---|---|---|
| 1 | **A reliable SOS pipeline.** Send the SMS immediately without waiting for location, then send a follow-up SMS once a fresh fix arrives. Never abort because of location permission or FGS-type failure | The core promise is "my contacts will be told", so it can't depend on optional inputs |
| 2 | **Real delivery tracking** (sent-status receiver, per-contact result) and a **persistent result notification** | Users must know whether to call for help another way |
| 3 | **Location freshness rules** (age and accuracy limits), with accuracy and time included in the SMS | A wrong location is worse than no location |
| 4 | **Countdown with cancel for every trigger** (widget, volume, voice), plus an **"I'm safe"** follow-up SMS | False alarms erode contacts' trust, and a retraction message limits panic |
| 5 | **In-context, graceful permissions.** Only SMS and location are needed up front; accept coarse location; provide a Settings deep-link | No dead ends, a better policy posture, and higher conversion |
| 6 | **"Send test alert"** to verify the setup | Users find out about failures before an emergency, not during one |
| 7 | **Contact validation and normalization**, edit, dedupe, and a sensible cap | A mistyped number is a silent failure |
| 8 | **Fix the exported widget receiver; remove unnecessary permissions and Firebase Analytics** (or make it opt-in and disclosed) | Security, and the honesty of the privacy claims |
| 9 | **targetSdk 35/36, edge-to-edge, release signing, R8, AAB** | Play requirements |
| 10 | **Privacy policy, in-app disclosures, a disclaimer, and a 112 shortcut** | Required by policy, and the app should never replace emergency services |
| 11 | **Unit tests for SOS orchestration and location/SMS logic** | These are the parts that must never regress |

### R.2 Should Have

| Recommendation | Why it improves the product |
|---|---|
| **Periodic location updates by SMS** for N minutes after SOS (genuinely "live", offline, no backend) | The person may be moving |
| **Quick Settings tile** and a **notification action** as SOS triggers | Fast access with lower policy risk than Accessibility |
| **Crash reporting** (Crashlytics, or Sentry with PII scrubbing), with a consent toggle | You can't fix field crashes you can't see |
| SOS history log (local only) | Users and contacts can review what was sent and when |
| Onboarding flow | Explains triggers and permissions at the moment they matter |
| Hindi (and later other regional language) localization | The primary audience is in India |
| A fixed light/dark theme and accessibility contrast fixes | Readable in sunlight and for low-vision users |
| Dual-SIM choice | The SMS goes out on a SIM that actually has service |
| Volume Guard kept but hardened (minimal config, disclosure, countdown) | Keeps a useful discreet trigger while reducing review risk |
| Voice Guard rebuilt as an **opt-in beta** (grammar mode, no audio focus, honest battery note, a short countdown) | Delivers the hands-free promise without harming the phone experience |

### R.3 Nice to Have

| Recommendation | Why it improves the product |
|---|---|
| Wear OS trigger | Discreet activation without taking out the phone |
| Safe-walk / check-in timer with auto-SOS | Covers situations where the user can't trigger SOS |
| Fake incoming call | A socially acceptable way out of an uncomfortable situation |
| Customizable message template | Personal and cultural fit |
| Widget states (configuration, contacts missing) | The widget never looks ready when it isn't |

### R.4 Do Not Add (now)

| Feature | Why not |
|---|---|
| **Ambient audio or video recording** | Heavy policy, legal and privacy burden; storage and consent issues |
| **Background location** (`ACCESS_BACKGROUND_LOCATION`) | Hard Play approval and not needed for SOS |
| **Server-side live tracking (Firebase)** | Needs accounts, a backend, data retention policy and security work, and it breaks the strong "offline, private" positioning. Revisit after v1 |
| **Auto-calling police or automatic `CALL_PHONE`** | Liability and false-alarm risk. Offer a dial-intent shortcut instead |
| **Shake-to-SOS** as currently written | dataSync FGS misuse, false positives |
| **"AI" features added only for the name** (scream classification, chatbots) | They raise reliability, battery and privacy risk and don't improve safety outcomes |
| SQLCipher database encryption | The threat model doesn't justify it for a short contact list |
| Ads or ad SDKs | Destroys trust in a safety app and adds policy obligations |

---

## S. Recommended Development Roadmap

**Production-ready → Closed Testing → Play Store Production**

### Phase 0 — Repo hygiene (1–2 days)

- [ ] Untrack `local.properties` and `.idea`
- [ ] Remove the snapshot repo, kapt, Jetifier and unused dependencies
- [ ] Make the build work without `google-services.json` (or remove Firebase)
- [ ] Correct the README so it matches the code
- [ ] Add CI (GitHub Actions: build, lint, unit tests)

*Why: a clean, honest, reproducible repo is the first thing an interviewer sees.*

### Phase 1 — SOS reliability core (highest priority, ~1–2 weeks)

- [ ] Extract an `SosOrchestrator` (pure Kotlin, injected `LocationProvider`, `SmsSender`, `ContactsRepository`, `Clock`)
- [ ] Send immediately, then follow up with location
- [ ] Track per-contact sent results
- [ ] Keep a persistent result notification with "Cancel / I'm safe" actions
- [ ] Persist the SOS state so it survives process death
- [ ] Apply location freshness rules and accept coarse location
- [ ] Use a GSM-7 message
- [ ] Write unit tests for all of the above

*Why: this is the product. Everything else is a trigger into this pipeline.*

### Phase 2 — Permissions, security, policy (~1 week)

- [ ] Contextual permission flows with a Settings fallback
- [ ] Remove `READ_CONTACTS`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK` (if unneeded) and `INTERNET`
- [ ] Fix the widget receiver exposure
- [ ] Strip logs in release
- [ ] Write the privacy policy and in-app disclosures
- [ ] Add the emergency-services disclaimer and a 112 button

*Why: this unblocks the Play declarations and removes dead ends.*

### Phase 3 — Triggers (~1–2 weeks)

- [ ] Widget and volume get a countdown and cancel
- [ ] Restrict the accessibility config to key events and add a disclosure screen
- [ ] Add a Quick Settings tile
- [ ] **Decide on Voice Guard:** either ship v1 without it, or rebuild it (grammar, lifecycle, no audio focus, service-state sync, model integrity check, 16 KB-compatible native libraries) and gate it behind "Beta"

*Why: trigger reliability and policy risk vary a lot. Ship the safe ones first.*

### Phase 4 — UI/UX polish (~1–2 weeks)

- [ ] A real Material 3 theme with tokens
- [ ] String resources plus Hindi
- [ ] Contrast fixes
- [ ] Edge-to-edge and insets
- [ ] A scrollable, responsive Home screen
- [ ] Contact edit and validation
- [ ] Onboarding
- [ ] "Send test alert"
- [ ] SOS history

*Why: a polished, accessible UI drives trust, retention and the resume demo.*

### Phase 5 — Platform and release engineering (~1 week)

- [ ] Upgrade Kotlin to 2.x, AGP and Gradle, and the Compose BOM
- [ ] Raise targetSdk/compileSdk to 35, then 36
- [ ] Enable R8 and resource shrinking with correct keep rules
- [ ] Add signing configs (Play App Signing), an AAB and a versioning scheme
- [ ] Add crash reporting with consent
- [ ] Add a Baseline Profile
- [ ] Run tests on the device matrix

*Why: Play requirements, plus stability you can measure.*

### Phase 6 — Internal → Closed testing (minimum 14 days)

- [ ] Internal track first
- [ ] Closed testing with at least 12 real testers (a requirement for new personal accounts)
- [ ] Run a structured test script covering lock screen, no SIM, dual-SIM, OEM battery savers and Android 14/15/16
- [ ] Submit the SMS, FGS and Accessibility declarations with videos
- [ ] Complete the Data Safety form

*Why: finds OEM-specific failures before real users depend on the app.*

### Phase 7 — Production

- [ ] Staged rollout (10% → 50% → 100%)
- [ ] Monitor Vitals (crashes, ANRs, wake locks)
- [ ] Give support contact details in the listing
- [ ] Keep a changelog

*Why: limits the impact of any regression in a safety-critical app.*

---

## T. Verified Findings vs. Items Needing Verification

### T.1 Verified during the audit (code, build, APK, or git history)

- All file and line references in sections B–Q
- The project builds: `./gradlew --offline :app:assembleDebug` → **BUILD SUCCESSFUL**
- 5 Kotlin deprecation warnings (listed in M.3)
- Debug APK size: **99,223,235 bytes (~99 MB)**
- Native library ELF alignment: `arm64-v8a/libvosk.so` = 4 KB, `x86_64/libvosk.so` = 4 KB, `x86_64/libjnidispatch.so` = 4 KB, `arm64-v8a/libjnidispatch.so` = 64 KB
- The APK ships `libjnidispatch.so` for mips, mips64 and armeabi
- Merged manifest includes the Firebase-added `AD_ID`, AdServices, network-state and install-referrer permissions, plus `RECEIVE_BOOT_COMPLETED` from WorkManager
- `google-services.json` was never committed; no `AIza` key in git history
- `local.properties` tracked since the initial commit; build artifacts present in history; `.git` is 133 MB
- No unit, instrumentation or UI tests exist
- `ShakeDetectorService` is not declared in the manifest

### T.2 Needs verification (device testing or policy check)

| # | Item | How to verify |
|---|---|---|
| 1 | Whether a location-type FGS can start from `VoiceGuardService` / `RakshakAccessibilityService` with the screen locked on Android 14, 15 and 16 | Device test with the screen locked, then watch logcat for `SecurityException` |
| 2 | Crash when `stopSelf()` runs before `startForeground()` after `startForegroundService()` | Deny location, trigger SOS from the widget, then watch logcat |
| 3 | Whether backgrounding during the 3-second countdown can throw `ForegroundServiceStartNotAllowedException` | Tap SOS, press Home immediately (Android 12+) |
| 4 | Dual-SIM / no default SMS SIM behavior of `SmsManager` | Test on dual-SIM devices with "ask every time" |
| 5 | Android's outgoing SMS rate limit and how multipart messages count | Send repeated SOS to several contacts |
| 6 | Whether "rakshak" exists in the Vosk small-en model vocabulary | Inspect the model / test recognition |
| 7 | Real-world Voice Guard false positives/negatives and battery drain | Field tests; Battery Historian / Android Studio profiler |
| 8 | Volume-key collision with the Accessibility Shortcut and OEM shortcuts; behavior with the screen locked per OEM | Device tests (Pixel, Samsung, Xiaomi, OnePlus) |
| 9 | Possible stuck key state in the volume detector | Stress test rapid presses |
| 10 | Room WAL file excluded from backup | `adb shell bmgr` backup/restore test |
| 11 | Visual issues: white launch flash, adaptive-icon green grid background, notification icon rendering | Install and observe |
| 12 | Touch target hit areas of the 32/36 dp icon buttons | Layout Inspector / Accessibility Scanner |
| 13 | Current Play policy: SMS permission exception list, Accessibility API rules, battery-optimization exemption rules | Read the current Play Policy Center pages |
| 14 | Current target-API deadline (35 vs 36) and 16 KB page-size enforcement for this app | Play Console warnings / policy pages |
| 15 | Closed-testing requirement (12 testers / 14 days) for your account type | Play Console |
| 16 | Status of the `oss.sonatype.org` snapshots host | Try resolution without the cache |
| 17 | Exact latest versions of dependencies before upgrading | Check official release notes |
| 18 | Android Lint results (not run) | `./gradlew :app:lintDebug` |

---

*End of audit. No source code, Gradle files, manifests, resources or configuration files were modified while producing this report.*
