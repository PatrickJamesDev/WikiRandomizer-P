# Building WikiReader on Windows

1. Install **Android Studio** (it bundles JDK 21 and the SDK manager).
   Open *Settings > Languages & Frameworks > Android SDK* and install **Android SDK Platform 36**
   and **Android SDK Build-Tools**.
2. Unzip this project to a path **without spaces**, e.g. `C:\dev\WikiReader`.
3. Double-click `build_windows.bat` (or run it from cmd). First run downloads Gradle 9.1 and
   dependencies (~1 GB, several minutes; internet required).
4. Result: `app\build\outputs\apk\debug\app-debug.apk`. Copy to the phone, or run
   `build_windows.bat install` with USB debugging enabled.

Troubleshooting
- "SDK location not found": the script writes `local.properties` for you; if the SDK is elsewhere,
  set `ANDROID_HOME` first.
- Stale or odd errors: `build_windows.bat clean`.
- Compile errors: paste the first line starting with `e: file:///` and I will fix it.
- A signed *release* APK needs your own keystore; the debug APK is fully functional.
