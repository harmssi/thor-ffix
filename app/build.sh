#!/bin/bash
# Baut die Begleit-App lokal (Gradle + Android-SDK aus local.properties) nach dist/ThorFFIX.apk.
# Optional: --install (adb install -r; adb nutzt ANDROID_SERIAL, falls gesetzt).
set -euo pipefail
cd "$(dirname "$0")"
./gradlew -q assembleRelease
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/ThorFFIX.apk
echo "APK: $PWD/dist/ThorFFIX.apk"
if [ "${1:-}" = "--install" ]; then adb install -r dist/ThorFFIX.apk; fi
