# Optional donations — Google Play setup

The app uses **Google Play Billing Library 8.0.0** for optional support. There are no ads and no
tracking. One-time donations support the developer (Patrick James); an optional
`sync_subscription` pays for cross-device cloud sync. Neither is a donation to the Wikimedia
Foundation.

## Product IDs

Create these **in-app products** in Play Console → Monetise → Products → In-app products.
The IDs must match exactly (they are compiled into the app):

| Product ID        | Type          | Suggested price | Behaviour                              |
|-------------------|---------------|-----------------|----------------------------------------|
| `donation_small`  | Consumable    | €1.99 / $1.99   | Can be bought again and again          |
| `donation_medium` | Consumable    | €4.99 / $4.99   | Can be bought again and again          |
| `donation_large`  | Consumable    | €9.99 / $9.99   | Can be bought again and again          |
| `supporter`       | Non-consumable| €9.99 / $9.99   | Acknowledged once, unlocks the badge and cloud sync |

Create one **subscription** in Play Console → Monetise → Products → Subscriptions. It must have an
active base plan and at least one offer:

| Product ID          | Type          | Suggested price | Behaviour                              |
|---------------------|---------------|-----------------|----------------------------------------|
| `sync_subscription` | Auto-renewing | €0.99 / month   | Acknowledged while active; unlocks cloud sync |

The consumable products are consumed immediately after purchase so the same tier can be bought
again later. The `supporter` product is acknowledged but never consumed, so it stays owned.

## Steps in Play Console

1. Upload a signed build to **Internal testing** first. Billing products are only queryable from
   an installed build whose application ID and signing key match the Play listing.
2. Go to **Monetise → Products → In-app products** and create the four one-time products above.
3. Go to **Monetise → Products → Subscriptions**, create `sync_subscription` and activate its base
   plan and offer.
4. Give each product a title and description. The app displays the Play-provided title and
   formatted price, so localisation is automatic.
5. Activate every product. Inactive products do not appear in `queryProductDetailsAsync`.
6. Under **Settings → License testing**, add the Google accounts that will test purchases.
   License testers are not charged.
7. Install the build from the internal testing link on the test device, open **About → Support
   the app**, and complete a test purchase.
8. Verify the supporter badge appears, then use the hidden developer menu
   (About header, ten taps in ten seconds) → **Reset donation state** if you need to re-test.

## How the app behaves

- `DonationBilling.start()` connects lazily when the donation UI opens, or at launch when Firebase
  is configured so an active `sync_subscription` can be verified.
- `queryProductDetailsAsync` requests `donation_small`, `donation_medium`, `donation_large` and
  `supporter` in one call, plus `sync_subscription` as a separate subscription query. Only active
  products are returned.
- If Play Billing is unavailable (sideloaded APK, emulator, unsupported country, no Play Store),
  the dialog shows a short reason and a **Support on GitHub** fallback. The app never crashes.
- Purchases of a consumable are consumed; `supporter` is acknowledged (if needed) and then
  stored locally as a boolean in the private `donations` preferences file. An active
  `sync_subscription` is acknowledged and cached; if Play reports it inactive, the cache is cleared.
- Cloud sync requires an active subscription, a one-time `supporter` purchase, or developer mode.
  It also requires a Firebase account; without Firebase build properties the feature is disabled
  and the app remains fully usable.
- The optional milestone prompt is **off by default**. When the user enables
  **Settings → About → Donation prompts**, the app may show a small prompt after 100, 300, 600,
  1000 and 2000 opened articles. Supporters never see it.
- Users can hide the prompt permanently by leaving the setting off or by donating.

## Code map

- `app/src/main/java/com/wikirandomizerp/app/data/billing/DonationBilling.kt` — billing client,
  product/subscription query, purchase/consume/acknowledge, supporter/subscription flags, debug
  helpers.
- `app/src/main/java/com/wikirandomizerp/app/data/sync/LocalSyncStore.kt` — merge/sync gating,
  tombstone handling and local snapshot.
- `app/src/main/java/com/wikirandomizerp/app/data/sync/FirebaseSyncClient.kt` — manual Firebase
  initialization, email/password auth, Firestore read/write/delete.
- `app/src/main/java/com/wikirandomizerp/app/data/sync/CloudSyncScheduler.kt` — hourly WorkManager
  sync with network constraints.
- `app/src/main/java/com/wikirandomizerp/app/ui/donation/DonationDialog.kt` — support dialog.
- `app/src/main/java/com/wikirandomizerp/app/ui/aboutScreen/AboutScreen.kt` — About entry.
- `app/src/main/java/com/wikirandomizerp/app/ui/settingsScreen/SettingsScreen.kt` — support row
  and the donation-prompt switch.
- `app/src/main/java/com/wikirandomizerp/app/ui/debugScreen/DebugScreen.kt` — hidden test menu.
- `app/src/main/java/com/wikirandomizerp/app/ui/homeScreen/viewModel/HomeScreenViewModel.kt` —
  article-open counting and milestone prompt.

## Policy notes

- One-time donations must remain **optional**. Cloud sync is a paid feature, never a requirement
  for reading articles or using the rest of the app.
- Do not describe `sync_subscription` as a donation. It is a paid subscription for a feature.
- Do not describe donations as charitable or as supporting Wikipedia/Wikimedia.
- Do not use Google Play's donation features for a charity unless you are a registered charity.
  This is a developer-support product, not a charity.
- Keep prices inclusive of tax as configured by Play. Google handles tax and receipts.
