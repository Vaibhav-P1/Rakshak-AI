# Phase 3 — physical device test script

Run on a **real Android 14+ phone** with a SIM. Results go into `docs/DEVELOPMENT_PROGRESS.md` (Phase 3, "Not verified" -> verified or failed). Use **test contacts** (a second phone of yours): every run that is not cancelled sends real SMS.

Setup: install the debug build, add 1 contact (your second phone), allow SMS, location and notifications, turn on Volume Guard (read the disclosure, enable "Rakshak" in Accessibility), add the Quick Settings tile and the widget. Note phone model, Android version and OEM skin.

For every row record: PASS / FAIL, and anything odd (delay, sound, no vibration).

## A. Countdown, every trigger (screen on, app open, then app closed)

| # | Action | Expected |
|---|---|---|
| A1 | Tap the app SOS button | Dialog "3, 2, 1" and a heads-up notification with **Cancel** / **Send now**. SMS arrives on the second phone after about 3 s |
| A2 | App SOS, tap **Cancel** in the dialog | No SMS. Notification disappears |
| A3 | App SOS, tap **Send now** | SMS within about 1 s |
| A4 | Press Volume Up + Down together | Countdown starts. Cancel from the notification: no SMS |
| A5 | Tap the widget | Countdown starts. Let it finish: SMS arrives |
| A6 | Tap the Quick Settings tile | Countdown starts (the shade closes or stays, note which) |
| A7 | Hold both volume keys for 10 s, cancel at 1 s | The countdown does not restart while the keys stay held. Release one key and press again: a new countdown |
| A8 | During a countdown, trigger a second time from another source | Ignored: still one countdown, one SMS |

## B. Locked screen (the open question from Phase 1)

Lock the phone with a PIN, screen off, then:

| # | Action | Expected / what to record |
|---|---|---|
| B1 | Volume Up + Down | Does the countdown notification show on the lock screen with Cancel / Send now? Does the SMS go out? **Does it contain a location, or "Finding my location" and later a follow-up?** (tells us if the location foreground service started) |
| B2 | Widget (if reachable on the lock screen) | Same questions |
| B3 | Quick Settings tile from the lock screen | Does Android ask for unlock first? After unlock, same questions |
| B4 | Cancel from the lock screen notification | Does it cancel without unlocking? Does the phone ask to unlock? |

Check `adb logcat -s SOSService ActivityManager | grep -i "foreground\|FGS"` for `Background started FGS: Allowed` or a denial. Record the result per trigger. If `location` is refused for a trigger, the SMS still goes (shortService fallback) but location may be missing: note it, it feeds the Phase 3 follow-up.

## C. Trimmed accessibility config

| # | Action | Expected |
|---|---|---|
| C1 | Volume Up + Down with the screen **on** and the app closed | Works |
| C2 | Same with the screen **locked** | Works. If it does **not**, the trimmed config lost lock-screen key events: record the phone and tell me, only the flag that restores it will be added back |
| C3 | Android Settings -> Accessibility -> Rakshak | Description reads "Only watches for Volume Up and Volume Down...". No "observe your actions / retrieve window content" permission prompt when enabling |
| C4 | Press only Volume Up | Normal volume change |
| C5 | Phone's own accessibility shortcut (hold both volume keys) is on | Note which one wins |

## D. Resilience

| # | Action | Expected |
|---|---|---|
| D1 | Trigger SOS, force-stop Rakshak from Settings within 1 s | Note whether the SOS still goes out (a force-stop cancels everything: this is expected to send nothing) |
| D2 | Trigger SOS, swipe the app away from recents during the countdown | The SOS still completes |
| D3 | Airplane mode, trigger, let it finish | Result notification says it failed, with Call 112 and Open SMS app |
| D4 | Battery saver on, trigger from the widget | Works |

## E. Removed / settings checks

| # | Check | Expected |
|---|---|---|
| E1 | App info -> Permissions | No Microphone entry |
| E2 | Home screen | No Voice Guard card. Volume Guard banner shows the explanation dialog before Settings opens |
| E3 | Add the tile on Android 13+ with the Home "Add" banner | System dialog appears, tile is added |
| E4 | Contacts: add/delete a contact with the widget on the home screen | The widget's contact count updates within a few seconds |
