# Phase 4 — physical device test script

Run on your OnePlus Nord CE3 Lite 5G (Android 15). Use a **second phone of yours** as the contact: every SOS and test message is a real SMS. Record PASS / FAIL and anything odd; results go into `docs/DEVELOPMENT_PROGRESS.md` (Phase 4).

Setup: install the debug build, allow SMS, location and notifications, add your second phone as a contact.

## A. Branding
| # | Check | Expected |
|---|---|---|
| A1 | Launcher icon (app drawer and home screen) | Red circle or squircle with the white shield-R, readable at a glance. No Android robot, no green grid |
| A2 | Settings -> Wallpaper & style -> Themed icons ON | Rakshak's icon takes the theme colour, still a recognisable shield-R |
| A3 | Cold start (swipe the app away, open it) | Splash with the shield-R mark on the correct light or dark background, no white flash in dark mode |
| A4 | Start an SOS countdown and look at the status bar and the pulled-down notification | The small icon is the shield-R glyph, not the robot |
| A5 | Quick Settings tile (add it, pull down the panel) | Shield-R tile icon, label "Rakshak SOS" |
| A6 | Home screen widget | Shield-R mark and "RAKSHAK" in a dark card, contact count readable, red SOS button |
| A7 | Onboarding (Settings -> Apps -> Rakshak -> Storage -> Clear data, then open) | Large shield-R mark with "Rakshak" and the Devanagari name |
| A8 | About screen header | Shield-R mark next to the title |

## B. Light and dark
Switch the system theme (Settings -> Display -> Dark theme) and check Home, Contacts, History, About, Onboarding and every dialog in **both** modes.
- All text readable, no pale grey on white and no maroon-tinted dialogs or menus.
- Cards are visible against the background in light mode.
- The SOS button label and the "Send now" button are readable.

## C. Edge-to-edge and sizes
| # | Check | Expected |
|---|---|---|
| C1 | Gesture navigation and 3-button navigation | Nothing is hidden behind the status bar or navigation bar; content scrolls to the end |
| C2 | Settings -> Display -> Font size to the largest, and Display size to the largest | Home scrolls, the SOS button is the first thing, nothing is cut off sideways |
| C3 | Rotate to landscape | The SOS button stays reachable and its label reads "SOS" (not "S/O/S") |
| C4 | Settings -> Accessibility -> Remove animations | The pulse rings stop |

## D. TalkBack
Turn TalkBack on. Swipe through Home: the SOS button should read "Send SOS alert. A 3-second countdown starts, and you can cancel it." (or "SOS is off. Add an emergency contact first." with no contacts); About is "About and privacy"; Contacts shows "More actions for <name>"; the primary contact reads "Primary contact". Every control reachable and 48 dp.

## E. Hindi
Settings -> Apps -> Rakshak -> Language -> हिन्दी.
- Home, Contacts, History, About, onboarding, setup dialog, Volume Guard disclosure: fully Hindi, no cut-off text.
- Trigger an SOS: the countdown notification and the result notification are in Hindi.
- The SMS to your contact stays **English** (by design).
- Note anything that reads unnaturally: the Hindi was written by the developer assistant and needs a native speaker's review before release.

## F. Contacts and primary contact
| # | Action | Expected |
|---|---|---|
| F1 | Add a contact manually with a valid number | Added. The first contact automatically shows the Primary badge (star + "Primary") |
| F2 | Number without + (e.g. 98xxxxxxxx) | Saves, with a warning about the country code |
| F3 | Same number again | Blocked: "Already added as <name>" |
| F4 | A contact with country code, then the same without it | Warning "may be the same person as <name>", still saves |
| F5 | Blank name; 3-digit number; a 16-digit number | Each shows its own error |
| F6 | Add up to 5 contacts | "5 of 5 contacts"; both add buttons disabled with a note |
| F7 | Choose from phone contacts | The system picker opens; the chosen contact fills the dialog; it goes through the same checks. No contacts permission prompt |
| F8 | Edit a contact (menu -> Edit) | Name and number change, validation applies |
| F9 | Make another contact primary | The badge moves; only one contact has it. Home shows "Call <name>" for the new primary |
| F10 | Remove primary (menu) | No primary; the Home call button disappears |
| F11 | Delete the primary contact while others exist | The first other contact (by name) becomes primary and a notice says so |
| F12 | Tap "Call <name>" on Home | The dialer opens with the number (no call placed, no permission prompt) |

## G. Test alert (not an SOS)
| # | Action | Expected |
|---|---|---|
| G1 | Contact menu -> Send test alert | A confirmation names the contact and number and says it is not an SOS |
| G2 | Confirm | "Sending a test message..." then "Test message sent". Your second phone receives "Rakshak TEST message: this is only a test, not an emergency..." |
| G3 | During and after | No countdown, **no SOS notification**, no foreground-service notification, nothing in SOS history, no "Last SOS" card change |
| G4 | Cancel on the confirmation | Nothing is sent |
| G5 | Turn SMS permission off in Settings, try again | A clear "SMS permission is off" result, nothing sent |
| G6 | Airplane mode | A clear failure result (not a false "sent") |

## H. SOS history
| # | Action | Expected |
|---|---|---|
| H1 | Do a real SOS to your second phone | "Last SOS: Alert sent, just now" appears on Home (it updates without leaving Home) |
| H2 | Open it | History shows time, trigger, "Sent N, not confirmed 0, failed 0", location status. No phone number or coordinates anywhere |
| H3 | Tap "I'm safe" on the result notification | The entry shows "Marked safe" |
| H4 | Start an SOS and Cancel during the countdown | Nothing is added to history |
| H5 | Do 21+ SOS runs (or trust the unit test) | Only the latest 20 are kept |
| H6 | Clear history | Confirmation, then the list is empty |

## I. Failure notification shortcuts
Airplane mode on, start an SOS and let it finish. The result notification should offer **Call 112**, **Open SMS app** and, if a primary contact is set, **Call <name>**. Each opens the dialer or SMS app; no permission prompt.

## J. Phase 3 behaviour still works
Countdown with Cancel and Send now from the app button, the volume keys, the widget and the tile, with the screen on and locked (see `docs/PHASE3_DEVICE_TEST.md`). The app button now shows the same countdown as the others (notification and dialog).
