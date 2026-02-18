# 🛡️ Rakshak - Women Safety App

**Rakshak** is a comprehensive safety application designed to provide emergency assistance through multiple activation methods including voice commands, manual SOS button, and location sharing.

## ✨ Features

### Core Features
- 🚨 **Emergency SOS Button** - Large, accessible button for instant alerts
- 🎤 **Voice Activation** - Say "Help Rakshak" to trigger emergency alert hands-free
- 📍 **Real-time Location Sharing** - Sends GPS coordinates via SMS
- 👥 **Emergency Contacts Management** - Add up to 5+ trusted contacts
- 📱 **SMS Alerts** - Sends emergency messages with location to all contacts
- 🔔 **Foreground Services** - Always active when enabled
- 🌙 **Dark Mode Support** - Material 3 design with theme support

### Technical Features
- **MVVM Architecture** with Clean Architecture principles
- **Jetpack Compose** for modern, declarative UI
- **Room Database** for persistent data storage
- **Coroutines & Flow** for asynchronous operations
- **FusedLocationProvider** for accurate GPS location
- **SpeechRecognizer** for voice command detection
- **Foreground Services** for reliable background operation

## 📱 Screenshots

### Home Screen
- Large red SOS button (animated pulse effect)
- Voice Guard toggle with status indicator
- Emergency contacts summary card

### Contacts Screen
- List of emergency contacts
- Add contacts manually or from phone contacts
- Delete contacts with confirmation

### Permission Screen
- Clear explanation of required permissions
- One-tap permission request

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or later
- Android SDK 26+ (Android 8.0 Oreo)
- Kotlin 1.9.20+
- Gradle 8.2+

### Installation Steps

1. **Clone or Download** the project
2. **Open in Android Studio**
   - File → Open → Select Rakshak folder

3. **Sync Gradle**
   - Android Studio will automatically sync
   - If not, click "Sync Now" in the notification bar

4. **Build the Project**
   ```
   Build → Make Project (Ctrl+F9)
   ```

5. **Run on Device/Emulator**
   ```
   Run → Run 'app' (Shift+F10)
   ```

### Required Permissions

