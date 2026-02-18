# 🚀 Quick Setup Guide - Rakshak App

## Step-by-Step Setup for Your Exhibition

### 📋 Pre-requisites Checklist

Before starting, ensure you have:
- [ ] Android Studio Hedgehog (2023.1.1) or later installed
- [ ] Android SDK 26+ installed
- [ ] Physical Android device OR emulator (Android 8.0+)
- [ ] USB cable (if using physical device)
- [ ] Stable internet connection (for first build only)

---

## 🎯 Step 1: Import Project

1. **Open Android Studio**
2. **File** → **Open**
3. Navigate to `Rakshak` folder
4. Click **OK**
5. Wait for Gradle sync (3-5 minutes first time)

### ⚠️ If Gradle Sync Fails:
```
- File → Invalidate Caches → Invalidate and Restart
- Ensure internet connection is stable
- Check if JDK 17 is selected (File → Project Structure)
```

---

## 🎯 Step 2: Configure Device

### Option A: Physical Device (Recommended for Exhibition)

1. **Enable Developer Options**
   - Settings → About Phone
   - Tap "Build Number" 7 times
   - Go back → Developer Options

2. **Enable USB Debugging**
   - Developer Options → USB Debugging (ON)

3. **Connect Device**
   - Connect via USB
   - Allow USB debugging popup
   - Select "File Transfer" mode

4. **Verify Connection**
   - In Android Studio toolbar, check device name appears

### Option B: Emulator

1. **Tools** → **Device Manager**
2. **Create Device**
3. Select **Pixel 5** or similar
4. Select **API 33 (Android 13)** or higher
5. Click **Finish**
6. Click **Play** to start emulator

---

## 🎯 Step 3: Build & Run

1. **Build Project**
   - **Build** → **Make Project** (Ctrl+F9)
   - Wait for build to complete (2-3 minutes)

2. **Run App**
   - Click green **Run** button (▶️)
   - Or press **Shift+F10**
   - Select your device
   - Click **OK**

3. **First Launch**
   - App installs (30 seconds)
   - App opens automatically

---

## 🎯 Step 4: Setup App for Demo

### Grant Permissions

1. App shows **Permission Screen**
2. Click **"Grant Permissions"**
3. Allow each permission:
   - ✅ Location
   - ✅ SMS
   - ✅ Contacts
   - ✅ Microphone
   - ✅ Notifications
4. Click **"Allow"** for each

### Add Emergency Contacts

**Method 1: Manual Entry**
1. Tap **Edit icon** (pencil) on Emergency Contacts card
2. Tap **"+" FAB** (bottom right)
3. Enter:
   - Name: "Your Name" or "Friend Name"
   - Phone: Your actual phone number (for testing)
4. Tap **"Add"**
5. Repeat for 2-3 contacts

**Method 2: From Contacts** (if device has contacts)
1. Tap **Person icon FAB**
2. Select contact from list
3. Tap **"Add"**

### Test SOS Feature

1. **Go back to Home Screen**
2. **Test Manual SOS:**
   - Tap large red **SOS button**
   - 3-second countdown appears
   - Either wait or tap **"Send Now"**
   - Check if SMS received (if using real number)

3. **Test Voice Guard:**
   - Toggle **"Voice Guard"** ON
   - Wait 2-3 seconds
   - Say clearly: **"Help Rakshak"**
   - SOS should trigger automatically

---

## 🎯 Step 5: Prepare for Exhibition

### Create Demo Scenario

**Suggested Script:**
```
"Hello, this is Rakshak - a women safety application.

Let me show you how it works:

1. [Show Home Screen] 
   This is our main interface with emergency features.

2. [Point to SOS Button]
   This large button triggers immediate emergency alerts.
   
3. [Show Voice Guard Toggle]
   We have hands-free activation - just say 'Help Rakshak'
   
4. [Tap Edit → Show Contacts]
   Emergency contacts receive SMS with your GPS location.
   
5. [Demonstrate SOS]
   When triggered, it sends alerts to all contacts instantly.
   
6. [Show notification]
   The app confirms message delivery.

This works even with the phone in your pocket or bag."
```

