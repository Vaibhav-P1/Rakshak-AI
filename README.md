# 🛡️ Rakshak — Women Safety Application

[![Android CI](https://github.com/Vaibhav-P1/Rakshak-AI/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Vaibhav-P1/Rakshak-AI/actions/workflows/android-ci.yml)

**Rakshak** is a personal safety app for Android. It sends emergency SMS alerts with your location to trusted contacts. You can trigger it in several ways, and the core SOS flow works offline (SMS + GPS, no internet, no servers).

> **Project status: in active development, not yet production-ready.**
> A full production-readiness audit lives in [`RAKSHAK_PRODUCTION_AUDIT.md`](RAKSHAK_PRODUCTION_AUDIT.md). It covers known reliability bugs, Play Store blockers and the development roadmap. See [Known Limitations](#-known-limitations) below before relying on the app.

---

## ✨ Features

### SOS Triggers
Every trigger starts the **same 3-second countdown** with **Cancel** and **Send now** (shown as a notification, so it works on the lock screen, and as a dialog when the app is open). Nothing is sent, and no SOS session is created, until the countdown completes.
- 🚨 **SOS Button** — Large animated in-app button
- 🔊 **Volume Guard** — Press **Volume Up + Volume Down together** (requires enabling Rakshak's Accessibility Service, which only filters key events and cannot read the screen. A disclosure explains this before Settings opens)
- 📲 **Home Screen Widget** — 4×2 widget, one tap starts the countdown. Its contact count refreshes whenever contacts change
- ⚙️ **Quick Settings tile** — "Rakshak SOS". On Android 13+ Home offers a one-tap add. Android may ask you to unlock the phone before it runs

> Voice Guard (always-on microphone) was **removed from v1**: battery cost, false triggers and Play policy risk. The app no longer uses the microphone. Any SOS can be followed by **"I'm safe"** from the notification.

### Core Functionality
- 🚨 **Reliable SOS pipeline** — The alert SMS is sent **immediately** and never waits for or depends on location. If location permission is off or no fix is available, the SMS still goes out
- 📍 **Location in SMS** — A fresh location (< 2 min old) goes in the first SMS with accuracy and time. Otherwise a fresh fix is requested and sent as a follow-up SMS. If none arrives, an older fix is sent **clearly labelled with its age**, or contacts are told the location is unavailable. Approximate location is supported. This is a location snapshot, **not continuous live tracking**
- ✅ **Per-contact SMS results** — Each SMS waits for the network's sent-result. Failures are retried once and reported as sent / not confirmed / failed
- 🔔 **Persistent result notification** — Shows exactly what happened and stays until dismissed. It has an **"I'm safe"** action that tells alerted contacts to stand down. On failure it offers **Call 112** and **Open SMS app**
- 💾 **Survives process death** — SOS progress is saved. If Android kills the app mid-SOS, the service restarts and resumes without re-alerting contacts who already got the SMS
- ✉️ **Single-part SMS** — Messages use GSM-7 text only (no emoji) and fit in one 160-character SMS
- 👥 **Emergency Contacts** — Add contacts manually or pick them from your phone. Delete with confirmation (editing is not yet supported)
- 🌙 **Dark UI** — Built with Jetpack Compose
- ♿ **Accessibility Setup Banner** — Guides you to enable Volume Guard

### Technical Highlights
- ✅ Core SOS flow is offline — SMS + GPS, no network calls, no backend
- ✅ No analytics or tracking SDKs, and **no internet permission**: the app cannot send data off the device
- ✅ All data stored locally (Room)
- ✅ Foreground service types for Android 14: SOS uses `location`, with `shortService` as a fallback
- ✅ SOS logic in a pure-Kotlin, unit-tested `SosOrchestrator`; one shared `SosCountdown` and a unit-tested `VolumeChordDetector`
- ✅ Kotlin, Jetpack Compose, Room, StateFlow, Coroutines
- ✅ CI on every push / PR: build, unit tests, instrumentation-test compile, Android Lint

---

## 📱 Screens

| Screen | Description |
|--------|-------------|
| Introduction (first launch) | What the app does, emergency-services disclaimer and a privacy summary. Requests no permissions |
| About and Privacy | What is stored and why, per feature. Reached from the (i) icon on Home |
| Home Screen | SOS button, **Call 112**, Volume Guard status, add-tile prompt, setup banner, emergency contacts summary |
| Contacts Screen | Add/delete emergency contacts, pick from phone contacts |

---

## 📸 Screenshots

<p align="center">
  <img src="https://github.com/user-attachments/assets/44e41503-0963-463a-be75-d28cf198af5e" width="250"/>
  <img src="https://github.com/user-attachments/assets/565d7c6b-0dae-4241-af4b-db86d1c7d0c2" width="250"/>
  <img src="https://github.com/user-attachments/assets/3fc8e176-b9dc-4e72-861b-3e7360209b1a" width="250"/>
  <img src="https://github.com/user-attachments/assets/2243aece-d1da-407d-afca-d1fbfe89fed2" width="250"/>
  <img src="https://github.com/user-attachments/assets/de9e6a52-e771-4347-b916-fa10a1f83720" width="250"/>
</p>

---

## 🚀 Getting Started

### Prerequisites
- Android Studio (recent stable version)
- Android SDK API 34
- JDK 17
- No API keys, Firebase config, or other secrets are required

### Build

```bash
git clone https://github.com/Vaibhav-P1/Rakshak-AI.git
cd Rakshak-AI
./gradlew :app:assembleDebug        # build debug APK
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:lintDebug            # Android Lint (fails on new issues; existing ones are in app/lint-baseline.xml)
```

Or open the project in Android Studio and run the `app` configuration.

> ⚠️ Test on a real device with a SIM card. SMS and GPS do not work properly on the emulator.
> ⚠️ Triggering SOS sends **real SMS messages** to your saved contacts. Use test contacts while developing.

---

## 🔐 Permissions

| Permission | Purpose |
|------------|---------|
| `SEND_SMS` | Send emergency alerts to contacts. **Required** for SOS |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Location in the SOS message. Optional: approximate is enough, and SOS still sends without it |
| `POST_NOTIFICATIONS` | SOS progress/result notifications (Android 13+). Optional |
| `FOREGROUND_SERVICE` | Run the SOS service |
| `FOREGROUND_SERVICE_LOCATION` | Location foreground service type (Android 14) |
| `BIND_ACCESSIBILITY_SERVICE` | Volume key SOS trigger (system binds the service; enabled by the user in Settings) |
| `BIND_QUICK_SETTINGS_TILE` | Quick Settings tile (held by the system, not requested from the user) |

Permissions are requested **in context, with an explanation first**, and the app never blocks on them. Removed in Phase 2: `READ_CONTACTS` (the system contact picker doesn't need it), `INTERNET`, `FOREGROUND_SERVICE_DATA_SYNC` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Removed in Phase 3 with Voice Guard: `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE` and `WAKE_LOCK`.

---

## 📖 User Guide

### Setting Up
1. Open the app. After a short introduction, a setup dialog explains each permission before Android asks. **Allow SMS** at minimum (required for SOS); location and notifications are recommended. You can return to this from the setup banner at any time. If a permission was blocked, the dialog offers **Open Settings**
2. Go to Emergency Contacts → add at least one contact (include the country code, e.g. `+91`)
3. For Volume Guard: tap the "Enable" banner → read the explanation → find Rakshak in Accessibility settings → enable it
4. For the Quick Settings tile: tap "Add" on Home (Android 13+), or edit your Quick Settings panel and drag "Rakshak SOS" in

### SOS Triggers

**Button:** Tap the red SOS button → 3-second countdown → alert sends. Tap **Send now** to skip the countdown or **Cancel** to abort.

**Volume Guard:** With the Accessibility Service enabled, press Volume Up and Volume Down at the same time. Holding the keys does not trigger twice, and cancelling while still holding them does not restart the countdown.

**Widget:** Long-press the home screen → Widgets → Rakshak → drag to the home screen → tap SOS.

**Quick Settings tile:** Open Quick Settings → tap "Rakshak SOS".

**Cancelling:** use the **Cancel** button on the countdown notification or in the app's countdown dialog. If the app is killed during the countdown, Android restarts the SOS and the countdown begins again, so an SOS is never silently lost.

### What Happens When SOS Triggers
1. The SOS foreground service starts (type `location`; if Android refuses that, `shortService` on Android 14+) and runs the 3-second countdown. Cancel here stops everything and nothing is sent
2. After the countdown it loads your emergency contacts and **sends the alert SMS to every contact immediately.** If a fresh location (< 2 min) is cached, it's included; otherwise the SMS says your location will follow, or that it's unavailable if location permission is off
3. In parallel it requests a fresh location (up to 30 s) and sends it as a follow-up SMS. If none arrives, it sends the last known location labelled with its age, or says location is unavailable
4. It waits for each SMS's sent-result from the network, retries failures once after 10 s, and saves progress after each step
5. A result notification stays on screen with what happened and an **"I'm safe"** action. On failure it offers **Call 112** and **Open SMS app**

---

## 🏗️ Project Structure

```
Rakshak/
├── .github/workflows/android-ci.yml     # CI: build, unit tests, lint
├── docs/PRIVACY_POLICY.md               # Draft privacy policy (owner to finalize and host)
├── app/
│   ├── lint-baseline.xml                # Known lint issues (to be burned down)
│   ├── src/main/
│   │   ├── java/com/safety/rakshak/
│   │   │   ├── data/                    # Room entity, DAO, repository, database
│   │   │   ├── sos/                     # SOS pipeline (pure Kotlin, unit-tested)
│   │   │   │   ├── SosOrchestrator.kt   # Send → locate → follow-up → persist
│   │   │   │   ├── SosCountdown.kt      # The one 3-second countdown every trigger uses
│   │   │   │   ├── VolumeChordDetector.kt  # Volume Up + Down logic (unit-tested)
│   │   │   │   ├── SosMessages.kt       # GSM-7, single-part SMS texts
│   │   │   │   ├── SosModels.kt / SosPorts.kt / SosSessionCodec.kt
│   │   │   │   └── platform/            # Android impls: SmsManager, Fused location, Room, prefs
│   │   │   ├── service/
│   │   │   │   ├── SOSService.kt        # Foreground service running the orchestrator
│   │   │   │   ├── SosNotifications.kt  # Progress + persistent result notifications
│   │   │   │   ├── RakshakAccessibilityService.kt   # Volume Guard (key events only)
│   │   │   │   ├── SosTileService.kt                # Quick Settings tile
│   │   │   ├── ui/                      # HomeScreen, ContactsScreen, theme
│   │   │   ├── permissions/             # In-context permission logic (pure core is unit-tested)
│   │   │   ├── viewmodel/MainViewModel.kt
│   │   │   ├── widget/Soswidget.kt      # SOSWidget
│   │   │   └── MainActivity.kt          # Navigation + permission screen
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   ├── src/test/                        # JVM unit tests (SOS pipeline, messages, codec)
│   ├── src/androidTest/                 # On-device tests (SMS gateway, service end-to-end)
│   └── build.gradle.kts
├── RAKSHAK_PRODUCTION_AUDIT.md          # Production-readiness audit & roadmap
└── build.gradle.kts
```

---

## ⚙️ Customization

### SOS countdown duration
Edit `DEFAULT_SECONDS` in `sos/SosCountdown.kt`. It applies to every trigger.

### SMS messages
Edit `sos/SosMessages.kt`. Keep messages GSM-7 only and at most 160 characters; `SosMessagesTest` enforces both.

### SOS timing
Edit `SosConfig` in `sos/SosModels.kt` (fresh-location age, location timeout, retry delay, resume window).

---

## ⚠️ Known Limitations

These are documented in detail in [`RAKSHAK_PRODUCTION_AUDIT.md`](RAKSHAK_PRODUCTION_AUDIT.md) and are being fixed in phases:

- "Sent" means the **network accepted** the SMS. Delivery to the recipient's phone is not confirmed (no delivery reports yet)
- If Android won't let the SOS run as a *location* foreground service (e.g. some background triggers on Android 14+), it runs as a short service. The SMS is still sent, but a fresh location is usually unavailable, so contacts get the last known location (labelled with its age) or "unavailable"
- The first-launch setup is a dialog on the Home screen. The Home layout is not yet scrollable or adaptive for very small screens (Phase 4)
- The countdown is cancelled from the notification or the dialog. On the lock screen, Android may require unlocking before it runs a Quick Settings tile
- Dual-SIM: SMS uses the system's default SMS SIM
- The emergency number in messages and the **Call 112** action is fixed to 112 (India)
- Volume Guard may not work on the lock screen on some OEM builds (e.g. OxygenOS). The accessibility config was trimmed to key events only in Phase 3; verify on the target phones (see `docs/PHASE3_DEVICE_TEST.md`)
- Not yet compliant with current Google Play target-SDK requirements (Phase 5)
- Automated tests cover the SOS pipeline, the countdown and the volume-key logic. UI, the service and the tile have no automated tests yet

---

## 🔧 Troubleshooting

### SOS Button Disabled
- Add at least one emergency contact

### No Notification When SOS Triggered
- Settings → Apps → Rakshak → Notifications → make sure notifications are enabled

### Location Unavailable
- Enable location services in device settings and move to an open area
- If GPS is off or the location permission is missing, the SMS is still sent and says location is unavailable

### SMS Not Sending
- Check phone numbers include the country code (e.g. +91 for India)
- Check the SMS permission is granted
- Make sure an active SIM with SMS service is present
- Check the SOS result notification: it shows which contacts failed and offers **Open SMS app** as a fallback

### Volume Guard Not Triggering
- Check Rakshak is enabled in Settings → Accessibility, and that no other accessibility shortcut uses the volume keys
- Press both keys at the same time and hold them briefly

---

## 🔒 Privacy & Security

- Emergency contacts are stored locally on the device (Room). They are included in Android's system backup
- No analytics, no tracking SDKs, no backend servers, and **no internet permission**
- Location is accessed only when SOS is triggered
- Rakshak does not use the microphone. The accessibility service only filters Volume Up/Down key events and cannot read the screen
- The widget receiver is not exported, so other apps cannot trigger an SOS through it
- Draft privacy policy: [`docs/PRIVACY_POLICY.md`](docs/PRIVACY_POLICY.md) (contact details and hosting still to be filled in by the owner)
- SMS is sent directly from the device SIM — no gateway or relay
- No API keys or secrets are required to build

---

## 🗺️ Roadmap

See section **S. Recommended Development Roadmap** in [`RAKSHAK_PRODUCTION_AUDIT.md`](RAKSHAK_PRODUCTION_AUDIT.md):

- **Phase 0** — Repo hygiene & CI ✅
- **Phase 1** — SOS reliability core ✅
- **Phase 2** — Permissions, security, Play policy ✅
- **Phase 3** — Trigger hardening: one countdown for every trigger, trimmed Volume Guard, Quick Settings tile, Voice Guard removed ✅ (physical-device check pending)
- **Phase 4** — UI/UX polish, accessibility, localization
- **Phase 5** — Platform upgrades & release engineering
- **Phase 6–7** — Closed testing → Play Store production

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | ViewModel + Repository (MVVM-style) |
| Database | Room (SQLite) |
| Async | Kotlin Coroutines + Flow |
| Location | FusedLocationProviderClient |
| Volume Keys | Android AccessibilityService |
| Widget | AppWidget API |
| Services | Android Foreground Services |
| Navigation | Navigation Compose |
| Permissions | Accompanist Permissions |
| CI | GitHub Actions |

---

## 📄 License

MIT License — feel free to use, modify and distribute.

---

## 👨‍💻 Developer

Built by Vaibhav Pandey and team — Android Developer

---

**Made with ❤️ for Women's Safety**

*"Safety is not a gadget but a state of mind."*
