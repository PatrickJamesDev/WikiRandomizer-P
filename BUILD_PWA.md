# Web app (PWA)

WikiRandomizer-P includes an installable Progressive Web App in [`pwa/`](pwa/). It uses the same
platform-neutral Kotlin core as the Android and iOS apps, compiled to WebAssembly, while the
interface is plain HTML, CSS and JavaScript. This keeps the web app small and makes Wikipedia's
article HTML render directly.

## Requirements

- JDK 17 or newer.
- An Android SDK configured through `ANDROID_HOME`, `ANDROID_SDK_ROOT` or `local.properties`.
  The Gradle build configures the Android modules even when only the WASM task is requested.
- Python 3 for the local preview server (any static file server works).
- A modern browser. The WASM core needs WebAssembly GC and is used when available. Browsers
  without it automatically use the JavaScript fallback core.

## Build

```bash
./build_pwa.sh
```

This runs `:shared:wasmJsBrowserProductionLibraryDistribution` and copies the generated
`.mjs`, `.uninstantiated.mjs` and `.wasm` files into `pwa/wasm/`.

To build and preview in one step:

```bash
./build_pwa.sh serve
```

The app is then available at <http://localhost:8080>. Press `Ctrl+C` to stop the server.
`PORT=9000 ./build_pwa.sh serve` selects another port.

Use `./build_pwa.sh clean` to remove generated WASM output.

## Local testing

Service workers require a secure context. `http://localhost` and `http://127.0.0.1` are accepted;
other hosts need HTTPS.

A useful check sequence:

1. Open the app and confirm the home feed loads.
2. Open a featured or random article. The full article is downloaded and cached.
3. Select a passage in an article and save it as a highlight.
4. Save the article, then find it under **Saved**; reopen it from **Tabs**.
5. Turn the network off and reopen the article, then reload the app.
6. Use the browser's install button or **Settings → About → Install app**.
7. In **Settings → Data & privacy**, confirm cached-article counts and storage usage.
8. Open **Settings → Reading & topics → Random article topics**, choose a built-in topic, and add
   a custom topic phrase. Use the shuffle button to check both paths.

The PWA cannot be tested reliably by opening `pwa/index.html` directly from the filesystem because
ES modules, `fetch` and service workers need an HTTP origin.

## GitHub Pages

The workflow in `.github/workflows/pages.yml` rebuilds the WASM core and deploys `pwa/` whenever
`main` changes. To enable it:

1. Open the repository's **Settings → Pages**.
2. Set **Source** to **GitHub Actions**.
3. Run the workflow, or push a change to `main`.

The workflow uses the repository's Pages URL; no custom domain is required. Because the app uses
relative URLs, it works from a repository subpath as well as from a custom domain.

## Architecture

- `shared/src/commonMain/` contains URL, title and text helpers shared with Android and iOS.
- `shared/src/wasmJsMain/` exposes those helpers to JavaScript through `WikiWasmExports.kt`.
- `pwa/app.js` contains the reader UI, feed/search/article loading, saved articles, history,
  highlights, tabs, built-in and custom random topics, settings, statistics and backup/restore.
- `pwa/sw.js` caches the app shell plus visited article HTML, API responses and images.
- `pwa/manifest.webmanifest` supplies the install metadata, icons and shortcuts.

The JavaScript fallback in `pwa/app.js` mirrors the exported core functions if the WASM module
cannot load. The interface stays usable; only the Kotlin/WASM implementation is skipped.

## Offline behaviour

The service worker uses four caches:

- `wikireader-shell-*` — app shell, styles, scripts, icons and WASM.
- `wikireader-articles-*` — full article HTML that has been opened.
- `wikireader-runtime-*` — Wikipedia feed and search API responses.
- `wikireader-images-*` — article and feed images, trimmed to a bounded number of entries.

Navigations use the network when available and fall back to the cached shell. Article HTML and
API requests use network-first behaviour with cached fallback; images use cache-first behaviour.
Unvisited articles are not available offline.

After changing shell assets, bump `VERSION` in `pwa/sw.js` to force a clean service-worker cache.
The app tells the reader when an updated worker is ready to activate.

## Differences from the native apps

The PWA is a lightweight web reader, not a replacement for every native capability:

- Kiwix/ZIM offline libraries, Android intents, widgets, background work and other native
  integrations remain in the Android app.
- The web app offers its own Material 3-style colour schemes and typography settings rather than
  Android Material You dynamic colours and Compose-specific layouts.
- Built-in topics use Wikipedia categories. Custom topics use Wikipedia search, because the web
  app does not build the native on-device topic index.
- The PWA interface is English; article language follows the language picker.
- Some complex Wikipedia content (for example certain math or interactive elements) may be
  simplified by the article HTML sanitizer.

All existing Android and iOS features remain in their native targets.
