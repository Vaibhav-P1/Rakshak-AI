# 🏗️ Rakshak - Technical Architecture

## Overview

Rakshak is built using modern Android development practices with Kotlin, Jetpack Compose, and follows Clean Architecture principles with MVVM pattern.

---

## 📐 Architecture Pattern

### MVVM (Model-View-ViewModel)

```
┌─────────────────────────────────────────────────────────┐
│                         UI Layer                         │
│  ┌─────────────────┐            ┌──────────────────┐   │
│  │  HomeScreen.kt  │────────────│ContactsScreen.kt │   │
│  └────────┬────────┘            └────────┬─────────┘   │
│           │                               │              │
│           └───────────────┬───────────────┘              │
│                           │                              │
│                    ┌──────▼─────────┐                   │
│                    │ MainActivity   │                   │
│                    └──────┬─────────┘                   │
└───────────────────────────┼──────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────┐
│                    ViewModel Layer                        │
│                  ┌──────────────────┐                    │
│                  │ MainViewModel.kt │                    │
│                  └────────┬─────────┘                    │
└───────────────────────────┼──────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────┐
│                   Repository Layer                        │
│           ┌──────────────────────────────┐               │
│           │EmergencyContactRepository.kt │               │
│           └────────┬─────────────────────┘               │
└────────────────────┼─────────────────────────────────────┘
                     │
┌────────────────────▼─────────────────────────────────────┐
│                    Data Layer                             │
│  ┌─────────────────┐  ┌──────────────────────────────┐  │
│  │ RakshakDatabase │  │ EmergencyContactDao          │  │
│  │    (Room)       │  │                              │  │
│  └─────────────────┘  └──────────────────────────────┘  │
│                                                           │
│  ┌──────────────────────────────────────────────────┐   │
│  │         EmergencyContact (Entity)                │   │
│  └──────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────┘
```

---

## 🔧 Component Breakdown

### 1. UI Layer (Jetpack Compose)

#### HomeScreen.kt
**Purpose:** Main dashboard with SOS button and Voice Guard toggle

**Key Components:**
- `SOSButton`: Large animated button (250dp circle)
- `VoiceGuardCard`: Toggle switch with status indicator
- `EmergencyContactsSummary`: Quick view of contacts
- `SOSCountdownDialog`: 3-second confirmation dialog

**State Management:**
```kotlin
val contacts by viewModel.contacts.collectAsState()
val isVoiceGuardActive by viewModel.isVoiceGuardActive.collectAsState()
```

**Animations:**
- Pulsing effect using `infiniteTransition`
- Scale animation (1.0f to 1.05f)
- Smooth countdown timer

#### ContactsScreen.kt
**Purpose:** Manage emergency contacts

**Features:**
- LazyColumn for efficient contact list
- Contact picker integration
- Manual contact entry
- Delete with confirmation

**Key Functions:**
- `ContactCard`: Individual contact display
- `ActivityResultContracts` for contact picker
- Real-time contact updates via Flow

---

### 2. ViewModel Layer

#### MainViewModel.kt
**Purpose:** Bridge between UI and data layer

**State Management:**
```kotlin
private val _contacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()

private val _isVoiceGuardActive = MutableStateFlow(false)
val isVoiceGuardActive: StateFlow<Boolean> = _isVoiceGuardActive.asStateFlow()
```

**Operations:**
- `addContact()`: Insert new contact
- `updateContact()`: Modify existing contact
- `deleteContact()`: Remove contact
- `setVoiceGuardActive()`: Toggle voice guard state
- `triggerSOS()`: Initiate emergency alert

**Lifecycle:**
- Survives configuration changes
- Cleaned up automatically
- Coroutines scoped to ViewModel

---

### 3. Data Layer

#### Room Database

**Entity: EmergencyContact**
```kotlin
@Entity(tableName = "emergency_contacts")
data class EmergencyContact(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val phoneNumber: String,
    val isPrimary: Boolean = false
)
```

**DAO Operations:**
- `getAllContacts()`: Flow<List<EmergencyContact>>
- `insertContact()`: Suspend function
- `updateContact()`: Suspend function
- `deleteContact()`: Suspend function

