# Google Play launch checklist — WikiRandomizer-P

Package name: `com.wikirandomizerp.app`
Version name/code: `BuildConfig.VERSION_NAME` / `versionCode` in `app/build.gradle.kts`

## 1. Signing and release build

1. Create an upload keystore once and keep it outside the repository:
   ```bash
   keytool -genkeypair -v -keystore ~/wikirandomizerp-upload.jks \
     -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Add a `keystore.properties` file **outside** version control, or configure the
   `signingConfigs` block to read from environment variables / CI secrets.
3. Build an Android App Bundle:
   ```bash
   JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:bundleRelease
   ```
4. Output: `app/build/outputs/bundle/release/app-release.aab`.
5. Enrol in **Play App Signing** (recommended). Never commit the keystore or its passwords.

## 2. Store listing assets

- [ ] App name: **WikiRandomizer-P** (max 30 characters).
- [ ] Short description (max 80 characters), e.g. "A calm, random Wikipedia reader. Offline-friendly, ad-free."
- [ ] Full description: explain random articles, offline preloading, topics, highlights,
      reading stats and the optional donation. State that the app is independent and not
      affiliated with Wikimedia.
- [ ] App icon: 512 × 512 PNG, no transparency. Source: `ic_launcher-playstore.png`.
- [ ] Feature graphic: 1024 × 500 PNG/JPG.
- [ ] At least 2 phone screenshots (16:9 or 9:16), ideally 4–8.
- [ ] Optional 7-inch and 10-inch tablet screenshots.
- [ ] Category: **Books & Reference** (or News & Magazines).
- [ ] Contact email and website/GitHub URL.
- [ ] Privacy policy URL: host `PRIVACY_POLICY.md` (GitHub Pages, GitLab Pages or your site).

## 3. App content / policy declarations

- [ ] **Privacy policy:** public URL, linked in Play Console and inside the app (About/Settings).
- [ ] **Data safety:** declare "No data collected" and "No data shared" — the app has no
      analytics, ads or account. Network requests to Wikipedia/GitHub are user-initiated and
      do not collect data for the developer. Donations are processed by Google Play.
- [ ] **Ads:** "No ads".
- [ ] **Content rating:** complete the questionnaire. The app shows user-selected Wikipedia
      content and may link to external sites; answer accordingly.
- [ ] **Target audience:** 13+ (or 18+) unless you explicitly design for children.
- [ ] **Government apps:** not a government app.
- [ ] **Financial features:** not a financial app. Donations are optional support for the
      developer, not charity or investment.
- [ ] **Health:** not a health app.

## 4. Permissions to review

Declared in `app/src/main/AndroidManifest.xml`:

- `INTERNET` — required for Wikipedia and update checks.
- `com.android.vending.BILLING` — required for optional donations.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` — long downloads/prefetch.
- `POST_NOTIFICATIONS` — download progress notifications (runtime permission on Android 13+).
- `REQUEST_INSTALL_PACKAGES` — only for the built-in APK updater from GitHub.
  - If Play policy review objects, remove the in-app APK installer for the Play build and rely
    on Play updates. Keep the update *check* (which only opens the release page).

## 5. Wikipedia / Wikimedia compliance

- [ ] Attribution: article text and images come from Wikipedia/Wikimedia under **CC BY-SA**.
      Keep the in-app attribution in About and the Wikipedia section.
- [ ] Do not imply endorsement by the Wikimedia Foundation. `aboutIndependence` states this.
- [ ] Donations must not be presented as donations to Wikipedia/Wikimedia. The donation dialog
      states that they support the developer.
- [ ] Do not use the Wikipedia/Wikimedia trademarks as your app icon or app name.

## 6. Donations

See `DONATION_SETUP.md`. Summary:

- [ ] Create in-app products `donation_small`, `donation_medium`, `donation_large`
      (consumable) and `supporter` (non-consumable).
- [ ] Add license testers and test purchases on an internal testing track.
- [ ] Keep donations optional, skippable and never blocking content.
- [ ] Keep the fallback to GitHub when Play Billing is unavailable.

## 7. Pre-launch testing

- [ ] Install on a clean device/emulator without the Play Store to confirm the app still works
      and the donation dialog shows the GitHub fallback.
- [ ] Install from an internal testing track to confirm real prices load.
- [ ] Check the offline prefetch: after reading, open the app in airplane mode and confirm at
      least 5 preloaded articles are available.
- [ ] Verify 10 taps on the About header in 5 seconds unlocks Developer options.
- [ ] Verify the debug "Reset donation state" returns the supporter badge to off.
- [ ] Run `./gradlew :app:lintRelease` and fix release-blocking issues.
- [ ] Test backup/restore, language switching and dark theme.

## 8. Release tracks

1. **Internal testing** — you and license testers.
2. **Closed testing** — 12+ testers for 14 days if you are a new personal developer account.
3. **Open testing** — optional.
4. **Production** — staged rollout (10% → 50% → 100%).

## 9. Post-launch

- [ ] Watch Android vitals (crash-free rate, ANRs).
- [ ] Respond to reviews, especially billing issues.
- [ ] Keep `PRIVACY_POLICY.md`, data safety answers and the donation product list in sync with
      the app.
