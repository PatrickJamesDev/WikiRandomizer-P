#!/usr/bin/env bash
# =============================================================================
#  WikiRandomizer-P - build the installable web app
#
#  Usage:
#    ./build_pwa.sh          compile the Kotlin/WASM core and copy it into pwa/
#    ./build_pwa.sh serve    build, then serve pwa/ at http://localhost:8080
#    ./build_pwa.sh clean    remove generated WASM output
#
#  Requires JDK 17+ and an Android SDK because the Gradle build configures the
#  Android modules too. The PWA itself runs in the browser; it does not need
#  an emulator or a connected device.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

MODE="${1:-build}"
DIST="$ROOT/shared/build/dist/wasmJs/productionLibrary"
WASM_OUT="$ROOT/pwa/wasm"
PORT="${PORT:-8080}"
TASK=":shared:wasmJsBrowserProductionLibraryDistribution"

case "$MODE" in
    build|serve) ;;
    clean)
        rm -rf "$DIST" "$WASM_OUT"
        echo "[OK] Removed generated PWA/WASM output."
        exit 0
        ;;
    *)
        echo "Unknown option: $MODE"
        echo "Use: build | serve | clean"
        exit 2
        ;;
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
        echo "[ERROR] No JDK found. Install JDK 17+ and set JAVA_HOME."
        exit 1
    fi
}

find_java
chmod +x gradlew 2>/dev/null || true

echo "Building the Kotlin/WASM core ($TASK)..."
./gradlew "$TASK" --no-configuration-cache --console=plain

for file in \
    WikiRandomizer-P-shared.mjs \
    WikiRandomizer-P-shared.uninstantiated.mjs \
    WikiRandomizer-P-shared.wasm
do
    if [ ! -f "$DIST/$file" ]; then
        echo "[ERROR] Missing expected WASM output: $DIST/$file"
        exit 1
    fi
done

mkdir -p "$WASM_OUT"
cp "$DIST/WikiRandomizer-P-shared.mjs" \
   "$DIST/WikiRandomizer-P-shared.uninstantiated.mjs" \
   "$DIST/WikiRandomizer-P-shared.wasm" \
   "$WASM_OUT/"

echo
echo "[OK] PWA ready: $ROOT/pwa"
echo "     WASM core:  $WASM_OUT"

if [ "$MODE" = "serve" ]; then
    echo
    echo "Serving at http://localhost:$PORT (Ctrl+C to stop)."
    exec python3 -m http.server "$PORT" --directory "$ROOT/pwa"
fi

echo
echo "Preview with: python3 -m http.server $PORT --directory pwa"
