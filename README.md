# BigOTP

A free accessibility app for elderly and disabled users in India. When an OTP arrives by SMS, BigOTP surfaces it as a floating bubble with large, readable digits — and reads it aloud digit by digit so the user never has to squint at a notification.

**No data leaves the device. Ever.**

---

## Why this exists

Standard SMS notifications are small, disappear quickly, and require the user to switch apps to read and copy the code. For people with low vision, motor difficulties, or who are simply unfamiliar with smartphones, this is a real barrier. BigOTP solves it with:

- A floating bubble overlay that appears over whatever app the user is in
- Large, high-contrast digits using the device's own accessibility settings
- Text-to-speech that reads each digit individually ("4… 7… 2… 1…")
- One-tap copy to clipboard
- Automatic expiry — codes disappear when they're no longer valid

The guiding principle: **enhance the user's accessibility settings, never override them**. Every colour, font size, and animation defers to what the user has configured on their device.

---

## Features

- Floating bubble overlay — appears without interrupting the current app
- Large-digit full-screen display (optional, for maximum accessibility)
- Text-to-speech with per-digit pacing (400 ms pause between digits)
- Coordinates with TalkBack — skips auto-TTS if TalkBack is already reading the screen
- Payment OTP detection — elevated treatment with amount, source, and a "never share" warning
- Recent codes history with blur-until-reveal privacy
- Zero network transmission of OTP content or user identifiers
- Works fully offline after first install
- OTP patterns fetched from a versioned CDN and cached locally — always falls back to bundled patterns
- Material 3 dynamic colour — follows the system theme including dark mode and wallpaper-based colour

---

## Screenshots

See [bigotp.in](https://bigotp.in) for screenshots and a live demo.

---

## Download

Available on the Google Play Store: *coming soon*

Or build from source — see below.

---

## Building from source

### Prerequisites

- Android Studio Hedgehog or later (or the Android command-line tools)
- JDK 17+
- Android SDK with API 35

### Clone and build

```bash
git clone https://github.com/FrailWords/bigotp-android.git
cd bigotp-android
./gradlew assembleDebug
```

Install on a connected device:

```bash
./gradlew installDebug
```

### Run tests

```bash
# All unit tests
./gradlew test

# Parser tests only (fast, no emulator needed)
./gradlew :app:testDebugUnitTest --tests "com.bigotp.app.parser.*"

# Lint
./gradlew lint
```

---

## Architecture

BigOTP is a single-module Android app written in Kotlin with Jetpack Compose.

```
app/src/main/java/com/bigotp/app/
  parser/          # Pure Kotlin, zero Android deps — testable with plain JUnit
  service/         # NotificationListenerService + floating bubble overlay
  display/         # Full-screen OTP display (optional accessibility mode)
  history/         # Encrypted local history (Android Keystore + DataStore)
  config/          # Pattern config: CDN → DataStore cache → bundled fallback
  tts/             # Text-to-speech wrapper
  onboarding/      # First-run setup flow
  ui/theme/        # Material 3 theme (dynamic colour only, no hardcoded values)
```

### Key decisions

| Decision | Chosen | Rejected | Why |
|---|---|---|---|
| SMS interception | `NotificationListenerService` | `READ_SMS` | No raw message content access required |
| Storage | `DataStore` | `SharedPreferences` | Structured, coroutine-friendly, type-safe |
| UI | Jetpack Compose | XML layouts | Modern, concise, testable |
| Colour | Material 3 dynamic colour | Custom theme | Defers to user's accessibility settings |
| Background | `WorkManager` | `AlarmManager` / foreground service | Battery-efficient, survives reboots |
| OTP patterns | External JSON (CDN + local fallback) | Hardcoded | Community-updatable without app release |

### OTP patterns

Patterns live in `app/src/main/assets/patterns_fallback.json` and are fetched weekly from a versioned CDN endpoint. The three-layer resolution order is:

1. Remote CDN (`https://bigotp.in/patterns/v1.json`)
2. DataStore cache (last successful fetch)
3. Bundled asset (always available, works offline)

Pattern contributions are the most impactful way to help — see [CONTRIBUTING.md](CONTRIBUTING.md).

### Privacy by design

- OTP codes are encrypted at rest using Android Keystore (AES-GCM)
- History entries expire within minutes and can be manually deleted
- No analytics SDK, no crash reporter, no identifiers
- No network calls that include OTP content or device identifiers
- The full privacy policy is at [bigotp.in/privacy_policy](https://bigotp.in/privacy_policy)

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full guide. The short version:

- **Adding a bank or service pattern** — edit `app/src/main/assets/patterns_fallback.json`, run parser tests, open a PR
- **Bug reports** — use the GitHub issue template, include device model, Android version, and an anonymised SMS sample
- **Code changes** — read the architecture constraints in CONTRIBUTING.md before starting; many rules exist for accessibility reasons and are non-negotiable

---

## License

Apache 2.0 — see [LICENSE](LICENSE).
