#!/usr/bin/env bash
# =============================================================================
#  WikiReader / WikiRandomizer-P - iOS installer & build script
#
#  Usage:
#    ./build_ios.sh                 install dependencies, build for the simulator,
#                                   install it and launch it
#    ./build_ios.sh simulator       same as above
#    ./build_ios.sh build           build for the simulator only
#    ./build_ios.sh device          build a signed app for a connected iPhone/iPad
#                                   (set DEVELOPMENT_TEAM=ABCDE12345)
#    ./build_ios.sh open            generate the Xcode project and open it
#    ./build_ios.sh clean           remove build output and the Kotlin framework
#    ./build_ios.sh setup           only install the tools needed by the project
#
#  Environment overrides:
#    IOS_SIMULATOR="iPhone 16 Pro"   simulator name (default: first available iPhone)
#    IOS_SIMULATOR_UDID=...          simulator UDID (takes precedence)
#    DEVELOPMENT_TEAM=ABCDE12345     Apple team id, required for device builds
#    JAVA_HOME=...                   JDK 17+ to use instead of the detected one
#
#  The script only runs on macOS: iOS apps cannot be built on Linux or Windows.
#  Everything it installs (Homebrew packages) is documented in BUILD_IOS.md.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

MODE="${1:-install}"
LOG="$ROOT/build_ios_log.txt"
ERRLOG="$ROOT/build_ios_errors.txt"
DERIVED="$ROOT/build/ios-derived"
PROJECT_DIR="$ROOT/iosApp"
PROJECT="$PROJECT_DIR/WikiReader.xcodeproj"
SCHEME="WikiReader"
BUNDLE_ID="org.nsh07.wikireader"

# Show output and keep a UTF-8 copy, like build_windows.bat does on Windows.
exec > >(tee "$LOG") 2>&1
trap 'status=$?; if [ $status -ne 0 ]; then echo; echo "================== BUILD FAILED =================="; grep -E "(^error:| error:|BUILD FAILED|Execution failed|Caused by:)" "$LOG" > "$ERRLOG" 2>/dev/null || true; cat "$ERRLOG" 2>/dev/null || true; echo "=================================================="; echo "Full log: $LOG"; fi; exit $status' EXIT

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }
have() { command -v "$1" >/dev/null 2>&1; }

require_macos() {
    if [ "$(uname -s)" != "Darwin" ]; then
        echo "[ERROR] iOS builds require macOS with Xcode."
        echo "        This machine is $(uname -s). Use build_windows.bat or build_android.sh for Android."
        exit 1
    fi
}

require_xcode() {
    if ! xcode-select -p >/dev/null 2>&1; then
        echo "[ERROR] Xcode command line tools were not found."
        echo "        Install Xcode from the App Store, then run: sudo xcode-select --switch /Applications/Xcode.app"
        exit 1
    fi
    if ! have xcodebuild; then
        echo "[ERROR] xcodebuild is missing. Open Xcode once to finish its installation."
        exit 1
    fi
    echo "Xcode: $(xcodebuild -version | tr '\n' ' ')"
}

install_homebrew() {
    if have brew; then return; fi
    echo "[INFO] Homebrew is not installed. Installing it now (this is the same installer from brew.sh)..."
    NONINTERACTIVE=1 /bin/bash -c \
        "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    if [ -x /opt/homebrew/bin/brew ]; then
        eval "$(/opt/homebrew/bin/brew shellenv)"
    elif [ -x /usr/local/bin/brew ]; then
        eval "$(/usr/local/bin/brew shellenv)"
    fi
    have brew || { echo "[ERROR] Homebrew installation did not complete."; exit 1; }
}

find_java() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        return
    fi
    if /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
        export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
    elif /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
        export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
    else
        say "Installing Temurin JDK 17"
        brew install --cask temurin@17
        export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
    fi
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "JAVA_HOME=$JAVA_HOME"
}

setup_tools() {
    require_macos
    require_xcode
    install_homebrew
    find_java

    if ! have xcodegen; then
        say "Installing XcodeGen"
        brew install xcodegen
    fi

    # The Xcode build phase calls the wrapper, which must stay executable after a zip copy.
    chmod +x "$ROOT/gradlew" 2>/dev/null || true
    ( cd "$PROJECT_DIR" && xcodegen generate --spec project.yml )
    echo "Generated $PROJECT"
}

