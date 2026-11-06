<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="128">

# WikiRandomizer-P

**Read random Wikipedia articles of your desired topics**

</div>

---

WikiRandomizer-P is a lightweight, distraction-free Wikipedia reader for Android, iOS and the
web, built around random discovery. Choose the subjects you care about, let the app build a local library of
matching articles, and press Random whenever you want something new to read. It is a fork of
[WikiReader](https://github.com/nsh07/WikiReader) by nsh07, with a new random-topic engine,
on-device relevance and personalization, curated reading lists, and many reading and library
improvements.

## Features

- **Random by topic:** Build on-device libraries for plants, history, physics, music, or any
  category you choose. Random picks come from those libraries, not from all of Wikipedia.
- **Relevance that improves:** Title, short description and article introductions are scored
  against your topic. Like/dislike votes and reading behaviour gently refine future picks. All
  learning stays on the device.
- **Never a dead end:** If a topic library is still building, exhausted, or temporarily has no
  usable article, the app falls back to a general Wikipedia random article instead of showing an
  error page.
- **Feed and search:** Read the featured article, trending articles, the picture of the day, the
  news and “on this day”, or search Wikipedia in your chosen language.
- **Saved and History:** Keep articles for later, sort by date or topic, and switch between list
  and 2/3/4-column grid layouts.
- **Curated picks:** Follow readers whose taste matches yours and open their curated Wikipedia
  articles. The list is hosted on GitHub, so it can grow without an app update.
- **Reading comfort:** Wikitext rendering by default (HTML is optional), open sections by default,
  adjustable font size, immersive mode, a right-edge tab switcher, and a searchable Settings
  screen.
- **Languages:** Choose from 300+ Wikipedia languages. The app language and the Wikipedia
  language are separate settings, and article links keep the language of the article they came
  from.
- **Cladograms:** Phylogenetic trees in plant and biology articles render as trees, in both
  wikitext and HTML modes.
- **Better tables:** Tables stretch to fit when they are narrow, scroll sideways when they are
  wide, keep a readable cell width, highlight the header row and re-parse when the theme or font
  size changes.
- **Large screens and foldables:** The reading column stays centred at a comfortable width on
  tablets, desktop windows and unfolded foldables, the navigation rail adapts to the window size,
  and tables get more columns before they need scrolling.
- **Material 3 Expressive:** Light/dark themes, dynamic Material You colours, and smooth
  animations.

## Download

- **GitHub Releases:** Download the latest APK from
  [github.com/PatrickJamesDev/WikiRandomizer-P/releases](https://github.com/PatrickJamesDev/WikiRandomizer-P/releases).
- **Obtainium:** Add the repository URL to
  [Obtainium](https://obtainium.imranr.dev/) to receive updates directly from GitHub releases.

## Building from source

### Android

- **Windows:** run `build_windows.bat`, `build_windows.bat install`, `build_windows.bat release`
  or `build_windows.bat releaseinstall`.
- **macOS / Linux:** run `./build_android.sh`, `./build_android.sh install`,
  `./build_android.sh release` or `./build_android.sh releaseinstall`.

Both scripts find a JDK and the Android SDK, write `local.properties` when needed, and keep
`build_log.txt` / `build_errors.txt` for troubleshooting. You need JDK 17+ and Android SDK
platform 36.

### iOS

The app has a Kotlin Multiplatform core (`shared`) consumed by the Android app and by a native
SwiftUI iOS shell. On macOS with Xcode:

```bash
./build_ios.sh              # install tools, build, install and launch in the simulator
./build_ios.sh device       # signed device build (set DEVELOPMENT_TEAM=ABCDE12345)
```

See [BUILD_IOS.md](BUILD_IOS.md) for the full prerequisites, manual Xcode workflow and
troubleshooting. iOS binaries must be built on macOS + Xcode; Linux and Windows can build the
Android APK only.

### Web app (PWA)

The repository also contains an installable web app in [`pwa/`](pwa/) that shares the Kotlin core
compiled to WebAssembly. Build it and start a local preview:

```bash
./build_pwa.sh serve
```

Then open <http://localhost:8080>. The app shell, visited articles, API responses and images are
cached for offline reading, and it can be installed from the browser menu or Settings. A GitHub
Actions workflow deploys it to GitHub Pages when Pages is enabled for the repository.

See [BUILD_PWA.md](BUILD_PWA.md) for build, deployment and testing details.

## Curator feed

The **Curators** screen reads a public JSON file from the repository root:

```text
https://raw.githubusercontent.com/PatrickJamesDev/WikiRandomizer-P/main/curators.json
```

If GitHub cannot be reached, the app shows the bundled copy in
`app/src/main/assets/curators.json` and tells you that it is offline. Following a curator only
stores their ID in the local `followed-curators` preference; no account is required and no
reading data is uploaded.

### `curators.json` format

```json
{
  "version": 1,
  "curators": [
    {
      "id": "your-stable-id",
      "name": "Your name",
      "bio": "One or two sentences about what you like to read.",
      "github": "https://github.com/your-handle",
      "picks": [
        {
          "title": "Athyriaceae",
          "lang": "en",
          "note": "Why this article is worth opening."
        }
      ]
    }
  ]
}
```

- `id` must be stable and unique; it is what the app stores when someone follows you.
- `lang` is the Wikipedia language code used to open the pick (for example `en`, `de`, `ja`).
- `picks` is an ordered list; each pick opens the article in the app.
- Add your entry with a pull request to `curators.json`. Keep the file valid JSON and do not
  remove other curators.

## Translation

The app UI is translated through Android string resources. To help translate
WikiRandomizer-P into your language, open an issue or a pull request at
[github.com/PatrickJamesDev/WikiRandomizer-P](https://github.com/PatrickJamesDev/WikiRandomizer-P)
and include the language code and the translated `strings.xml`.

## Donate

You can support Patrick James's work on GitHub:

[github.com/PatrickJamesDev](https://github.com/PatrickJamesDev)

## Credits

- [WikiReader](https://github.com/nsh07/WikiReader) by nsh07 — the upstream project this fork is
  based on.
- [Wikimedia Foundation](https://wikimediafoundation.org/) and the Wikipedia volunteers,
  contributors and donors for providing Wikipedia.
- [Retrofit 2](https://square.github.io/retrofit/) — REST APIs
- [OkHttp](https://square.github.io/okhttp/) — networking
- [Coil](https://coil-kt.github.io/coil/) — image loading
- [MaterialKolor](https://github.com/jordond/MaterialKolor) — custom colour themes
- [ComposeCharts](https://github.com/ehsannarmani/ComposeCharts) — view-count history graph
- [Latex2Unicode](https://github.com/tomtung/latex2unicode) — math to Unicode conversion

## License

WikiRandomizer-P is free software under the
[GNU General Public License v3.0](https://github.com/PatrickJamesDev/WikiRandomizer-P/blob/main/LICENSE).
