#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "$0")/.."

if [ "$#" -eq 0 ]; then
    set -- assembleDebug
fi

AAPT2="${ANDROID_AAPT2:-/data/data/com.termux/files/usr/bin/aapt2}"

echo "========== BUILD ENVIRONMENT =========="
if [ -x "$AAPT2" ]; then
    echo "Using compatible local AAPT2: $AAPT2"
    "$AAPT2" version
    echo
    echo "========== GRADLE BUILD =========="
    ./gradlew -Dandroid.aapt2FromMavenOverride="$AAPT2" "$@" --console=plain
else
    echo "No local AAPT2 override found; using the Android Gradle Plugin default."
    echo
    echo "========== GRADLE BUILD =========="
    ./gradlew "$@" --console=plain
fi

echo
echo "========== BUILD FINISHED =========="
if [ -f app/build/outputs/apk/debug/app-debug.apk ]; then
    ls -lh app/build/outputs/apk/debug/app-debug.apk
    sha256sum app/build/outputs/apk/debug/app-debug.apk
fi
