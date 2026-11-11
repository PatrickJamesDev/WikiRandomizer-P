# WikiRandomizer-P Privacy Policy

**Effective date:** 11 November 2026

WikiRandomizer-P ("the app") is an independent, offline-first Wikipedia reader made by
Patrick James. This policy explains exactly what the app does with your information. It is
short because the app is deliberately simple: there is no advertising, no analytics and no
tracking. An account exists only if you choose to turn on optional cloud sync.

## What the app stores on your device

Everything the app remembers stays in its private storage on your phone or tablet. If you turn on
optional cloud sync, a copy of your reading history is also stored in your Firebase account.

- articles you open, your reading history, saved articles and highlights;
- your settings (language, theme, fonts, topics, reading options);
- optional reading statistics (articles read, time spent, streaks);
- the optional supporter/subscription flag after a purchase;
- an on-device debug log, only when you enable debug logging in Settings.

You can clear this at any time by clearing the app's storage in Android Settings or by
uninstalling the app. The app also offers backup and restore, which writes a file only where
you choose.

## Network requests

The app only contacts the network to fetch content you asked for:

- **Wikipedia / Wikimedia projects** (`*.wikipedia.org`, `*.wiktionary.org`,
  `commons.wikimedia.org`, `upload.wikimedia.org`, `www.wikidata.org`) to download articles,
  images, definitions and gallery data.
- **GitHub** (`api.github.com`, `raw.githubusercontent.com`, `github.com`) to check for app
  updates and to load the optional curator list.
- **Google Play Billing** when you choose to open the donation screen. Google processes the
  payment; the app never sees or stores your card details.
- **Google Firebase (Authentication and Firestore)** only if you turn on optional cloud sync and
  sign in. Firebase processes your email address and stores the reading-history records described
  below under your account.

Like any web request, these services receive standard technical information such as your IP
address, the requested URL and a user-agent string. The app does not add advertising IDs,
analytics identifiers or any other tracking data. The app's user-agent identifies the app and
version only.

If you use Kiwix/ZIM files, those are read locally and need no network access.

## Optional cloud sync

Cloud sync is off by default. If you enable it, you create or sign in to a Firebase account with an
email address and password. The app uploads a gzip-compressed snapshot of your reading history:
article title, language, timestamp, short description, thumbnail URL and topic. It does not upload
the article text, your saved notes, highlights, vocabulary or settings.

Firestore security rules allow only the signed-in owner to read or write their own snapshot. The
data is used only to merge history between your devices. You can sign out at any time, or choose
**Delete cloud copy** in **Settings → Sync account** to remove the Firebase copy while keeping the
history on this device. Signing out does not delete the cloud copy by itself. If another device is
still signed in with sync enabled, it can upload its local history again; turn sync off there too.
To remove the Firebase account and its email address, use the contact link below.

## Donations and subscriptions

One-time donations are **optional** and are **not** donations to the Wikimedia Foundation. The
optional `sync_subscription` purchase is a paid subscription for cloud sync; neither purchase is
required to read articles. Payments are handled entirely by Google Play. The app receives only the
purchase confirmation needed to unlock supporter features or sync; it does not receive your name,
address or payment details. Manage or cancel a subscription in Google Play.

## Children

The app does not knowingly collect personal information from anyone, including children. Cloud
sync is optional and requires an email address; the app contains no ads and no social features.

## Permissions

- **Internet / network state:** to download articles and images, check for updates, and (only if
  you enable cloud sync) contact Firebase.
- **Foreground service (data sync):** to keep a large download or article prefetch running
  while you use the app.
- **Notifications:** to show progress for long downloads, only if you allow notifications.
- **Billing:** to offer optional donations and the cloud-sync subscription through Google Play.
- **Request install packages:** only used by the built-in APK updater when you explicitly
  download an update from GitHub. It is not used for anything else.

## Third-party services

- Wikipedia and Wikimedia projects, governed by the Wikimedia Foundation's policies.
- GitHub, governed by GitHub's privacy statement.
- Google Play Billing and Google Firebase, governed by Google's privacy policy.

The app is not affiliated with or endorsed by the Wikimedia Foundation.

## Changes

If this policy changes, the new version will be published in this repository with a new
effective date.

## Contact

Questions or requests: open an issue at
<https://github.com/PatrickJamesDev/WikiRandomizer-P/issues>.
