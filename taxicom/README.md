# Taxi Commandos

Driver assistant for Bolt, Uber and FREENOW. Scores every offer to maximize income, protects acceptance rate, reports the shift by e-mail and Telegram, and is controlled from the steering-wheel buttons.

- Site / APK: https://taxicom.app
- API: https://api.taxicom.app
- Android `applicationId`: `app.taxicom.commandos` (full) · `app.taxicom.commandos.lite` (Play)

Architecture and feature specs live in `/docs` of this repository. The existing Telegram bot (`bot.py`) is unrelated and must not be modified.

## Backend

```bash
cd taxicom/backend
python3 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
TAXICOM_SCHEDULER=0 .venv/bin/uvicorn app.main:app --reload --port 8000
.venv/bin/pytest
```

## Android

Requires JDK 17+, Android SDK 35/36.

```bash
cd taxicom/android
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :core-parser:test :core-scoring:test
./gradlew :app:assembleFullDebug :app:assembleLiteDebug
```

- **full** — Accessibility card reading, auto-tap, overlay. Distributed as APK from taxicom.app (blocked by Google Play Accessibility policy).
- **lite** — Google Play edition without those capabilities.

Emulator debug API points at `http://10.0.2.2:8000` (host machine).