### Pre-Demo Checklist

- [ ] App installed and running smoothly
- [ ] 2-3 contacts added (use your own numbers for real demo)
- [ ] Permissions granted
- [ ] Device fully charged
- [ ] Screen brightness at 100%
- [ ] Do Not Disturb OFF
- [ ] Test both SOS methods (button + voice)
- [ ] Prepare backup device (if possible)

### Common Demo Issues & Fixes

**Issue: Voice Guard not detecting**
- Solution: Speak louder, slower
- Move to quieter area
- Toggle Voice Guard OFF then ON

**Issue: SMS not sending**
- Solution: Check SMS permission
- Verify phone number format
- Ensure device has network

**Issue: Location not accurate**
- Solution: Enable high accuracy mode
- Move near window (if indoors)
- Wait 5-10 seconds before triggering

---

## 🎯 Step 6: Exhibition Tips

### Presentation Points

**Technical Highlights:**
- "Built with latest Kotlin & Jetpack Compose"
- "Uses MVVM architecture for maintainability"
- "Foreground services ensure reliability"
- "Room database for local data storage"
- "Material 3 design for modern UI"

**Safety Features:**
- "Works without internet connection"
- "Location shared via SMS (no app required)"
- "Voice activation for hands-free use"
- "Multiple emergency contacts support"
- "Privacy-focused - all data local"

**Real-World Usage:**
- "Walking alone at night"
- "Using public transportation"
- "In uncomfortable situations"
- "Medical emergencies"
- "Natural disasters"

### Answering Questions

**Q: Does it need internet?**
A: No, it works completely offline using SMS.

**Q: Battery consumption?**
A: Voice Guard uses moderate battery (foreground service). Users can toggle it as needed.

**Q: Can family track location always?**
A: No, location is only sent during emergency trigger. Privacy is protected.

**Q: What if phone is locked?**
A: Voice command works even with screen off/locked.

**Q: Multiple languages?**
A: Currently English only, but can be expanded.

---

## 🔧 Troubleshooting

### Build Errors

**Error: "Gradle sync failed"**
```
Solution:
1. File → Invalidate Caches → Restart
2. Ensure internet connection
3. Update Android Studio if prompted
```

**Error: "SDK not found"**
```
Solution:
1. Tools → SDK Manager
2. Install Android 13 (API 33)
3. Apply changes
```

### Runtime Errors

**App crashes on launch**
```
Solution:
1. Check logcat for error
2. Rebuild project (Build → Rebuild Project)
3. Uninstall app from device
4. Run again
```

**Permissions not working**
```
Solution:
1. Settings → Apps → Rakshak
2. Manually grant all permissions
3. Restart app
```

---

## 📱 Testing Before Exhibition

### Day Before Exhibition

1. **Full Test Run**
   - Install fresh
   - Grant permissions
   - Add contacts
   - Test SOS button
   - Test voice activation
   - Verify SMS delivery

2. **Backup Plan**
   - Install on 2 devices (if available)
   - Take screenshots of all screens
   - Record video demo (backup)
   - Print QR code to GitHub/Drive

3. **Prepare Materials**
   - Poster with app name & features
   - Device holder/stand
   - Charging cable & power bank
   - Business cards (optional)

---

## 🎉 You're Ready!

Your Rakshak app is now ready for exhibition. Good luck! 🚀

### Final Checklist
- [ ] App working perfectly
- [ ] Demo script prepared
- [ ] Backup device ready
- [ ] All materials organized
- [ ] Confident about features
- [ ] Ready to answer questions

---

**Need Help?**
- Review README.md for detailed documentation
- Check code comments for technical details
- Test each feature multiple times

**Remember:** 
- Speak clearly and confidently
- Demonstrate features slowly
- Engage with audience
- Have fun presenting!

---

*Built with ❤️ for Women's Safety*
