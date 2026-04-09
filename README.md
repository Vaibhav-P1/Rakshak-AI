# 🛡️ Rakshak — Women Safety Application

**Rakshak** is a personal safety application for Android that provides emergency SOS alerts through multiple trigger methods, working fully offline without any internet connection or external APIs.

---

## ✨ Features

### SOS Triggers
- 🚨 **SOS Button** — Large animated button with 3-second countdown and instant "Send Now" option
- 🎤 **Voice Guard** — Say "Help Rakshak" hands-free to trigger SOS
- 🔊 **Volume Guard** — Press Volume Up + Volume Down simultaneously for silent SOS trigger
- 📲 **Home Screen Widget** — 4x2 widget for one-tap SOS without opening the app

### Core Functionality
- 📍 **Live Location Sharing** — Sends Google Maps URL with real-time GPS coordinates via SMS
- 👥 **Emergency Contacts** — Add contacts manually or pick from phone contacts
- 🔔 **Smart Notifications** — Progressive notifications through each SOS stage
- 🌙 **Dark Minimal UI** — Clean dark theme built with Jetpack Compose
- 🔋 **Battery Optimization Guidance** — In-app banner guides user through device-specific setup
- ♿ **Accessibility Service Setup** — In-app banner for Volume Guard activation

### Technical Highlights
- ✅ Fully offline — SMS + GPS, no internet required
- ✅ Android 14 compliant — proper foreground service types
- ✅ MVVM architecture with Room + StateFlow + Coroutines
- ✅ No external APIs or paid services
- ✅ All data stored locally — no analytics, no tracking

---

## 📱 Screens

