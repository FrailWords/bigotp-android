# Contributing to BigOTP

BigOTP is a free accessibility app for elderly and disabled users in India. Contributions that help more people receive their OTPs reliably are warmly welcome.

---

## Ways to contribute

| Area | What helps |
|------|------------|
| **OTP patterns** | Add/improve bank or service patterns in `patterns_fallback.json` |
| **Bug reports** | Open a GitHub issue with device model, Android version, and an anonymised SMS sample |
| **Translations** | Future work — English only for v1 |
| **Accessibility** | Improvements that better serve TalkBack, large-text, and switch-access users |

---

## Adding or improving OTP patterns

All OTP patterns live in one JSON file:

```
app/src/main/assets/patterns_fallback.json
```

### Pattern format

```json
{
  "id": "your-bank-login",
  "sourceKeywords": ["YOURBANK", "YBKLTD", "Your Bank"],
  "otpRegex": "\\b(\\d{6})\\b",
  "type": "login",
  "amountRegex": null,
  "friendlyName": "Your Bank"
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `id` | ✅ | Unique slug, kebab-case (`bank-name-login`, `bank-name-payment`) |
| `sourceKeywords` | ✅ | Strings matched against the SMS sender ID (case-insensitive substring match) |
| `otpRegex` | ✅ | Java regex. **Capturing group 1** must be the OTP code itself. |
| `type` | ✅ | `"login"` or `"payment"` |
| `amountRegex` | ❌ | Payment patterns only. Capturing group 1 = the numeric amount (no currency symbol). |
| `friendlyName` | ❌ | Human-readable label shown in the app. Falls back to the first `sourceKeyword`. |

### Rules the parser enforces

1. `otpRegex` capturing group 1 is the code — `\b(\d{6})\b` is typical for 6-digit codes.
2. Keep `sourceKeywords` tight. Overly broad keywords (e.g. `"OTP"`) increase false positives — that's what the `generic` catch-all pattern is for.
3. Payment patterns must also match login OTPs from the same sender (the parser promotes them automatically when it detects payment keywords and an amount).
4. Prefer adding a **separate** `login` and `payment` entry for the same bank so amounts are extracted correctly (see existing `sbi` / `sbi-payment` pair).
5. Never hardcode a specific OTP value in `otpRegex` — only the structural pattern.

### How to test your pattern

1. Open the app in debug mode.
2. Go to **Settings → Parser test** (visible in debug builds only).
3. Paste a sample SMS and verify that your pattern is matched and the correct fields are extracted.

Run all parser unit tests before submitting:

```bash
./gradlew :app:testDebugUnitTest --tests "com.bigotp.app.parser.*"
```

### Pattern versioning

The file has a top-level `"version"` integer. Increment it when you add or change patterns — the CDN delivery layer uses this to decide whether to push an update to devices.

```json
{
  "version": 2,
  "patterns": [ ... ]
}
```

---

## Code contributions

### Before you start

- Read `CLAUDE.md` for the complete list of architectural decisions and design rules — many constraints exist for accessibility reasons and are non-negotiable. (Note: `CLAUDE.md` is a configuration file for the AI coding assistant used in this project; it doubles as an authoritative architecture reference.)
- Open an issue first for anything beyond a pattern change so we can align on approach.

### Build and test

```bash
./gradlew test          # all unit tests
./gradlew assembleDebug # verify it compiles
./gradlew lint          # lint check
```

### Coding rules (from `CLAUDE.md`)

- Kotlin only. No Java.
- Jetpack Compose only. No XML layouts.
- Material 3 semantic colour tokens only. No hardcoded colours.
- Material 3 type scale only. No hardcoded `sp` values.
- Minimum 64 dp tap targets.
- Every state change needs colour + icon + text label simultaneously (never colour alone).
- Check `ANIMATOR_DURATION_SCALE == 0` before any animation.
- Check `isTouchExplorationEnabled` before auto-TTS.

### Pull request checklist

- [ ] Parser tests pass (`./gradlew :app:testDebugUnitTest --tests "com.bigotp.app.parser.*"`)
- [ ] `./gradlew lint` clean
- [ ] Pattern change: `version` integer incremented
- [ ] No hardcoded colours or sp values
- [ ] No new network calls that transmit OTP content or user identifiers

---

## Out of scope for v1

Do not send PRs for: Hindi TTS, Firebase/Crashlytics, analytics SDKs, READ_SMS permission, Wear OS, caregiver mode, or any network call that transmits OTP content.
