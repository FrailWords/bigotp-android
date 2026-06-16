## What this PR does

<!-- One paragraph describing the change and why. -->

## Type of change

- [ ] Pattern addition / improvement (`patterns_fallback.json`)
- [ ] Bug fix
- [ ] Accessibility improvement
- [ ] New feature
- [ ] Refactor / cleanup

## Checklist

- [ ] Parser tests pass: `./gradlew :app:testDebugUnitTest --tests "com.bigotp.app.parser.*"`
- [ ] `./gradlew lint` is clean
- [ ] **Pattern change only:** `version` integer incremented in `patterns_fallback.json`
- [ ] No hardcoded colours (`#RRGGBB`, `Color(...)`) — Material 3 tokens only
- [ ] No hardcoded `sp` values — Material 3 type scale only
- [ ] No new network call that transmits OTP content or user identifiers
- [ ] Accessibility: every new state has colour + icon + text label simultaneously

## Testing

<!-- How did you verify this works? Device model and Android version if relevant. -->
