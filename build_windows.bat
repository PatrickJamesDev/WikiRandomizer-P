@echo off
setlocal EnableDelayedExpansion
rem ===== WikiReader - Windows build script =====
rem Usage:  build_windows.bat            -> builds debug APK (installable)
rem         build_windows.bat install    -> builds and installs on a connected phone (USB debugging on)
rem         build_windows.bat clean      -> clean build
rem         build_windows.bat release    -> builds the FAST (optimised) APK: much smoother scrolling than debug
rem         build_windows.bat releaseinstall -> builds the FAST APK and installs it on a connected phone
rem On failure it writes build_errors.txt (just the errors) and build_log.txt (everything).

cd /d "%~dp0"
set "RESULT=0"

rem --- 1. Find a JDK (17+). Android Studio's bundled JBR is preferred. ---
if not defined JAVA_HOME (
  for %%P in ("%ProgramFiles%\Android\Android Studio\jbr" "%ProgramFiles%\Android\Android Studio1\jbr" "%LOCALAPPDATA%\Programs\Android Studio\jbr") do (
    if exist "%%~P\bin\java.exe" set "JAVA_HOME=%%~P"
  )
)
if not defined JAVA_HOME (
  where java >nul 2>nul && (echo Using java from PATH) || (
    echo [ERROR] No JDK found. Install Android Studio or JDK 17+ ^(e.g. Temurin^) and re-run.
    set "RESULT=1" & goto :end)
) else (
  set "PATH=%JAVA_HOME%\bin;%PATH%"
  echo JAVA_HOME=%JAVA_HOME%
)

rem --- 2. Find the Android SDK and write local.properties if missing. ---
if not exist local.properties (
  set "SDK="
  if defined ANDROID_HOME set "SDK=%ANDROID_HOME%"
  if not defined SDK if defined ANDROID_SDK_ROOT set "SDK=%ANDROID_SDK_ROOT%"
  if not defined SDK if exist "%LOCALAPPDATA%\Android\Sdk" set "SDK=%LOCALAPPDATA%\Android\Sdk"
  if not defined SDK (
    echo [ERROR] Android SDK not found. Install Android Studio, open SDK Manager, install
    echo         "Android SDK Platform 36" and "Build-Tools", then re-run.
    set "RESULT=1" & goto :end
  )
  set "SDK_ESC=!SDK:\=\\!"
  set "SDK_ESC=!SDK_ESC::=\:!"
  > local.properties echo sdk.dir=!SDK_ESC!
  echo Wrote local.properties ^(sdk.dir=!SDK!^)
)

rem --- 3. Build. --no-configuration-cache avoids stale-cache failures. ---
set "TASK=:app:assembleDebug"
set "APKPATH=app\build\outputs\apk\debug\app-debug.apk"
if /i "%~1"=="release" (
  set "TASK=:app:assembleRelease -PlocalRelease"
  set "APKPATH=app\build\outputs\apk\release\app-release.apk"
)
if /i "%~1"=="install" set "TASK=:app:installDebug"
if /i "%~1"=="releaseinstall" set "TASK=:app:installRelease -PlocalRelease"
if /i "%~1"=="clean" call gradlew.bat clean --no-configuration-cache

if exist build_log.txt del build_log.txt
if exist build_errors.txt del build_errors.txt
echo Building (%TASK%)... the first run downloads dependencies and can take 10+ minutes.

rem Show Gradle output live AND keep a UTF-8 copy in build_log.txt.
powershell -NoProfile -ExecutionPolicy Bypass -Command "& .\gradlew.bat %TASK% --no-configuration-cache --console=plain 2>&1 | ForEach-Object { $_; Add-Content -LiteralPath 'build_log.txt' -Value $_ -Encoding UTF8 }; exit $LASTEXITCODE"
if errorlevel 1 set "RESULT=1"

if "%RESULT%"=="1" (
  echo.
  echo ================== BUILD FAILED ==================
  findstr /B /C:"e: " /C:"error:" /C:"* What went wrong" /C:"Execution failed" /C:"Caused by" build_log.txt > build_errors.txt
  type build_errors.txt
  echo ==================================================
  echo Errors saved to: %CD%\build_errors.txt
  echo Full log saved to: %CD%\build_log.txt
  echo Please send build_errors.txt to get it fixed.
  goto :end
)

echo.
echo [OK] APK: %CD%\%APKPATH%
if /i "%~1"=="install" call :aot
if /i "%~1"=="releaseinstall" call :aot

:end
echo.
if /i not "%~2"=="nopause" pause
endlocal & exit /b %RESULT%

rem --- After an install Android runs the app interpreted/JIT until it compiles it in the background
rem     (can take a day), which feels laggy. Compile it ahead of time now.
:aot
set "ADBEXE="
where adb >nul 2>nul && set "ADBEXE=adb"
if not defined ADBEXE if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADBEXE=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADBEXE if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADBEXE=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADBEXE (
  echo [note] adb not found - skipped the ahead-of-time compile step
  exit /b 0
)
echo Compiling the app ahead of time on the phone ^(smoother from the first launch, takes up to a minute^)...
"%ADBEXE%" shell cmd package compile -m speed -f org.nsh07.wikireader
exit /b 0