**Database Configuration:**
- Single database instance (Singleton pattern)
- Automatic migration support
- Export schema enabled

---

### 4. Service Layer

#### VoiceGuardService.kt
**Type:** Foreground Service (Microphone)

**Lifecycle:**
```
START → onCreate() → onStartCommand() → startForeground()
                                      ↓
                                startListening()
                                      ↓
                            [Continuous listening loop]
                                      ↓
                          Wake word detected → triggerSOS()
                                      ↓
STOP ← stopSelf() ← stopListening() ← onDestroy()
```

**Speech Recognition:**
- Uses Android `SpeechRecognizer`
- Partial results enabled
- Auto-restart on error
- Wake words: ["help rakshak", "rakshak help"]

**Notification:**
- Persistent notification (high priority)
- Shows "Listening for 'Help Rakshak'..."
- Tap to open MainActivity

**Key Methods:**
```kotlin
startListening() → speechRecognizer.startListening()
stopListening() → speechRecognizer.cancel()
triggerSOS() → Start SOSService + Broadcast
```

#### SOSService.kt
**Type:** Foreground Service (Location)

**Workflow:**
```
1. Start foreground with notification
2. Get current GPS location (FusedLocationProvider)
3. Query emergency contacts from database
4. Send SMS to each contact with location
5. Update notification (Success/Error)
6. Auto-stop after 5 seconds
```

**Error Handling:**
- Location unavailable → Send SMS without location
- SMS permission denied → Show error notification
- No contacts → Show error notification

---

### 5. Utility Layer

#### LocationHelper.kt
**Purpose:** GPS location management

**Key Features:**
- Uses `FusedLocationProviderClient`
- High accuracy mode (Priority.PRIORITY_HIGH_ACCURACY)
- Suspending function with coroutines
- Google Maps URL generation

**Methods:**
```kotlin
suspend fun getCurrentLocation(): Location?
fun getGoogleMapsUrl(lat: Double, lng: Double): String
```

#### SMSHelper.kt
**Purpose:** SMS sending functionality

**Features:**
- Multi-part SMS support (long messages)
- Batch sending to multiple contacts
- Error handling per contact
- Success/failure callbacks

**SMS Format:**
```
🚨 EMERGENCY ALERT FROM RAKSHAK 🚨

I need immediate help!

My current location:
https://maps.google.com/?q=LAT,LNG

Please contact me immediately or call emergency services.

- Sent via Rakshak Safety App
```

#### PermissionHelper.kt
**Purpose:** Permission management

**Required Permissions:**
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `SEND_SMS`
- `READ_CONTACTS`
- `RECORD_AUDIO`
- `POST_NOTIFICATIONS` (Android 13+)
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MICROPHONE`

---

## 🔄 Data Flow

### SOS Button Flow
```
User taps SOS Button
    ↓
Show countdown dialog (3 seconds)
    ↓
User confirms / Timer expires
    ↓
HomeScreen calls triggerSOS()
    ↓
Start SOSService as foreground
    ↓
SOSService.triggerSOS():
    ├─ LocationHelper.getCurrentLocation()
    ├─ Query emergency contacts from Room
    └─ SMSHelper.sendSOSMessage()
        ├─ For each contact:
        │   └─ Send SMS with location
        ├─ Update notification (success)
        └─ Stop service after 5s
```

### Voice Activation Flow
```
User toggles Voice Guard ON
    ↓
Start VoiceGuardService as foreground
    ↓
Service creates SpeechRecognizer
    ↓
Continuous listening loop:
    ├─ startListening()
    ├─ onPartialResults() → Check wake words
    ├─ onResults() → Check wake words
    └─ onError() → Restart listening
        ↓
Wake word detected
    ↓
Cancel speech recognizer
    ↓
Send ACTION_TRIGGER_SOS broadcast
    ↓
Start SOSService
    ↓
[Same flow as SOS Button]
```

### Contact Management Flow
```
User opens ContactsScreen
    ↓
Observe contacts Flow from ViewModel
    ↓
ViewModel collects from Repository
    ↓
Repository queries Room database
    ↓
Contacts displayed in LazyColumn
    ↓
User adds/updates/deletes contact
    ↓
ViewModel calls repository method
    ↓
Repository updates Room database
    ↓
