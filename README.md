# 🛡️ Rakshak — Women Safety Application

[![Android CI](https://github.com/Vaibhav-P1/Rakshak-AI/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Vaibhav-P1/Rakshak-AI/actions/workflows/android-ci.yml)

**Rakshak** is a personal safety app for Android. It sends emergency SMS alerts with your location to trusted contacts. You can trigger it in several ways, and the core SOS flow works offline (SMS + GPS, no internet, no servers).

> **Project status: in active development, not yet production-ready.**
> A full production-readiness audit lives in [`RAKSHAK_PRODUCTION_AUDIT.md`](RAKSHAK_PRODUCTION_AUDIT.md). It covers known reliability bugs, Play Store blockers and the development roadmap. See [Known Limitations](#-known-limitations) below before relying on the app.

---

## ✨ Features

### SOS Triggers
- 🚨 **SOS Button** — Large animated in-app button with a 3-second countdown, **Cancel**, and **Send Now**
- 🎤 **Voice Guard** — Offline speech recognition ([Vosk](https://alphacephei.com/vosk/)). It triggers SOS when it hears the word **"help" three times in one phrase** (e.g. *"help help help"*). It runs as a foreground service while enabled
- 🔊 **Volume Guard** — Press **Volume Up + Volume Down together** to trigger SOS (requires enabling Rakshak's Accessibility Service)
- 📲 **Home Screen Widget** — 4×2 widget. **One tap sends SOS immediately** (no countdown)

> Only the in-app button has a countdown/cancel step. Voice, Volume and Widget triggers send immediately.

### Core Functionality
- 📍 **Location in SMS** — The alert includes a Google Maps link for the location obtained at trigger time. It is a **single location snapshot, not continuous live tracking**
- 👥 **Emergency Contacts** — Add contacts manually or pick them from your phone. Delete with confirmation (editing is not yet supported)
- 🔔 **Status Notifications** — Notification updates through each stage: Triggered → Getting Location → Sending SMS → Result
- 🌙 **Dark UI** — Built with Jetpack Compose
- 🔋 **Battery Optimization Banner** — Shown while Voice Guard is on, linking to system settings
- ♿ **Accessibility Setup Banner** — Guides you to enable Volume Guard

### Technical Highlights
- ✅ Core SOS flow is offline — SMS + GPS, no network calls, no backend
- ✅ No analytics or tracking SDKs
- ✅ All data stored locally (Room)
- ✅ Foreground service types declared for Android 14 (`location`, `microphone`)
- ✅ Kotlin, Jetpack Compose, Room, StateFlow, Coroutines
- ✅ CI on every push / PR: build, Android Lint, unit tests

---

## 📱 Screens

| Screen | Description |
|--------|-------------|
| Permission Screen | Lists required permissions with explanations; the app requires all of them before continuing |
| Home Screen | SOS button, Voice Guard toggle, Volume Guard status, emergency contacts summary |
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
| `ACCESS_FINE_LOCATION` | GPS location for the SOS message |
| `ACCESS_COARSE_LOCATION` | Approximate location |
| `SEND_SMS` | Send emergency alerts to contacts |
| `RECORD_AUDIO` | Microphone for Voice Guard |
| `READ_CONTACTS` | Currently requested for the contact picker (planned for removal — the system picker doesn't need it) |
| `FOREGROUND_SERVICE` | Run SOS and Voice Guard services |
| `FOREGROUND_SERVICE_LOCATION` | Location foreground service type (Android 14) |
| `FOREGROUND_SERVICE_MICROPHONE` | Microphone foreground service type (Android 14) |
| `FOREGROUND_SERVICE_DATA_SYNC` | Not used by active code (planned for removal) |
| `POST_NOTIFICATIONS` | Show SOS / service notifications (Android 13+) |
| `WAKE_LOCK` | Keeps CPU awake for Voice Guard |
| `INTERNET` | Not used by app code (planned for removal) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Battery optimization banner |
| `BIND_ACCESSIBILITY_SERVICE` | Volume key SOS trigger (system binds the service) |

---

## 📖 User Guide

### Setting Up
1. Open the app and grant the requested permissions
2. Go to Emergency Contacts → add at least one contact (include the country code, e.g. `+91`)
3. For Volume Guard: tap the "Enable" banner → find Rakshak in Accessibility settings → enable it
4. For Voice Guard: toggle it on from the home screen. If prompted, use the battery banner to allow background operation

### SOS Triggers

**Button:** Tap the red SOS button → 3-second countdown → alert sends. Tap **Send Now** to skip the countdown or **Cancel** to abort.

**Voice Guard:** Enable the toggle, then say **"help help help"** clearly. There is a 10-second cooldown before it can trigger again. The first start extracts the bundled voice model (~68 MB) to internal storage, which takes a moment.

**Volume Guard:** With the Accessibility Service enabled, press Volume Up and Volume Down at the same time.

**Widget:** Long-press the home screen → Widgets → Rakshak → drag to the home screen → tap SOS.

### What Happens When SOS Triggers
1. The SOS foreground service starts and loads your emergency contacts
2. It requests your location (up to a 10-second timeout; a recently cached location may be used)
3. It sends an SMS to every contact with an emergency message and a Google Maps link (or "Location unavailable")
4. Notifications update through each stage
5. The service stops about 4 seconds after finishing, and the notification is removed

---

## 🏗️ Project Structure

```
Rakshak/
├── .github/workflows/android-ci.yml     # CI: build, lint, unit tests
├── app/
│   ├── lint-baseline.xml                # Known lint issues (to be burned down)
│   ├── src/main/
│   │   ├── assets/vosk-model-small-en-us-0.15/   # Offline speech model (~68 MB)
│   │   ├── java/com/safety/rakshak/
│   │   │   ├── data/                    # Room entity, DAO, repository, database
│   │   │   ├── service/
│   │   │   │   ├── SOSService.kt
│   │   │   │   ├── VoiceGuardService.kt
│   │   │   │   ├── RakshakAccessibilityService.kt
│   │   │   │   └── ShakeDetectorService.kt   # Experimental, NOT registered/active
│   │   │   ├── ui/                      # HomeScreen, ContactsScreen, theme
│   │   │   ├── utils/                   # LocationHelper, SMSHelper, PermissionHelper
│   │   │   ├── viewmodel/MainViewModel.kt
│   │   │   ├── widget/Soswidget.kt      # SOSWidget
│   │   │   └── MainActivity.kt          # Navigation + permission screen
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── RAKSHAK_PRODUCTION_AUDIT.md          # Production-readiness audit & roadmap
└── build.gradle.kts
```

---

## ⚙️ Customization

### Voice trigger phrase
Edit `checkForWakeWord()` in `VoiceGuardService.kt`. It currently counts occurrences of the word "help":
```kotlin
val helpCount = text.split(Regex("\\s+")).count { it == "help" }
if (helpCount >= 3) { /* trigger SOS */ }
```

### SOS countdown duration
Edit `HomeScreen.kt`:
```kotlin
var sosCountdown by remember { mutableIntStateOf(3) } // seconds
```

### SMS message
Edit `SMSHelper.kt` (`sendSOSMessage`).

---

## ⚠️ Known Limitations

These are documented in detail in [`RAKSHAK_PRODUCTION_AUDIT.md`](RAKSHAK_PRODUCTION_AUDIT.md) and are being fixed in phases:

- **SOS is not sent if precise location permission is missing.** The service aborts before sending SMS
- **"Alert Sent" means the SMS was handed to the system, not confirmed sent.** Delivery results are not checked yet (e.g. no SIM / no signal can still show success)
- A **cached, possibly outdated location** may be used
- The result notification is removed about 4 seconds after SOS finishes
- Choosing **"Approximate location"** on Android 12+ keeps the app on the permission screen
- Voice, Volume and Widget triggers have **no countdown/cancel**
- Voice Guard requests audio focus, which can pause music playback, and it uses significant battery
- Volume Guard may not work on the lock screen on some OEM builds (e.g. OxygenOS)
- Not yet compliant with current Google Play target-SDK and 16 KB page-size requirements
- No automated tests yet (CI runs the test task so tests are enforced as they are added)

---

## 🔧 Troubleshooting

### SOS Button Disabled
- Add at least one emergency contact

### No Notification When SOS Triggered
- Settings → Apps → Rakshak → Notifications → make sure notifications are enabled

### Location Unavailable
- Enable location services in device settings and move to an open area
- If GPS is off, SMS is still sent with "Location unavailable"
- If the location **permission** is missing, SOS currently does not send (see Known Limitations)

### SMS Not Sending
- Check phone numbers include the country code (e.g. +91 for India)
- Check the SMS permission is granted
- Make sure an active SIM with SMS service is present

### Voice Guard Not Triggering
- Say "help" three times in one phrase, then pause briefly
- If the notification shows "Model load failed", clear app storage and re-enable Voice Guard

---

## 🔒 Privacy & Security

- Emergency contacts are stored locally on the device (Room). They are included in Android's system backup
- No analytics, no tracking SDKs, no backend servers
- Location is accessed only when SOS is triggered
- SMS is sent directly from the device SIM — no gateway or relay
- No API keys or secrets are required to build

---

## 🗺️ Roadmap

See section **S. Recommended Development Roadmap** in [`RAKSHAK_PRODUCTION_AUDIT.md`](RAKSHAK_PRODUCTION_AUDIT.md):

- **Phase 0** — Repo hygiene & CI ✅
- **Phase 1** — SOS reliability core
- **Phase 2** — Permissions, security, Play policy
- **Phase 3** — Trigger hardening (widget/volume countdown, Voice Guard decision, Quick Settings tile)
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
| Voice | Vosk (offline speech recognition) |
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