select_simulator() {
    SIM_NAME=""
    SIM_UDID=""

    if [ -n "${IOS_SIMULATOR_UDID:-}" ]; then
        SIM_UDID="$IOS_SIMULATOR_UDID"
        SIM_NAME="$(xcrun simctl list devices available | awk -v id="$SIM_UDID" '
            index($0, "(" id ")") {
                line = $0
                sub(/^[[:space:]]*/, "", line)
                sub(/ \([0-9A-Fa-f-]+\).*/, "", line)
                print line
                exit
            }
        ')"
        if [ -z "$SIM_NAME" ]; then
            echo "[ERROR] Simulator UDID $SIM_UDID was not found."
            echo "        Run: xcrun simctl list devices available"
            exit 1
        fi
    elif [ -n "${IOS_SIMULATOR:-}" ]; then
        SIM_NAME="$IOS_SIMULATOR"
        SIM_UDID="$(xcrun simctl list devices available | awk -v name="$SIM_NAME" '
            index($0, name " (") {
                if (match($0, /\([0-9A-Fa-f-]+\)/)) {
                    print substr($0, RSTART + 1, RLENGTH - 2)
                    exit
                }
            }
        ')"
        if [ -z "$SIM_UDID" ]; then
            echo "[ERROR] Simulator \"$SIM_NAME\" was not found or is unavailable."
            echo "        Run: xcrun simctl list devices available"
            exit 1
        fi
    else
        SIM_UDID="$(xcrun simctl list devices available | awk -F '[()]' '/iPhone/ {print $2; exit}')"
        if [ -z "$SIM_UDID" ]; then
            echo "[ERROR] No iPhone simulator is installed."
            echo "        Open Xcode > Settings > Platforms and install an iOS Simulator runtime."
            exit 1
        fi
        SIM_NAME="$(xcrun simctl list devices available | awk -F '[()]' '/iPhone/ {print $1; exit}' | sed 's/[[:space:]]*$//')"
    fi

    DESTINATION="platform=iOS Simulator,id=$SIM_UDID"
}

clean_build() {
    say "Cleaning Android/Kotlin and Xcode output"
    ./gradlew clean --no-configuration-cache || true
    rm -rf "$ROOT/build/ios-derived" "$ROOT/build/ios" "$PROJECT"
    echo "Clean."
}

build_simulator() {
    setup_tools
    select_simulator
    say "Building $SCHEME for the simulator ($SIM_NAME)"
    xcodebuild \
        -project "$PROJECT" \
        -scheme "$SCHEME" \
        -configuration Debug \
        -destination "$DESTINATION" \
        -derivedDataPath "$DERIVED" \
        CODE_SIGNING_ALLOWED=NO \
        build
}

install_simulator() {
    build_simulator
    say "Installing on $SIM_NAME"
    xcrun simctl boot "$SIM_UDID" 2>/dev/null || true
    xcrun simctl bootstatus "$SIM_UDID" -b >/dev/null 2>&1 || true
    open -a Simulator
    APP="$(find "$DERIVED/Build/Products/Debug-iphonesimulator" -maxdepth 2 -name 'WikiReader.app' -print -quit)"
    if [ -z "$APP" ]; then
        echo "[ERROR] WikiReader.app was not produced."
        exit 1
    fi
    xcrun simctl install "$SIM_UDID" "$APP"
    xcrun simctl launch "$SIM_UDID" "$BUNDLE_ID"
    echo
    echo "[OK] WikiReader is running in the simulator."
}

build_device() {
    setup_tools
    if [ -z "${DEVELOPMENT_TEAM:-}" ]; then
        echo "[ERROR] A device build needs an Apple development team."
        echo "        Run: DEVELOPMENT_TEAM=ABCDE12345 ./build_ios.sh device"
        exit 1
    fi
    say "Building $SCHEME for a connected device"
    xcodebuild \
        -project "$PROJECT" \
        -scheme "$SCHEME" \
        -configuration Release \
        -destination "generic/platform=iOS" \
        -derivedDataPath "$DERIVED" \
        -allowProvisioningUpdates \
        DEVELOPMENT_TEAM="$DEVELOPMENT_TEAM" \
        CODE_SIGN_STYLE=Automatic \
        build
    APP="$(find "$DERIVED/Build/Products/Release-iphoneos" -maxdepth 2 -name 'WikiReader.app' -print -quit)"
    echo "[OK] Device app: $APP"
    echo "     Install it with Xcode (Window > Devices and Simulators) or:"
    echo "     xcrun devicectl device install app --device <UDID> \"$APP\""
}

case "$MODE" in
    install|simulator|sim)
        install_simulator
        ;;
    build)
        build_simulator
        echo "[OK] Simulator app built in $DERIVED/Build/Products/Debug-iphonesimulator"
        ;;
    device)
        build_device
        ;;
    open)
        setup_tools
        open "$PROJECT"
        ;;
    setup)
        setup_tools
        echo "[OK] iOS toolchain ready."
        ;;
    clean)
        clean_build
        ;;
    *)
        echo "Unknown option: $MODE"
        echo "Use: install | simulator | build | device | open | setup | clean"
        exit 2
        ;;
esac
