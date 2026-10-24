#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "$0")/.."

AAPT2="/data/data/com.termux/files/usr/bin/aapt2"
PROPS="gradle.properties"
BACKUP="$(mktemp)"

if [ ! -x "$AAPT2" ]; then
    echo "ERROR: Termux AAPT2 is unavailable: $AAPT2"
    rm -f "$BACKUP"
    exit 1
fi

cp "$PROPS" "$BACKUP"

restore_props() {
    cp "$BACKUP" "$PROPS"
    rm -f "$BACKUP"
}
trap restore_props EXIT

if [ "$#" -eq 0 ]; then
    set -- assembleDebug
fi

echo "========== VERIFY AAPT2 =========="
"$AAPT2" version

echo
echo "========== APPLY TEMPORARY OVERRIDE =========="
if ! grep -q '^android\.aapt2FromMavenOverride=' "$PROPS"; then
    printf '\nandroid.aapt2FromMavenOverride=%s\n' "$AAPT2" >> "$PROPS"
fi

echo
echo "========== RUN GRADLE =========="
./gradlew "$@" --console=plain

echo
echo "========== BUILD FINISHED =========="
if [ -f app/build/outputs/apk/debug/app-debug.apk ]; then
    ls -lh app/build/outputs/apk/debug/app-debug.apk
    sha256sum app/build/outputs/apk/debug/app-debug.apk
fi