Database emits new Flow
    ↓
UI automatically updates (reactive)
```

---

## 🎨 UI Architecture

### Compose Navigation
```kotlin
NavHost(startDestination = "home") {
    composable("home") { HomeScreen() }
    composable("contacts") { ContactsScreen() }
}
```

### Theme System
- Material 3 design
- Dynamic colors (Android 12+)
- Light/Dark theme support
- Custom color scheme for safety emphasis (red primary)

### State Hoisting
```kotlin
// State in ViewModel
val contacts: StateFlow<List<EmergencyContact>>

// Collected in Composable
val contacts by viewModel.contacts.collectAsState()

// Passed down to child composables
ContactList(contacts = contacts)
```

---

## 🔐 Security Considerations

### Data Security
- **Local Storage**: All data in Room (SQLite encrypted)
- **No Cloud**: Zero remote data storage
- **Permission-Based**: User controls all access

### Privacy
- **Location**: Only accessed during emergency
- **Contacts**: Read permission, never uploaded
- **SMS**: Sent locally, no third-party relay
- **Audio**: Only processed for wake word, not recorded

### Service Protection
- Foreground services with notifications
- User can stop services anytime
- Clear indicators when active
- No background data collection

---

## ⚡ Performance Optimizations

### Memory Management
- Lazy loading of contacts (LazyColumn)
- ViewModelScope for coroutines (auto-cleanup)
- Efficient Room queries with Flow
- Proper service lifecycle management

### Battery Optimization
- Voice Guard as opt-in feature
- Services stop when not needed
- Efficient location updates (one-time fetch)
- SMS batch sending (single operation)

### UI Performance
- Jetpack Compose recomposition optimization
- Remember memoization for expensive operations
- Stable collections for list rendering
- Minimal state changes

---

## 🧪 Testing Strategy

### Unit Tests
```
viewmodel/
├── MainViewModelTest.kt
data/
├── EmergencyContactRepositoryTest.kt
utils/
├── LocationHelperTest.kt
└── SMSHelperTest.kt
```

### Integration Tests
```
service/
├── VoiceGuardServiceTest.kt
└── SOSServiceTest.kt
```

### UI Tests
```
ui/
├── HomeScreenTest.kt
└── ContactsScreenTest.kt
```

---

## 📊 Tech Stack Summary

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 1.9.20 |
| UI | Jetpack Compose |
| Architecture | MVVM + Clean Architecture |
| Database | Room 2.6.1 |
| DI | Manual (no framework) |
| Async | Coroutines + Flow |
| Location | Play Services Location |
| Navigation | Compose Navigation |
| Permissions | Accompanist Permissions |

---

## 🚀 Build Configuration

### Gradle Setup
- Kotlin DSL (`.kts`)
- Android Gradle Plugin 8.2.1
- Target SDK: 34 (Android 14)
- Min SDK: 26 (Android 8.0)

### Dependencies
```kotlin
// Compose BOM for version management
implementation(platform("androidx.compose:compose-bom:2024.02.00"))

// Core libraries
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

// Room
implementation("androidx.room:room-runtime:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// Location
implementation("com.google.android.gms:play-services-location:21.1.0")
```

---

## 🔮 Future Enhancements

### Technical Improvements
1. **Dependency Injection**: Add Hilt/Koin
2. **Testing**: Comprehensive test coverage
3. **CI/CD**: GitHub Actions pipeline
4. **Crashlytics**: Firebase integration
5. **Analytics**: Privacy-focused analytics

### Feature Additions
1. **Offline Maps**: Cached map tiles
2. **ML Kit**: Better voice recognition
3. **Biometric Auth**: Secure app access
4. **WebRTC**: Live video streaming
5. **Blockchain**: Immutable emergency logs

---

## 📚 Key Learnings

### Android Best Practices
✅ Single Activity architecture
✅ Reactive UI with Compose
✅ Repository pattern for data
✅ Proper coroutine scoping
✅ Lifecycle-aware components

### Safety App Specific
✅ Foreground services for reliability
✅ Permission-first approach
✅ Offline-first architecture
✅ Battery-conscious design
✅ Clear user indicators

---

*This architecture is designed for reliability, privacy, and user safety.*
