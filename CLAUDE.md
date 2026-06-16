# BigOTP — Android Project Context

## What this app is
A free accessibility app for elderly and disabled users in India. It intercepts
incoming OTP SMS notifications, displays them full-screen in large high-contrast
digits, reads them aloud via TTS, then auto-wipes them when they expire or are
dismissed.

The single most important principle: **we enhance the user's accessibility settings,
we never override them.** Every colour, font size, and animation must defer to what
the user has configured on their device.

## Tech stack
- Language: Kotlin only. No Java.
- UI: Jetpack Compose exclusively. No XML layouts anywhere.
- Design system: Material 3 with dynamic colour. No hardcoded colours ever.
- Background work: WorkManager for the weekly config fetch.
- Local storage: DataStore (Preferences). Not SharedPreferences.
- OTP interception: NotificationListenerService. No READ_SMS permission.
- TTS: Android TextToSpeech. On-device only, no network.
- Min SDK: 26. Target SDK: 35.
- Build: Gradle with Kotlin DSL (.kts files). Not Groovy.

## Project structure
```
app/src/main/java/com/bigotp/app/
  parser/          # Pure Kotlin, zero Android deps — test with plain JUnit
    OtpParser.kt
    OtpPattern.kt
    OtpResult.kt
    OtpType.kt
    UrgencyLevel.kt
  service/
    OtpNotificationService.kt
  display/
    OtpDisplayActivity.kt
    OtpDisplayViewModel.kt
  onboarding/
    OnboardingActivity.kt
    OnboardingViewModel.kt
  history/
    OtpHistoryStore.kt
  config/
    ConfigRepository.kt
    ConfigFetcher.kt
  tts/
    OtpSpeaker.kt
  ui/theme/
    Theme.kt
    Type.kt
  MainActivity.kt
```

## Core rules — enforced in every session

### Colours — never hardcode
Use only Material 3 semantic tokens:
  MaterialTheme.colorScheme.primary / .onSurface / .error / .errorContainer etc.
Countdown urgency: .primary (healthy) → .tertiary (warning) → .error (critical)

### Typography — never hardcode sp values
Use only Material 3 type scale:
  MaterialTheme.typography.displayLarge for OTP digits
  MaterialTheme.typography.titleMedium for source/context
  MaterialTheme.typography.bodyMedium for supporting text

### Animations — check reduce motion first
```kotlin
@Composable
fun reduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    return Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f
}
```

### TTS vs TalkBack — coordinate, never fight
```kotlin
val talkBackActive = accessibilityManager.isEnabled &&
    accessibilityManager.isTouchExplorationEnabled
// If talkBackActive: skip auto-TTS, let TalkBack read the screen
```

### Tap targets — minimum 64.dp everywhere
Copy and Done buttons: full width, minimum 64.dp height.

### Digit announcement — individual digits only
TTS: "Your code is... 4... 7... 2... 1" with 400ms pause between digits.
NEVER as a number. contentDescription for the digit group must match this.

### Redundant signalling — never colour alone
Every state needs colour + icon + text label simultaneously.
| State     | Colour   | Icon | Text              |
|-----------|----------|------|-------------------|
| HEALTHY   | primary  | 🕐   | "X min remaining" |
| WARNING   | tertiary | 🕐   | "X min remaining" |
| CRITICAL  | error    | ⚠️   | "Expires soon"    |
| EXPIRED   | outline  | ✕   | "Expired"         |

### Payment OTP — elevated treatment
When OtpResult.type == OtpType.PAYMENT:
- Show amount and source above digits
- Use errorContainer colour scheme
- TTS preamble: "Payment OTP. This approves [amount]. Your code is..."
- Warning: "Never share this code with anyone — not even bank staff"

## Verification commands
```bash
./gradlew test                                              # all unit tests
./gradlew :app:testDebugUnitTest --tests "com.bigotp.app.parser.*"  # parser only
./gradlew assembleDebug                                     # debug build
./gradlew lint                                              # lint
./gradlew installDebug                                      # install on device
./gradlew check                                             # everything
```

Always run parser tests after any parser change. Parser tests must pass
before any other work proceeds.

## Pattern config
Remote CDN: https://bigotp.in/patterns/v1.json
Dev fallback: app/src/main/assets/patterns_fallback.json
Three layers: remote CDN → DataStore cache → bundled assets. Always works offline.

## Strictly OUT OF SCOPE for v1
Do not implement: Hindi TTS, Firebase/Crashlytics,
any analytics SDK, READ_SMS permission, Wear OS, caregiver mode,
any network call that transmits OTP content or user identifiers.

## Decisions already made — do not revisit
NotificationListenerService (not READ_SMS) · DataStore (not SharedPreferences)
Jetpack Compose (not XML) · Material 3 dynamic colour (not custom theme)
WorkManager for background · English only ·
