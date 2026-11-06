# Kiwix / ZIM support — implementation notes

Last updated: 2026-11-03

This document records what was added for optional offline Kiwix/ZIM reading and the
non-obvious technical constraints that were verified against the actual artifacts.

## Dependency

- `org.kiwix:libkiwix:2.7.0` (Maven Central, AAR, GPLv3 — the app is GPLv3).
- Declared in `gradle/libs.versions.toml` (`libkiwix = "2.7.0"`) and
  `app/build.gradle.kts` (`implementation(libs.libkiwix)`).
- The AAR contains `org.kiwix.libkiwix.JNIKiwix` (loader) and the `org.kiwix.libzim.*`
  Java bindings, plus native `.so` files for arm64-v8a, armeabi-v7a, x86 and x86_64
  (~200 MB uncompressed). `packaging { jniLibs { useLegacyPackaging = true } }` keeps
  them compressed in the APK; they are extracted at install time.
- The arm64 libraries are already 16 KB page-size aligned (`LOAD` alignment 0x4000),
  so they are compatible with Android 15+ 16 KB devices.
- `libkiwix` has a `runtime` dependency on `com.getkeepsafe.relinker:relinker:1.4.5`.
  Gradle includes it in the app's runtime classpath, and ProGuard keeps its classes.

## Files

| File | Purpose |
| --- | --- |
| `data/kiwix/KiwixRepository.kt` | Opens the SAF `.zim`, reads metadata/articles/resources, title search, persists the choice. |
| `ui/kiwix/KiwixScreen.kt` | Offline WebView reader (toolbar, history, random, title search dialog). |
| `ui/settingsScreen/KiwixSettingsCard.kt` | Settings card: picker, metadata, "use for random", open/remove. |
| `ui/AppScreen.kt` | `KiwixReader(random, path)` route; Random buttons route to Kiwix when enabled. |
| `ui/homeScreen/viewModel/HomeScreenViewModel.kt` | Repository ownership, refresh on start, offline download selection. |
| `res/values/strings.xml` | All new UI strings. |

## Verified libzim behaviour (important)

### File descriptor lifetime

`zim::Archive(int fd)` documents:

> Fd is used only at Archive creation. Ownership of the fd is not taken and it must be
> closed by caller.

The JNI constructor is implemented as `FilePart(int fd)` → `getFilePathFromFD(fd)` →
`open("/dev/fd/<fd>")`. In other words libzim opens its **own** descriptor during
construction and never closes the one passed in. Therefore
`KiwixRepository.openLocked()` opens the SAF descriptor, constructs the archive, and
closes the descriptor in a `finally`. Detaching it (as earlier revisions did) leaked
one fd per archive selection.

### ICU data — do not use `SuggestionSearcher`

The bundled `libzim.so` / `libkiwix.so` link ICU in **stubdata** mode: the symbol
`icudt73_dat` is a 64-byte "stubdata" record in `.rodata`, not the real ICU data, and
the AAR ships no `icudt73l.dat`. `zim::SuggestionSearcher`, when the ZIM has an
embedded Xapian suggestion database, calls `removeAccents()` which creates an ICU
transliterator; without the data file that returns null and dereferences it in native
code (crash) or fails the query.

`KiwixRepository.suggestions()` therefore uses the plain title index
(`Archive.findByTitle(prefix)`) and filters to HTML items. It needs no ICU data, and
prefix matching is the expected behaviour for a search-as-you-type field.

If Xapian-quality fuzzy search is ever wanted, the app would have to ship an
ICU 73 data file (tens of MB, e.g. `icudt73l.dat`), copy it to `filesDir`, and call
`JNIKiwix.setDataDirectory(dir)` before creating a `SuggestionSearcher`. That is
deliberately not done.

### Opening with a SAF descriptor

`/dev/fd/N` → `/proc/self/fd/N` is re-opened by libzim, so the archive works for
documents outside the app sandbox (Downloads, SD card, document providers). The read
permission is persisted with `takePersistableUriPermission`. Reopening after a reboot
works if the provider grants persistable permissions; otherwise the settings card shows
the error and the user can pick the file again.

## Reading model