| Screen | Description |
|--------|-------------|
| Home Screen | SOS button, Voice Guard toggle, Volume Guard status, emergency contacts summary |
| Contacts Screen | Add/delete emergency contacts, pick from phone contacts |
| Permission Screen | Clear permission explanations with one-tap grant |

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
- Android Studio Hedgehog 2023.1.1 or later
- Android SDK API 34 (Android 14)
- Kotlin 1.9.x
- Gradle 8.x
- JDK 17

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/Vaibhav-P1/Rakshak-AI.git
   ```

2. **Open in Android Studio**
   ```
   File → Open → Select Rakshak folder
   ```

3. **Sync Gradle**
   Android Studio will sync automatically. If not, click "Sync Now".

4. **Build the project**
   ```
   Build → Make Project  (Ctrl+F9)
   ```

5. **Run on device**
   ```
   Run → Run 'app'  (Shift+F10)
   ```
   > ⚠️ Test on a real device — SMS and GPS do not work on emulator.

---

## 🔐 Permissions

| Permission | Purpose |
|------------|---------|
| ACCESS_FINE_LOCATION | GPS location for SOS alert |
| ACCESS_COARSE_LOCATION | Network-based location fallback |
| SEND_SMS | Send emergency alerts to contacts |
| RECORD_AUDIO | Microphone for Voice Guard |
| READ_CONTACTS | Pick contacts from phone |
| FOREGROUND_SERVICE | Run services in background |
| FOREGROUND_SERVICE_LOCATION | Location foreground service type (Android 14) |
| FOREGROUND_SERVICE_MICROPHONE | Microphone foreground service type (Android 14) |
| POST_NOTIFICATIONS | Show SOS notifications (Android 13+) |
| WAKE_LOCK | Keep CPU alive for background detection |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Prevent system from killing services |
| BIND_ACCESSIBILITY_SERVICE | Volume key SOS trigger |

---

## 📖 User Guide

### Setting Up

1. Open the app and grant all required permissions
2. Go to Emergency Contacts → add at least one contact
3. For Volume Guard: tap the green "Enable" banner → find Rakshak in Accessibility settings → enable it
4. For background detection: tap the orange battery banner → select "Don't optimize" for Rakshak
5. For Voice Guard: toggle it on from the home screen

### SOS Triggers

**Button:**
Tap the red SOS button → 3-second countdown → alert sends. Tap "Send Now" to skip countdown.

**Voice Guard:**
Enable the toggle → say "Help Rakshak" clearly → SOS triggers automatically. 10-second cooldown before it can trigger again.

**Volume Guard:**
Enable Accessibility Service → press Volume Up and Volume Down simultaneously → SOS triggers anywhere, anytime.

**Widget:**
Long press home screen → Widgets → Rakshak → drag to home screen → tap SOS button.

### What Happens When SOS Triggers

1. Gets your GPS location (parallel dual-strategy, 10-second timeout)
2. Sends SMS to all emergency contacts with emergency message + Google Maps URL
3. Notification updates through each stage: Triggered → Getting Location → Sending SMS → Alert Sent
4. Service stops automatically 4 seconds after completion

---

## 🏗️ Project Structure

```
Rakshak/
├── app/
│   ├── src/main/
│   │   ├── java/com/safety/rakshak/
│   │   │   ├── data/
│   │   │   │   ├── EmergencyContact.kt
│   │   │   │   ├── EmergencyContactDao.kt
│   │   │   │   ├── EmergencyContactRepository.kt
│   │   │   │   └── RakshakDatabase.kt
│   │   │   ├── service/
│   │   │   │   ├── SOSService.kt
│   │   │   │   ├── VoiceGuardService.kt
│   │   │   │   └── RakshakAccessibilityService.kt
│   │   │   ├── ui/
│   │   │   │   ├── HomeScreen.kt
│   │   │   │   ├── ContactsScreen.kt
│   │   │   │   └── theme/
│   │   │   ├── utils/
│   │   │   │   ├── LocationHelper.kt
│   │   │   │   ├── SMSHelper.kt
│   │   │   │   └── PermissionHelper.kt
│   │   │   ├── viewmodel/
│   │   │   │   └── MainViewModel.kt
│   │   │   ├── widget/
│   │   │   │   └── SOSWidget.kt
│   │   │   └── MainActivity.kt
│   │   ├── res/
│   │   │   ├── drawable/
│   │   │   │   ├── widget_background.xml
│   │   │   │   └── widget_sos_button_bg.xml
│   │   │   ├── layout/
│   │   │   │   └── widget_sos.xml
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   └── themes.xml
│   │   │   └── xml/
│   │   │       ├── accessibility_service_config.xml
│   │   │       └── sos_widget_info.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
└── build.gradle.kts
```

---

## ⚙️ Customization

### Change Wake Words
Edit `VoiceGuardService.kt`:
```kotlin
private val WAKE_WORDS = listOf("help rakshak", "rakshak help", "help rakshaak")
```

### Change SOS Countdown Duration
Edit `HomeScreen.kt`:
```kotlin
var sosCountdown by remember { mutableIntStateOf(3) } // seconds
```

### Customize SMS Message
Edit `SMSHelper.kt`:
```kotlin
val message = """
    🚨 EMERGENCY ALERT FROM RAKSHAK 🚨
    I need immediate help!
    My location: $locationUrl
    Please contact me immediately.
""".trimIndent()
```

### Adjust Shake Detection Sensitivity
If using ShakeDetectorService, edit `ShakeDetectorService.kt`:
```kotlin
private val SHAKE_THRESHOLD = 20f   // lower = more sensitive
private val REQUIRED_SHAKES  = 3    // number of shakes needed
private val SHAKE_WINDOW_MS  = 3000L // time window in ms
```

---

## 🔧 Troubleshooting

### SOS Button Disabled
- Add at least one emergency contact
- Verify SMS permission is granted

### Voice Guard Beep Sound
- This is Android's built-in microphone privacy indicator (Android 12+)
- It plays once per listening session — this is expected behavior and cannot be fully suppressed
- Each session lasts up to 6 seconds of silence before restarting

### Volume Guard Not Working on Lock Screen
- This is a known OEM restriction on OnePlus/OPPO (OxygenOS) devices
- Works correctly on Samsung One UI and Stock Android (Pixel)
- The AccessibilityService is active — the restriction is at manufacturer OS level

### No Notification When SOS Triggered
- Go to Settings → Apps → Rakshak → Notifications → ensure it is enabled
- Disable battery optimization for Rakshak (use the in-app banner)

### Location Unavailable
- Enable GPS in device settings
- Move to an open area for better GPS signal
- SMS will still send even if location is unavailable (without the Maps link)

### SMS Not Sending
- Verify phone numbers include correct country code (e.g. +91 for India)
- Check SMS permission is granted
- Ensure active SIM with SMS plan

---

## 📊 Performance

| Metric | Value |
|--------|-------|
| SOS to SMS (with location) | 4–5 seconds |
| SOS to SMS (without location) | 2–3 seconds |
| Voice detection latency | 1–2 seconds |
| Volume key trigger latency | < 500ms |
| App cold start | < 2 seconds |
| APK size | ~15 MB |

---

## 🔒 Privacy & Security

- All data stored locally using Room Database
- No internet connection required for core features
- No analytics, no data collection, no third-party servers
- Location accessed only during SOS trigger
- SMS sent directly from device SIM — no gateway or relay

---

## 🔮 Planned Features

- [ ] Porcupine/Vosk wake word engine for silent always-on detection
- [ ] Fake call simulation for discreet escape
- [ ] Ambient audio recording during SOS
- [ ] Timer-based auto-SOS
- [ ] Live location sharing via Firebase
- [ ] Safe Walk mode with auto-SOS on inactivity
- [ ] Wear OS companion app
- [ ] Multi-language support (Hindi, Tamil, Telugu)
- [ ] iOS version

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM |
| Database | Room (SQLite) |
| Async | Kotlin Coroutines + Flow |
| Location | FusedLocationProviderClient |
| Voice | Android SpeechRecognizer |
| Volume Keys | Android AccessibilityService |
| Widget | AppWidget API |
| Services | Android Foreground Services |
| Navigation | Navigation Compose |
| Permissions | Accompanist Permissions |

---

## 📄 License

MIT License — feel free to use, modify and distribute.

---

## 👨‍💻 Developer

Built by Vaibhav Pandey and team — Android Developer

---

**Made with ❤️ for Women's Safety**

*"Safety is not a gadget but a state of mind."*
