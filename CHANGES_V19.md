# v19 – changes (not compiled or run on a device yet: build and test before relying on it)

1. Hold a picture in an article (body, <gallery>, infobox, lead picture, Commons strip) -> menu with "Download image", no full-screen view needed.
2. Hold a link for 1 s (LINK_HOLD_MILLIS in ParsedBodyText.kt) -> article goes into the tabs. A pill with the tab count and a check mark slides in from the right edge. The text message ("Added to your tabs...") is shown only the very first time ever (pref ui_hints/tab_added_notice_shown); after that only the animation. Holding a link that is already a tab plays the animation without the check.
3. Tabs panel: the drag handler was attached to the sheet that the same drag moves (finger position relative to a moving node => stutter). It now sits on a stationary wrapper, like ModalNavigationDrawer on the left; the fling behaviour is remembered.
4. Full-screen viewer = one gallery for the whole article (lead picture, body pictures, <gallery> pictures, Commons strip), swipe first -> last. New files: ui/image/ArticleGallery.kt, ui/image/FullScreenGallery.kt.
5. Caption of the current picture at the bottom of the viewer (hides together with the bars on tap).
6. Preloading: first 3 article pictures (lead first) are loaded at full-screen size immediately; Commons strip: every thumbnail cached, first 3 at view size; the viewer also warms the neighbouring pictures.
7. Small pictures in the viewer are scaled up to the largest size that fits the screen, aspect ratio untouched.
8. App icon: tools/set_icon.py installs your own PNG (all densities, adaptive icon, store icon).
9. Tutorial: practice cards removed; new step "Your tabs on the right" after "Slide in the menu".
10. Settings -> Random topics -> "Your article library": no longer stuck on "Loading…" (stats were only loaded while no build was running, and a read error was shown as Loading forever). Now loaded always; an error shows "Could not read the library. Tap to try again." and is written to the debug log.
