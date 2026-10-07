# Rakshak brand assets

The mark is **Shield-R**: an R cut out of a shield (the counter of the R is filled back in). It was chosen by the owner from four concepts on 2026-10-07.

| File | What it is |
|---|---|
| `rakshak-mark.svg` | The master mark, one path, brand red `#D92336`, 96x96 viewBox |
| `rakshak-launcher.svg` | The launcher icon: white mark scaled 0.7 inside the 66 dp safe zone of a 108 dp canvas, on brand red |

The same path is used everywhere; only the colour treatment differs. It is copied into Android resources, not redrawn:

| Surface | Resource | Treatment |
|---|---|---|
| In-app logo | `res/drawable/ic_logo_mark.xml` | White, tinted by `RakshakMark()` (theme `primary`) |
| Launcher (adaptive) | `ic_launcher_foreground.xml` + `ic_launcher_background.xml` | White mark on brand red |
| Themed icon | `ic_launcher_monochrome.xml` | One colour, tinted by Android |
| Notifications | `ic_notification.xml` | Alpha-only, tinted by Android |
| Quick Settings tile | `ic_tile_sos.xml` | Alpha-only |
| Splash / launch | `ic_splash_mark.xml` + `launch_background.xml` + `values*/themes.xml` | Brand red mark on the light or dark background |
| Widget | `widget_sos.xml` | Mark tinted `brand_red` on a fixed dark card |
| Play Store | `app/src/main/ic_launcher-playstore.png` (512 px) | Rendered from `rakshak-launcher.svg` |

Rules (enforced by `UiGuardsTest`): the notification, tile and monochrome glyphs are fill-only, single-colour and have no strokes or gradients; the legacy raster `mipmap-*dpi` icons and the Android template robot / green grid must not return; brand colours in XML equal `RakshakPalette`.

To regenerate the Play icon after changing the mark, render `rakshak-launcher.svg` at 512x512 (a headless browser screenshot works) and replace `ic_launcher-playstore.png`.

The wordmark is plain bold text ("Rakshak" with the Devanagari "रक्षक"), not an image, so it follows the user's language and font settings.
