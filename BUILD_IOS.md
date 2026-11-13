# Building WikiRandomizer-P for iOS

WikiRandomizer-P uses a Kotlin Multiplatform module (`shared`) for the shared core and the Compose UI, hosted by Android and iOS:

```
shared/                 Kotlin Multiplatform (Android + iOS) – core logic + shared Compose UI
app/                    Android host (Jetpack Compose entry point)
iosApp/                 SwiftUI host that displays the shared Compose reader
build_ios.sh            Installer/build script for macOS
build_android.sh        Android build script for macOS/Linux
build_windows.bat       Android build script for Windows
```

Kotlin/Native compiles the `shared` module to a static `shared.framework` for `iosArm64`,
`iosX64` and `iosSimulatorArm64`. Xcode compiles the small SwiftUI host and links that
framework. The shared Compose UI compiles for both targets; iOS renders it today while the
remaining Android screens migrate to it, so URL encoding and text helpers cannot drift.

> **Apple limitation:** producing an iOS `.app`/`.ipa` requires macOS and Xcode. There is no
> legal or practical way to cross-compile the final iOS binary on Linux or Windows. The script
> below automates everything that can be automated on a Mac.

## One-command build

On macOS:

```bash
./build_ios.sh
```

This will:

1. verify Xcode and the command-line tools,
2. install Homebrew (if missing), Temurin JDK 17 (if missing) and XcodeGen (if missing),
3. generate `iosApp/WikiReader.xcodeproj` from `iosApp/project.yml`,
4. let the Xcode build phase run `:shared:embedAndSignAppleFrameworkForXcode`,
5. build `WikiReader.app` for an available iPhone simulator,
6. boot the simulator, install the app and launch it.

Useful modes:

```bash
./build_ios.sh simulator        # same as the default
./build_ios.sh build            # build for the simulator, do not launch
./build_ios.sh open             # generate the project and open it in Xcode
./build_ios.sh setup            # install/check the toolchain only
./build_ios.sh clean            # remove generated project and build output

# Choose a simulator:
IOS_SIMULATOR="iPhone 16 Pro" ./build_ios.sh
IOS_SIMULATOR_UDID=XXXXXXXX-... ./build_ios.sh

# Build a signed app for a connected device:
DEVELOPMENT_TEAM=ABCDE12345 ./build_ios.sh device
```

The script writes `build_ios_log.txt` and, on failure, `build_ios_errors.txt`, the same way
`build_windows.bat` does for Android.

## Manual Xcode build

If you prefer Xcode:

```bash
brew install xcodegen temurin@17
cd iosApp
xcodegen generate
open WikiReader.xcodeproj
```

Select the `WikiReader` scheme and run. The pre-build script builds the Kotlin framework
automatically. For device builds, set your team under **Signing & Capabilities**.

## Requirements

| Tool | Version | Install |
| --- | --- | --- |
| macOS | 13+ | App Store |
| Xcode | 15+ | App Store |
| Xcode command-line tools | matching Xcode | `xcode-select --install` |
| JDK | 17 or newer | `brew install --cask temurin@17` |
| XcodeGen | 2.40+ | `brew install xcodegen` |
| iOS deployment target | 16.0 | set in `iosApp/project.yml` |

The first build downloads the Kotlin/Native toolchain and Gradle dependencies, so it can take
several minutes. Later builds are incremental.

## What runs on iOS?

- The shared core builds every Wikipedia URL (article, mobile article, random summary, image,
  search) and provides title/text helpers.
- `iosApp` hosts `ReaderViewController()`, which renders the shared Compose app shell. It picks a
  language, fetches a random summary through Ktor/Darwin, shows the title, description, read time
  and extract, and opens the full article in an embedded `WKWebView` (Android uses `WebView`).
  The drawer also contains Topics, Saved, History, Search, Wikipedia, Stats, Settings and About. Topic picks load random
  category articles; Saved articles and opened-article history use the shared `LibraryRepository`;
  library search filters both lists and keeps recent queries; the Wikipedia screen runs full-text
  search through the shared search-source contract; theme and language persist through
  `NSUserDefaults` via the shared `SettingsStore`.
- The Android app keeps all of its existing features; `build_ios.sh` does not change the APK.
- Remaining Android screens are migrated into `shared/src/commonMain` in phases, then both hosts
  use the same implementation.

## Adding shared code

Put platform-neutral Kotlin in `shared/src/commonMain/kotlin`. If code needs a platform API,
use `expect`/`actual` in `androidMain` / `iosMain`. Rebuild the framework with:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Run the shared tests with:

```bash
./gradlew :shared:allTests          # macOS, runs iOS tests too
./gradlew :shared:testDebugUnitTest # Android/JVM unit tests
```

## Troubleshooting

- **`xcodebuild: error: SDK "iphonesimulator" cannot be located`** – open Xcode once and install
  an iOS Simulator runtime under Settings > Platforms.
- **`No such module 'shared'`** – run the build from the repository root so the pre-build script
  can call `./gradlew`; do not move `iosApp` out of the repository.
- **Kotlin/Native download fails** – check the network/proxy and run `./gradlew --refresh-dependencies`.
- **Device signing** – `DEVELOPMENT_TEAM` must match a team in your Apple developer account; free
  personal teams work for local 7-day builds.
