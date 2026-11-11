#!/usr/bin/env bash
# =============================================================================
#  WikiReader / WikiRandomizer-P - Android build script for macOS and Linux
#
#  Usage:
#    ./build_android.sh              build an installable debug APK
#    ./build_android.sh release      build the optimised release APK (debug key)
#    ./build_android.sh install      build and install on a connected phone
#    ./build_android.sh releaseinstall
#    ./build_android.sh clean
#
#  Android builds need a JDK 17+ and the Android SDK (platform 36).
#  On Windows use build_windows.bat; it does the same and finds Android Studio's JDK/SDK.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

MODE="${1:-debug}"
LOG="$ROOT/build_log.txt"
ERRLOG="$ROOT/build_errors.txt"
TASK=":app:assembleDebug"
APK="app/build/outputs/apk/debug/app-debug.apk"

exec > >(tee "$LOG") 2>&1
trap 'status=$?; if [ $status -ne 0 ]; then echo; echo "================== BUILD FAILED =================="; grep -E "(^e: |error:|\\* What went wrong|Execution failed|Caused by:)" "$LOG" > "$ERRLOG" 2>/dev/null || true; cat "$ERRLOG" 2>/dev/null || true; echo "=================================================="; echo "Full log: $LOG"; fi; exit $status' EXIT

case "$MODE" in
    release)        TASK=":app:assembleRelease -PlocalRelease"; APK="app/build/outputs/apk/release/app-release.apk" ;;
    install)        TASK=":app:installDebug" ;;
    releaseinstall) TASK=":app:installRelease -PlocalRelease" ;;
    clean)          TASK="clean" ;;
    debug)          ;;
    *) echo "Unknown option: $MODE"; echo "Use: debug | release | install | releaseinstall | clean"; exit 2 ;;
esac

find_java() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then return; fi
    if [ "$(uname -s)" = "Darwin" ] && /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
        export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
    elif [ -d "${HOME}/.jdks" ]; then
        export JAVA_HOME="$(find "${HOME}/.jdks" -maxdepth 1 -type d -name '*17*' | head -1)"
    fi
    if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
    if ! command -v java >/dev/null 2>&1; then
        echo "[ERROR] No JDK found. Install JDK 17 (Temurin/Android Studio) and set JAVA_HOME."
        exit 1
    fi
}

find_sdk() {
    if [ -f local.properties ]; then return; fi
    SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [ -z "$SDK" ] && [ "$(uname -s)" = "Darwin" ] && [ -d "$HOME/Library/Android/sdk" ]; then
        SDK="$HOME/Library/Android/sdk"
    fi
    if [ -z "$SDK" ] && [ -d "$HOME/Android/Sdk" ]; then
        SDK="$HOME/Android/Sdk"
    fi
    if [ -z "$SDK" ]; then
        echo "[ERROR] Android SDK not found. Install Android Studio's SDK, then set ANDROID_HOME."
        exit 1
    fi
    escaped="${SDK//\\/\\\\}"
    escaped="${escaped//:/\\:}"
    printf 'sdk.dir=%s\n' "$escaped" > local.properties
    echo "Wrote local.properties (sdk.dir=$SDK)"
}

chmod +x gradlew 2>/dev/null || true
find_java
find_sdk

echo "Building ($TASK)... the first run downloads dependencies and can take 10+ minutes."
./gradlew $TASK --no-configuration-cache --console=plain

if [ "$TASK" != "clean" ] && [ "$TASK" != ":app:installDebug" ] && [ "$TASK" != ":app:installRelease -PlocalRelease" ]; then
    echo
    echo "[OK] APK: $ROOT/$APK"
fi