- One `WebView` per reader screen. The ZIM entry is loaded with
  `loadDataWithBaseURL("https://kiwix.local/<path>", html, "text/html", "utf-8", null)`.
- `shouldInterceptRequest` answers every `kiwix.local` URL from the archive
  (`Archive.getEntryByPath`); every other host gets an empty response, so the reader is
  genuinely offline. `allowFileAccess` / `allowContentAccess` are off. Entries larger than
  24 MB are skipped (404) so a video/audio file cannot allocate its full size as a byte array
  and crash the app.
- `shouldOverrideUrlLoading` sends `kiwix.local` links to the in-app reader (and keeps a
  small in-app history) and external `http(s)` links to the system browser.
- JavaScript stays disabled: ZIM articles are static documents, and this removes a whole
  class of script/network behaviour. Lazy images still load through the interceptor.
- Double-tap/pinch zoom is delegated to the WebView (`setSupportZoom(true)`); the app's
  own article renderer (and its gestures) is untouched.
- The path of the page currently shown is kept with `rememberSaveable`, so rotating the
  device (which recreates the activity) restores that page instead of drawing a new random
  article.

## Random article routing

- `KiwixStatus.useForRandom` is persisted separately (SharedPreferences file `kiwix`).
- Every Random entry point (toolbar, search bar button, pull-to-refresh, tutorial,
  settings) goes through `onHomeAction(HomeAction.LoadRandom)` in `AppScreen.kt`.
  When an archive is ready and `useForRandom` is on, that opens `KiwixReader(random=true)`
  instead of calling the Wikipedia random API.
- `KiwixReader.path` is non-null with an empty default so `toRoute<KiwixReader>()` is
  always safe.

## Offline downloads (Wikipedia article cache)

- Downloaded articles live in `<digest>.offline.json` next to the normal cache and are
  exempt from cache trimming.
- `balancedOfflineRows()` uses `includeSeen = false`, skips titles where
  `articleCache.isOffline(...)` is already true, and samples extra candidates so that
  already-downloaded rows do not starve the batch.
- The download card shows the number of *unseen, not-yet-downloaded* articles available
  and an explicit "nothing new" message when there are none.
- Each download is also stored under the topic-index spelling of the title (`alsoAs`), so a
  later run recognises it even when the API capitalises the title differently.
- The old fallback that reused already-seen articles is gone.

## Build & test

### Build log

- **2026-11-03, debug build:** all Kiwix/ZIM code compiled; the only errors were in
  `ui/image/FullScreenImage.kt`:
  `Cannot infer type for type parameter 'T'` at the two hoisted
  `val doubleTapSpec = motionScheme.defaultSpatialSpec()` lines. `defaultSpatialSpec` is
  generic (`<T>`) and a bare assignment gives the compiler no expected type. Fixed by
  writing `defaultSpatialSpec<Float>()` (it is used for the `Float` zoom value in
  `animate(...)`). Other uses pass it to typed parameters, where inference works.

Build on Windows with the existing script:

```
build_windows.bat            REM debug APK
build_windows.bat release    REM fast/minified APK
```

After installing:

1. Settings › Reading & topics › Kiwix database › Choose `.zim` file.
2. Confirm the article count/size appear, then tap **Open reader**.
3. Tap an in-page link (stays in the reader), a back arrow (in-app history), the random
   icon, and the search icon (type at least two letters).
4. Tap an external `http(s)` link: it must open the system browser, not load inside the
   reader.
5. Toggle **Use for random articles**, then press Random on the home screen.
6. Disable the toggle again; Random must return to Wikipedia.
7. In Random topics, run an offline download twice: the second run must not re-download
   the same articles and must show the reduced available count.

## Known limitations

- APK size grows substantially (libzim + libkiwix, four ABIs). An App Bundle would split
  native libs per ABI; a plain APK contains all four. `abiFilters` could be used if only
  arm64 devices matter.
- Split ZIM files (`.zimaa`/`.zimab`…) are not supported through a single SAF URI.
- JavaScript-dependent ZIM features (some math/collapsible widgets) are inert.
- Title suggestions use prefix matching, not Xapian fuzzy ranking (see ICU note above).
- The archive is re-opened on app start only when a file was selected; no native library
  is loaded for users who never choose one.
