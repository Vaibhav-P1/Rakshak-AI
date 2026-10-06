# Rakshak — Privacy Policy

> **DRAFT for the app owner to review before publishing.**
> Fill in the two placeholders marked `TODO`, have the text checked against the final release build, and host it at a public URL (for example GitHub Pages) for the Play Console. It describes what the code does at the time of writing (Phase 2); update it whenever behavior changes.

**Effective date:** TODO (date of first release)
**Developer / contact:** TODO (name and support email address)

Rakshak is a personal safety app. When you trigger SOS, it sends an SMS with your location to the emergency contacts you chose. This policy explains what the app does with your information.

## In short

- Rakshak has **no account, no server, no analytics, no advertising and no tracking.**
- The app **does not request internet permission**, so it cannot send your data to the developer or anyone else over the internet.
- Your information is used **only to send the alerts you trigger**.

## What information is involved

| Information | Why | Where it goes |
|---|---|---|
| **Emergency contacts** (name and phone number you enter or pick) | To know whom to alert | Stored on your phone. Android may include them in your device backup (Google backup), if you have backup turned on |
| **Your location** | To add a map link to your alert | Read only when you trigger SOS, using Google Play services. It is placed in the SMS sent to your contacts. Rakshak does not store location history |
| **SMS messages you send through the app** | The SOS alert, the location follow-up and "I'm safe" | Sent from your SIM to your contacts through your mobile carrier. Your carrier's own terms apply, and normal SMS charges may apply |
| **Status of your most recent SOS** (which contacts were alerted, and whether it succeeded) | To finish an SOS if the app is interrupted, and to show you the result | Stored on your phone only; replaced by the next SOS; **not** included in backups |
| **Microphone audio** (only if you turn on Voice Guard) | To listen for the phrase "help help help" | Processed on your phone in real time. **It is not recorded, stored or sent anywhere.** Recognized words are not logged |
| **Volume button presses** (only if you turn on the Volume Guard accessibility service) | To notice Volume Up and Volume Down pressed together | Used on the phone only. Rakshak does not read your screen or what you type |

## Permissions and why they are requested

Rakshak asks for each permission only when it is needed, and explains it first.

- **Send SMS** — to text your emergency contacts. Required for SOS to work.
- **Location (precise or approximate)** — to add your location to the alert. Optional; approximate works too.
- **Notifications** — to show SOS progress and the "I'm safe" button. Optional.
- **Microphone** — only for Voice Guard. Optional.
- **Accessibility service** — only for Volume Guard. Optional, and enabled by you in Android settings.
- **Foreground service, wake lock** — to keep an SOS or Voice Guard running while the screen is off.

You can withdraw any permission at any time in Android Settings. Without a permission, the matching feature stops working.

## Who sees your information

- **Your emergency contacts** receive the messages you trigger, including your location.
- **Your mobile carrier** carries the SMS.
- **Google Play services** provides location to the app on your device, under Google's own privacy policy. Rakshak receives the result; it does not send location to Google itself.
- **The developer does not receive any of your information.**

## Retention and deletion

Contacts stay on your phone until you delete them in the app or uninstall Rakshak. Uninstalling removes the app's data from your phone. If Android backup is on, a copy of your contacts may remain in your Google backup until you delete that backup in your Google account settings.

## Children

Rakshak is not directed at children under 13 and does not knowingly collect their information.

## Not a replacement for emergency services

Rakshak is a helper, not a guarantee. SMS can fail or be delayed when there is no signal, no SIM or no balance. In danger, call **112** (or your local emergency number) first whenever you can.

## Changes

If this policy changes, the new version will be published at the same address with a new effective date, and the in-app "About and Privacy" screen will be updated.

## Contact

TODO: support email address
