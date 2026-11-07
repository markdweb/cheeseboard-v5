# Color Grid Keyboard

A real Android keyboard (Input Method Service) with a built-in **Color Grid**.
Tap a color: its text is copied to the clipboard, inserted straight into the focused field with
`InputConnection.commitText()`, the panel closes, and the keyboard hides - the browser/app is never closed
and no extra activity is opened.

- Package: `com.colorgrid.keyboard` - Kotlin - minSdk 26 - targetSdk 35
- Keyboard UI: native Views (reliable inside an IME). Settings app: Jetpack Compose + Material 3.
- No internet permission, no signing secrets needed.

## Features

- QWERTY with number row, shift (tap = once, double-tap = caps lock, auto-capital after sentences),
  backspace (hold to repeat), enter (shows Go/Search/Send/... when the field asks for it), space,
  symbols pages, emoji panel, globe key (keyboard picker), settings button, hide button
- Light / Dark / System theme, adjustable keyboard height and key size, key sound, vibration
- **Color Grid** panel: 11 default colors (Red, Blue, Green, Yellow, Orange, Purple, Pink, Brown, Black, White, Gray),
  each with its own color swatch. Long-press a color to favorite it; "Favorites" tab shows only those.
- **Clipboard history**: recent colors (and text copied elsewhere while the keyboard is active), tap to insert,
  pin items, delete items, clear (pinned items are kept)
- **Custom colors** (Colors tab in the app): add name + HEX, edit, delete, reorder, favorite, reset to defaults.
  Each color can optionally insert different text (for example its HEX code) instead of its name.
- If automatic insertion fails, the text is already on the clipboard and a message tells you to paste it.

## Build the APK with GitHub Actions

1. Create a repository on GitHub and upload this whole folder (keep `gradlew` and `gradle/wrapper/gradle-wrapper.jar`).
   ```bash
   git init
   git add .
   git commit -m "Color Grid Keyboard"
   git branch -M main
   git remote add origin https://github.com/<you>/<repo>.git
   git push -u origin main
   ```
2. Open the **Actions** tab. The *Build APK* workflow runs on every push to `main`/`master`;
   you can also start it with **Run workflow** (workflow_dispatch).
3. When it finishes, open the run and download the artifacts (GitHub wraps each one in a ZIP):
   - `ColorGrid-debug-apk` -> contains `ColorGrid-debug.apk`
   - `ColorGrid-Android-Source` -> contains `ColorGrid-Android-Source.zip`

Optional release: push a tag like `v1.0.0` (`git tag v1.0.0 && git push origin v1.0.0`) and the *Release* workflow
attaches both files to a GitHub Release.

## Install and enable the keyboard

1. Copy `ColorGrid-debug.apk` to your phone and install it (allow "install unknown apps" for your file manager/browser).
2. Open **Color Grid Keyboard**. Step 1 -> *Open keyboard settings* -> switch **Color Grid Keyboard** on
   (Android shows a standard warning for all third-party keyboards).
3. Step 2 -> *Choose keyboard* -> select **Color Grid Keyboard**.
   (Manual path: Settings -> System -> Keyboard -> On-screen keyboard -> Manage keyboards.)
4. Open Chrome/Brave, tap a text field, tap the 4-square icon above the keys, tap a color.

## Project layout

```
app/src/main/java/com/colorgrid/keyboard/
  ColorGridKeyboardService.kt   InputMethodService: commitText, clipboard, enter/backspace, dismissal
  KeyboardView.kt               keyboard, symbols, Color Grid / clipboard / emoji panels
  KeyboardHost.kt               interface between view and service
  Store.kt  Models.kt           settings, colors, clipboard history (SharedPreferences + JSON)
  Palette.kt  IconView.kt       themes and canvas-drawn icons
  MainActivity.kt               Compose app: setup guide, colors manager, settings, about
app/src/main/res/xml/method.xml IME declaration
.github/workflows/              build-apk.yml, release.yml
```

## Notes

- Key sound uses Android's system key sound, so "Touch sounds" must be enabled in system settings.
- The debug APK is signed with the standard Android debug key. It is fine for personal use.
- Local build: `./gradlew assembleDebug` (JDK 17 + Android SDK 35). Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Sound Studio

Open **Settings → Sound Studio** to enable key sounds, choose one of the built-in profiles, preview it, and adjust volume. Profiles are generated locally with Android's built-in tone generator; the switch-style names are creative inspirations only and are not recordings or endorsements of those switch manufacturers. No audio is downloaded and the feature works offline. Choose **Silent Switch** or turn Keyboard sounds off for silent typing.

The default Color Grid palette also includes Cyan, Magenta, Gold, Silver, Navy, Teal, Violet, Maroon, and Lime.


## Calculator mode

The keyboard toolbar includes **ABC** and **CALCU** modes. Calculator mode supports digits 0–9, decimal input, addition, subtraction, multiplication, division, clear, backspace, and equals. The **Copy** action copies the calculated result, inserts it into the focused field, records it in clipboard history, and hides the keyboard after successful insertion.

### Calculator panel

The keyboard toolbar includes a CALCU mode styled as a compact standalone calculator. It has a large result display, delete, AC, percent, arithmetic operators, 00/0/decimal keys, and a full-width Copy button. Copy sends the calculated result to the focused input field and hides the keyboard, using the same host insertion flow as Color Grid.


## Latest keyboard improvements

- Kept the regular emoji browser with categories, search, and a recent-emoji list for the current keyboard session.
- Removed predictive-text suggestions and the Emoji Mix sticker feature from the keyboard and Settings, as requested.
- Reworked Calculator mode to use a taller, more balanced panel with a clearly separated expression preview, green result display, compact keypad spacing, and a full-width Copy button.

### Keyboard sizing

The default portrait key rows are now shorter. Actual visible height can still vary by Android version, navigation mode, display scaling, and the host app's input field.