The app requests the following permissions:
- ✅ **Location** (ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
- ✅ **SMS** (SEND_SMS)
- ✅ **Contacts** (READ_CONTACTS)
- ✅ **Microphone** (RECORD_AUDIO)
- ✅ **Notifications** (POST_NOTIFICATIONS - Android 13+)
- ✅ **Foreground Service** (FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE)

## 📖 User Guide

### Setting Up Emergency Contacts

1. Open the app
2. Grant all required permissions
3. Tap the "Edit" icon on Emergency Contacts card
4. Add contacts using:
   - **Manual Entry**: Tap the "+" FAB, enter name and phone
   - **From Contacts**: Tap the "person" FAB, select from phone contacts
5. Add 3-5 contacts for best results

### Using the SOS Button

1. **Method 1: Manual Trigger**
   - Press the large red SOS button
   - Confirm in the 3-second countdown dialog
   - Or tap "Send Now" to skip countdown

2. **Method 2: Voice Activation**
   - Enable "Voice Guard" toggle on home screen
   - Say **"Help Rakshak"** clearly
   - Alert triggers automatically

### What Happens When SOS is Triggered?

1. ✅ Gets your current GPS location
2. ✅ Sends SMS to all emergency contacts with:
   - Emergency alert message
   - Google Maps link to your location
   - Timestamp
3. ✅ Shows notification of success/failure
4. ✅ Continues tracking in background

### Voice Guard Tips

- 🎤 Works with screen off
- 🔋 Moderate battery usage (uses foreground service)
- 🔊 Best in quiet environment
- 🗣️ Speak clearly: "Help Rakshak"
- ⚡ Instant trigger (no delay)

## 🔧 Configuration

### Customizing SMS Message

Edit `SMSHelper.kt`:

```kotlin
val message = """
    🚨 YOUR CUSTOM MESSAGE 🚨
    
    I need immediate help!
    
    My current location:
    $locationUrl
    
    Please contact me immediately.
""".trimIndent()
```

### Changing Wake Word

Edit `VoiceGuardService.kt`:

```kotlin
private val WAKE_WORDS = listOf("help rakshak", "emergency", "sos rakshak")
```

### Adjusting SOS Countdown

Edit `HomeScreen.kt`:

```kotlin
var sosCountdown by remember { mutableIntStateOf(3) } // Change 3 to desired seconds
```

## 🏗️ Project Structure

```
Rakshak/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/safety/rakshak/
│   │   │   │   ├── data/              # Database, entities, repositories
│   │   │   │   │   ├── EmergencyContact.kt
│   │   │   │   │   ├── EmergencyContactDao.kt
│   │   │   │   │   ├── EmergencyContactRepository.kt
│   │   │   │   │   └── RakshakDatabase.kt
│   │   │   │   ├── service/           # Background services
│   │   │   │   │   ├── VoiceGuardService.kt
│   │   │   │   │   └── SOSService.kt
│   │   │   │   ├── ui/                # Compose UI screens
│   │   │   │   │   ├── HomeScreen.kt
│   │   │   │   │   ├── ContactsScreen.kt
│   │   │   │   │   └── theme/
│   │   │   │   ├── utils/             # Helper utilities
│   │   │   │   │   ├── LocationHelper.kt
│   │   │   │   │   ├── SMSHelper.kt
│   │   │   │   │   └── PermissionHelper.kt
│   │   │   │   ├── viewmodel/         # ViewModels
│   │   │   │   │   └── MainViewModel.kt
│   │   │   │   └── MainActivity.kt
│   │   │   ├── res/                   # Resources
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 🎨 UI/UX Features

### Material Design 3
- Dynamic color theming (Android 12+)
- Smooth animations and transitions
- Accessible touch targets
- Clear visual hierarchy

### Animations
- Pulsing SOS button (when contacts are added)
- Smooth screen transitions
- Loading states
- Countdown timer animation

### Responsive Design
- Adapts to different screen sizes
- Portrait orientation optimized
- Accessibility features

## 🔒 Privacy & Security

- ✅ All data stored locally (Room Database)
- ✅ No internet connection required
- ✅ No data collection or analytics
- ✅ Location only accessed during emergency
- ✅ SMS sent directly to contacts (no third-party servers)
- ✅ Open source and transparent

## 🐛 Troubleshooting

### Voice Guard Not Working
- Ensure microphone permission is granted
- Check if quiet environment
- Try speaking louder/clearer
- Restart Voice Guard toggle

### SOS Button Disabled
- Add at least one emergency contact
- Check if SMS permission is granted
- Verify phone numbers are correct

### Location Not Sharing
- Enable GPS/Location services
- Grant location permission
- Check if location accuracy is high
- Try triggering SOS in open area

### SMS Not Sending
- Verify phone numbers (include country code if needed)
- Check SMS permission
- Ensure phone has SMS capability
- Check network signal

## 📊 Testing Recommendations

### Before Exhibition

1. **Permission Testing**
   - Test all permission flows
   - Verify permission denial handling

2. **SOS Testing**
   - Test with real phone numbers (your own)
   - Verify SMS delivery
   - Check location accuracy
   - Test countdown cancellation

3. **Voice Testing**
   - Test in quiet room
   - Test in noisy environment
   - Test with different accents
   - Test multiple trigger phrases

4. **UI/UX Testing**
   - Test on different screen sizes
   - Test light/dark themes
   - Check all navigation flows
   - Verify error messages

### Demo Script for Exhibition

```
1. "This is Rakshak, a women safety app"
2. Show permissions screen → grant permissions
3. Add 2-3 emergency contacts
4. Demonstrate SOS button with countdown
5. Enable Voice Guard
6. Say "Help Rakshak" to trigger voice SOS
7. Show SMS sent notification
8. Explain real-world use cases
```

## 🎯 Future Enhancements (Post-Exhibition)

- [ ] Fake call feature
- [ ] Audio recording during emergency
- [ ] Police station locator
- [ ] Safe zone alerts
- [ ] Battery optimization
- [ ] Widget for quick access
- [ ] Multiple language support
- [ ] WhatsApp integration
- [ ] Cloud backup of contacts
- [ ] Panic alarm sound

## 🤝 Contributing

This is a project exhibition app. If you want to contribute:

1. Fork the repository
2. Create feature branch
3. Commit changes
4. Push to branch
5. Create Pull Request

## 📄 License

This project is created for educational purposes (Project Exhibition).

## 👨‍💻 Developer

Created for Project Exhibition - Rakshak Women Safety App

## 🙏 Acknowledgments

- Android Jetpack Compose team
- Material Design 3 guidelines
- Women safety initiatives worldwide

## 📞 Support

For exhibition queries or technical support:
- Check troubleshooting section
- Review code comments
- Test on real device (not just emulator)

---

**Made with ❤️ for Women's Safety**

*"Safety is not a gadget but a state of mind" - Eleanor Everet*
